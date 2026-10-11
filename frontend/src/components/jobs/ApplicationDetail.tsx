"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import {
  deleteApplication,
  describeError,
  getApplication,
  tailorDownloadUrl,
  tailorRunsForApplication,
  updateApplication,
  type TailorRun,
  type Application,
  type ApplicationStatus,
  type ApplicationUpdate,
} from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";
import { STATUS_META, StatusBadge, timeAgo } from "@/components/jobs/ui";
import { ApplyKitPanel } from "@/components/apply/ApplyKitPanel";

const PIPELINE: ApplicationStatus[] = ["APPROVED", "APPLIED", "ASSESSMENT", "INTERVIEW", "OFFER"];
const OUTCOMES: ApplicationStatus[] = ["REJECTED", "GHOSTED", "WITHDRAWN"];

/** What usually follows each stage — presets for the "what's next" field, not predictions. */
const NEXT_PRESETS: Partial<Record<ApplicationStatus, string[]>> = {
  APPROVED: ["Submit the application"],
  APPLIED: ["Follow up with the recruiter", "Check the portal for status"],
  ASSESSMENT: ["Complete the online assessment", "Submit the take-home"],
  INTERVIEW: ["Recruiter call", "Technical interview", "Send thank-you notes"],
  OFFER: ["Reply to the offer"],
};

const inputClass =
  "w-full border border-line bg-paper px-3 py-2.5 text-sm outline-none transition placeholder:text-muted/70 focus:border-accent";

