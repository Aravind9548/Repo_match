"""Reads the built PDF back the way an applicant tracking system would, and reports what it found.

Two separate things, deliberately kept apart:

* **Parse** — entirely deterministic. The PDF is re-extracted and the result inspected: is there a text layer at all,
  is the contact detail machine-readable, are the section headings recognisable, did the spacing survive, is the
  layout one a parser reads in order. These are the failures that silently cost an application, and none of them
  needs a model.
* **Coverage** — the job ad's own requirements, listed by the model, then checked against the extracted text by
  plain string matching. The model says what the job asks for; it never says whether the resume answers it.

No employer's parser is reproduced here, and no score predicts one. What this can honestly say is "the file is
readable, and these requirements appear in it; these don't".
"""

from __future__ import annotations

import io
import re
from typing import Protocol

from pydantic import BaseModel, Field

from app import resume as resumes
from app.schemas import TokenUsage

# A resume page carries roughly 2,500–6,000 characters; far below that and the text layer is missing or partial.
_MIN_CHARS_PER_PAGE = 600

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
_PHONE = re.compile(r"(?:\+\d{1,3}[\s-]?)?(?:\(\d{2,4}\)[\s-]?)?\d{3,5}[\s-]?\d{3,5}(?:[\s-]?\d{2,4})?")
_URL = re.compile(r"https?://\S+|(?:www\.|github\.com/|linkedin\.com/)\S+", re.I)

# The headings a parser looks for when it splits a resume into sections.
_HEADINGS = {
    "experience": ("experience", "employment", "work history", "internship", "professional"),
    "education": ("education", "academics", "qualification"),
    "skills": ("skills", "technologies", "technical", "tools"),
    "projects": ("projects", "portfolio", "open source"),
}

# Ligatures and the like: present in the file, absent from most parsers' output.
_LIGATURES = "ﬁﬂﬀﬃﬄﬅﬆœæ"


class ParseReport(BaseModel):
    pages: int = 0
    characters: int = 0
    has_text_layer: bool = False
    email: str = ""
    phone: str = ""
    links: int = 0
    sections_found: list[str] = Field(default_factory=list)
    sections_missing: list[str] = Field(default_factory=list)
    # One line per thing that would trip a parser, in the order they'd hurt.
    problems: list[str] = Field(default_factory=list)
    # The first lines of the extracted text, so the person can see what a parser sees.
    first_lines: list[str] = Field(default_factory=list)


class Requirement(BaseModel):
    term: str
    # "required" when the ad words it as a must, "preferred" when it's a nice-to-have.
    importance: str = "required"
    # The job ad's own words, so the person can judge the requirement for themselves.
    quote: str = ""


class RequirementPlan(BaseModel):
    requirements: list[Requirement] = Field(default_factory=list)


class CheckedRequirement(Requirement):
    found: bool = False
    # Where it turned up in the extracted text, quoted.
    where: str = ""


class Coverage(BaseModel):
    required_total: int = 0
    required_found: int = 0
    preferred_total: int = 0
    preferred_found: int = 0
    # Percentage of the ad's required terms present in the extracted text. Not an employer's score.
    required_percent: int = 0
    requirements: list[CheckedRequirement] = Field(default_factory=list)


INSTRUCTION = """You read one job advertisement and list what it asks for, as terms that would appear in a resume.

Rules:
- List concrete, checkable things: languages, frameworks, tools, platforms, degrees, years, certifications, domains \
("distributed systems", "REST APIs"). Skip anything unmeasurable ("passionate", "team player", "fast-paced").
- term is ONE atomic thing in 1-3 words, written as a resume writes it: "Kubernetes", not "experience with Kubernetes"; "Java", not "Java 17+" or "4 years of Java" or "Java (Android development)". No parentheses, no year counts, no qualifiers.
- Where the ad bundles several things together ("Java and Kotlin", "SIP/RTP", "degree in CS or related field"), list each as its own requirement.
- importance is "required" when the ad words it as a must ("required", "must have", "you have"), "preferred" when it \
is optional ("nice to have", "bonus", "plus", "preferred").
- quote is the ad's own words for that requirement, copied exactly, at most 160 characters.
- At most 30 requirements, most important first. Never invent a requirement the ad does not state."""


class RequirementAi(Protocol):
    model: str

    async def requirements(self, title: str, description: str,
                           model: str | None = None) -> tuple[RequirementPlan, TokenUsage | None, str]: ...


# --- parsing -----------------------------------------------------------------


def _squash(s: str) -> str:
    """"Node.js", "node js" and "NodeJS" all become "nodejs", so a spelling difference isn't a miss."""
    return re.sub(r"[^a-z0-9+#]", "", s.lower())


