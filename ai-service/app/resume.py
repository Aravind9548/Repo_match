"""Resume tailoring: read a resume, propose grounded edits for one job, render LaTeX, compile a PDF.

The contract, same as everywhere else in RepoMatch: the model may only reword, reorder and emphasise what the
resume already says. Every edit names the exact text it replaces (checked against the resume), and any wording
that brings in a term the resume never mentioned is flagged so the person decides, not the model.
"""

import asyncio
import io
import logging
import os
import re
import shutil
import tempfile
from typing import Protocol

from google import genai
from google.genai import types
from pydantic import BaseModel, Field

from app.schemas import TokenUsage

log = logging.getLogger("repomatch.ai")

MAX_RESUME_CHARS = 40_000
MAX_JD_CHARS = 20_000


# --- Structured resume -------------------------------------------------------


class ResumeItem(BaseModel):
    title: str = ""
    subtitle: str = ""
    date: str = ""
    location: str = ""
    bullets: list[str] = []


class ResumeSection(BaseModel):
    title: str
    # experience | projects | education | skills | other
    kind: str = "other"
    items: list[ResumeItem] = []
    # Free lines for sections without entries, e.g. "Languages: Java, Python".
    lines: list[str] = []


class Resume(BaseModel):
    name: str = ""
    headline: str = ""
    contact: list[str] = []
    sections: list[ResumeSection] = []


def resume_strings(r: Resume) -> list[str]:
    """Every piece of text an edit is allowed to replace."""
    out: list[str] = []
    for s in r.sections:
        out.extend(s.lines)
        for it in s.items:
            out.extend(it.bullets)
    if r.headline:
        out.append(r.headline)
    return out


def resume_text(r: Resume) -> str:
    parts = [r.name, r.headline, " ".join(r.contact)]
    for s in r.sections:
        parts.append(s.title)
        parts.extend(s.lines)
        for it in s.items:
            parts.extend([it.title, it.subtitle, it.date, it.location, *it.bullets])
    return "\n".join(p for p in parts if p)


# --- Extraction --------------------------------------------------------------


def extract_text(filename: str, data: bytes) -> tuple[str, str]:
    """Returns (text, kind) where kind is "tex" when the upload is LaTeX source, else "text"."""
    name = (filename or "").lower()
    if name.endswith(".pdf"):
        from pypdf import PdfReader

        reader = PdfReader(io.BytesIO(data))
        text = "\n".join((page.extract_text() or "") for page in reader.pages)
        return _clean(text), "text"
    if name.endswith(".docx"):
        import docx

        document = docx.Document(io.BytesIO(data))
        lines = [p.text for p in document.paragraphs]
        for table in document.tables:
            for row in table.rows:
                lines.append(" | ".join(cell.text for cell in row.cells))
        return _clean("\n".join(lines)), "text"
    text = data.decode("utf-8", errors="replace")
    kind = "tex" if name.endswith(".tex") or "\\documentclass" in text or "\\begin{document}" in text else "text"
    return (text if kind == "tex" else _clean(text))[:MAX_RESUME_CHARS * 2], kind


def _clean(text: str) -> str:
    text = text.replace("\r\n", "\n").replace(" ", " ")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()[:MAX_RESUME_CHARS]


# --- Gemini: structure and tailor ---------------------------------------------

STRUCTURE_INSTRUCTION = """\
You convert a resume into structured data, faithfully.

Rules:
- Copy text exactly as written. Do not rewrite, summarise, fix grammar or translate.
- Keep every bullet, in order. Keep every section, in order.
- For LaTeX input, output the visible text only: drop commands, keep their text (\\textbf{Java} -> Java).
- kind is one of: experience, projects, education, skills, other.
- Put contact details (email, phone, LinkedIn, GitHub, website, city) in contact, one per entry.
"""