export function ApplicationDetail({ id }: { id: number }) {
  const router = useRouter();
  const { key, ready } = useTrackerKey();
  const [app, setApp] = useState<Application | null>(null);
  const [next, setNext] = useState({ step: "", due: "" });
  const [note, setNote] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tailored, setTailored] = useState<TailorRun[]>([]);

  const accept = useCallback((a: Application) => {
    setApp(a);
    setNotes(a.notes ?? "");
  }, []);

  useEffect(() => {
    if (!ready) return;
    if (!key) {
      setError("This tracker belongs to another device. Open the link from your email, or paste your key under Find → Saved searches.");
      return;
    }
    getApplication(key, id).then(accept).catch((err) => setError(describeError(err)));
    tailorRunsForApplication(key, id).then(setTailored).catch(() => setTailored([]));
  }, [ready, key, id, accept]);

  async function save(body: ApplicationUpdate) {
    setBusy(true);
    setError(null);
    try {
      accept(await updateApplication(key!, id, body));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  if (!app) {
    return error ? (
      <div role="alert" className="border border-accent/40 bg-accent-soft px-5 py-4">
        {error}
      </div>
    ) : (
      <p className="label">Loading…</p>
    );
  }

  const stageIndex = PIPELINE.indexOf(app.status);

  return (
    <div className="grid gap-10 lg:grid-cols-[1fr_360px]">
      <div className="min-w-0">
        <StatusBadge status={app.status} />
        <h1 className="display mt-6 text-[clamp(1.8rem,3.6vw,2.8rem)]">{app.company}</h1>
        <p className="mt-3 text-xl text-body">{app.role}</p>
        <div className="mt-5 flex flex-wrap gap-x-6 gap-y-2">
          {app.applyUrl && (
            <a href={app.applyUrl} target="_blank" rel="noreferrer" className="label text-accent-2 hover:text-ink">
              Application page ↗
            </a>
          )}
          {app.postingId && (
            <Link href={`/jobs/${app.postingId}`} className="label hover:text-ink">
              Posting
            </Link>
          )}
          {app.appliedAt && <span className="label">Applied {timeAgo(app.appliedAt)}</span>}
          {app.resumeChoice && <span className="label">{app.resumeChoice === "TAILORED" ? "Tailored" : "Current"} resume</span>}
        </div>

        {/* Pipeline: click a stage to move there. */}
        <div className="mt-10">
          <p className="label">Where it stands</p>
          <ol className="mt-4 grid grid-cols-5 gap-px border border-line bg-line">
            {PIPELINE.map((s, i) => {
              const done = stageIndex >= 0 && i < stageIndex;
              const current = app.status === s;
              return (
                <li key={s}>
                  <button
                    type="button"
                    disabled={busy || current}
                    onClick={() => save({ status: s })}
                    className={`relative h-full w-full px-2 py-4 text-center font-mono text-[10px] uppercase tracking-[0.12em] transition ${
                      current ? "bg-accent text-white" : done ? "bg-accent-soft text-ink hover:bg-card" : "bg-paper text-muted hover:bg-card hover:text-ink"
                    }`}
                  >
                    {done ? "✓ " : ""}
                    {STATUS_META[s].label}
                  </button>
                </li>
              );
            })}
          </ol>
          <div className="mt-3 flex flex-wrap gap-2">
            {OUTCOMES.map((s) => (
              <button
                key={s}
                type="button"
                disabled={busy || app.status === s}
                onClick={() => save({ status: s })}
                className={`border px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.14em] transition ${
                  app.status === s ? "border-line-strong bg-card text-ink" : "border-line text-muted hover:text-ink"
                }`}
              >
                {STATUS_META[s].label}
              </button>
            ))}
          </div>
        </div>

        {/* What arrived: log it. */}
        <div className="mt-10 border border-line bg-card p-5">
          <p className="label">Something came in?</p>
          <p className="mt-2 text-sm text-muted">
            An email from the company, an OA link, a call. Log it — it goes on the timeline.
          </p>
          <div className="mt-4 flex gap-3">
            <input
              className={inputClass}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. HackerRank OA received, 90 minutes"
            />
            <button
              type="button"
              className="btn btn-primary shrink-0 !px-5 !py-2.5"
              disabled={busy || !note.trim()}
              onClick={async () => {
                await save({ eventNote: note });
                setNote("");
              }}
            >
              Log
            </button>
          </div>
        </div>

        <div className="mt-10">
          <p className="label">Timeline</p>
          <ol className="relative mt-5 space-y-6 border-l border-line pl-6">
            {app.events.map((e, i) => (
              <li key={e.id} className="animate-rise relative" style={{ animationDelay: `${Math.min(i, 10) * 35}ms` }}>
                <span
                  className="absolute -left-[29px] top-1.5 size-2.5 border-2 border-paper"
                  style={{ background: e.status ? STATUS_META[e.status].color : "var(--line-strong)" }}
                />
                {e.status && <p className="font-mono text-[11px] uppercase tracking-[0.16em] text-ink">{STATUS_META[e.status].label}</p>}
                {e.note && <p className="mt-1 text-sm text-body">{e.note}</p>}
                <p className="label mt-1">{new Date(e.occurredAt).toLocaleString()}</p>
              </li>
            ))}
          </ol>
        </div>
      </div>

      <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start">
        <div className="border border-line bg-card p-5">
          <p className="label">Next step</p>
          {app.nextStep ? (
            <div className="mt-3">
              <p className="text-ink">{app.nextStep}</p>
              {app.nextStepDue && <p className="mt-1 text-sm text-amber">Due {new Date(`${app.nextStepDue}T00:00:00`).toLocaleDateString()}</p>}
              <button type="button" className="btn btn-ghost mt-4 w-full !py-2.5" disabled={busy} onClick={() => save({ clearNextStep: true })}>
                Mark done
              </button>
            </div>
          ) : (
            <p className="mt-3 text-sm text-muted">Nothing scheduled.</p>
          )}

          <div className="mt-5 space-y-3 border-t border-line pt-5">
            <div className="flex flex-wrap gap-2">
              {(NEXT_PRESETS[app.status] ?? []).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setNext({ ...next, step: p })}
                  className="border border-line px-2.5 py-1 text-xs text-muted transition hover:border-accent hover:text-ink"
                >
                  {p}
                </button>
              ))}
            </div>
            <input className={inputClass} value={next.step} onChange={(e) => setNext({ ...next, step: e.target.value })} placeholder="What's next?" />
            <input type="date" className={inputClass} value={next.due} onChange={(e) => setNext({ ...next, due: e.target.value })} />
            <button
              type="button"
              className="btn btn-primary w-full !py-2.5"
              disabled={busy || !next.step.trim()}
              onClick={async () => {
                await save({ nextStep: next.step, nextStepDue: next.due || null });
                setNext({ step: "", due: "" });
              }}
            >
              Set next step
            </button>
            <p className="text-xs text-muted">With an email on one of your alerts, you&apos;ll be reminded two days before.</p>
          </div>
        </div>

        <div className="border border-accent/40 bg-card p-5">
          <p className="label">Resume for this job</p>
          <p className="mt-2 text-sm text-body">
            Upload a resume — or the one you already sent — and get a version tailored to this role as a PDF.
          </p>
          <Link href={`/tailor/new?application=${app.id}`} className="btn btn-primary mt-4 w-full !py-2.5">
            {tailored.length ? "Tailor again" : "Tailor my resume"}
          </Link>
          {tailored.length > 0 && key && (
            <ul className="mt-4 space-y-2 border-t border-line pt-4">
              {tailored.slice(0, 4).map((t) => (
                <li key={t.id} className="flex items-center justify-between gap-3 text-sm">
                  <Link href={`/tailor/${t.id}`} className="truncate text-body hover:text-accent-2">
                    {t.step === "DONE" ? "Built" : "In progress"} · {timeAgo(t.updatedAt)}
                  </Link>
                  {t.hasPdf && (
                    <a href={tailorDownloadUrl(key, t.id, "pdf")} className="label shrink-0 text-accent-2 hover:text-ink">
                      PDF ↓
                    </a>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        {key && <ApplyKitPanel id={id} applyUrl={app.applyUrl} trackerKey={key} />}

        <div className="border border-line bg-card p-5">
          <label className="block">
            <span className="label">Notes</span>
            <textarea
              className={`${inputClass} mt-3 min-h-32 resize-y`}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              onBlur={() => notes !== (app.notes ?? "") && save({ notes })}
              placeholder="Recruiter's name, what they asked, salary range…"
            />
          </label>
          <p className="mt-2 text-xs text-muted">Saves when you click away.</p>
        </div>

        {error && (
          <p role="alert" className="text-sm text-amber">
            {error}
          </p>
        )}

        <button
          type="button"
          className="label hover:text-amber"
          onClick={async () => {
            if (!window.confirm(`Stop tracking ${app.company}? The timeline is deleted too.`)) return;
            try {
              await deleteApplication(key!, id);
              router.push("/tracker");
            } catch (err) {
              setError(describeError(err));
            }
          }}
        >
          Delete application
        </button>
      </aside>
    </div>
  );
}
