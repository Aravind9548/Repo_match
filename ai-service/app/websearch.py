"""Finds job postings on the open web with Gemini grounded in live Google Search.

The feed covers the boards we sync; this reaches everything else (company career pages, Naukri-style portals,
LinkedIn listings) on demand. The model is told to report only postings it actually saw in the search results,
and the backend then opens every link before showing it — a search model's URLs are not trusted on their own.
"""

import json
import logging
import re
from typing import Protocol

from google import genai
from google.genai import types

from app.schemas import TokenUsage, WebJob, WebSearchResponse, WebSource

log = logging.getLogger("repomatch.ai")

PROMPT = """\
Use Google Search to find job postings that are open right now and match this request:

  {query}

Search several ways (the company career sites, job boards, and "careers" pages) so you find individual postings, \
not articles about hiring. Prefer postings published in the last 60 days.

Return ONLY a JSON array, no prose, with up to {limit} items shaped like:
[{{"title": "...", "company": "...", "location": "...", "url": "...", "posted": "..."}}]

Rules:
- Every item must be a single job posting you saw in the search results. Never invent a posting, company or URL.
- "url" is the page for that one posting (or the company's careers page listing it), exactly as found.
- Leave "location" or "posted" empty when the result doesn't say.
- If you find nothing that fits, return [].
"""


class WebSearcher(Protocol):
    model: str

    async def search(self, query: str, limit: int) -> WebSearchResponse: ...


def parse_jobs(text: str) -> list[WebJob]:
    """Pulls the JSON array out of the reply, tolerating code fences and stray prose around it."""
    match = re.search(r"\[.*\]", text or "", re.DOTALL)
    if not match:
        return []
    try:
        raw = json.loads(match.group(0))
    except json.JSONDecodeError:
        return []
    jobs: list[WebJob] = []
    seen: set[str] = set()
    for item in raw if isinstance(raw, list) else []:
        if not isinstance(item, dict):
            continue
        url = str(item.get("url") or "").strip()
        title = str(item.get("title") or "").strip()
        if not url.startswith("http") or not title or url in seen:
            continue
        seen.add(url)
        jobs.append(
            WebJob(
                title=title[:300],
                company=str(item.get("company") or "").strip()[:200],
                location=str(item.get("location") or "").strip()[:200],
                url=url[:2000],
                posted=str(item.get("posted") or "").strip()[:60],
            )
        )
    return jobs


class GeminiWebSearcher:
    def __init__(self, client: genai.Client, model: str):
        self._client = client
        self.model = model

    async def search(self, query: str, limit: int) -> WebSearchResponse:
        resp = await self._client.aio.models.generate_content(
            model=self.model,
            contents=PROMPT.format(query=query, limit=limit),
            config=types.GenerateContentConfig(
                tools=[types.Tool(google_search=types.GoogleSearch())],
                temperature=0.1,
            ),
        )
        jobs = parse_jobs(resp.text or "")[:limit]

        # The pages Google actually returned — the evidence behind the answer.
        sources: list[WebSource] = []
        candidate = resp.candidates[0] if resp.candidates else None
        grounding = candidate.grounding_metadata if candidate else None
        for chunk in (grounding.grounding_chunks or []) if grounding else []:
            if chunk.web and chunk.web.uri:
                sources.append(WebSource(title=(chunk.web.title or "")[:300], url=chunk.web.uri))
        queries = list(grounding.web_search_queries or []) if grounding else []

        usage = None
        if resp.usage_metadata:
            usage = TokenUsage(
                prompt_tokens=resp.usage_metadata.prompt_token_count or 0,
                output_tokens=resp.usage_metadata.candidates_token_count or 0,
                thinking_tokens=resp.usage_metadata.thoughts_token_count or 0,
            )
        log.info("web search: %d jobs, %d grounding sources, queries=%s", len(jobs), len(sources), queries)
        return WebSearchResponse(model=self.model, jobs=jobs, sources=sources, search_queries=queries, usage=usage)
