from pydantic import BaseModel, Field


class RankCandidate(BaseModel):
    id: int
    text: str = Field(min_length=1)
    # A previously computed embedding, re-used when it was made by the current model.
    embedding: list[float] | None = None
    embedding_model: str | None = None


class RankRequest(BaseModel):
    job_description: str = Field(min_length=1)
    repos: list[RankCandidate] = Field(min_length=1, max_length=500)


class RankedRepo(BaseModel):
    id: int
    similarity: float
    # Only returned for repos embedded in this request, so the caller can cache it.
    embedding: list[float] | None = None


class RankResponse(BaseModel):
    embedding_model: str
    results: list[RankedRepo]


class ExplainCandidate(BaseModel):
    id: int
    name: str
    description: str | None = None
    languages: list[str] = []
    topics: list[str] = []
    readme: str | None = None
    similarity: float | None = None


class ExplainRequest(BaseModel):
    job_description: str = Field(min_length=1)
    repos: list[ExplainCandidate] = Field(min_length=1, max_length=15)


class RepoExplanation(BaseModel):
    id: int
    fit_score: int = Field(ge=0, le=100)
    reason: str
    matched_requirements: list[str] = []
    evidence: list[str] = []


class TokenUsage(BaseModel):
    prompt_tokens: int = 0
    output_tokens: int = 0
    thinking_tokens: int = 0


class ExplainResponse(BaseModel):
    llm_model: str
    results: list[RepoExplanation]
    usage: TokenUsage | None = None


# --- Ask your codebase ------------------------------------------------------


class EmbedRequest(BaseModel):
    """General-purpose embedding, used to index repository code chunks."""

    texts: list[str] = Field(min_length=1, max_length=400)
    # "document" for stored content, "query" for a question.
    kind: str = Field(default="document", pattern="^(document|query)$")


class EmbedResponse(BaseModel):
    model: str
    vectors: list[list[float]]


class CodeChunk(BaseModel):
    path: str
    start_line: int = Field(ge=1)
    end_line: int = Field(ge=1)
    text: str


class AnswerRequest(BaseModel):
    repo: str
    question: str = Field(min_length=3, max_length=1000)
    chunks: list[CodeChunk] = Field(min_length=1, max_length=40)


class Citation(BaseModel):
    path: str
    start_line: int
    end_line: int


class ReadmeRequest(BaseModel):
    repo: str
    description: str | None = None
    languages: list[str] = []
    chunks: list[CodeChunk] = Field(min_length=1, max_length=40)


class ReadmeResponse(BaseModel):
    llm_model: str
    markdown: str
    files_used: int = 0
    usage: TokenUsage | None = None


class AnswerResponse(BaseModel):
    llm_model: str
    answer: str
    citations: list[Citation] = []
    # False when the retrieved code doesn't contain the answer.
    answered: bool = True
    usage: TokenUsage | None = None


# --- Web job search (Gemini + Google Search) ---------------------------------


class WebSearchRequest(BaseModel):
    query: str = Field(min_length=3, max_length=300)
    limit: int = Field(default=12, ge=1, le=20)


class WebJob(BaseModel):
    title: str
    company: str = ""
    location: str = ""
    url: str
    posted: str = ""


class WebSource(BaseModel):
    title: str = ""
    url: str


class WebSearchResponse(BaseModel):
    model: str
    jobs: list[WebJob] = []
    # Pages Google returned for the grounded answer.
    sources: list[WebSource] = []
    search_queries: list[str] = []
    usage: TokenUsage | None = None
