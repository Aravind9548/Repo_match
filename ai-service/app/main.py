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


class ExtractRequest(BaseModel):
    filename: str = Field(max_length=300)
    data_base64: str = Field(max_length=8_000_000)


class ExtractResponse(BaseModel):
    text: str
    kind: str


@app.post("/resume/extract", response_model=ExtractResponse)
def resume_extract(req: ExtractRequest) -> ExtractResponse:
    """Reads text out of an uploaded PDF, DOCX, .tex or text file. No model involved."""
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
    """Turns resume text (or LaTeX) into sections, items and bullets, copied verbatim."""
    try:
        resume, usage = await ai.structure(req.text)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    return StructureResponse(resume=resume, model=ai.model, usage=usage)


class TailorRequest(BaseModel):
    resume: resumes.Resume
    job_title: str = Field(default="", max_length=300)
    job_description: str = Field(min_length=40, max_length=40_000)


class TailorResponse(BaseModel):
    summary: str
    edits: list[resumes.CheckedEdit]
    reorder: list[resumes.SectionOrder]
    matched_keywords: list[str]
    missing_keywords: list[str]
    dropped_edits: int
    model: str
    usage: TokenUsage | None = None


@app.post("/resume/tailor", response_model=TailorResponse)
async def resume_tailor(req: TailorRequest, ai: resumes.ResumeAi = Depends(get_resume_ai)) -> TailorResponse:
    """Proposes edits for one job; every edit is checked against the resume before it's returned."""
    try:
        plan, usage = await ai.tailor(req.resume, req.job_description, req.job_title)
    except _UPSTREAM_ERRORS as e:
        raise _upstream_error(e) from e
    checked = resumes.check_plan(req.resume, plan)
    return TailorResponse(
        summary=plan.summary,
        edits=checked,
        reorder=plan.reorder,
        matched_keywords=plan.matched_keywords[:25],
        missing_keywords=plan.missing_keywords[:25],
        dropped_edits=max(0, len(plan.edits) - len(checked)),
        model=ai.model,
        usage=usage,
    )


class BuildRequest(BaseModel):
    resume: resumes.Resume
    edits: list[resumes.TailorEdit] = []
    reorder: list[resumes.SectionOrder] = []
    # When the person uploaded their own LaTeX, edits are applied to it so their design is kept.
    source_tex: str | None = Field(default=None, max_length=200_000)
    compile: bool = True


class BuildResponse(BaseModel):
    latex: str
    pdf_base64: str | None = None
    compiled: bool
    compile_log: str = ""
    # Edits that couldn't be located in the person's own .tex (they're in the text, but the markup differs).
    missed_edits: list[int] = []
    used_own_template: bool


@app.post("/resume/build", response_model=BuildResponse)
async def resume_build(req: BuildRequest) -> BuildResponse:
    """Applies accepted edits, renders LaTeX (the person's own, or the RepoMatch template) and compiles a PDF."""
    missed: list[int] = []
    own = bool(req.source_tex and r"\begin{document}" in req.source_tex)
    if own:
        latex, missed = resumes.apply_to_tex(req.source_tex, req.edits)
    else:
        latex = resumes.render_latex(resumes.apply_to_resume(req.resume, req.edits, req.reorder))
    if not req.compile:
        return BuildResponse(latex=latex, compiled=False, missed_edits=missed, used_own_template=own)
    result = await resumes.compile_pdf(latex)
    return BuildResponse(
        latex=latex,
        pdf_base64=base64.b64encode(result.pdf).decode() if result.pdf else None,
        compiled=result.ok,
        compile_log="" if result.ok else result.log,
        missed_edits=missed,
        used_own_template=own,
    )
