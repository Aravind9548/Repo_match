"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  approvePosting,
  describeError,
  getPosting,
  skipPosting,
  updateApplication,
  type Application,
  type Posting,
  type ResumeChoice,
} from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";
import { StatusBadge, kindLabel, timeAgo } from "@/components/jobs/ui";

type Stage = "decide" | "resume" | "apply" | "tracked";

const STAGES: { id: Stage; label: string }[] = [
  { id: "decide", label: "Approve" },
  { id: "resume", label: "Resume" },
  { id: "apply", label: "Submit" },
  { id: "tracked", label: "Track" },
];

function stageFor(app: Application | null): Stage {
  if (!app) return "decide";
  if (app.status !== "APPROVED") return "tracked";
  return app.resumeChoice ? "apply" : "resume";
}

/**
 * The decision flow for one posting, one gate at a time: approve → choose a resume → submit it yourself → tracked.
 * Nothing is ever submitted for the person; the last gate only records that they did.
 */
export function JobDecision({ postingId }: { postingId: number }) {
  const { key, ready } = useTrackerKey({ create: true });
  const [posting, setPosting] = useState<Posting | null>(null);
  const [app, setApp] = useState<Application | null>(null);
  const [skipped, setSkipped] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await getPosting(postingId, key);
      setPosting(res.posting);
      setApp(res.application);
    } catch (err) {
      setError(describeError(err));
    }
  }, [postingId, key]);

  useEffect(() => {
    if (ready) load();
  }, [ready, load]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  const stage = stageFor(app);
  const stageIndex = STAGES.findIndex((s) => s.id === stage);

  if (error && !posting) {
    return (
      <div role="alert" className="border border-accent/40 bg-accent-soft px-5 py-4">
        {error}
      </div>
    );
  }
  if (!posting) return <p className="label">Loading posting…</p>;

  return (
    <div className="grid gap-10 lg:grid-cols-[1fr_380px]">
      <article>
        <div className="flex flex-wrap items-center gap-3">
          <span className="chip">{kindLabel(posting.kind)}</span>
          {posting.closed && <span className="chip-teal">Closed</span>}
          {app && <StatusBadge status={app.status} />}
        </div>
        <h1 className="display mt-6 text-[clamp(1.8rem,3.6vw,2.8rem)]">{posting.role}</h1>
        <p className="mt-4 text-xl text-body">{posting.company}</p>

        <dl className="mt-10 grid grid-cols-2 gap-px border border-line bg-line sm:grid-cols-3">
          {[
            ["Location", posting.location ?? "Not listed"],
            ["Listed", timeAgo(posting.firstSeenAt)],
            ["Found on", posting.source ?? "—"],
          ].map(([k, v]) => (
            <div key={k} className="bg-paper px-5 py-4">
              <dt className="label">{k}</dt>
              <dd className="mt-2 truncate text-sm text-ink" title={v}>
                {v}
              </dd>
            </div>
          ))}
        </dl>

        <a
          href={posting.applyUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-6 inline-block max-w-full truncate font-mono text-xs text-accent-2 hover:underline"
        >
          {posting.applyUrl}
        </a>

        {app && app.events.length > 0 && (
          <div className="mt-12">
            <p className="label">History</p>
            <ol className="mt-4 space-y-3 border-l border-line pl-5">
              {app.events.slice(0, 5).map((e) => (
                <li key={e.id} className="text-sm">
                  <span className="text-ink">{e.note ?? e.status}</span>
                  <span className="label ml-3">{timeAgo(e.occurredAt)}</span>
                </li>
              ))}
            </ol>
          </div>
        )}
      </article>

      <aside className="lg:sticky lg:top-24 lg:self-start">
        <ol className="grid grid-cols-4 border border-line">
          {STAGES.map((s, i) => (
            <li
              key={s.id}
              className={`border-r border-line px-2 py-3 text-center font-mono text-[10px] uppercase tracking-[0.14em] last:border-r-0 ${
                i === stageIndex ? "bg-accent text-white" : i < stageIndex ? "bg-accent-soft text-ink" : "text-muted"
              }`}
            >
              {i < stageIndex ? "✓ " : ""}
              {s.label}
            </li>
          ))}
        </ol>

        <div key={stage} className="animate-rise border border-t-0 border-line bg-card p-6">
          {stage === "decide" && !skipped && (
            <>
              <p className="label">Gate 1 · your call</p>
              <h2 className="mt-3 text-xl text-ink">Is this one worth applying to?</h2>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                Approving starts tracking it. Nothing is sent anywhere.
              </p>
              <div className="mt-6 flex gap-3">
                <button
                  type="button"
                  className="btn btn-primary flex-1"
                  disabled={busy || !key || posting.closed}
                  onClick={() => run(async () => setApp(await approvePosting(key!, postingId)))}
                >
                  Approve
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={busy || !key}
                  onClick={() =>
                    run(async () => {
                      await skipPosting(key!, postingId);
                      setSkipped(true);
                    })
                  }
                >
                  Skip
                </button>
              </div>
              {posting.closed && <p className="mt-4 text-sm text-amber">This posting is closed on its list.</p>}
            </>
          )}

          {stage === "decide" && skipped && (
            <>
              <p className="label">Skipped</p>
              <p className="mt-3 text-sm text-body">It won&apos;t show as new in your alerts again.</p>
              <div className="mt-6 flex gap-3">
                <Link href="/alerts" className="btn btn-primary flex-1">
                  Back to alerts
                </Link>
                <button type="button" className="btn btn-ghost" onClick={() => setSkipped(false)}>
                  Undo
                </button>
              </div>
            </>
          )}

          {stage === "resume" && app && (
            <ResumeGate
              busy={busy}
              onChoose={(choice) => run(async () => setApp(await updateApplication(key!, app.id, { resumeChoice: choice })))}
            />
          )}

          {stage === "apply" && app && (
            <>
              <p className="label">Gate 3 · you submit</p>
              <h2 className="mt-3 text-xl text-ink">Apply on their site</h2>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                Using your {app.resumeChoice === "TAILORED" ? "tailored" : "current"} resume.{" "}
                <button
                  type="button"
                  className="text-accent-2 underline-offset-2 hover:underline"
                  disabled={busy}
                  onClick={() =>
                    run(async () =>
                      setApp(
                        await updateApplication(key!, app.id, {
                          resumeChoice: app.resumeChoice === "TAILORED" ? "CURRENT" : "TAILORED",
                        }),
                      ),
                    )
                  }
                >
                  Switch
                </button>
              </p>
              {app.resumeChoice === "TAILORED" && (
                <p className="mt-4 border border-amber/40 bg-amber-soft px-3 py-2 text-xs leading-relaxed text-body">
                  Automatic tailoring isn&apos;t built yet — it&apos;s the next feature. For now this records your choice
                  so you remember to adjust the resume before submitting.
                </p>
              )}
              <ol className="mt-5 space-y-2 text-sm text-body">
                <li>1. Open the application in a new tab.</li>
                <li>2. Fill it in and submit it yourself.</li>
                <li>3. Come back and mark it submitted — reminders start from there.</li>
              </ol>
              <a href={posting.applyUrl} target="_blank" rel="noreferrer" className="btn btn-ghost mt-6 w-full">
                Open application ↗
              </a>
              <button
                type="button"
                className="btn btn-primary mt-3 w-full"
                disabled={busy}
                onClick={() => run(async () => setApp(await updateApplication(key!, app.id, { status: "APPLIED" })))}
              >
                I&apos;ve submitted it
              </button>
            </>
          )}

          {stage === "tracked" && app && (
            <>
              <p className="label">In your tracker</p>
              <h2 className="mt-3 text-xl text-ink">{app.status === "APPLIED" ? "Submitted — nice." : "Being tracked"}</h2>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                When they reply — an assessment, an interview, a no — record it on the tracker. You&apos;ll get a
                reminder before any deadline you add, and a nudge if it goes quiet for two weeks.
              </p>
              <Link href={`/tracker/${app.id}`} className="btn btn-primary mt-6 w-full">
                Open in tracker
              </Link>
            </>
          )}

          {error && (
            <p role="alert" className="mt-4 text-sm text-amber">
              {error}
            </p>
          )}
        </div>

        {app && stageIndex > 0 && stageIndex < 3 && (
          <p className="label mt-4">
            Already tracking this — <Link href={`/tracker/${app.id}`} className="text-accent-2 hover:underline">see it</Link>
          </p>
        )}
      </aside>
    </div>
  );
}

function ResumeGate({ busy, onChoose }: { busy: boolean; onChoose: (c: ResumeChoice) => void }) {
  return (
    <>
      <p className="label">Gate 2 · which resume</p>
      <h2 className="mt-3 text-xl text-ink">Send it as is, or tailor it?</h2>
      <div className="mt-6 space-y-3">
        <button
          type="button"
          disabled={busy}
          onClick={() => onChoose("CURRENT")}
          className="group block w-full border border-line bg-paper p-4 text-left transition hover:border-accent"
        >
          <span className="flex items-center justify-between text-ink">
            Current resume <span className="text-muted transition group-hover:translate-x-1">→</span>
          </span>
          <span className="mt-1 block text-sm text-muted">Fastest. Best when the role is a close fit already.</span>
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => onChoose("TAILORED")}
          className="group block w-full border border-line bg-paper p-4 text-left transition hover:border-accent"
        >
          <span className="flex items-center justify-between text-ink">
            Tailor to this role <span className="text-muted transition group-hover:translate-x-1">→</span>
          </span>
          <span className="mt-1 block text-sm text-muted">
            Reorder projects and bullets toward the posting. You review every change before using it.
          </span>
        </button>
      </div>
    </>
  );
}
