"""Help with a gap the resume doesn't answer — without answering it for the person.

A job asking for "product thinking" is not evidence that the person has done any. So nothing here invents work. Two
kinds of help are offered instead, and they are kept apart on purpose:

* **Questions** — prompts to jog the memory ("Did you ever decide what to build, not just how?"). They contain no
  claims at all, so they are always safe to show.
* **Drafts** — a line restating something the person's own resume or repository already says, in a way that answers
  the gap. Every draft must quote the source it rests on; the quote is checked against the real text, and a draft
  that adds a tool, a number or an outcome the source doesn't contain is dropped rather than shown. The person still
  edits it and still decides whether it is true.
"""

from __future__ import annotations

from typing import Protocol

from pydantic import BaseModel, Field

from app.resume import (
    MAX_JD_CHARS,
    MAX_README_CHARS,
    RepoEvidence,
    Resume,
    _key,
    _looks_like_claim,
    _name_key,
    _WORD,
    introduced_terms,
    resume_text,
    strip_markup,
)
from app.schemas import TokenUsage

INSTRUCTION = """A job asks for something the person's resume doesn't show. You help them answer it themselves.

Return two things.

questions: 2-4 short questions that would remind them of real work they may have done that answers this. Ask about \
concrete situations, not qualities. Good: "Did you ever change what you were building after someone used it?" Bad: \
"Can you describe your product thinking?" Never imply they did it.

drafts: at most 3 resume lines, each RESTATING something their resume or one of their repositories ALREADY says, \
worded so it answers what the job asks. Rules, and a draft that breaks one must not be written at all:
- Every draft needs `grounded_on`: a sentence copied EXACTLY from their resume or that repository's README. Copy it \
character for character. If you cannot find a real sentence to quote, write no draft.
- The draft may only restate what that quote says. No tool, number, scale, outcome, team size or timeframe that is \
not in the quote. Rewording is allowed; adding is not.
- source is "resume" or "github"; for "github" also give the repository name in `repo`.
- Plain past-tense verb, one sentence, at most 28 words.
- Never name the company applying, the job, or the quality being demonstrated.

If their work genuinely shows nothing about this, return an empty drafts list and good questions. That is the right \
answer far more often than a thin draft."""


class GapDraft(BaseModel):
    text: str
    # The person's own sentence this restates, copied from their resume or a repository.
    grounded_on: str = ""
    source: str = "resume"
    repo: str = ""


class GapIdeas(BaseModel):
    questions: list[str] = Field(default_factory=list)
    drafts: list[GapDraft] = Field(default_factory=list)


class CheckedDraft(GapDraft):
    repo_url: str = ""
    # Words the draft uses that its source doesn't contain. A draft with claims among them is never returned.
    new_terms: list[str] = []


class CheckedIdeas(BaseModel):
    questions: list[str] = Field(default_factory=list)
    drafts: list[CheckedDraft] = Field(default_factory=list)
    # Drafts thrown away because their quote wasn't real, or because they added something the quote doesn't say.
    dropped: int = 0


class GapIdeasAi(Protocol):
    model: str

    async def ideas(self, gap: str, job_title: str, job_description: str, resume: Resume, repos: list[RepoEvidence],
                    model: str | None = None) -> tuple[GapIdeas, TokenUsage | None, str]: ...


def check_ideas(ideas: GapIdeas, resume: Resume, repos: list[RepoEvidence]) -> CheckedIdeas:
    """Keeps only drafts that quote something real and say nothing the quote doesn't."""
    by_repo = {_name_key(r.name): r for r in repos}
    all_resume = resume_text(resume)
    resume_key = _key(all_resume)

    kept: list[CheckedDraft] = []
    dropped = 0
    for d in ideas.drafts:
        quote = _key(d.grounded_on)
        text = (d.text or "").strip()
        if len(quote) < 12 or not text:
            dropped += 1
            continue

        repo = by_repo.get(_name_key(d.repo)) if d.source == "github" else None
        source_text = repo.text() if repo else all_resume
        if quote not in (_key(repo.text()) if repo else resume_key):
            dropped += 1  # the quote isn't in the source it claims to come from
            continue

        terms = introduced_terms(d.grounded_on, text, source_text)
        first = (_WORD.findall(strip_markup(text)) or [""])[0].strip(".,;:()*")
        if any(_looks_like_claim(t, first_word=(t == first)) for t in terms):
            dropped += 1  # it added something its own source doesn't say
            continue

        kept.append(CheckedDraft(text=text, grounded_on=d.grounded_on.strip(), source="github" if repo else "resume",
                                 repo=repo.name if repo else "", repo_url=repo.url if repo else "", new_terms=terms))
        if len(kept) >= 3:
            break

    return CheckedIdeas(questions=[q.strip() for q in ideas.questions if q.strip()][:4], drafts=kept, dropped=dropped)


class GeminiGapIdeasAi:
    def __init__(self, client, model: str) -> None:
        self._client = client
        self.model = model

    async def ideas(self, gap, job_title, job_description, resume, repos, model=None):
        from google.genai import types

        used = model or self.model
        parts = [f"# What the job asks for and the resume doesn't show\n\n{gap}"]
        if job_title or job_description:
            parts.append(f"# The job\n\n{job_title}\n\n{(job_description or '')[:MAX_JD_CHARS]}")
        parts.append(f"# Their resume\n\n{resume.model_dump_json(indent=1)}")
        if repos:
            parts.append("# Their repositories\n\n" + "\n\n".join(
                f"## {r.name}\nurl: {r.url}\ndescription: {r.description}\nREADME:\n{r.readme[:MAX_README_CHARS]}"
                for r in repos))

        res = await self._client.aio.models.generate_content(
            model=used,
            contents="\n\n".join(parts),
            config=types.GenerateContentConfig(
                system_instruction=INSTRUCTION,
                response_mime_type="application/json",
                response_schema=GapIdeas,
                temperature=0.2,
            ),
        )
        parsed = res.parsed if isinstance(res.parsed, GapIdeas) else GapIdeas.model_validate_json(res.text or "{}")
        from app.resume import _usage

        return parsed, _usage(res), used
