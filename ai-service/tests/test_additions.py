"""Lines the person adds themselves: where they land, and what the model is not allowed to do to them."""

from fastapi.testclient import TestClient

from app import resume as r
from app.main import app, get_resume_ai


def sample() -> r.Resume:
    return r.Resume(
        name="Asha Rao",
        contact=["asha@example.com"],
        sections=[
            r.ResumeSection(
                title="Projects",
                kind="projects",
                items=[r.ResumeItem(title="Raft KV Store", bullets=["Built a Raft-replicated key-value store in C++17"])],
            ),
            r.ResumeSection(title="Technical Skills", kind="skills", lines=["Languages: Java, C++, Python"]),
        ],
    )


def test_an_addition_lands_on_the_item_it_names():
    out = r.apply_to_resume(sample(), [], [], additions=[
        r.BulletAddition(section="Projects", item_title="Raft KV Store", text="Paired with two classmates for a term",
                         gap="collaboration"),
    ])

    assert out.sections[0].items[0].bullets[-1] == "Paired with two classmates for a term"


def test_an_addition_to_a_section_of_plain_lines_becomes_a_line():
    out = r.apply_to_resume(sample(), [], [], additions=[
        r.BulletAddition(section="Technical Skills", text="Tools: Docker, Git"),
    ])

    assert out.sections[1].lines[-1] == "Tools: Docker, Git"


def test_a_section_that_does_not_exist_yet_is_created():
    out = r.apply_to_resume(sample(), [], [], additions=[
        r.BulletAddition(section="Leadership", text="Ran the coding club for a year", gap="collaboration"),
    ])

    assert out.sections[-1].title == "Leadership"
    assert out.sections[-1].lines == ["Ran the coding club for a year"]


def test_an_empty_addition_changes_nothing():
    out = r.apply_to_resume(sample(), [], [], additions=[r.BulletAddition(section="Projects", text="   ")])

    assert out.sections[0].items[0].bullets == sample().sections[0].items[0].bullets


def test_phrasing_keeps_a_tightened_bullet_that_adds_nothing():
    phrasing = r.check_phrasing(
        "i worked with two classmates on the raft project and we split the work",
        r.Phrasing(bullet="Built the Raft project with two classmates, splitting the work between us"),
        sample(),
    )

    assert phrasing.bullet.startswith("Built the Raft project with two classmates")
    assert not phrasing.unsupported


def test_phrasing_hands_back_the_persons_own_words_when_the_model_invents():
    sentence = "i worked with two classmates on the raft project"
    phrasing = r.check_phrasing(
        sentence,
        r.Phrasing(bullet="Led a team of two using Kubernetes and Kafka, cutting latency 40%"),
        sample(),
    )

    assert phrasing.bullet == sentence  # their sentence, not the model's story
    assert phrasing.unsupported
    assert "Kubernetes" in phrasing.new_terms and "Kafka" in phrasing.new_terms


def test_phrase_endpoint_reports_what_the_model_tried_to_add():
    class FakeAi:
        model = "fake"

        async def structure(self, text):  # pragma: no cover - not used here
            raise NotImplementedError

        async def phrase(self, sentence, resume, model=None):
            return r.Phrasing(bullet="Shipped a Kafka pipeline processing 2M events daily"), None, "fake-model"

        async def tailor(self, *a, **k):  # pragma: no cover - not used here
            raise NotImplementedError

    app.dependency_overrides[get_resume_ai] = lambda: FakeAi()
    try:
        res = TestClient(app).post("/resume/phrase", json={
            "sentence": "i helped run the deployment for our class project",
            "resume": sample().model_dump(),
        })
    finally:
        app.dependency_overrides.pop(get_resume_ai, None)

    body = res.json()["phrasing"]
    assert res.status_code == 200
    assert body["unsupported"] is True
    assert body["bullet"] == "i helped run the deployment for our class project"
    assert "Kafka" in body["new_terms"]


def test_apply_endpoint_returns_the_resume_the_build_would_use():
    res = TestClient(app).post("/resume/apply", json={
        "resume": sample().model_dump(),
        "edits": [{"section": "Projects", "before": "Built a Raft-replicated key-value store in C++17",
                   "after": "Built a Raft-replicated key-value store in C++17 with leader election",
                   "reason": "the job asks for consensus work"}],
        "additions": [{"section": "Projects", "item_title": "Raft KV Store", "text": "Paired with two classmates"}],
    })

    bullets = res.json()["resume"]["sections"][0]["items"][0]["bullets"]
    assert bullets[0].endswith("with leader election")
    assert bullets[-1] == "Paired with two classmates"


def test_a_line_with_no_entry_named_is_never_attached_to_an_arbitrary_project():
    out = r.apply_to_resume(sample(), [], [], additions=[
        r.BulletAddition(section="Projects", text="Mentored two juniors through their first pull request"),
    ])

    # It belongs to the section, not to whichever project happened to be listed first.
    assert out.sections[0].items[0].bullets == sample().sections[0].items[0].bullets
    assert out.sections[0].lines[-1] == "Mentored two juniors through their first pull request"


def test_a_line_naming_an_entry_that_no_longer_exists_falls_back_to_the_section():
    out = r.apply_to_resume(sample(), [], [], additions=[
        r.BulletAddition(section="Projects", item_title="A project that was swapped out", text="Paired with a friend"),
    ])

    assert out.sections[0].items[0].bullets == sample().sections[0].items[0].bullets
    assert out.sections[0].lines[-1] == "Paired with a friend"
