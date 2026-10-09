import base64
import binascii
import logging
from functools import lru_cache

import numpy as np
from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, Field
from google.auth import exceptions as auth_errors
from google.genai import errors as genai_errors

from app.answerer import Answerer, GeminiAnswerer
from app.config import get_settings
from app.drafts import GeminiReadmeDrafter, ReadmeDrafter
from app.embeddings import Embedder, GeminiEmbedder
from app.explainer import Explainer, GeminiExplainer
from app.gemini import build_clients
from app.ranking import cosine_similarities
from app.websearch import GeminiWebSearcher, WebSearcher
from app import resume as resumes
from app import applykit
from app import atscheck
from app.schemas import (
    AnswerRequest,
    AnswerResponse,
    EmbedRequest,
    EmbedResponse,
    ExplainRequest,
    ExplainResponse,
    RankedRepo,
    RankRequest,
    RankResponse,
    TokenUsage,
    ReadmeRequest,
    ReadmeResponse,
    WebSearchRequest,
    WebSearchResponse,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("repomatch.ai")

app = FastAPI(title="RepoMatch AI service", version="0.1.0")


@lru_cache
def _clients():
    return build_clients(get_settings())


def get_embedder() -> Embedder:
    s = get_settings()
    return GeminiEmbedder(_clients().embed, s.embedding_model, s.embedding_dim)


def get_explainer() -> Explainer:
    s = get_settings()
    return GeminiExplainer(_clients().llm, s.llm_model, s.llm_temperature, s.max_readme_chars_in_prompt)


def get_answerer() -> Answerer:
    s = get_settings()
    return GeminiAnswerer(_clients().llm, s.llm_model, s.llm_temperature)


def get_readme_drafter() -> ReadmeDrafter:
    s = get_settings()
    return GeminiReadmeDrafter(_clients().llm, s.llm_model, s.llm_temperature)


def get_web_searcher() -> WebSearcher:
    s = get_settings()
    return GeminiWebSearcher(_clients().llm, s.web_search_model)


def get_resume_ai() -> resumes.ResumeAi:
    s = get_settings()
    return resumes.GeminiResumeAi(_clients().llm, s.llm_model)


def get_apply_kit_ai() -> applykit.ApplyKitAi:
    s = get_settings()
    return applykit.GeminiApplyKitAi(_clients().llm, s.llm_model)


def get_requirement_ai() -> atscheck.RequirementAi:
    s = get_settings()
    return atscheck.GeminiRequirementAi(_clients().llm, s.llm_model)


def _upstream_error(e: Exception) -> HTTPException:
    if isinstance(e, genai_errors.APIError):
        msg = f"Gemini API error {e.code}: {e.message}"
    elif isinstance(e, auth_errors.GoogleAuthError):
        msg = f"Google credentials problem: {e}"
    else:
        msg = f"{type(e).__name__}: {e}"
    log.warning(msg)
    return HTTPException(status_code=502, detail=msg)


_UPSTREAM_ERRORS = (genai_errors.APIError, auth_errors.GoogleAuthError)


@app.get("/health")
def health():
    s = get_settings()
    return {"status": "ok", "embedding_model": s.embedding_model, "llm_model": s.llm_model}


@app.post("/rank", response_model=RankResponse, response_model_exclude_none=True)
async def rank(req: RankRequest, embedder: Embedder = Depends(get_embedder)) -> RankResponse:
    """Embed the JD and any repos without a usable cached embedding, then rank by cosine similarity."""
    cached = {
        c.id: c.embedding
        for c in req.repos
        if c.embedding is not None and c.embedding_model == embedder.model and len(c.embedding) == embedder.dim
    }
    to_embed = [c for c in req.repos if c.id not in cached]

    try:
        fresh_vectors = await embedder.embed_documents([c.text for c in to_embed]) if to_embed else []
        query = await embedder.embed_query(req.job_description)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e

    fresh = {c.id: v for c, v in zip(to_embed, fresh_vectors)}
    vectors = {**cached, **fresh}
    ids = [c.id for c in req.repos]
    sims = cosine_similarities(np.asarray(query, dtype=np.float32), np.asarray([vectors[i] for i in ids], dtype=np.float32))

    results = [
        RankedRepo(id=i, similarity=round(float(s), 6), embedding=fresh.get(i))
        for i, s in sorted(zip(ids, sims), key=lambda p: p[1], reverse=True)
    ]
    log.info("ranked %d repos (%d embedded, %d cached)", len(ids), len(fresh), len(cached))
    return RankResponse(embedding_model=embedder.model, results=results)


@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest, embedder: Embedder = Depends(get_embedder)) -> EmbedResponse:
    """General embedding endpoint — used to index repository code for 'ask your codebase'."""
    try:
        vectors = (
            await embedder.embed_documents(req.texts)
            if req.kind == "document"
            else [await embedder.embed_query(req.texts[0])]
        )
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    return EmbedResponse(model=embedder.model, vectors=vectors)


