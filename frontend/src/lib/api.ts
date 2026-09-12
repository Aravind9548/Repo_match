// Typed client for the RepoMatch Spring Boot API.

export type AccountSummary = {
  login: string;
  name: string | null;
  avatarUrl: string | null;
  htmlUrl: string | null;
  syncedAt: string;
};

export type MatchResult = {
  rank: number;
  repoId: number;
  name: string;
  url: string;
  description: string | null;
  languages: string[];
  topics: string[];
  stars: number;
  hasReadme: boolean;
  /** Raw cosine similarity between JD and repo embeddings. */
  similarity: number;
  /** 0-100 LLM judgement grounded in repo content; null when the LLM was unavailable. */
  fitScore: number | null;
  /** 0-100 final score used for ordering. */
  matchScore: number;
  reason: string | null;
  matchedRequirements: string[];
  /** Verbatim snippets from the repo, verified to exist in it. */
  evidence: string[];
};

export type OtherRepo = {
  rank: number;
  repoId: number;
  name: string;
  url: string;
  similarity: number;
};

export type Analysis = {
  id: string;
  account: AccountSummary | null;
  jobDescription: string;
  reposAnalyzed: number;
  embeddingModel: string;
  llmModel: string | null;
  matches: MatchResult[];
  otherRepos: OtherRepo[];
  warnings: string[];
  createdAt: string;
};

export type AnalysisSummary = {
  id: string;
  jobDescriptionPreview: string;
  topRepos: string[];
  createdAt: string;
};

export type AnalyzeRequest = {
  username: string;
  jobDescription: string;
  topK?: number;
  includeForks?: boolean;
  refresh?: boolean;
};

// --- Human-in-the-loop workflow -------------------------------------------

export type WorkflowStep = "REVIEW_REPOS" | "REVIEW_SHORTLIST" | "REVIEW_RESULTS" | "DONE";

export type RepoOption = {
  id: number;
  name: string;
  fullName: string;
  url: string;
  description: string | null;
  languages: string[];
  stars: number;
  fork: boolean;
  hasReadme: boolean;
  pushedAt: string | null;
  selected: boolean;
};

export type RankedOption = {
  id: number;
  name: string;
  url: string;
  similarity: number;
  hasReadme: boolean;
  shortlisted: boolean;
};

export type TokenUsage = { promptTokens: number; outputTokens: number; thinkingTokens: number };

export type Workflow = {
  id: string;
  account: AccountSummary | null;
  jobDescription: string;
  topK: number;
  step: WorkflowStep;
  repos: RepoOption[];
  ranking: RankedOption[];
  judgements: MatchResult[];
  acceptedIds: number[];
  embeddingModel: string | null;
  llmModel: string | null;
  usage: TokenUsage | null;
  warnings: string[];
  analysisId: string | null;
  createdAt: string;
  updatedAt: string;
};

// --- Ask your codebase ------------------------------------------------------

export type RepoSummary = {
  id: number;
  name: string;
  url: string;
  description: string | null;
  languages: string[];
  stars: number;
  fork: boolean;
  pushedAt: string | null;
  readmeChars: number;
};

export type RepoListResponse = {
  account: AccountSummary;
  fromCache: boolean;
  repos: RepoSummary[];
};

export type IndexSummary = {
  repoId: number;
  repo: string;
  commitSha: string | null;
  fileCount: number;
  chunkCount: number;
  skippedFiles: number;
  model: string | null;
  indexedAt: string;
  rebuilt: boolean;
};

export type CodeSource = {
  path: string;
  startLine: number;
  endLine: number;
  /** The indexed code behind a citation; null for the "searched" list. */
  snippet: string | null;
  similarity: number;
};

export type CodeAnswer = {
  repoId: number;
  repo: string;
  question: string;
  answer: string;
  /** False when the indexed code doesn't contain the answer. */
  answered: boolean;
  citations: CodeSource[];
  searched: CodeSource[];
  llmModel: string | null;
  usage: TokenUsage | null;
};

export type ReadmeDraft = {
  repoId: number;
  repo: string;
  markdown: string;
  /** True when the repo already has a README — the draft would replace it. */
  hadReadme: boolean;
  filesUsed: number;
  sources: string[];
  llmModel: string | null;
  usage: TokenUsage | null;
};

