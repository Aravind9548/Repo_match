"""Drafts a README for a repository from its own source.

Grounded like everything else here: the model sees only the excerpts we retrieved, and is told to
leave a section out rather than invent it. No badges, no benchmarks, no fabricated install steps.
"""

import logging
from typing import Protocol

from google import genai
from google.genai import types

from app.schemas import CodeChunk, ReadmeResponse, TokenUsage

log = logging.getLogger("repomatch.ai")

SYSTEM_INSTRUCTION = """\
You write the README a working engineer would write for their own project, from its source code.

Rules:
- Use ONLY the provided excerpts. Never invent features, metrics, benchmarks, license terms, \
install steps, environment variables or endpoints that the code does not show.
- Leave a section out entirely rather than guessing at it.
- Open with one sentence saying what the project does and for whom — no "This repository contains".
- Prefer concrete detail pulled from the code: real module names, real commands, real endpoints.
- Output GitHub-flavoured Markdown only: a title, a short intro, then the sections that the code \
actually supports (how it works, stack, running it, layout). No badges, no emoji, no placeholder text.
"""

PROMPT = """\
# Repository
{repo}{description}{languages}

# Source excerpts
{chunks}

Write the README.md for this repository from these excerpts alone.
"""


class ReadmeDrafter(Protocol):
    model: str

    async def draft(self, repo: str, description: str | None, languages: list[str], chunks: list[CodeChunk]) -> ReadmeResponse: ...


def build_prompt(repo: str, description: str | None, languages: list[str], chunks: list[CodeChunk]) -> str:
    blocks = [f'<excerpt path="{c.path}" lines="{c.start_line}-{c.end_line}">\n{c.text}\n</excerpt>' for c in chunks]
    return PROMPT.format(
        repo=repo,
        description=f"\nDescription on GitHub: {description}" if description else "",
        languages=f"\nLanguages: {', '.join(languages)}" if languages else "",
        chunks="\n\n".join(blocks),
    )


class GeminiReadmeDrafter:
    def __init__(self, client: genai.Client, model: str, temperature: float):
        self._client = client
        self.model = model
        self._temperature = temperature

    async def draft(self, repo: str, description: str | None, languages: list[str], chunks: list[CodeChunk]) -> ReadmeResponse:
        resp = await self._client.aio.models.generate_content(
            model=self.model,
            contents=build_prompt(repo, description, languages, chunks),
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                temperature=self._temperature,
            ),
        )
        usage = None
        if resp.usage_metadata:
            usage = TokenUsage(
                prompt_tokens=resp.usage_metadata.prompt_token_count or 0,
                output_tokens=resp.usage_metadata.candidates_token_count or 0,
                thinking_tokens=resp.usage_metadata.thoughts_token_count or 0,
            )
            log.info("readme usage: %s", usage)
        markdown = (resp.text or "").strip()
        # Models sometimes wrap the whole document in a markdown fence; unwrap it.
        if markdown.startswith("```"):
            lines = markdown.splitlines()
            if lines[-1].strip() == "```":
                markdown = "\n".join(lines[1:-1]).strip()
        return ReadmeResponse(llm_model=self.model, markdown=markdown, files_used=len({c.path for c in chunks}), usage=usage)