@app.post("/answer", response_model=AnswerResponse)
async def answer(req: AnswerRequest, answerer: Answerer = Depends(get_answerer)) -> AnswerResponse:
    """Answers a question about a repository from retrieved code chunks, with verified citations."""
    try:
        return await answerer.answer(req.repo, req.question, req.chunks)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e


@app.post("/readme", response_model=ReadmeResponse)
async def readme(req: ReadmeRequest, drafter: ReadmeDrafter = Depends(get_readme_drafter)) -> ReadmeResponse:
    """Drafts a README from the repository's own source — nothing invented."""
    try:
        return await drafter.draft(req.repo, req.description, req.languages, req.chunks)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e


@app.post("/explain", response_model=ExplainResponse)
async def explain(req: ExplainRequest, explainer: Explainer = Depends(get_explainer)) -> ExplainResponse:
    """Grounded LLM judgement of how well each candidate repo fits the JD."""
    try:
        results, usage = await explainer.explain(req.job_description, req.repos)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    return ExplainResponse(llm_model=explainer.model, results=results, usage=usage)


@app.post("/websearch", response_model=WebSearchResponse)
async def websearch(req: WebSearchRequest, searcher: WebSearcher = Depends(get_web_searcher)) -> WebSearchResponse:
    """Finds open postings on the web with Gemini grounded in Google Search. Links are verified by the backend."""
    try:
        return await searcher.search(req.query, req.limit)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e


# --- Resume tailoring ---------------------------------------------------------

# Models a person may pick for tailoring. Pro reasons more carefully; Flash is faster and cheaper.
TAILOR_MODELS = {"pro": "gemini-3.1-pro-preview", "flash": "gemini-2.5-flash"}


class ExtractRequest(BaseModel):
    filename: str = Field(max_length=300)
    data_base64: str = Field(max_length=8_000_000)


class ExtractResponse(BaseModel):
    text: str
    kind: str


@app.post("/resume/extract", response_model=ExtractResponse)
def resume_extract(req: ExtractRequest) -> ExtractResponse:
    """Reads text out of an uploaded PDF, DOCX, .tex or text file, keeping bold and links. No model involved."""
    try:
        data = base64.b64decode(req.data_base64, validate=True)
    except (binascii.Error, ValueError) as e:
        raise HTTPException(status_code=400, detail="File isn't valid base64") from e
    try:
        text, kind = resumes.extract_text(req.filename, data)
    except Exception as e:  # malformed PDFs/DOCX raise library-specific errors
        raise HTTPException(status_code=422, detail=f"Couldn't read {req.filename}: {type(e).__name__}") from e
    if len(text.strip()) < 80:
        raise HTTPException(status_code=422, detail="Found almost no text — is this a scanned image? Paste the text instead.")
    return ExtractResponse(text=text, kind=kind)


class StructureRequest(BaseModel):
    text: str = Field(min_length=80, max_length=80_000)


class StructureResponse(BaseModel):
    resume: resumes.Resume
    model: str
    usage: TokenUsage | None = None


@app.post("/resume/structure", response_model=StructureResponse)
async def resume_structure(req: StructureRequest, ai: resumes.ResumeAi = Depends(get_resume_ai)) -> StructureResponse:
    """Turns resume text (or LaTeX) into sections, items and bullets, copied verbatim with bold and links."""
    try:
        resume, usage = await ai.structure(req.text)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    return StructureResponse(resume=resume, model=ai.model, usage=usage)


