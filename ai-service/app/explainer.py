"""Grounded "why this repo fits the JD" explanations (the RAG step).

The LLM only sees content we retrieved from the repo itself, is told to cite nothing else,
and every evidence snippet it returns is verified against that content before we pass it on.
"""

import logging
import re
from typing import Protocol

from google import genai
from google.genai import types
from pydantic import BaseModel, Field

from app.schemas import ExplainCandidate, RepoExplanation, TokenUsage

log = logging.getLogger("repomatch.ai")

SYSTEM_INSTRUCTION = """\
You are a staff engineer and technical recruiter helping a candidate decide which of their \
GitHub projects to feature on a resume for one specific job.

Rules:
- Judge each project ONLY from the repository content provided. Never invent features, \
technologies, metrics, users or outcomes that the content does not state.
- If a repository has little content (no README, vague description), say so and score it conservatively.
- Be specific: name the concrete technologies/features in the repo and the job requirement each one maps to.
- Write reasons in plain, confident resume-coach language, addressed to the candidate ("This project shows...").
"""

PROMPT_TEMPLATE = """\
# Job description
{jd}

# Candidate repositories
{repos}

# Task
For EVERY repository above, return:
- id: the repository id exactly as given.
- fit_score: integer 0-100 for how strongly this project evidences the job's core requirements.
  90-100 directly demonstrates several core requirements; 70-89 strong overlap with the core stack or domain;
  40-69 partial or transferable overlap; 0-39 little relevant evidence.
- reason: 1-2 sentences on why it fits (or why it is weak), grounded in the repository content.
- matched_requirements: up to 5 short phrases naming the job requirements this repo evidences.
- evidence: up to 3 short snippets (max 12 words each) copied VERBATIM from the repository content \
that support the reason.
"""


class _LlmRepoFit(BaseModel):
    id: int
    fit_score: int = Field(description="0-100")
    reason: str
    matched_requirements: list[str]
    evidence: list[str]


class _LlmOutput(BaseModel):
    results: list[_LlmRepoFit]


class Explainer(Protocol):
    model: str

    async def explain(
        self, jd: str, repos: list[ExplainCandidate]
    ) -> tuple[list[RepoExplanation], TokenUsage | None]: ...


def _format_repo(r: ExplainCandidate, max_readme_chars: int) -> str:
    lines = [f'<repository id="{r.id}">', f"Name: {r.name}"]
    if r.description:
        lines.append(f"Description: {r.description}")
    if r.languages:
        lines.append(f"Languages: {', '.join(r.languages)}")
    if r.topics:
        lines.append(f"Topics: {', '.join(r.topics)}")
    readme = (r.readme or "").strip()
    lines.append("README:\n" + (readme[:max_readme_chars] if readme else "(no README)"))
    lines.append("</repository>")
    return "\n".join(lines)


def build_prompt(jd: str, repos: list[ExplainCandidate], max_readme_chars: int) -> str:
    return PROMPT_TEMPLATE.format(
        jd=jd.strip(), repos="\n\n".join(_format_repo(r, max_readme_chars) for r in repos)
    )


_NON_WORD = re.compile(r"[^a-z0-9+#./-]+")


def _normalize(text: str) -> str:
    return _NON_WORD.sub(" ", text.lower()).strip()


def _repo_corpus(r: ExplainCandidate) -> str:
    parts = [r.name, r.description or "", " ".join(r.languages), " ".join(r.topics), r.readme or ""]
    return _normalize(" ".join(parts))


def postprocess(output: _LlmOutput, repos: list[ExplainCandidate]) -> list[RepoExplanation]:
    """Drop results for unknown ids and any evidence snippet that isn't actually in the repo."""
    by_id = {r.id: r for r in repos}
    results: list[RepoExplanation] = []
    seen: set[int] = set()
    for fit in output.results:
        repo = by_id.get(fit.id)
        if repo is None or fit.id in seen:
            continue
        seen.add(fit.id)
        corpus = _repo_corpus(repo)
        evidence = [e.strip() for e in fit.evidence if _normalize(e) and _normalize(e) in corpus][:3]
        results.append(
            RepoExplanation(
                id=fit.id,
                fit_score=max(0, min(100, fit.fit_score)),
                reason=fit.reason.strip(),
                matched_requirements=[m.strip() for m in fit.matched_requirements if m.strip()][:5],
                evidence=evidence,
            )
        )
    return results


class GeminiExplainer:
    def __init__(self, client: genai.Client, model: str, temperature: float, max_readme_chars: int):
        self._client = client
        self.model = model
        self._temperature = temperature
        self._max_readme_chars = max_readme_chars

    async def explain(
        self, jd: str, repos: list[ExplainCandidate]
    ) -> tuple[list[RepoExplanation], TokenUsage | None]:
        resp = await self._client.aio.models.generate_content(
            model=self.model,
            contents=build_prompt(jd, repos, self._max_readme_chars),
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                temperature=self._temperature,
                response_mime_type="application/json",
                response_schema=_LlmOutput,
            ),
        )
        parsed = resp.parsed if isinstance(resp.parsed, _LlmOutput) else _LlmOutput.model_validate_json(resp.text)
        return postprocess(parsed, repos), _usage(resp)


def _usage(resp: types.GenerateContentResponse) -> TokenUsage | None:
    u = resp.usage_metadata
    if u is None:
        return None
    usage = TokenUsage(
        prompt_tokens=u.prompt_token_count or 0,
        output_tokens=u.candidates_token_count or 0,
        thinking_tokens=u.thoughts_token_count or 0,
    )
    log.info("explain usage: %s", usage)
    return usage
