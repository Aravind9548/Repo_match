import logging
from functools import lru_cache

import numpy as np
from fastapi import Depends, FastAPI, HTTPException
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
