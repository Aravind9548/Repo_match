"""What the gap helper is allowed to hand a person — and everything it must throw away."""

from fastapi.testclient import TestClient

from app import gapideas as g
from app import resume as r
from app.main import app, get_gap_ideas_ai


def sample() -> r.Resume:
    return r.Resume(
        name="Asha Rao",
        contact=["asha@example.com"],
        sections=[
            r.ResumeSection(
                title="Projects",
                kind="projects",
                items=[r.ResumeItem(title="Raft KV Store", bullets=[
                    "Built a Raft-replicated key-value store in C++17 with a classmate",
                    "Wrote the test suite before the storage layer",
                ])],
            ),
        ],
    )


def repo() -> r.RepoEvidence:
    return r.RepoEvidence(
        name="raft-kv",
        url="https://github.com/asha/raft-kv",
        description="A small Raft key-value store",
        languages=["C++"],
        readme="We rewrote the client API after watching two people fail to use the first one.",
    )


def test_a_draft_that_restates_a_real_resume_line_is_kept():
    ideas = g.GapIdeas(
        questions=["Did you ever change what you were building after someone used it?"],
        drafts=[g.GapDraft(text="Wrote the test suite before building the storage layer",
                           grounded_on="Wrote the test suite before the storage layer", source="resume")],
    )

    out = g.check_ideas(ideas, sample(), [])

    assert out.dropped == 0
    assert out.drafts[0].text.startswith("Wrote the test suite")
    assert out.drafts[0].source == "resume"
    assert out.questions == ["Did you ever change what you were building after someone used it?"]


def test_a_draft_quoting_a_repository_keeps_the_repository_it_came_from():
    ideas = g.GapIdeas(drafts=[g.GapDraft(
        text="Rewrote the client API after watching two people fail to use the first one",
        grounded_on="We rewrote the client API after watching two people fail to use the first one.",
        source="github", repo="raft-kv")])

    out = g.check_ideas(ideas, sample(), [repo()])

    assert out.drafts[0].repo == "raft-kv"
    assert out.drafts[0].repo_url == "https://github.com/asha/raft-kv"


def test_a_draft_whose_quote_is_not_in_the_source_is_thrown_away():
    ideas = g.GapIdeas(drafts=[g.GapDraft(text="Led the product direction for the team",
                                          grounded_on="Led the product direction for the team", source="resume")])

    out = g.check_ideas(ideas, sample(), [])

    assert out.drafts == []
    assert out.dropped == 1


def test_a_draft_that_adds_to_its_own_quote_is_thrown_away():
    ideas = g.GapIdeas(drafts=[g.GapDraft(
        text="Built a Raft-replicated key-value store in C++17 serving 10k requests a second on Kubernetes",
        grounded_on="Built a Raft-replicated key-value store in C++17 with a classmate", source="resume")])

    out = g.check_ideas(ideas, sample(), [])

    assert out.drafts == []  # the scale and the platform are not in the quote
    assert out.dropped == 1


def test_a_github_draft_quoting_the_wrong_repository_is_thrown_away():
    ideas = g.GapIdeas(drafts=[g.GapDraft(text="Rewrote the client API after watching people struggle",
                                          grounded_on="We rewrote the client API after watching two people fail to use the first one.",
                                          source="github", repo="some-other-repo")])

    assert g.check_ideas(ideas, sample(), [repo()]).dropped == 1


def test_questions_survive_even_when_every_draft_is_thrown_away():
    ideas = g.GapIdeas(
        questions=["Have you ever dropped a feature on purpose?", "  "],
        drafts=[g.GapDraft(text="Shipped a product used by thousands", grounded_on="not in the resume at all")],
    )

    out = g.check_ideas(ideas, sample(), [])

    assert out.questions == ["Have you ever dropped a feature on purpose?"]
    assert out.drafts == []


def test_endpoint_returns_checked_ideas():
    class FakeAi:
        model = "fake"

        async def ideas(self, gap, job_title, job_description, resume, repos, model=None):
            return g.GapIdeas(
                questions=["Did anyone else use what you built?"],
                drafts=[
                    g.GapDraft(text="Wrote the test suite before the storage layer existed",
                               grounded_on="Wrote the test suite before the storage layer", source="resume"),
                    g.GapDraft(text="Cut deployment time by 60% with Terraform",
                               grounded_on="Wrote the test suite before the storage layer", source="resume"),
                ],
            ), None, "fake-model"

    app.dependency_overrides[get_gap_ideas_ai] = lambda: FakeAi()
    try:
        res = TestClient(app).post("/resume/gap-ideas", json={
            "gap": "product thinking",
            "resume": sample().model_dump(),
        })
    finally:
        app.dependency_overrides.pop(get_gap_ideas_ai, None)

    body = res.json()["ideas"]
    assert res.status_code == 200
    assert len(body["drafts"]) == 1  # the Terraform one invented both the tool and the number
    assert body["dropped"] == 1
    assert body["questions"] == ["Did anyone else use what you built?"]


def test_a_paraphrased_quote_still_finds_the_real_sentence_and_cites_it():
    # The model rarely copies its source exactly. The pointer is followed; the person's own sentence is shown.
    ideas = g.GapIdeas(drafts=[g.GapDraft(
        text="Wrote the tests before building the storage layer",
        grounded_on="wrote a test suite before the storage layer was written", source="resume")])

    out = g.check_ideas(ideas, sample(), [])

    assert out.dropped == 0
    assert out.drafts[0].grounded_on == "Wrote the test suite before the storage layer"  # theirs, not the model's


def test_a_draft_says_which_entry_it_belongs_under():
    ideas = g.GapIdeas(drafts=[g.GapDraft(
        text="Built a Raft-replicated key-value store with a classmate",
        grounded_on="Built a Raft-replicated key-value store in C++17 with a classmate", source="resume")])

    draft = g.check_ideas(ideas, sample(), []).drafts[0]

    assert draft.section == "Projects"
    assert draft.item_title == "Raft KV Store"


def test_a_repository_draft_is_filed_under_the_entry_that_repository_is_written_up_as():
    ideas = g.GapIdeas(drafts=[g.GapDraft(
        text="Rewrote the client API after watching two people fail to use it",
        grounded_on="rewrote the client API after watching people fail to use the first one",
        source="github", repo="raft-kv")])

    draft = g.check_ideas(ideas, sample(), [repo()]).drafts[0]

    assert draft.repo == "raft-kv"
    assert (draft.section, draft.item_title) == ("Projects", "Raft KV Store")
    assert "rewrote the client api" in draft.grounded_on.lower()


def test_a_pointer_to_nothing_of_theirs_is_still_thrown_away():
    ideas = g.GapIdeas(drafts=[g.GapDraft(
        text="Led the product roadmap for a payments team",
        grounded_on="owned the product roadmap and prioritised the payments backlog", source="resume")])

    out = g.check_ideas(ideas, sample(), [])

    assert out.drafts == [] and out.dropped == 1
