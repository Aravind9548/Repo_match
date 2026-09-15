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
  if (res.status === 204) return undefined as T;
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

// --- Jobs, alerts and the application tracker ------------------------------------------------------------------

export type JobKind = "INTERNSHIP" | "NEW_GRAD" | "FULL_TIME";

export type Posting = {
  id: number;
  company: string;
  role: string;
  location: string | null;
  kind: JobKind;
  applyUrl: string;
  closed: boolean;
  ageLabel: string | null;
  firstSeenAt: string;
  /** When the employer published it, if the source says. */
  postedAt: string | null;
  country: string | null;
  remote: boolean;
  source: string | null;
  sourceType: "GITHUB_LIST" | "GREENHOUSE" | "LEVER" | "ASHBY" | "SMARTRECRUITERS" | "WORKDAY" | "GOOGLE_SEARCH" | null;
};

export type PostingPage = {
  items: Posting[];
  total: number;
  offset: number;
  limit: number;
  /** How the search was read, e.g. "internships · in India · “backend”". */
  understood: string;
  /** Where the matches are, ignoring the place filter — used to suggest a better search. */
  facets: { kinds: Partial<Record<JobKind, number>>; countries: Record<string, number>; remote: number };
};

export type ApplicationStatus =
  | "APPROVED"
  | "APPLIED"
  | "ASSESSMENT"
  | "INTERVIEW"
  | "OFFER"
  | "REJECTED"
  | "GHOSTED"
  | "WITHDRAWN";

export type ResumeChoice = "CURRENT" | "TAILORED";

export type ApplicationEvent = { id: number; status: ApplicationStatus | null; note: string | null; occurredAt: string };

export type Application = {
  id: number;
  postingId: number | null;
  company: string;
  role: string;
  applyUrl: string | null;
  status: ApplicationStatus;
  resumeChoice: ResumeChoice | null;
  nextStep: string | null;
  nextStepDue: string | null;
  notes: string | null;
  appliedAt: string | null;
  createdAt: string;
  updatedAt: string;
  events: ApplicationEvent[];
};

export type Watch = {
  id: number;
  name: string;
  email: string | null;
  keywords: string[];
  excludeKeywords: string[];
  locations: string[];
  kinds: JobKind[];
  active: boolean;
  createdAt: string;
  lastNotifiedAt: string | null;
  newMatches: number;
};

export type WatchInput = Omit<Watch, "id" | "active" | "createdAt" | "lastNotifiedAt" | "newMatches"> & {
  active?: boolean;
};

export type MatchStatus = "NEW" | "APPROVED" | "SKIPPED";

export type JobMatch = {
  id: number;
  watchId: number;
  watchName: string;
  status: MatchStatus;
  matchedAt: string;
  emailed: boolean;
  posting: Posting;
};

export type JobSource = {
  fullName: string;
  type: Posting["sourceType"];
  companyName: string | null;
  branch: string;
  enabled: boolean;
  lastCheckedAt: string | null;
  lastCount: number;
  lastError: string | null;
};

export type JobSearch = { q?: string; location?: string; kind?: JobKind | ""; sinceDays?: number; offset?: number; limit?: number };

const keyed = (key: string, init?: RequestInit): RequestInit => ({
  ...init,
  headers: { ...init?.headers, "X-Tracker-Key": key },
});

export function searchJobs(s: JobSearch) {
  const params = new URLSearchParams();
  if (s.q) params.set("q", s.q);
  if (s.location) params.set("location", s.location);
  if (s.kind) params.set("kind", s.kind);
  if (s.sinceDays) params.set("sinceDays", String(s.sinceDays));
  params.set("offset", String(s.offset ?? 0));
  params.set("limit", String(s.limit ?? 30));
  return request<PostingPage>(`/api/jobs?${params}`);
}

export const getPosting = (id: number, key: string | null) =>
  request<{ posting: Posting; application: Application | null }>(`/api/jobs/${id}`, key ? keyed(key) : undefined);
export const getJobSources = () => request<JobSource[]>("/api/jobs/sources");
export const refreshJobs = () => post<unknown>("/api/jobs/refresh", {});
export const newTrackerKey = () => post<{ key: string }>("/api/tracker/keys", {});

export const listWatches = (key: string) => request<Watch[]>("/api/alerts", keyed(key));
export const createWatch = (key: string, body: WatchInput) =>
  request<Watch>("/api/alerts", keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const updateWatch = (key: string, id: number, body: WatchInput) =>
  request<Watch>(`/api/alerts/${id}`, keyed(key, { method: "PATCH", body: JSON.stringify(body) }));
export const deleteWatch = (key: string, id: number) =>
  request<void>(`/api/alerts/${id}`, keyed(key, { method: "DELETE" }));
export const listMatches = (key: string, status?: MatchStatus) =>
  request<JobMatch[]>(`/api/alerts/matches${status ? `?status=${status}` : ""}`, keyed(key));

export const listApplications = (key: string) => request<Application[]>("/api/tracker/applications", keyed(key));
export const getApplication = (key: string, id: number) =>
  request<Application>(`/api/tracker/applications/${id}`, keyed(key));
export const approvePosting = (key: string, postingId: number) =>
  request<Application>(`/api/tracker/postings/${postingId}/approve`, keyed(key, { method: "POST", body: "{}" }));
export const skipPosting = (key: string, postingId: number) =>
  request<void>(`/api/tracker/postings/${postingId}/skip`, keyed(key, { method: "POST", body: "{}" }));
export const addApplication = (key: string, body: { company: string; role: string; applyUrl?: string }) =>
  request<Application>("/api/tracker/applications", keyed(key, { method: "POST", body: JSON.stringify(body) }));
export type ApplicationUpdate = Partial<{
  status: ApplicationStatus;
  resumeChoice: ResumeChoice;
  nextStep: string;
  nextStepDue: string | null;
  clearNextStep: boolean;
  notes: string;
  eventNote: string;
}>;
export const updateApplication = (key: string, id: number, body: ApplicationUpdate) =>
  request<Application>(`/api/tracker/applications/${id}`, keyed(key, { method: "PATCH", body: JSON.stringify(body) }));
export const deleteApplication = (key: string, id: number) =>
  request<void>(`/api/tracker/applications/${id}`, keyed(key, { method: "DELETE" }));

export type WebJob = {
  title: string;
  company: string;
  location: string;
  posted: string;
  url: string;
  /** "open": the page loaded; "blocked": the site refuses automated checks, open it yourself. */
  status: "open" | "blocked";
  /** Set when the same job is already in the feed. */
  postingId: number | null;
};

export type WebSearchResult = { query: string; jobs: WebJob[]; dropped: number; searchQueries: string[]; model: string };

/** Paid (one grounded Gemini + Google Search call) — only on an explicit click. */
export const webSearchJobs = (body: { q: string; location: string; kind: JobKind | "" }) =>
  post<WebSearchResult>("/api/jobs/web-search", { ...body, kind: body.kind || null });
