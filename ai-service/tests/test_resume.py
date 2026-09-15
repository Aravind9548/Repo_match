import base64
import io

from fastapi.testclient import TestClient

from app import resume as r
from app.main import app, get_resume_ai


def sample() -> r.Resume:
    return r.Resume(
        name="Asha Rao",
        contact=["asha@example.com", "github.com/asha"],
        sections=[
            r.ResumeSection(
                title="Projects",
                kind="projects",
                items=[
                    r.ResumeItem(title="Chat app", bullets=["Built a chat app with WebSockets and Redis"]),
                    r.ResumeItem(title="Payments API", bullets=["Wrote a REST API in Spring Boot for payments"]),
                ],
            ),
            r.ResumeSection(title="Skills", kind="skills", lines=["Java, Python, SQL"]),
        ],
    )


def test_check_plan_keeps_real_edits_and_flags_invented_skills():
    plan = r.TailorPlan(
        summary="s",
        edits=[
            r.TailorEdit(section="Projects", before="Wrote a REST API in Spring Boot for payments",
                         after="Built a payments REST API in Spring Boot", reason="leads with the match"),
            # Invents Kubernetes, which the resume never mentions.
            r.TailorEdit(section="Projects", before="Built a chat app with WebSockets and Redis",
                         after="Built a chat app with WebSockets, Redis and Kubernetes", reason="k8s in JD"),
            # "before" isn't resume text at all.
            r.TailorEdit(section="Projects", before="Led a team of 10", after="Led a team of 12", reason="x"),
        ],
    )
    edits = r.check_plan(sample(), plan)

    assert [e.before for e in edits] == [
        "Wrote a REST API in Spring Boot for payments",
        "Built a chat app with WebSockets and Redis",
    ]
    assert edits[0].unsupported is False
    assert edits[1].unsupported is True and "Kubernetes" in edits[1].new_terms


def test_apply_and_render_escapes_latex():
    resume = sample()
    resume.sections[1].lines = ["C++, C#, 100% & more_things"]
    applied = r.apply_to_resume(
        resume,
        [r.TailorEdit(section="Projects", before="Built a chat app with WebSockets and Redis",
                      after="Built a real-time chat app with WebSockets and Redis", reason="x")],
        [r.SectionOrder(section="projects", item_titles=["Payments API", "Chat app"])],
    )
    assert [i.title for i in applied.sections[0].items] == ["Payments API", "Chat app"]
    tex = r.render_latex(applied)
    assert r"C++, C\#, 100\% \& more\_things" in tex
    assert "real-time chat app" in tex
    assert tex.startswith(r"\documentclass") and tex.rstrip().endswith(r"\end{document}")


def test_apply_to_own_tex_keeps_design_and_reports_misses():
    source = r"\begin{document}\item Wrote a REST API in Spring Boot for payments \& refunds\end{document}"
    edits = [
        r.TailorEdit(section="P", before="Wrote a REST API in Spring Boot for payments & refunds",
                     after="Built a payments & refunds REST API in Spring Boot", reason="x"),
        r.TailorEdit(section="P", before="Not in the file", after="whatever", reason="x"),
    ]
    out, missed = r.apply_to_tex(source, edits)
    assert r"Built a payments \& refunds REST API in Spring Boot" in out
    assert missed == [1]


def test_extract_reads_docx_and_detects_tex():
    import docx

    d = docx.Document()
    d.add_paragraph("Asha Rao — Software Engineer. " * 5)
    buf = io.BytesIO()
    d.save(buf)
    text, kind = r.extract_text("cv.docx", buf.getvalue())
    assert kind == "text" and "Asha Rao" in text

    _, kind = r.extract_text("cv.txt", rb"\documentclass{article}\begin{document}hi\end{document}")
    assert kind == "tex"


def test_tailor_endpoint_returns_only_checked_edits():
    class FakeAi:
        model = "fake"

        async def structure(self, text):
            return sample(), None

        async def tailor(self, resume, jd, title):
            return r.TailorPlan(
                summary="ok",
                edits=[r.TailorEdit(section="Skills", before="Java, Python, SQL", after="Java, SQL, Python", reason="x"),
                       r.TailorEdit(section="Skills", before="made up", after="also made up", reason="x")],
                matched_keywords=["Java"],
                missing_keywords=["Kafka"],
            ), None

    app.dependency_overrides[get_resume_ai] = lambda: FakeAi()
    try:
        client = TestClient(app)
        res = client.post("/resume/tailor", json={
            "resume": sample().model_dump(), "job_title": "Backend Intern",
            "job_description": "We want Java and Kafka experience for backend services at scale.",
        })
        assert res.status_code == 200
        body = res.json()
        assert len(body["edits"]) == 1 and body["dropped_edits"] == 1
        assert body["missing_keywords"] == ["Kafka"]

        res = client.post("/resume/extract", json={"filename": "a.txt", "data_base64": base64.b64encode(b"hi").decode()})
        assert res.status_code == 422
    finally:
        app.dependency_overrides.clear()