TAILOR_INSTRUCTION = """\
You tailor a student's resume to one job. You are careful and honest: a recruiter will read this, and an \
interviewer will ask about every line.

You may only:
- Reword an existing bullet or line so the relevant part leads and matches the job's own vocabulary — only where \
the bullet already supports it.
- Reorder items inside a section so the most relevant come first.

You must never:
- Add a skill, tool, metric, employer, responsibility or result that the resume does not already state.
- Change dates, titles, companies, schools or numbers.
- Touch contact details.

For each edit, "before" must be copied character-for-character from one bullet or line of the resume. Make at most \
12 edits and only where they genuinely help for this job. List job requirements the resume already shows in \
matched_keywords, and requirements it doesn't show in missing_keywords — those are gaps for the person, not text \
to insert.
"""


class TailorEdit(BaseModel):
    section: str
    before: str
    after: str
    reason: str


class SectionOrder(BaseModel):
    section: str
    item_titles: list[str]


class TailorPlan(BaseModel):
    summary: str
    edits: list[TailorEdit] = Field(default_factory=list)
    reorder: list[SectionOrder] = Field(default_factory=list)
    matched_keywords: list[str] = Field(default_factory=list)
    missing_keywords: list[str] = Field(default_factory=list)


class CheckedEdit(TailorEdit):
    id: int
    # Terms the new wording introduces that appear nowhere in the original resume.
    new_terms: list[str] = []
    # True when new_terms look like claims (skills/tools) rather than ordinary words.
    unsupported: bool = False


_WORD = re.compile(r"[A-Za-z][A-Za-z0-9+#./-]*")
_COMMON = set(
    """a an and or the of to in on for with by from as at into via using used use built build developed develop
    designed design implemented implement created create led lead improved improve reduced reduce increased
    increase optimized optimize managed manage worked work across over under within including include
    which that this these those their its his her our your my we i it is are was were be been being has have had
    team teams project projects system systems service services application applications app apps feature features
    data user users performance scalable scale high low new end end-to-end real real-time time based driven
    efficient efficiency reliable reliability production across multiple several key core main""".split()
)


def introduced_terms(before: str, after: str, resume_all_text: str) -> list[str]:
    """Words in `after` that appear neither in `before` nor anywhere else in the resume."""
    known = {w.lower() for w in _WORD.findall(resume_all_text)} | {w.lower() for w in _WORD.findall(before)}
    out: list[str] = []
    for w in _WORD.findall(after):
        lw = w.lower().strip(".")
        if len(lw) < 2 or lw in known or lw in _COMMON or lw.rstrip("s") in known or lw + "s" in known:
            continue
        if lw not in (x.lower() for x in out):
            out.append(w.strip("."))
    return out


def _looks_like_claim(term: str) -> bool:
    # Tools and skills tend to be capitalised, acronyms, or carry +#./ (C++, Node.js, CI/CD).
    return term[:1].isupper() or term.isupper() or any(c in term for c in "+#./") or any(c.isdigit() for c in term)


def check_plan(resume: Resume, plan: TailorPlan) -> list[CheckedEdit]:
    """Keeps edits whose `before` really is resume text, and flags wording that adds unstated claims."""
    allowed = {re.sub(r"\s+", " ", s).strip(): s for s in resume_strings(resume)}
    all_text = resume_text(resume)
    checked: list[CheckedEdit] = []
    seen: set[str] = set()
    for e in plan.edits:
        key = re.sub(r"\s+", " ", e.before).strip()
        if key not in allowed or key in seen or not e.after.strip() or e.after.strip() == key:
            continue
        seen.add(key)
        terms = introduced_terms(e.before, e.after, all_text)
        checked.append(
            CheckedEdit(
                id=len(checked) + 1,
                section=e.section,
                before=allowed[key],
                after=e.after.strip(),
                reason=e.reason,
                new_terms=terms,
                unsupported=any(_looks_like_claim(t) for t in terms),
            )
        )
    return checked[:12]


def _usage(resp) -> TokenUsage | None:
    if not resp.usage_metadata:
        return None
    return TokenUsage(
        prompt_tokens=resp.usage_metadata.prompt_token_count or 0,
        output_tokens=resp.usage_metadata.candidates_token_count or 0,
        thinking_tokens=resp.usage_metadata.thoughts_token_count or 0,
    )


class ResumeAi(Protocol):
    model: str

    async def structure(self, text: str) -> tuple[Resume, TokenUsage | None]: ...

    async def tailor(self, resume: Resume, job_description: str, job_title: str) -> tuple[TailorPlan, TokenUsage | None]: ...


