import re

from fastapi.testclient import TestClient

from app.main import app, get_embedder, get_explainer
from app.schemas import RepoExplanation, TokenUsage

VOCAB = ["java", "spring", "react", "typescript", "python", "kubernetes"]


class FakeEmbedder:
    """Bag-of-words over a tiny vocabulary: deterministic and offline."""

    model = "fake-embed"
    dim = len(VOCAB)

    def __init__(self):
        self.embedded_docs = 0

    def _vec(self, text):
        words = re.findall(r"[a-z]+", text.lower())
        return [float(words.count(w)) for w in VOCAB]

    async def embed_documents(self, texts):
        self.embedded_docs += len(texts)
        return [self._vec(t) for t in texts]

    async def embed_query(self, text):
        return self._vec(text)


class FakeExplainer:
    model = "fake-llm"

    async def explain(self, jd, repos):
        results = [RepoExplanation(id=r.id, fit_score=50, reason=f"{r.name} fits.") for r in repos]
        return results, TokenUsage(prompt_tokens=10, output_tokens=5, thinking_tokens=2)


def _client(embedder):
    app.dependency_overrides[get_embedder] = lambda: embedder
    app.dependency_overrides[get_explainer] = lambda: FakeExplainer()
    return TestClient(app)


def test_rank_orders_by_similarity_and_returns_new_embeddings():
    embedder = FakeEmbedder()
    client = _client(embedder)
    resp = client.post(
        "/rank",
        json={
            "job_description": "Java Spring backend engineer",
            "repos": [
                {"id": 1, "text": "React TypeScript frontend"},
                {"id": 2, "text": "Java Spring Boot service"},
            ],
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["embedding_model"] == "fake-embed"
    assert [r["id"] for r in body["results"]] == [2, 1]
    assert all(len(r["embedding"]) == len(VOCAB) for r in body["results"])


def test_rank_reuses_cached_embeddings_from_same_model():
    embedder = FakeEmbedder()
    client = _client(embedder)
    cached = [1.0, 1.0, 0, 0, 0, 0]
    resp = client.post(
        "/rank",
        json={
            "job_description": "Java Spring",
            "repos": [
                {"id": 1, "text": "anything", "embedding": cached, "embedding_model": "fake-embed"},
                {"id": 2, "text": "python", "embedding": cached, "embedding_model": "old-model"},
            ],
        },
    )
    body = resp.json()
    assert embedder.embedded_docs == 1  # only the stale-model repo was re-embedded
    by_id = {r["id"]: r for r in body["results"]}
    assert "embedding" not in by_id[1]
    assert "embedding" in by_id[2]


def test_explain_returns_llm_model_name():
    client = _client(FakeEmbedder())
    resp = client.post(
        "/explain",
        json={"job_description": "Java", "repos": [{"id": 7, "name": "docuflow"}]},
    )
    assert resp.status_code == 200
    assert resp.json() == {
        "llm_model": "fake-llm",
        "results": [{"id": 7, "fit_score": 50, "reason": "docuflow fits.", "matched_requirements": [], "evidence": []}],
        "usage": {"prompt_tokens": 10, "output_tokens": 5, "thinking_tokens": 2},
    }


def test_rank_validates_input():
    client = _client(FakeEmbedder())
    assert client.post("/rank", json={"job_description": "x", "repos": []}).status_code == 422
