"""Drafts the answers an application form asks for, grounded in the person's resume and repositories.

Same contract as tailoring: nothing is invented. Every answer must rest on the resume, a repository, or a fact the
person themselves gave us (their profile). Answers that drift are dropped by the checker, not shown.
"""

import logging
import re
from typing import Protocol

from google import genai
from google.genai import types
from pydantic import BaseModel, Field

from app.resume import MAX_JD_CHARS, MAX_README_CHARS, RepoEvidence, Resume, _norm, _WORD, resume_text, strip_markup
from app.schemas import TokenUsage

log = logging.getLogger("repomatch.ai")

INSTRUCTION = """\
You draft the answers a student will paste into one job application. They will read every word before sending it, \
and they must be able to defend it in an interview.

Rules:
- Use ONLY what the resume, the repositories and the person's own details state. Never invent a project, a metric, \
a course, an employer or an interest.
- Be concrete and specific to THIS job and THIS company: name the real project, the real tool, the real result.
- Write in the person's voice: plain first-person, no buzzwords, no "I am passionate about leveraging synergies".
- Length: "why this company" and "why you" about 60-90 words; project answers about 80-120 words; everything else \
one or two sentences.
- For each answer, set grounded_on to the resume line or repository name it rests on.
- If the resume and repos genuinely don't support an answer, leave that answer empty rather than guessing.
"""

PROMPT = """\
# Job
{title} at {company}

{description}

# The person's details (their own words)
{profile}

# Their resume
{resume}

# Their repositories most relevant to this job
{repos}

# Questions to answer
{questions}
"""

DEFAULT_QUESTIONS = [
    "Why do you want to work at this company?",
    "Why are you a good fit for this role?",
    "Describe a project most relevant to this role, and your part in it.",
    "What is a technical challenge you solved, and how?",
    "Anything else you'd like us to know? (short)",
]


class KitAnswer(BaseModel):
    question: str
    answer: str = ""
    # The resume line or repository the answer rests on.
    grounded_on: str = ""


class KitPlan(BaseModel):
    answers: list[KitAnswer] = Field(default_factory=list)


class CheckedAnswer(KitAnswer):
    # Words the answer uses that appear nowhere in the resume, repos or the person's own details.
    new_terms: list[str] = []
    # True when those words look like claims (tools, companies, numbers) rather than ordinary language.
    unsupported: bool = False


class ApplyKitAi(Protocol):
    model: str

    async def answers(self, title: str, company: str, description: str, profile: str, resume: Resume,
                      repos: list[RepoEvidence], questions: list[str],
                      model: str | None = None) -> tuple[KitPlan, TokenUsage | None, str]: ...


def check_answers(plan: KitPlan, resume: Resume, repos: list[RepoEvidence], profile: str) -> list[CheckedAnswer]:
    """Flags wording that introduces claims none of the sources make."""
    known = set()
    for text in [resume_text(resume), profile, *(r.text() for r in repos)]:
        for w in _WORD.findall(strip_markup(text or "")):
            known.add(_norm(w))
            known.update(_norm(p) for p in w.split("-") if p)

    out: list[CheckedAnswer] = []
    for a in plan.answers:
        answer = (a.answer or "").strip()
        if not answer:
            continue
        new_terms: list[str] = []
        plain = strip_markup(answer)
        # Words that open a sentence are capitalised by grammar, not because they name something.
        sentence_starts = {m.group(1) for m in re.finditer(r"(?:^|[.!?]\s+)([A-Za-z][\w+#./-]*)", plain)}
        for w in _WORD.findall(plain):
            n = _norm(w)
            if len(n) < 2 or n in known:
                continue
            claim = (w.isupper() or any(c.isupper() for c in w[1:]) or any(c in w for c in "+#/")
                     or any(c.isdigit() for c in w) or (w[:1].isupper() and w not in sentence_starts))
            if claim and n not in (_norm(x) for x in new_terms):
                new_terms.append(w.strip(".,;:()"))
        out.append(CheckedAnswer(question=a.question, answer=answer, grounded_on=a.grounded_on,
                                 new_terms=new_terms, unsupported=bool(new_terms)))
    return out


class GeminiApplyKitAi:
    def __init__(self, client: genai.Client, model: str):
        self._client = client
        self.model = model

    async def answers(self, title, company, description, profile, resume, repos, questions, model=None):
        used = model or self.model
        repo_text = "\n\n".join(
            f"## {r.name} ({r.url})\n{r.description}\nlanguages: {', '.join(r.languages)}\n{r.readme[:MAX_README_CHARS]}"
            for r in repos) or "(none given)"
        prompt = PROMPT.format(
            title=title or "this role", company=company or "this company",
            description=(description or "")[:MAX_JD_CHARS], profile=profile or "(none given)",
            resume=resume.model_dump_json(indent=1), repos=repo_text,
            questions="\n".join(f"- {q}" for q in (questions or DEFAULT_QUESTIONS)),
        )
        resp = await self._client.aio.models.generate_content(
            model=used,
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=INSTRUCTION,
                temperature=0.3,
                response_mime_type="application/json",
                response_schema=KitPlan,
            ),
        )
        plan = resp.parsed if isinstance(resp.parsed, KitPlan) else KitPlan.model_validate_json(resp.text)
        usage = None
        if resp.usage_metadata:
            usage = TokenUsage(
                prompt_tokens=resp.usage_metadata.prompt_token_count or 0,
                output_tokens=resp.usage_metadata.candidates_token_count or 0,
                thinking_tokens=resp.usage_metadata.thoughts_token_count or 0,
            )
        log.info("apply kit: %d answers for %s", len(plan.answers), company)
        return plan, usage, used
