import base64
import os

from fastapi.testclient import TestClient

from app import atscheck as a
from app.main import app, get_requirement_ai

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")


class NeverCalled:
    """Stands in for the model on paths that must not need one. Asking it anything fails the test."""

    model = "never-called"

    async def requirements(self, title, description, model=None):
        raise AssertionError("this path must not cost a model call")


def no_model():
    """Overrides the model dependency, so these tests pass with no credentials anywhere."""
    app.dependency_overrides[get_requirement_ai] = lambda: NeverCalled()


def fixture_pdf() -> bytes:
    with open(os.path.join(FIXTURES, "bold_links_resume.pdf"), "rb") as f:
        return f.read()


EXTRACTED = """Asha Rao
asha@example.com | github.com/asha
Projects
Raft KV Store | GitHub 2025
- Engineered a fault-tolerant, Raft-replicated C++17 key-value cluster with leader election
- Deployed a secure Java Spring Boot REST API gateway over a private Docker network
Technical Skills
Languages: Java, C++, Python
Cloud & Tools: AWS, Docker, Kubernetes, Node.js
"""


def plan(*terms) -> a.RequirementPlan:
    return a.RequirementPlan(requirements=[
        a.Requirement(term=t, importance=i, quote=f"the ad says {t}") for t, i in terms
    ])


def test_parse_report_reads_the_file_a_parser_would_read():
    report = a.parse_report(fixture_pdf())

    assert report.pages == 1
    assert report.has_text_layer
    assert report.email == "asha@example.com"
    assert report.links == 3
    assert "skills" in report.sections_found and "projects" in report.sections_found
    # The fixture genuinely has no phone and no experience heading: both are reported, not glossed over.
    assert any("phone" in p for p in report.problems)
    assert "experience" in report.sections_missing
    assert report.first_lines[0] == "Asha Rao"


def test_a_file_with_no_text_layer_is_the_first_thing_reported():
    # A real PDF with a page and no text on it — what an exported-as-image resume looks like to a parser.
    import io

    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    buf = io.BytesIO()
    writer.write(buf)
    report = a.parse_report(buf.getvalue())

    assert not report.has_text_layer
    assert "little or no text" in report.problems[0]


def test_coverage_matches_spelling_variants_and_quotes_where_it_found_them():
    coverage = a.check_coverage(
        plan(("Java", "required"), ("NodeJS", "required"), ("REST APIs", "required"), ("Kubernetes", "preferred")),
        EXTRACTED,
    )

    assert coverage.required_total == 3
    assert coverage.required_found == 3
    assert coverage.required_percent == 100
    assert coverage.preferred_found == 1
    rest = next(c for c in coverage.requirements if c.term == "REST APIs")
    assert "Spring Boot REST API gateway" in rest.where  # the resume line, so the person can judge it
    node = next(c for c in coverage.requirements if c.term == "NodeJS")
    assert node.found and "spelled differently" in node.where


def test_coverage_does_not_invent_a_match():
    coverage = a.check_coverage(
        plan(("Rust", "required"), ("Kafka", "required"), ("Terraform", "preferred")),
        EXTRACTED,
    )

    assert coverage.required_found == 0
    assert coverage.required_percent == 0
    assert all(not c.found for c in coverage.requirements)
    # Missing requirements come first: they're what the person can still act on.
    assert coverage.requirements[0].importance == "required"


def test_an_acronym_in_brackets_counts_on_its_own():
    assert a.check_coverage(plan(("Continuous Integration (CI)", "required")), "Built CI pipelines in GitHub Actions").required_found == 1
    assert a.check_coverage(plan(("Continuous Integration (CI)", "required")), "Wrote integration tests").required_found == 0


def test_a_substring_is_not_a_match():
    # "Go" must not match "Google", and "R" must not match every word with an r in it.
    assert a.check_coverage(plan(("Go", "required")), "Deployed on Google Cloud").required_found == 0
    assert a.check_coverage(plan(("Go", "required")), "Wrote the service in Go").required_found == 1


def test_endpoint_returns_the_parse_report_without_a_job_description():
    no_model()  # with no ad to read, the check must not reach for a model at all
    try:
        res = TestClient(app).post("/resume/ats", json={"pdf_base64": base64.b64encode(fixture_pdf()).decode()})
    finally:
        app.dependency_overrides.pop(get_requirement_ai, None)

    assert res.status_code == 200
    body = res.json()
    assert body["coverage"] is None  # no ad, so nothing is scored
    assert body["parse"]["email"] == "asha@example.com"
    assert "Raft" in body["extracted"]


def test_endpoint_scores_against_the_ad_with_one_model_call():
    calls = []

    class FakeAi:
        model = "fake"

        async def requirements(self, title, description, model=None):
            calls.append((title, description, model))
            return plan(("Java", "required"), ("Kafka", "required")), None, "fake-model"

    app.dependency_overrides[get_requirement_ai] = lambda: FakeAi()
    try:
        client = TestClient(app)
        res = client.post("/resume/ats", json={
            "pdf_base64": base64.b64encode(fixture_pdf()).decode(),
            "job_title": "Backend Intern",
            "job_description": "We need Java and Kafka experience for our payments platform team in Hyderabad.",
        })
    finally:
        app.dependency_overrides.pop(get_requirement_ai, None)

    assert res.status_code == 200
    assert len(calls) == 1
    coverage = res.json()["coverage"]
    assert coverage["required_total"] == 2
    assert coverage["required_found"] == 1  # Java is in the resume, Kafka is not
    assert coverage["required_percent"] == 50


def test_endpoint_rejects_something_that_is_not_a_pdf():
    no_model()
    try:
        res = TestClient(app).post("/resume/ats", json={"pdf_base64": base64.b64encode(b"x" * 200).decode()})
    finally:
        app.dependency_overrides.pop(get_requirement_ai, None)

    assert res.status_code == 400
    assert "could not be opened" in res.json()["detail"]


def test_a_requirement_still_wrapped_in_prose_is_matched_on_the_thing_itself():
    coverage = a.check_coverage(
        plan(("4+ years of Java", "required"), ("experience with Docker", "required"),
             ("Java (Android development)", "required"), ("SIP/RTP", "preferred")),
        EXTRACTED,
    )

    assert coverage.required_found == 3  # Java twice and Docker; the resume shows all three
    assert coverage.preferred_found == 0  # neither SIP nor RTP is in the resume


def test_clean_term_leaves_a_plain_term_alone():
    assert a.clean_term("Kubernetes") == "Kubernetes"
    assert a.clean_term("3 years of experience with Spring Boot") == "Spring Boot"
    assert a.clean_term("strong proficiency in Python") == "Python"


def test_a_ligature_never_makes_a_requirement_look_missing():
    # The file says "proﬁle" with one glyph; the job asks for "profile". That is the same word.
    coverage = a.check_coverage(plan(("profile", "required")), "Built the user proﬁle service")

    assert coverage.required_found == 1


def test_warnings_name_the_words_they_are_about():
    report = a.ParseReport()
    assert a._words_containing("Built the proﬁle page and the ﬂow chart", set(a._LIGATURES)) == ["proﬁle", "ﬂow"]
    assert report.problems == []  # a fresh report claims nothing