/** RFC 7807 problem document returned by the backend on errors. */
export type Problem = {
  status: number;
  title?: string;
  detail?: string;
  fieldErrors?: Record<string, string>;
  resetAt?: string;
};

export class ApiError extends Error {
  constructor(public readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`);
  }
}

// Server components talk to the backend directly; the browser uses the public URL.
const SERVER_BASE_URL =
  process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8081";
const BROWSER_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

function baseUrl() {
  return typeof window === "undefined" ? SERVER_BASE_URL : BROWSER_BASE_URL;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${baseUrl()}${path}`, {
      ...init,
      cache: "no-store",
      headers: { "Content-Type": "application/json", ...init?.headers },
    });
  } catch {
    throw new ApiError({
      status: 0,
      title: "Backend unreachable",
      detail: "Could not reach the RepoMatch API. Is the backend running?",
    });
  }
  if (!res.ok) {
    let problem: Problem = { status: res.status };
    try {
      problem = { ...problem, ...((await res.json()) as Partial<Problem>) };
    } catch {
      // non-JSON error body; keep the status only
    }
    throw new ApiError(problem);
  }
  return (await res.json()) as T;
}

const post = <T>(path: string, body: unknown) => request<T>(path, { method: "POST", body: JSON.stringify(body) });

export const getAnalysis = (id: string) => request<Analysis>(`/api/analyses/${encodeURIComponent(id)}`);
export const getHistory = (username: string) =>
  request<AnalysisSummary[]>(`/api/analyses?username=${encodeURIComponent(username)}`);

export const createWorkflow = (body: AnalyzeRequest) => post<Workflow>("/api/workflows", body);
export const getWorkflow = (id: string) => request<Workflow>(`/api/workflows/${encodeURIComponent(id)}`);
export const approveRepos = (id: string, repoIds: number[]) => post<Workflow>(`/api/workflows/${id}/repos`, { repoIds });
export const approveShortlist = (id: string, repoIds: number[]) =>
  post<Workflow>(`/api/workflows/${id}/shortlist`, { repoIds });
export const finalizeWorkflow = (id: string, repoIds: number[]) =>
  post<Workflow>(`/api/workflows/${id}/finalize`, { repoIds });
/** Go back to an earlier review gate, keeping the decisions made there. */
export const rewindWorkflow = (id: string, step: WorkflowStep) => post<Workflow>(`/api/workflows/${id}/rewind`, { step });

export const listRepos = (username: string) => request<RepoListResponse>(`/api/repos/${encodeURIComponent(username)}`);
export const getIndexStatus = (repoId: number) => request<IndexSummary>(`/api/repos/${repoId}/index`);
export const indexRepo = (repoId: number, force = false) =>
  post<IndexSummary>(`/api/repos/${repoId}/index?force=${force}`, {});
export const askRepo = (repoId: number, question: string) => post<CodeAnswer>(`/api/repos/${repoId}/ask`, { question });
/** Drafts a README from the indexed code. Paid step — only call on an explicit click. */
export const draftReadme = (repoId: number) => post<ReadmeDraft>(`/api/repos/${repoId}/readme`, {});

/** GitHub's generated social-preview card for a repo ("owner/name"). */
export const repoImageUrl = (fullName: string) => `https://opengraph.githubassets.com/1/${fullName}`;

/** A human sentence for each failure mode the backend can report. */
export function describeError(err: unknown): string {
  if (!(err instanceof ApiError)) return "Something went wrong. Please try again.";
  const { status, title, detail, resetAt } = err.problem;
  switch (status) {
    case 0:
      return detail!;
    case 404:
      return detail ?? "That GitHub user doesn't exist.";
    case 409:
      return detail ?? "This step was already completed. Reload to see the latest state.";
    case 422:
      return detail ?? "This user has no public repositories to analyze.";
    case 429: {
      if (title === "Usage limit reached") return detail ?? "Usage limit reached. Please try again later.";
      const when = resetAt ? ` Try again after ${new Date(resetAt).toLocaleTimeString()}.` : " Try again later.";
      return `GitHub's API rate limit was reached.${when}`;
    }
    case 503:
      return "The AI service is unavailable right now. Please try again in a minute.";
    default:
      return detail ?? "Something went wrong. Please try again.";
  }
}