class GeminiResumeAi:
    def __init__(self, client: genai.Client, model: str):
        self._client = client
        self.model = model

    async def structure(self, text: str) -> tuple[Resume, TokenUsage | None]:
        resp = await self._client.aio.models.generate_content(
            model=self.model,
            contents=f"# Resume\n\n{text[:MAX_RESUME_CHARS]}",
            config=types.GenerateContentConfig(
                system_instruction=STRUCTURE_INSTRUCTION,
                temperature=0.0,
                response_mime_type="application/json",
                response_schema=Resume,
            ),
        )
        resume = resp.parsed if isinstance(resp.parsed, Resume) else Resume.model_validate_json(resp.text)
        return resume, _usage(resp)

    async def tailor(self, resume: Resume, job_description: str, job_title: str) -> tuple[TailorPlan, TokenUsage | None]:
        prompt = (
            f"# Job: {job_title}\n\n{job_description[:MAX_JD_CHARS]}\n\n"
            f"# Resume (structured)\n\n{resume.model_dump_json(indent=1)}"
        )
        resp = await self._client.aio.models.generate_content(
            model=self.model,
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=TAILOR_INSTRUCTION,
                temperature=0.2,
                response_mime_type="application/json",
                response_schema=TailorPlan,
            ),
        )
        plan = resp.parsed if isinstance(resp.parsed, TailorPlan) else TailorPlan.model_validate_json(resp.text)
        return plan, _usage(resp)


# --- Applying edits ----------------------------------------------------------


def apply_to_resume(resume: Resume, edits: list[TailorEdit], reorder: list[SectionOrder]) -> Resume:
    r = resume.model_copy(deep=True)
    replace = {re.sub(r"\s+", " ", e.before).strip(): e.after for e in edits}

    def swap(s: str) -> str:
        return replace.get(re.sub(r"\s+", " ", s).strip(), s)

    for section in r.sections:
        section.lines = [swap(x) for x in section.lines]
        for it in section.items:
            it.bullets = [swap(b) for b in it.bullets]
        order = next((o for o in reorder if o.section.strip().lower() == section.title.strip().lower()), None)
        if order:
            rank = {t.strip().lower(): i for i, t in enumerate(order.item_titles)}
            # Stable: items the order doesn't mention keep their place after the ranked ones.
            section.items.sort(key=lambda it: rank.get(it.title.strip().lower(), len(rank)))
    if r.headline:
        r.headline = swap(r.headline)
    return r


def latex_variants(text: str) -> list[str]:
    """How a plain-text bullet may appear in LaTeX source."""
    escaped = latex_escape(text)
    return list(dict.fromkeys([text, escaped, escaped.replace("\\textasciitilde{}", "\\~{}")]))


def apply_to_tex(source: str, edits: list[TailorEdit]) -> tuple[str, list[int]]:
    """Replaces each edit's text inside the person's own .tex. Returns the new source and indexes not applied."""
    out = source
    missed: list[int] = []
    for i, e in enumerate(edits):
        for variant in latex_variants(e.before):
            if variant in out:
                out = out.replace(variant, latex_escape(e.after), 1)
                break
        else:
            missed.append(i)
    return out, missed


# --- LaTeX rendering ----------------------------------------------------------

_LATEX_SPECIALS = {
    "\\": r"\textbackslash{}",
    "&": r"\&",
    "%": r"\%",
    "$": r"\$",
    "#": r"\#",
    "_": r"\_",
    "{": r"\{",
    "}": r"\}",
    "~": r"\textasciitilde{}",
    "^": r"\textasciicircum{}",
}


def latex_escape(s: str) -> str:
    s = "".join(_LATEX_SPECIALS.get(c, c) for c in s)
    # Typographic characters pdfLaTeX-era fonts may not have; Tectonic handles UTF-8, but keep dashes/quotes tidy.
    return s.replace("–", "--").replace("—", "---").replace("’", "'").replace("“", "``").replace("”", "''").replace("₹", "Rs. ")


