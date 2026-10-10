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

import re

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

questions: 2-3 short questions, a fallback for when their own work shows nothing to draft from. Ask about \
concrete situations, not qualities. Good: "Did you ever change what you were building after someone used it?" Bad: \
"Can you describe your product thinking?" Never imply they did it.

drafts: up to 4 resume lines, each RESTATING something their resume or one of their repositories ALREADY says, \
worded so it answers what the job asks. These are the main thing you produce — write as many as the evidence honestly \
supports. Rules, and a draft that breaks one must not be written at all:
- Every draft needs `grounded_on`: the sentence from their resume or that repository's README that it rests on, \
copied as closely as you can. If you cannot point at a real sentence, write no draft.
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
    # Where this belongs: the entry whose own words it restates. The person can still move it.
    section: str = ""
    item_title: str = ""
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


_STOP = {"with", "that", "this", "from", "were", "their", "they", "them", "when", "what", "which", "while", "into",
         "over", "after", "before", "also", "some", "other", "about", "than", "then", "there", "where", "would",
         "could", "should", "have", "been", "each", "more", "most", "using", "used", "work", "project"}


def _terms_of(text: str) -> set[str]:
    """The distinctive words of a sentence — what makes it that sentence and not another."""
    return {w.lower() for w in re.findall(r"[A-Za-z][A-Za-z0-9+#.]{3,}", text or "")} - _STOP


class _Source:
    """One real sentence the person wrote, and where it lives."""

    def __init__(self, text: str, section: str = "", item_title: str = "", repo: RepoEvidence | None = None) -> None:
        self.text = text.strip()
        self.section = section
        self.item_title = item_title
        self.repo = repo
        self.terms = _terms_of(self.text)


def _resume_sources(resume: Resume) -> list[_Source]:
    out: list[_Source] = []
    for section in resume.sections:
        for line in section.lines:
            out.append(_Source(strip_markup(line), section.title))
        for item in section.items:
            head = " ".join(x for x in (item.title, item.subtitle) if x)
            if head:
                out.append(_Source(strip_markup(head), section.title, item.title))
            for bullet in item.bullets:
                out.append(_Source(strip_markup(bullet), section.title, item.title))
    return [s for s in out if len(s.text) > 12]


def _repo_sources(repo: RepoEvidence) -> list[_Source]:
    pieces = re.split(r"(?<=[.!?])\s+|\n+", repo.text())
    out = [_Source(re.sub(r"^[#>*\-\s]+", "", p), repo=repo) for p in pieces]
    return [s for s in out if len(s.text) > 12]


def _best_source(quote: str, text: str, sources: list[_Source]) -> _Source | None:
    """The real sentence a draft rests on.

    The model is asked to copy its source exactly and usually doesn't, so its quote is treated as a pointer rather
    than as evidence: the sentence is found here, in the person's own text, and that sentence is what gets shown.
    A pointer that leads nowhere close enough means there is no evidence, and the draft goes."""
    wanted = _terms_of(quote)
    if len(wanted) < 2:
        return None
    best: tuple[float, _Source] | None = None
    for source in sources:
        if not source.terms:
            continue
        overlap = len(wanted & source.terms) / len(wanted)
        if overlap < 0.6:
            continue
        # Between equally quoted sentences, prefer the one the draft itself reads like.
        score = overlap + 0.25 * (len(_terms_of(text) & source.terms) / max(1, len(source.terms)))
        if best is None or score > best[0]:
            best = (score, source)
    return best[1] if best else None


def check_ideas(ideas: GapIdeas, resume: Resume, repos: list[RepoEvidence]) -> CheckedIdeas:
    """Keeps drafts that rest on a real sentence of the person's own, and say nothing that sentence doesn't."""
    by_repo = {_name_key(r.name): r for r in repos}
    all_resume = resume_text(resume)
    resume_sources = _resume_sources(resume)
    repo_sources = {_name_key(r.name): _repo_sources(r) for r in repos}
    item_of_repo = {_name_key(r.name): _repo_item(resume, r) for r in repos}

    kept: list[CheckedDraft] = []
    dropped = 0
    for d in ideas.drafts:
        text = (d.text or "").strip()
        if not text or len(_key(d.grounded_on)) < 8:
            dropped += 1
            continue

        repo = by_repo.get(_name_key(d.repo)) if d.source == "github" else None
        sources = repo_sources.get(_name_key(d.repo), []) if repo else resume_sources
        found = _best_source(d.grounded_on, text, sources)
        if found is None:
            dropped += 1  # nothing of theirs says this
            continue

        source_text = repo.text() if repo else all_resume
        terms = introduced_terms(found.text, text, source_text)
        first = (_WORD.findall(strip_markup(text)) or [""])[0].strip(".,;:()*")
        if any(_looks_like_claim(t, first_word=(t == first)) for t in terms):
            dropped += 1  # it added something its own source doesn't say
            continue

        home = item_of_repo.get(_name_key(d.repo)) if repo else (found.section, found.item_title)
        kept.append(CheckedDraft(
            text=text,
            grounded_on=found.text,  # their sentence, not the model's recollection of it
            source="github" if repo else "resume",
            repo=repo.name if repo else "",
            repo_url=repo.url if repo else "",
            section=(home or ("", ""))[0],
            item_title=(home or ("", ""))[1],
            new_terms=terms,
        ))
        if len(kept) >= 4:
            break

    return CheckedIdeas(questions=[q.strip() for q in ideas.questions if q.strip()][:3], drafts=kept, dropped=dropped)


def _repo_item(resume: Resume, repo: RepoEvidence) -> tuple[str, str]:
    """The resume entry a repository is already written up as, if it is."""
    wanted = _name_key(repo.name)
    for section in resume.sections:
        for item in section.items:
            title = _name_key(item.title)
            if title and (title in wanted or wanted in title):
                return section.title, item.title
    return "", ""


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
