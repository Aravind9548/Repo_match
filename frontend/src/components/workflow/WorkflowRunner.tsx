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
  rewindWorkflow,
  type Workflow,
  type WorkflowStep,
} from "@/lib/api";
import { RepoReview } from "./RepoReview";
import { ResultsReview } from "./ResultsReview";
import { TextButton } from "./ReviewFooter";
import { ShortlistReview } from "./ShortlistReview";
import { statusesFor, WorkflowCanvas, type Busy } from "./WorkflowCanvas";

export function WorkflowRunner({ initial }: { initial: Workflow }) {
  const router = useRouter();
  const [wf, setWf] = useState(initial);
  const [busy, setBusy] = useState<Busy | null>(null);
  const [rewinding, setRewinding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Runs one workflow action; `kind` animates the matching canvas node (null = quick navigation). */
  async function run(kind: Busy | null, action: () => Promise<Workflow>) {
    if (kind) setBusy(kind);
    else setRewinding(true);
    setError(null);
    try {
      const next = await action();
      setWf(next);
      if (next.step === "DONE" && next.analysisId) router.push(`/results/${next.analysisId}`);
    } catch (e) {
      setError(describeError(e));
      // Someone moved this workflow on (another tab): show where it actually is now.
      if (e instanceof ApiError && e.problem.status === 409) {
        getWorkflow(wf.id).then(setWf, () => {});
      }
    } finally {
      setBusy(null);
      setRewinding(false);
    }
  }

  const goBack = (step: WorkflowStep) => run(null, () => rewindWorkflow(wf.id, step));
  const locked = busy !== null || rewinding;
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
              <span className="mt-1 block font-sans text-sm font-normal text-muted">
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
        step={wf.step}
        onGoBack={goBack}
        disabled={locked}
        caption="Nothing paid runs until you approve the shortlist."
      />

      {error && (
        <div role="alert" className="rounded-xl border border-accent/40 bg-accent-soft px-4 py-3 text-sm text-ink">
          {error}
        </div>
      )}

      {/* Re-keyed per gate so each new step slides in. */}
      <div key={`${wf.step}-${wf.updatedAt}`} className={`animate-rise ${rewinding ? "pointer-events-none opacity-60" : ""}`}>
        {wf.step === "REVIEW_REPOS" && (
          <RepoReview repos={wf.repos} busy={locked} onApprove={(ids) => run("rank", () => approveRepos(wf.id, ids))} />
        )}

        {wf.step === "REVIEW_SHORTLIST" && (
          <ShortlistReview
            ranking={wf.ranking}
            busy={locked}
            onApprove={(ids) => run("judge", () => approveShortlist(wf.id, ids))}
            onBack={() => goBack("REVIEW_REPOS")}
          />
        )}

        {wf.step === "REVIEW_RESULTS" && (
          <ResultsReview
            judgements={wf.judgements}
            acceptedIds={wf.acceptedIds}
            usage={wf.usage}
            llmModel={wf.llmModel}
            busy={locked}
            onApprove={(ids) => run("save", () => finalizeWorkflow(wf.id, ids))}
            onBack={() => goBack("REVIEW_SHORTLIST")}
          />
        )}

        {wf.step === "DONE" && (
          <div className="rounded-2xl border border-moss/30 bg-moss-soft p-6 text-center">
            <p className="font-display text-2xl font-semibold">Saved.</p>
            <p className="mt-1 text-muted">Your picks are ready to share.</p>
            <div className="mt-4 flex flex-wrap items-center justify-center gap-5">
              {wf.analysisId && (
                <Link href={`/results/${wf.analysisId}`} className="font-semibold underline underline-offset-4">
                  Open the results →
                </Link>
              )}
              <TextButton onClick={() => goBack("REVIEW_RESULTS")} disabled={locked}>
                ↺ Change my picks
              </TextButton>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