def _collapse(s: str) -> str:
    """Runs of spaces collapsed, line breaks kept — so a match can be quoted in the line it was found on."""
    return re.sub(r"[ 	 ]+", " ", s)


def _words(s: str) -> str:
    return _collapse(s).lower()


def parse_report(data: bytes) -> ParseReport:
    """Re-extracts the PDF and reports what a parser would get out of it."""
    import pdfplumber

    report = ParseReport()
    texts: list[str] = []
    columns = False
    with pdfplumber.open(io.BytesIO(data)) as pdf:
        report.pages = len(pdf.pages)
        for page in pdf.pages:
            texts.append(page.extract_text() or "")
            report.links += len([h for h in (page.hyperlinks or []) if h.get("uri")])
            columns = columns or _two_columns(page)

    text = "\n".join(texts)
    report.characters = len(text.strip())
    report.has_text_layer = report.characters >= _MIN_CHARS_PER_PAGE * max(1, report.pages) // 2
    report.first_lines = [ln.strip() for ln in text.splitlines() if ln.strip()][:6]

    email = _EMAIL.search(text)
    report.email = email.group(0) if email else ""
    phone = _PHONE.search(text.replace(" ", " "))
    report.phone = phone.group(0).strip() if phone else ""

    lowered = text.lower()
    for name, words in _HEADINGS.items():
        (report.sections_found if any(w in lowered for w in words) else report.sections_missing).append(name)

    if not report.has_text_layer:
        report.problems.append(
            "There is little or no text in the file — a parser would read almost nothing. This happens when a resume "
            "is exported as an image or a scan.")
    if not report.email:
        report.problems.append("No email address a parser can read. Most forms reject an application without one.")
    if not report.phone:
        report.problems.append("No phone number a parser can read.")
    if report.links == 0 and not _URL.search(text):
        report.problems.append("No links in the file — a GitHub or LinkedIn address typed as plain text still reads, "
                               "but nothing here does.")
    if columns:
        report.problems.append(
            "The layout looks like two columns. Some parsers read down one column and then the other, which jumbles "
            "dates and bullets together.")
    if any(c in text for c in _LIGATURES):
        report.problems.append("The file uses ligature glyphs (ﬁ, ﬂ). A few parsers drop them, turning \"profile\" "
                               "into \"prole\".")
    if re.search(r"\b\w{32,}\b", text):
        report.problems.append("Some words ran together with no space between them, which is how a parser will read "
                               "them too.")
    if report.sections_missing:
        report.problems.append(
            "No heading a parser recognises for: " + ", ".join(report.sections_missing)
            + ". It files everything under that heading as \"other\".")
    if report.pages > 1:
        report.problems.append(f"{report.pages} pages. Nothing breaks, but a one-page resume is read in full more often.")
    return report


def _two_columns(page) -> bool:
    """Two dense bands of text with a clear gutter between them, over most of the page's height."""
    words = page.extract_words(x_tolerance=1, y_tolerance=3) or []
    if len(words) < 60:
        return False
    width = page.width or 612
    gutter_lo, gutter_hi = width * 0.42, width * 0.58
    left = [w for w in words if w["x1"] < gutter_lo]
    right = [w for w in words if w["x0"] > gutter_hi]
    crossing = [w for w in words if w["x0"] <= gutter_hi and w["x1"] >= gutter_lo]
    if len(crossing) > len(words) * 0.08:
        return False  # plenty of lines run across the middle, so it's one column
    if len(left) < len(words) * 0.2 or len(right) < len(words) * 0.2:
        return False
    span = lambda ws: max(w["bottom"] for w in ws) - min(w["top"] for w in ws)  # noqa: E731
    height = page.height or 792
    return span(left) > height * 0.4 and span(right) > height * 0.4


# --- coverage ----------------------------------------------------------------


def clean_term(term: str) -> str:
    """Strips the wording a requirement sometimes keeps, so what's left is the thing itself.

    The prompt asks for atomic terms, but a model will still hand back "4+ years of Java" or "experience with
    Kubernetes" now and then, and neither can match any resume's words."""
    t = (term or "").strip()
    t = re.sub(r"^\s*(?:at least\s+)?\d+\+?\s*(?:-|to)?\s*\d*\+?\s*years?\s+(?:of\s+)?(?:experience\s+(?:with|in)\s+)?",
               "", t, flags=re.I)
    t = re.sub(r"^\s*(?:strong|solid|proven|demonstrated|hands[- ]on|deep|excellent|good)\s+", "", t, flags=re.I)
    t = re.sub(r"^\s*(?:experience|proficiency|familiarity|knowledge|expertise)\s+(?:with|in|of)\s+", "", t, flags=re.I)
    return t.strip(" .,;:-")


