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

export type AnalyzeRequest = {
  username: string;
  jobDescription: string;
  topK?: number;
  includeForks?: boolean;
  refresh?: boolean;
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

export function getAnalysis(id: string) {
  return request<Analysis>(`/api/analyses/${encodeURIComponent(id)}`);
}

export function analyze(body: AnalyzeRequest) {
  return request<Analysis>("/api/analyze", { method: "POST", body: JSON.stringify(body) });
}

/** A human sentence for each failure mode the backend can report. */
export function describeError(err: unknown): string {
  if (!(err instanceof ApiError)) return "Something went wrong. Please try again.";
  const { status, detail, resetAt } = err.problem;
  switch (status) {
    case 0:
      return detail!;
    case 404:
      return detail ?? "That GitHub user doesn't exist.";
    case 422:
      return detail ?? "This user has no public repositories to analyze.";
    case 429: {
      const when = resetAt ? ` Try again after ${new Date(resetAt).toLocaleTimeString()}.` : " Try again later.";
      return `GitHub's API rate limit was reached.${when}`;
    }
    case 503:
      return "The AI service is unavailable right now. Please try again in a minute.";
    default:
      return detail ?? "Something went wrong. Please try again.";
  }
}