class TailorRequest(BaseModel):
    resume: resumes.Resume
    job_title: str = Field(default="", max_length=300)
    job_description: str = Field(min_length=40, max_length=40_000)
    # The person's most job-relevant repositories (evidence) and all of them (for adding missing links).
    repos: list[resumes.RepoEvidence] = Field(default_factory=list, max_length=10)
    link_repos: list[resumes.RepoEvidence] = Field(default_factory=list, max_length=200)
    instructions: str = Field(default="", max_length=2000)
    one_page: bool = True
    model: str = Field(default="pro", pattern="^(pro|flash)$")


class TailorResponse(BaseModel):
    summary: str
    edits: list[resumes.CheckedEdit]
    swaps: list[resumes.CheckedSwap]
    link_additions: list[resumes.LinkAddition]
    reorder: list[resumes.SectionOrder]
    matched_keywords: list[str]
    missing_keywords: list[str]
    dropped_edits: int
    repos_used: list[str]
    model: str
    usage: TokenUsage | None = None


@app.post("/resume/tailor", response_model=TailorResponse)
async def resume_tailor(req: TailorRequest, ai: resumes.ResumeAi = Depends(get_resume_ai)) -> TailorResponse:
    """Proposes edits for one job from the resume and the person's repos; every edit is checked before it's returned."""
    try:
        plan, usage, used = await ai.tailor(req.resume, req.job_description, req.job_title, req.repos,
                                            req.instructions, req.one_page, TAILOR_MODELS[req.model])
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    edits, swaps = resumes.check_plan(req.resume, plan, req.job_description, req.repos)
    links = resumes.link_additions(req.resume, req.link_repos or req.repos)
    return TailorResponse(
        summary=plan.summary,
        edits=edits,
        swaps=swaps,
        link_additions=links,
        reorder=plan.reorder,
        matched_keywords=plan.matched_keywords[:25],
        missing_keywords=plan.missing_keywords[:25],
        dropped_edits=max(0, len(plan.edits) - len(edits)),
        repos_used=[r.name for r in req.repos],
        model=used,
        usage=usage,
    )


class BuildRequest(BaseModel):
    resume: resumes.Resume
    edits: list[resumes.TailorEdit] = []
    reorder: list[resumes.SectionOrder] = []
    swaps: list[resumes.ProjectSwap] = []
    link_additions: list[resumes.LinkAddition] = []
    # When the person uploaded their own LaTeX, edits are applied to it so their design is kept.
    source_tex: str | None = Field(default=None, max_length=200_000)
    one_page: bool = True
    compile: bool = True


class BuildResponse(BaseModel):
    latex: str
    pdf_base64: str | None = None
    compiled: bool
    compile_log: str = ""
    pages: int = 0
    # How much the template was tightened to fit one page (0 = not at all).
    compact_level: int = 0
    # Edits that couldn't be located in the person's own .tex (they're in the text, but the markup differs).
    missed_edits: list[int] = []
    used_own_template: bool


@app.post("/resume/build", response_model=BuildResponse)
async def resume_build(req: BuildRequest) -> BuildResponse:
    """Applies accepted changes, renders LaTeX (the person's own, or the template) and compiles a PDF.

    With one_page, a template resume that runs over is re-rendered tighter (smaller type, margins, spacing) until it
    fits or the tightest setting is reached."""
    missed: list[int] = []
    own = bool(req.source_tex and r"\begin{document}" in req.source_tex)
    if own:
        latex, missed = resumes.apply_to_tex(req.source_tex, req.edits)
        if not req.compile:
            return BuildResponse(latex=latex, compiled=False, missed_edits=missed, used_own_template=True)
        result = await resumes.compile_pdf(latex)
        level = 0
    else:
        tailored = resumes.apply_to_resume(req.resume, req.edits, req.reorder, req.swaps, req.link_additions)
        level = 0
        latex = resumes.render_latex(tailored, level)
        if not req.compile:
            return BuildResponse(latex=latex, compiled=False, used_own_template=False)
        result = await resumes.compile_pdf(latex)
        while req.one_page and result.ok and result.pages > 1 and level < 2:
            level += 1
            latex = resumes.render_latex(tailored, level)
            result = await resumes.compile_pdf(latex)
    return BuildResponse(
        latex=latex,
        pdf_base64=base64.b64encode(result.pdf).decode() if result.pdf else None,
        compiled=result.ok,
        compile_log="" if result.ok else result.log,
        pages=result.pages,
        compact_level=level,
        missed_edits=missed,
        used_own_template=own,
    )