def _present(term: str, lowered: str, original: str, squashed: str) -> str:
    """Whole-word first, then the squashed spelling. Returns the line it was found on, or ""."""
    bare = clean_term(term)
    if not bare:
        return ""
    parts = [bare]
    # A bracketed aside is checked on its own: "Continuous Integration (CI)" and "Java (Android development)" both
    # count when the resume shows either half.
    inner = re.match(r"^(.*?)\s*\(([^)]+)\)\s*$", bare)
    if inner:
        parts = [p for p in (inner.group(1).strip(), inner.group(2).strip()) if p]
    # "SIP/RTP" and "CI/CD" are two things the ad wrote as one.
    if "/" in bare and " " not in bare:
        parts.extend(p for p in bare.split("/") if len(p) >= 2)

    for part in parts:
        words = part.split()
        if words:
            # The ad writes "REST APIs" where the resume writes "REST API": match either way round.
            words[-1] = _singular(words[-1])
        pattern = re.escape(_words(" ".join(words))).replace(r"\ ", r"[\s\-/]+")
        # Allow a trailing plural or "-based", "-ing" on the last word.
        m = re.search(rf"(?<![a-z0-9]){pattern}(?:s|es|ing|ed|based)?(?![a-z0-9])", lowered)
        if m:
            # Quoted from the resume's own casing; the two differ only in case, so the offset holds.
            return _line_around(original, m.start())
        sq = _squash(part)
        if len(sq) >= 3 and sq in squashed:
            return f"spelled differently in the resume ({part})"
    return ""


def _singular(word: str) -> str:
    """Drops a plural ending, but never from an acronym that happens to end in S (AWS, CSS, JS)."""
    if re.fullmatch(r"[A-Z0-9+#./]{2,}s", word):
        return word[:-1]  # APIs, SDKs, IDEs
    if word.endswith("S"):
        return word  # NodeJS, AWS, iOS: the S belongs to the name
    low = word.lower()
    if len(word) > 4 and low.endswith("ies"):
        return word[:-3] + "y"
    if len(word) > 4 and low.endswith("es") and not low.endswith("ses"):
        return word[:-2]
    if len(word) > 4 and low.endswith("s") and not low.endswith("ss"):
        return word[:-1]
    return word


def _line_around(text: str, index: int) -> str:
    start = text.rfind("\n", 0, index) + 1
    end = text.find("\n", index)
    line = text[start:end if end != -1 else len(text)].strip()
    return line[:160]


def check_coverage(plan: RequirementPlan, extracted: str) -> Coverage:
    """Checks each requirement against the text the parser produced — not against the resume's source."""
    original = _collapse(extracted)
    lowered = original.lower()
    squashed = _squash(extracted)
    checked: list[CheckedRequirement] = []
    for req in plan.requirements:
        where = _present(req.term, lowered, original, squashed)
        checked.append(CheckedRequirement(term=req.term, importance=req.importance, quote=req.quote,
                                          found=bool(where), where=where))

    required = [c for c in checked if c.importance != "preferred"]
    preferred = [c for c in checked if c.importance == "preferred"]
    found_required = [c for c in required if c.found]
    return Coverage(
        required_total=len(required),
        required_found=len(found_required),
        preferred_total=len(preferred),
        preferred_found=len([c for c in preferred if c.found]),
        required_percent=round(100 * len(found_required) / len(required)) if required else 0,
        # Missing first: that's what the person can still act on.
        requirements=sorted(checked, key=lambda c: (c.found, c.importance == "preferred")),
    )


class GeminiRequirementAi:
    """Lists a job ad's requirements. One structured call, no resume in the prompt — it must not judge the fit."""

    def __init__(self, client, model: str) -> None:
        self._client = client
        self.model = model

    async def requirements(self, title: str, description: str,
                           model: str | None = None) -> tuple[RequirementPlan, TokenUsage | None, str]:
        from google.genai import types

        used = model or self.model
        prompt = f"Job title: {title or 'unknown'}\n\nJob advertisement:\n{description[:30_000]}"
        res = await self._client.aio.models.generate_content(
            model=used,
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=INSTRUCTION,
                response_mime_type="application/json",
                response_schema=RequirementPlan,
                temperature=0.1,
            ),
        )
        plan = RequirementPlan.model_validate_json(res.text or "{}")
        plan.requirements = plan.requirements[:30]
        return plan, resumes._usage(res), used


def extracted_text(data: bytes) -> str:
    """The text a parser gets, with the markup the uploader adds stripped back out."""
    return resumes.strip_markup(resumes._pdf_text(data))
