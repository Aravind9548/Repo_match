"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  ApiError,
  approveRepos,
  approveShortlist,
  describeError,
  finalizeWorkflow,
  getWorkflow,
  type Workflow,
} from "@/lib/api";
import { RepoReview } from "./RepoReview";
import { ResultsReview } from "./ResultsReview";
import { ShortlistReview } from "./ShortlistReview";
import { statusesFor, WorkflowCanvas, type Busy } from "./WorkflowCanvas";

/** Lets the human step back to an earlier gate the backend still accepts. */
type Revisit = "repos" | "shortlist" | null;

export function WorkflowRunner({ initial }: { initial: Workflow }) {
  const router = useRouter();
  const [wf, setWf] = useState(initial);
  const [busy, setBusy] = useState<Busy | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revisit, setRevisit] = useState<Revisit>(null);

  async function run(kind: Busy, action: () => Promise<Workflow>) {
    setBusy(kind);
    setError(null);
    try {
      const next = await action();
      setWf(next);
      setRevisit(null);
      if (next.step === "DONE" && next.analysisId) router.push(`/results/${next.analysisId}`);
    } catch (e) {
      setError(describeError(e));
      // Someone moved this workflow on (another tab): show where it actually is now.
      if (e instanceof ApiError && e.problem.status === 409) {
        getWorkflow(wf.id).then(setWf, () => {});
      }
    } finally {
      setBusy(null);
    }
  }

  const panel =
    revisit === "repos" ? "REVIEW_REPOS" : revisit === "shortlist" ? "REVIEW_SHORTLIST" : wf.step;
  const login = wf.account?.login;

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-center gap-4">
        {wf.account?.avatarUrl && (
          <Image
            src={wf.account.avatarUrl}
            alt=""
            width={52}
            height={52}
            className="size-13 rounded-full border-2 border-card shadow ring-1 ring-line"
          />
        )}
        <div className="min-w-0 flex-1">
          <p className="font-hand text-lg text-accent">matching {login ?? "your"} repos to a job</p>
          <details className="group">
            <summary className="cursor-pointer list-none font-display text-2xl font-semibold leading-snug sm:text-3xl">
              <span className="line-clamp-1 group-open:line-clamp-none">{wf.jobDescription.split("\n")[0]}</span>
              <span className="mt-1 block text-sm font-normal text-muted font-sans">
                <span className="group-open:hidden">Show full job description ▾</span>
                <span className="hidden group-open:inline">Hide ▴</span>
              </span>
            </summary>
            <p className="mt-3 whitespace-pre-line rounded-xl border border-line bg-card p-4 text-sm leading-relaxed">
              {wf.jobDescription}
            </p>
          </details>
        </div>
      </header>

      <WorkflowCanvas
        sticky
        statuses={statusesFor(wf.step, busy)}
        caption="Nothing paid runs until you approve the shortlist."
      />

      {error && (
        <div role="alert" className="rounded-xl border border-accent/30 bg-accent-soft px-4 py-3 text-sm text-ink">
          {error}
        </div>
      )}

      {/* Re-keyed per gate so each new step slides in. */}
      <div key={panel} className="animate-rise">
      {panel === "REVIEW_REPOS" && (
        <RepoReview
          key={`repos-${wf.updatedAt}`}
          repos={wf.repos}
          busy={busy !== null}
          onApprove={(ids) => run("rank", () => approveRepos(wf.id, ids))}
        />
      )}

      {panel === "REVIEW_SHORTLIST" && (
        <ShortlistReview
          key={`shortlist-${wf.updatedAt}`}
          ranking={wf.ranking}
          busy={busy !== null}
          onApprove={(ids) => run("judge", () => approveShortlist(wf.id, ids))}
          onBack={wf.step === "REVIEW_SHORTLIST" ? () => setRevisit("repos") : undefined}
        />
      )}

      {panel === "REVIEW_RESULTS" && (
        <ResultsReview
          key={`results-${wf.updatedAt}`}
          judgements={wf.judgements}
          acceptedIds={wf.acceptedIds}
          usage={wf.usage}
          llmModel={wf.llmModel}
          busy={busy !== null}
          onApprove={(ids) => run("save", () => finalizeWorkflow(wf.id, ids))}
          onBack={() => setRevisit("shortlist")}
        />
      )}

      {panel === "DONE" && (
        <div className="rounded-2xl border border-moss/30 bg-moss-soft p-6 text-center">
          <p className="font-display text-2xl font-semibold">Saved.</p>
          <p className="mt-1 text-muted">Your picks are ready to share.</p>
          {wf.analysisId && (
            <Link href={`/results/${wf.analysisId}`} className="mt-4 inline-block font-semibold underline underline-offset-4">
              Open the results →
            </Link>
          )}
        </div>
      )}
      </div>
    </div>
  );
}