class ApplyKitRequest(BaseModel):
    job_title: str = Field(default="", max_length=300)
    company: str = Field(default="", max_length=300)
    job_description: str = Field(default="", max_length=40_000)
    # The person's own details, as plain text ("Available from June 2027. No sponsorship needed.").
    profile: str = Field(default="", max_length=4_000)
    resume: resumes.Resume
    repos: list[resumes.RepoEvidence] = Field(default_factory=list, max_length=8)
    questions: list[str] = Field(default_factory=list, max_length=12)
    model: str = Field(default="pro", pattern="^(pro|flash)$")


class ApplyKitResponse(BaseModel):
    answers: list[applykit.CheckedAnswer]
    model: str
    usage: TokenUsage | None = None


@app.post("/apply/answers", response_model=ApplyKitResponse)
async def apply_answers(req: ApplyKitRequest, ai: applykit.ApplyKitAi = Depends(get_apply_kit_ai)) -> ApplyKitResponse:
    """Drafts this job's application answers from the resume, the repos and the person's own details."""
    try:
        plan, usage, used = await ai.answers(req.job_title, req.company, req.job_description, req.profile, req.resume,
                                             req.repos, req.questions, TAILOR_MODELS[req.model])
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    return ApplyKitResponse(answers=applykit.check_answers(plan, req.resume, req.repos, req.profile), model=used, usage=usage)


class AtsCheckRequest(BaseModel):
    """The built PDF exactly as it will be sent, and the ad it answers."""

    pdf_base64: str = Field(min_length=100)
    job_title: str = Field(default="", max_length=300)
    job_description: str = Field(default="", max_length=40_000)
    model: str = Field(default="flash", pattern="^(pro|flash)$")


class AtsCheckResponse(BaseModel):
    parse: atscheck.ParseReport
    coverage: atscheck.Coverage | None = None
    # What was read out of the file, so the person can see a parser's view of their resume.
    extracted: str = ""
    model: str = ""
    usage: TokenUsage | None = None


@app.post("/resume/ats", response_model=AtsCheckResponse)
async def resume_ats(req: AtsCheckRequest,
                     ai: atscheck.RequirementAi = Depends(get_requirement_ai)) -> AtsCheckResponse:
    """Reads the built PDF back like a parser, then checks the ad's requirements against what came out.

    The parse report is deterministic and always returned. The coverage needs the job ad, and one model call to list
    what the ad asks for — the matching itself is plain string work, never a judgement."""
    try:
        pdf = base64.b64decode(req.pdf_base64, validate=True)
    except (binascii.Error, ValueError) as e:
        raise HTTPException(status_code=400, detail=f"pdf_base64 is not valid base64: {e}") from e

    try:
        parse = atscheck.parse_report(pdf)
        extracted = atscheck.extracted_text(pdf)
    except Exception as e:  # a PDF we can't open is a finding, not a server error
        logging.getLogger(__name__).warning("ats parse failed: %s", e)
        raise HTTPException(status_code=400, detail=f"That PDF could not be opened: {e}") from e

    if len(req.job_description.strip()) < 40:
        return AtsCheckResponse(parse=parse, extracted=extracted[:20_000])

    try:
        plan, usage, used = await ai.requirements(req.job_title, req.job_description, TAILOR_MODELS[req.model])
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    return AtsCheckResponse(parse=parse, coverage=atscheck.check_coverage(plan, extracted),
                            extracted=extracted[:20_000], model=used, usage=usage)
