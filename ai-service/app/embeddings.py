from typing import Protocol

from google import genai
from google.genai import types

# Vertex embedding quotas count requests per minute, so a few large requests beat many small ones.
# ~48k chars stays well under the per-request token limit even for code-heavy READMEs.
MAX_BATCH_CHARS = 48_000
MAX_BATCH_ITEMS = 100


class Embedder(Protocol):
    model: str
    dim: int

    async def embed_documents(self, texts: list[str]) -> list[list[float]]: ...

    async def embed_query(self, text: str) -> list[float]: ...


def batch_by_chars(texts: list[str], max_chars: int = MAX_BATCH_CHARS, max_items: int = MAX_BATCH_ITEMS) -> list[list[int]]:
    """Group text indices into request-sized batches, preserving order."""
    batches: list[list[int]] = []
    current: list[int] = []
    size = 0
    for i, text in enumerate(texts):
        if current and (size + len(text) > max_chars or len(current) >= max_items):
            batches.append(current)
            current, size = [], 0
        current.append(i)
        size += len(text)
    if current:
        batches.append(current)
    return batches


class GeminiEmbedder:
    """Gemini embeddings with asymmetric task types: repos are documents, the JD is the query."""

    def __init__(self, client: genai.Client, model: str, dim: int):
        self._client = client
        self.model = model
        self.dim = dim

    async def _embed(self, texts: list[str], task_type: str) -> list[list[float]]:
        resp = await self._client.aio.models.embed_content(
            model=self.model,
            contents=texts,
            config=types.EmbedContentConfig(task_type=task_type, output_dimensionality=self.dim),
        )
        return [list(e.values) for e in resp.embeddings]

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for batch in batch_by_chars(texts):  # sequential on purpose, see MAX_BATCH_CHARS
            vectors.extend(await self._embed([texts[i] for i in batch], "RETRIEVAL_DOCUMENT"))
        return vectors

    async def embed_query(self, text: str) -> list[float]:
        return (await self._embed([text], "RETRIEVAL_QUERY"))[0]