PREAMBLE = r"""\documentclass[11pt,a4paper]{article}
\usepackage[margin=0.55in]{geometry}
\usepackage{fontspec}
\usepackage{enumitem}
\usepackage{titlesec}
\usepackage[hidelinks]{hyperref}
\usepackage{xcolor}
\definecolor{accent}{HTML}{1F3A93}
\pagestyle{empty}
\setlength{\parindent}{0pt}
\titleformat{\section}{\large\bfseries\color{accent}}{}{0em}{}[\vspace{-6pt}\rule{\linewidth}{0.4pt}]
\titlespacing*{\section}{0pt}{10pt}{5pt}
\setlist[itemize]{leftmargin=1.2em, itemsep=0.5pt, topsep=1pt, parsep=0pt, partopsep=0pt}
\newcommand{\entry}[2]{\par\noindent\textbf{#1}\hfill #2\par}
\newcommand{\entrysub}[2]{\noindent\textit{#1}\hfill\textit{#2}\par}
"""


def _link(contact: str) -> str:
    c = contact.strip()
    if re.fullmatch(r"[^@\s]+@[^@\s]+\.[a-z]{2,}", c, re.I):
        return rf"\href{{mailto:{c}}}{{{latex_escape(c)}}}"
    if re.match(r"(https?://)?(www\.)?(github|linkedin|gitlab)\.com/", c, re.I) or c.startswith("http"):
        url = c if c.startswith("http") else "https://" + c
        return rf"\href{{{url}}}{{{latex_escape(c.replace('https://', '').replace('http://', ''))}}}"
    return latex_escape(c)


def render_latex(r: Resume) -> str:
    body: list[str] = [PREAMBLE, r"\begin{document}", r"\begin{center}"]
    body.append(rf"{{\LARGE\bfseries {latex_escape(r.name or 'Your Name')}}}\\[3pt]")
    if r.headline:
        body.append(rf"{latex_escape(r.headline)}\\[2pt]")
    if r.contact:
        body.append(r" \textbar{} ".join(_link(c) for c in r.contact))
    body.append(r"\end{center}")

    for s in r.sections:
        body.append(rf"\section*{{{latex_escape(s.title)}}}")
        for i, it in enumerate(s.items):
            if i > 0:
                body.append(r"\vspace{4pt}")
            if it.title or it.date:
                body.append(rf"\entry{{{latex_escape(it.title)}}}{{{latex_escape(it.date)}}}")
            if it.subtitle or it.location:
                body.append(rf"\entrysub{{{latex_escape(it.subtitle)}}}{{{latex_escape(it.location)}}}")
            if it.bullets:
                body.append(r"\begin{itemize}")
                body.extend(rf"  \item {latex_escape(b)}" for b in it.bullets)
                body.append(r"\end{itemize}")
        if s.lines:
            body.append(r"\\ ".join(latex_escape(line) for line in s.lines) + r"\par")
    body.append(r"\end{document}")
    return "\n".join(body) + "\n"


# --- Compiling ---------------------------------------------------------------


class CompileResult(BaseModel):
    ok: bool
    pdf: bytes | None = None
    log: str = ""


async def compile_pdf(latex: str, timeout_s: int = 150) -> CompileResult:
    """Compiles with Tectonic (self-contained TeX engine; package bundle baked into the image)."""
    tectonic = shutil.which("tectonic")
    if not tectonic:
        return CompileResult(ok=False, log="Tectonic isn't installed on this server.")
    with tempfile.TemporaryDirectory() as work:
        src = os.path.join(work, "resume.tex")
        with open(src, "w", encoding="utf-8") as f:
            f.write(latex)
        proc = await asyncio.create_subprocess_exec(
            tectonic, "--chatter", "minimal", "--keep-logs", "--outdir", work, src,
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT,
        )
        try:
            out, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout_s)
        except TimeoutError:
            proc.kill()
            return CompileResult(ok=False, log=f"LaTeX compile timed out after {timeout_s}s")
        log_text = out.decode("utf-8", errors="replace")[-6000:]
        pdf_path = os.path.join(work, "resume.pdf")
        if proc.returncode != 0 or not os.path.exists(pdf_path):
            return CompileResult(ok=False, log=log_text)
        with open(pdf_path, "rb") as f:
            return CompileResult(ok=True, pdf=f.read(), log=log_text)
