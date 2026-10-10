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
  /** When on, a new match is tracked, tailored and its answers drafted before you look. */
  autoPrepare: boolean;
  autoResumeId: number | null;
  autoGithubUsername: string | null;
  createdAt: string;
  lastNotifiedAt: string | null;
  newMatches: number;
};

export type WatchInput = Omit<
  Watch,
  "id" | "active" | "createdAt" | "lastNotifiedAt" | "newMatches" | "autoPrepare" | "autoResumeId" | "autoGithubUsername"
> & {
  active?: boolean;
  autoPrepare?: boolean;
  autoResumeId?: number | null;
  autoGithubUsername?: string | null;
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

// --- Resume tailoring ------------------------------------------------------------------------------------------

export type ResumeDoc = { id: number; filename: string; kind: "TEX" | "TEXT"; chars: number; read: boolean; createdAt: string };

export type ResumeLink = { label: string; url: string };
export type ResumeItem = { title: string; subtitle: string; date: string; location: string; bullets: string[]; links: ResumeLink[] };
export type ResumeSection = { title: string; kind: string; items: ResumeItem[]; lines: string[] };
export type ResumeData = { name: string; headline: string; contact: string[]; sections: ResumeSection[] };

export type TailorEdit = {
  id: number;
  section: string;
  before: string;
  after: string;
  reason: string;
  /** Words the new wording adds that appear nowhere on the resume. */
  new_terms: string[];
  /** True when those words look like skills or tools the resume never claimed, or were lifted from the job ad. */
  unsupported: boolean;
  /** New words copied from the job description rather than from the resume. */
  from_job_ad: string[];
  /** "github" when the edit uses a detail from one of the person's repositories. */
  source: "resume" | "github";
  repo: string;
  repo_url: string;
  /** Words copied from that repository, proving the detail. */
  evidence: string;
};

export type TailorSwap = {
  id: number;
  section: string;
  replace_title: string;
  repo: string;
  repo_url: string;
  new_item: ResumeItem;
  reason: string;
  new_terms: string[];
  unsupported: boolean;
};

export type TailorLinkAddition = { id: number; section: string; item_title: string; repo: string; url: string };

export type TailorPlan = {
  summary: string;
  edits: TailorEdit[];
  reorder: { section: string; item_titles: string[] }[];
  swaps: TailorSwap[];
  link_additions: TailorLinkAddition[];
  matched_keywords: string[];
  missing_keywords: string[];
  dropped_edits: number;
  repos_used: string[];
  model: string;
};

export type TailorStep = "REVIEW_RESUME" | "REVIEW_EDITS" | "DONE";

export type TailorRun = {
  id: string;
  step: TailorStep;
  applicationId: number | null;
  postingId: number | null;
  resume: ResumeDoc | null;
  company: string | null;
  jobTitle: string | null;
  jobDescription: string | null;
  resumeData: ResumeData | null;
  plan: TailorPlan | null;
  acceptedIds: number[];
  compiled: boolean;
  hasPdf: boolean;
  compileLog: string | null;
  usedOwnTemplate: boolean;
  missedEdits: number[];
  githubUsername: string | null;
  instructions: string | null;
  onePage: boolean;
  model: "pro" | "flash" | null;
  pages: number | null;
  /** Lines you wrote yourself, in answer to something the job asks for. */
  additions: ResumeAddition[];
  /** Corrections you typed over the final resume. */
  manualEdits: ManualEdit[];
  /** The read-back check of the built PDF, once it's been run. */
  ats: AtsReport | null;
  atsCheckedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ResumeAddition = { section: string; item_title: string; text: string; gap: string };
export type ManualEdit = { section: string; before: string; after: string; reason: string };

/** Your own sentence, tightened into a bullet — or handed straight back when the model tried to embellish it. */
export type Phrasing = {
  bullet: string;
  new_terms: string[];
  /** True when the model reached for something you didn't say; `bullet` is then your own sentence. */
  unsupported: boolean;
};

/** What a parser gets out of the built PDF, and the ad's requirements counted against it. */
export type AtsReport = {
  parse: {
    pages: number;
    characters: number;
    has_text_layer: boolean;
    email: string;
    phone: string;
    links: number;
    sections_found: string[];
    sections_missing: string[];
    problems: string[];
    first_lines: string[];
  };
  coverage: {
    required_total: number;
    required_found: number;
    preferred_total: number;
    preferred_found: number;
    /** Share of the ad's required terms present in the extracted text. Not an employer's score. */
    required_percent: number;
    requirements: {
      term: string;
      importance: "required" | "preferred";
      quote: string;
      found: boolean;
      where: string;
    }[];
  } | null;
  extracted: string;
  model: string;
};

export type SuggestOptions = {
  resume: ResumeData | null;
  jobDescription: string;
  githubUsername: string;
  instructions: string;
  onePage: boolean;
  model: "pro" | "flash";
};

export const listResumes = (key: string) => request<ResumeDoc[]>("/api/tailor/resumes", keyed(key));
export const addResume = (key: string, body: { filename?: string; dataBase64?: string; text?: string }) =>
  request<ResumeDoc>("/api/tailor/resumes", keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const deleteResume = (key: string, id: number) =>
  request<void>(`/api/tailor/resumes/${id}`, keyed(key, { method: "DELETE" }));
export const getPostingDescription = (postingId: number) =>
  request<{ postingId: number; text: string }>(`/api/tailor/postings/${postingId}/description`);
/** May read the resume with Gemini the first time it's used (paid). */
export const startTailor = (key: string, body: { applicationId?: number; postingId?: number; resumeId: number }) =>
  request<TailorRun>("/api/tailor/runs", keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const getTailorRun = (key: string, id: string) => request<TailorRun>(`/api/tailor/runs/${id}`, keyed(key));
export const tailorRunsForApplication = (key: string, applicationId: number) =>
  request<TailorRun[]>(`/api/tailor/applications/${applicationId}/runs`, keyed(key));
/** Paid: one Gemini call proposing edits. */
export const suggestEdits = (key: string, id: string, body: SuggestOptions) =>
  request<TailorRun>(`/api/tailor/runs/${id}/suggest`, keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const buildTailored = (key: string, id: string, body: { acceptedIds: number[]; keepOrder: boolean }) =>
  request<TailorRun>(`/api/tailor/runs/${id}/build`, keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const rewindTailor = (key: string, id: string, step: TailorStep) =>
  request<TailorRun>(`/api/tailor/runs/${id}/rewind`, keyed(key, { method: "POST", body: JSON.stringify({ step }) }));
/** A draft that restates one of your own lines, with the line it restates. */
export type GapDraft = {
  text: string;
  /** The sentence from your resume or repository this rests on, quoted. */
  grounded_on: string;
  source: "resume" | "github";
  repo: string;
  repo_url: string;
  new_terms: string[];
};

export type GapIdeas = {
  /** Questions to jog your memory. They contain no claims. */
  questions: string[];
  drafts: GapDraft[];
  /** Drafts thrown away for quoting nothing real, or for adding to what they quoted. */
  dropped: number;
};

/** Paid: one cheap call. Nothing is added to the resume — you edit a draft and add it yourself. */
export const gapIdeas = (key: string, id: string, gap: string) =>
  request<{ ideas: GapIdeas; model: string }>(
    `/api/tailor/runs/${id}/gap-ideas`,
    keyed(key, { method: "POST", body: JSON.stringify({ gap }) }),
  );
/** No AI call: the line is yours, stored as you typed it. */
export const addResumeLine = (
  key: string,
  id: string,
  body: { section: string; itemTitle?: string; text: string; gap?: string },
) => request<TailorRun>(`/api/tailor/runs/${id}/lines`, keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const removeResumeLine = (key: string, id: string, index: number) =>
  request<TailorRun>(`/api/tailor/runs/${id}/lines/${index}`, keyed(key, { method: "DELETE" }));
/** A correction typed over the final resume. No AI call. */
export const editResumeLine = (key: string, id: string, body: { section?: string; before: string; after: string }) =>
  request<TailorRun>(`/api/tailor/runs/${id}/line-edit`, keyed(key, { method: "POST", body: JSON.stringify(body) }));
/** Paid: one cheap call tightening your sentence. It may add nothing you didn't write. */
export const phraseResumeLine = (key: string, id: string, sentence: string) =>
  request<{ phrasing: Phrasing; model: string }>(
    `/api/tailor/runs/${id}/phrase`,
    keyed(key, { method: "POST", body: JSON.stringify({ sentence }) }),
  );
/** The resume as it would be built. No AI call. */
export const previewResume = (key: string, id: string, acceptedIds?: number[]) =>
  request<{ resume: ResumeData }>(
    `/api/tailor/runs/${id}/preview`,
    keyed(key, { method: "POST", body: JSON.stringify({ acceptedIds: acceptedIds ?? null }) }),
  );
/** Paid when the run has a job description: one call lists what the ad asks for. The parse report is free. */
export const runAtsCheck = (key: string, id: string, body: { model?: "pro" | "flash" } = {}) =>
  request<TailorRun>(`/api/tailor/runs/${id}/ats`, keyed(key, { method: "POST", body: JSON.stringify(body) }));
export const tailorDownloadUrl = (key: string, id: string, format: "pdf" | "tex") =>
  `${baseUrl()}/api/tailor/runs/${id}/download?format=${format}&key=${encodeURIComponent(key)}`;


// --- apply kit -------------------------------------------------------------------------------------------------

export type ProfileExtra = { label: string; value: string };

/** Answers you give on every form, kept once. */
export type ApplicantProfile = {
  fullName: string;
  email: string;
  phone: string;
  location: string;
  githubUrl: string;
  linkedinUrl: string;
  portfolioUrl: string;
  workAuthorization: string;
  noticePeriod: string;
  expectedSalary: string;
  graduation: string;
  extras: ProfileExtra[];
  updatedAt?: string | null;
};

export const EMPTY_PROFILE: ApplicantProfile = {
  fullName: "",
  email: "",
  phone: "",
  location: "",
  githubUrl: "",
  linkedinUrl: "",
  portfolioUrl: "",
  workAuthorization: "",
  noticePeriod: "",
  expectedSalary: "",
  graduation: "",
  extras: [],
};

export type KitAnswer = {
  question: string;
  answer: string;
  /** The resume line or repository the answer rests on. */
  groundedOn?: string;
  grounded_on?: string;
  new_terms?: string[];
  /** True when the draft used words found in none of your sources — read it before sending. */
  unsupported?: boolean;
};

export type ApplyKit = {
  applicationId: number;
  company: string | null;
  role: string | null;
  applyUrl: string | null;
  profile: ApplicantProfile | null;
  answers: KitAnswer[] | null;
  model: string | null;
  tailorRunId: string | null;
  hasPdf: boolean;
  createdAt: string | null;
};

export const getProfile = (key: string) => request<ApplicantProfile | null>("/api/apply/profile", keyed(key));
export const saveProfile = (key: string, body: ApplicantProfile) =>
  request<ApplicantProfile>("/api/apply/profile", keyed(key, { method: "PUT", body: JSON.stringify(body) }));
export const getApplyKit = (key: string, applicationId: number) =>
  request<ApplyKit>(`/api/apply/kits/${applicationId}`, keyed(key));
/** Paid: one Gemini call drafting this job's answers. */
export const buildApplyKit = (key: string, applicationId: number, body: { githubUsername?: string; model?: "pro" | "flash" }) =>
  request<ApplyKit>(`/api/apply/kits/${applicationId}`, keyed(key, { method: "POST", body: JSON.stringify(body) }));
