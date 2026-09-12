"""Answers questions about a repository from retrieved code chunks only.

Same contract as the match explanations: the model sees nothing but the chunks we retrieved,
and every citation it returns is checked against those chunks before the answer is passed on.
"""

import logging
from typing import Protocol

from google import genai
from google.genai import types
from pydantic import BaseModel, Field

from app.schemas import AnswerResponse, Citation, CodeChunk, TokenUsage

log = logging.getLogger("repomatch.ai")

SYSTEM_INSTRUCTION = """\
You answer questions about one specific code repository, for the engineer who wrote it — often \
preparing to discuss it in an interview.

Rules:
- Use ONLY the code excerpts provided. They are the whole of your knowledge about this repository.
- Cite the file and line range for every concrete claim.
- If the excerpts do not contain the answer, say so plainly and set answered to false. Never guess \
at code you cannot see, and never describe a file that was not provided.
- Be concrete and technical: name the functions, classes and files involved. No filler openings.
- Two or three short paragraphs at most; use a short list when the answer is a sequence of steps.
"""

PROMPT = """\
# Repository
{repo}

# Question
{question}

# Code excerpts
{chunks}

Answer the question from these excerpts alone, citing the files and line ranges you used.
"""


class _LlmCitation(BaseModel):
    path: str
    start_line: int
    end_line: int


class _LlmAnswer(BaseModel):
    answer: str
    citations: list[_LlmCitation]
    answered: bool = Field(description="false when the excerpts do not contain the answer")


class Answerer(Protocol):
    model: str

    async def answer(self, repo: str, question: str, chunks: list[CodeChunk]) -> AnswerResponse: ...


def build_prompt(repo: str, question: str, chunks: list[CodeChunk]) -> str:
    blocks = [
        f'<excerpt path="{c.path}" lines="{c.start_line}-{c.end_line}">\n{c.text}\n</excerpt>' for c in chunks
    ]
    return PROMPT.format(repo=repo, question=question.strip(), chunks="\n\n".join(blocks))


def verified_citations(citations: list[_LlmCitation], chunks: list[CodeChunk]) -> list[Citation]:
    """Keep only citations that point at a file we actually sent, clamped to the lines we sent."""
    spans: dict[str, list[tuple[int, int]]] = {}
    for c in chunks:
        spans.setdefault(c.path, []).append((c.start_line, c.end_line))

    out: list[Citation] = []
    seen: set[tuple[str, int, int]] = set()
    for cite in citations:
        ranges = spans.get(cite.path)
        if not ranges:
            continue
        lo = min(r[0] for r in ranges)
        hi = max(r[1] for r in ranges)
        start = max(lo, min(cite.start_line, hi))
        end = max(start, min(cite.end_line, hi))
        key = (cite.path, start, end)
        if key not in seen:
            seen.add(key)
            out.append(Citation(path=cite.path, start_line=start, end_line=end))
    return out[:8]


class GeminiAnswerer:
    def __init__(self, client: genai.Client, model: str, temperature: float):
        self._client = client
        self.model = model
        self._temperature = temperature

    async def answer(self, repo: str, question: str, chunks: list[CodeChunk]) -> AnswerResponse:
        resp = await self._client.aio.models.generate_content(
            model=self.model,
            contents=build_prompt(repo, question, chunks),
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                temperature=self._temperature,
                response_mime_type="application/json",
                response_schema=_LlmAnswer,
            ),
        )
        parsed = resp.parsed if isinstance(resp.parsed, _LlmAnswer) else _LlmAnswer.model_validate_json(resp.text)
        usage = None
        if resp.usage_metadata:
            usage = TokenUsage(
                prompt_tokens=resp.usage_metadata.prompt_token_count or 0,
                output_tokens=resp.usage_metadata.candidates_token_count or 0,
                thinking_tokens=resp.usage_metadata.thoughts_token_count or 0,
            )
            log.info("answer usage: %s", usage)
        return AnswerResponse(
            llm_model=self.model,
            answer=parsed.answer.strip(),
            citations=verified_citations(parsed.citations, chunks),
            answered=parsed.answered,
            usage=usage,
        )
