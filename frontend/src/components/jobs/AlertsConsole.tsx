"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  approvePosting,
  createWatch,
  deleteWatch,
  describeError,
  listMatches,
  listWatches,
  skipPosting,
  updateWatch,
  type JobKind,
  type JobMatch,
  type MatchStatus,
  type Watch,
  listResumes,
  type ResumeDoc,
} from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";
import { EmptyState, PostingRow, timeAgo } from "@/components/jobs/ui";

const inputClass =
  "w-full border border-line bg-card px-4 py-3 text-base outline-none transition placeholder:text-muted/70 focus:border-accent";

const split = (s: string) =>
  s
    .split(",")
    .map((x) => x.trim())
    .filter(Boolean);

type Draft = { name: string; email: string; keywords: string; exclude: string; locations: string; kinds: JobKind[] };

const EMPTY: Draft = { name: "", email: "", keywords: "", exclude: "senior, staff, principal", locations: "", kinds: [] };

export function AlertsConsole() {
  const { key, ready, restore } = useTrackerKey({ create: true });
  const [watches, setWatches] = useState<Watch[]>([]);
  const [matches, setMatches] = useState<JobMatch[]>([]);
  const [tab, setTab] = useState<MatchStatus>("NEW");
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showKey, setShowKey] = useState(false);
  const [restoreValue, setRestoreValue] = useState("");
  const [resumes, setResumes] = useState<ResumeDoc[]>([]);
  // Which watch's auto-prepare panel is open.
  const [prepFor, setPrepFor] = useState<number | null>(null);
  const [prep, setPrep] = useState({ resumeId: 0, github: "" });

  // A link from the job feed pre-fills the form with that search.
  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    if (p.get("keywords") || p.get("locations") || p.get("kinds")) {
      setDraft((d) => ({
        ...d,
        name: p.get("keywords") ?? "My search",
        keywords: p.get("keywords") ?? "",
        locations: p.get("locations") ?? "",
        kinds: (p.get("kinds")?.split(",").filter(Boolean) as JobKind[]) ?? [],
      }));
      setShowForm(true);
    }
  }, []);

  const load = useCallback(async () => {
    if (!key) return;
    try {
      const [w, m, r] = await Promise.all([listWatches(key), listMatches(key), listResumes(key).catch(() => [])]);
      setWatches(w);
      setMatches(m);
      setResumes(r);
      if (w.length === 0) setShowForm(true);
    } catch (err) {
      setError(describeError(err));
    }
  }, [key]);

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

  function save(e: React.FormEvent) {
    e.preventDefault();
    run(async () => {
      await createWatch(key!, {
        name: draft.name.trim() || "My search",
        email: draft.email.trim() || null,
        keywords: split(draft.keywords),
        excludeKeywords: split(draft.exclude),
        locations: split(draft.locations),
        kinds: draft.kinds,
      });
      setDraft(EMPTY);
      setShowForm(false);
      await load();
    });
  }

  function decide(m: JobMatch, approve: boolean) {
    run(async () => {
      if (approve) await approvePosting(key!, m.posting.id);
      else await skipPosting(key!, m.posting.id);
      setMatches((all) =>
        all.map((x) => (x.posting.id === m.posting.id ? { ...x, status: approve ? "APPROVED" : "SKIPPED" } : x)),
      );
    });
  }

  const shown = matches.filter((m) => m.status === tab);
  const counts = { NEW: 0, APPROVED: 0, SKIPPED: 0 } as Record<MatchStatus, number>;
  matches.forEach((m) => counts[m.status]++);

  return (
    <div className="grid gap-10 lg:grid-cols-[400px_1fr]">
      <aside className="space-y-6">
        <div className="flex items-center justify-between">
          <p className="label">Your alerts · {watches.length}</p>
          {!showForm && (
            <button type="button" className="label text-accent-2 hover:text-ink" onClick={() => setShowForm(true)}>
              + New alert
            </button>
          )}
        </div>

        {showForm && (
          <form onSubmit={save} className="animate-rise space-y-4 border border-line bg-card p-5">
            <Field label="Name">
              <input
                className={inputClass}
                value={draft.name}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                placeholder="Backend internships"
                required
              />
            </Field>
            <Field label="Keywords · any of" hint="Comma separated. “sde” also finds “software engineer”.">
              <input
                className={inputClass}
                value={draft.keywords}
                onChange={(e) => setDraft({ ...draft, keywords: e.target.value })}
                placeholder="backend, java, software engineer"
              />
            </Field>
            <Field label="Never show" hint="Postings with any of these are dropped.">
              <input
                className={inputClass}
                value={draft.exclude}
                onChange={(e) => setDraft({ ...draft, exclude: e.target.value })}
              />
            </Field>
            <Field label="Locations · any of" hint="Countries, cities (any spelling) or Remote. Empty = everywhere.">
              <input
                className={inputClass}
                value={draft.locations}
                onChange={(e) => setDraft({ ...draft, locations: e.target.value })}
                placeholder="India, Bangalore, Remote"
              />
            </Field>
            <fieldset>
              <legend className="label">Type</legend>
              <div className="mt-3 flex gap-2">
                {(["INTERNSHIP", "NEW_GRAD", "FULL_TIME"] as const).map((k) => {
                  const on = draft.kinds.includes(k);
                  return (
                    <button
                      key={k}
                      type="button"
                      onClick={() => setDraft({ ...draft, kinds: on ? draft.kinds.filter((x) => x !== k) : [...draft.kinds, k] })}
                      className={`border px-3 py-2 font-mono text-[11px] uppercase tracking-[0.14em] transition ${
                        on ? "border-accent bg-accent-soft text-ink" : "border-line text-muted hover:text-ink"
                      }`}
                    >
                      {k === "INTERNSHIP" ? "Internships" : k === "NEW_GRAD" ? "New grad" : "Full-time"}
                    </button>
                  );
                })}
              </div>
            </fieldset>
            <Field label="Email me at" hint="Optional. Without it, matches still appear here.">
              <input
                type="email"
                className={inputClass}
                value={draft.email}
                onChange={(e) => setDraft({ ...draft, email: e.target.value })}
                placeholder="you@gmail.com"
              />
            </Field>
            <div className="flex gap-3 pt-2">
              <button type="submit" className="btn btn-primary flex-1" disabled={busy || !key}>
                Save alert
              </button>
              {watches.length > 0 && (
                <button type="button" className="btn btn-ghost" onClick={() => setShowForm(false)}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        )}

        <ul className="space-y-3">
          {watches.map((w) => (
            <li key={w.id} className={`border border-line p-4 transition ${w.active ? "bg-card" : "opacity-60"}`}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-ink">{w.name}</p>
                  <p className="mt-1 truncate text-sm text-muted">
                    {[w.keywords.join(", ") || "any role", w.locations.join(", ") || "anywhere"].join(" · ")}
                  </p>
                </div>
                {w.newMatches > 0 && (
                  <span className="shrink-0 bg-accent px-2 py-0.5 font-mono text-[11px] text-white">{w.newMatches}</span>
                )}
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2">
                <span className="label">{w.email ? `✉ ${w.lastNotifiedAt ? `sent ${timeAgo(w.lastNotifiedAt)}` : "email on"}` : "in-app only"}</span>
                <button
                  type="button"
                  className="label hover:text-ink"
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      await updateWatch(key!, w.id, { ...w, active: !w.active });
                      await load();
                    })
                  }
                >
                  {w.active ? "Pause" : "Resume"}
                </button>
                <button
                  type="button"
                  className="label hover:text-ink"
                  disabled={busy}
                  onClick={() => {
                    if (w.autoPrepare) {
                      run(async () => {
                        await updateWatch(key!, w.id, { ...w, autoPrepare: false });
                        await load();
                      });
                      return;
                    }
                    setPrep({ resumeId: w.autoResumeId ?? resumes[0]?.id ?? 0, github: w.autoGithubUsername ?? "" });
                    setPrepFor(prepFor === w.id ? null : w.id);
                  }}
                >
                  {w.autoPrepare ? "⚡ Auto-prepare on" : "Prepare automatically"}
                </button>
                <button
                  type="button"
                  className="label hover:text-amber"
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      if (!window.confirm(`Delete the "${w.name}" alert and its matches?`)) return;
                      await deleteWatch(key!, w.id);
                      await load();
                    })
                  }
                >
                  Delete
                </button>
              </div>

              {w.autoPrepare && (
                <p className="mt-3 border-t border-line pt-3 text-xs text-muted">
                  New matches here are tracked, tailored and drafted for you — up to five a day. Nothing is submitted;
                  each one waits in the tracker for you to read and send.
                </p>
              )}

              {prepFor === w.id && (
                <div className="animate-rise mt-3 space-y-3 border-t border-line pt-4">
                  <p className="text-sm text-body">
                    Pick the resume to tailor from. Each prepared application costs two Gemini calls, so it stops at
                    five a day.
                  </p>
                  {resumes.length === 0 ? (
                    <p className="text-sm text-muted">
                      No resume saved yet — upload one on an application&apos;s tailoring step first.
                    </p>
                  ) : (
                    <>
                      <select
                        className={inputClass}
                        value={prep.resumeId}
                        onChange={(e) => setPrep({ ...prep, resumeId: Number(e.target.value) })}
                      >
                        {resumes.map((r) => (
                          <option key={r.id} value={r.id}>
                            {r.filename || `Resume ${r.id}`}
                          </option>
                        ))}
                      </select>
                      <input
                        className={inputClass}
                        value={prep.github}
                        onChange={(e) => setPrep({ ...prep, github: e.target.value })}
                        placeholder="GitHub username (so edits can quote your repos)"
                      />
                      <div className="flex gap-3">
                        <button
                          type="button"
                          className="btn btn-primary flex-1 !py-2.5"
                          disabled={busy || !prep.resumeId}
                          onClick={() =>
                            run(async () => {
                              await updateWatch(key!, w.id, {
                                ...w,
                                autoPrepare: true,
                                autoResumeId: prep.resumeId,
                                autoGithubUsername: prep.github.trim() || null,
                              });
                              setPrepFor(null);
                              await load();
                            })
                          }
                        >
                          Turn it on
                        </button>
                        <button type="button" className="btn btn-ghost !py-2.5" onClick={() => setPrepFor(null)}>
                          Cancel
                        </button>
                      </div>
                    </>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>

        <div className="border-t border-line pt-5">
          <button type="button" className="label hover:text-ink" onClick={() => setShowKey(!showKey)}>
            {showKey ? "▾" : "▸"} Use on another device
          </button>
          {showKey && (
            <div className="animate-rise mt-4 space-y-3 text-sm text-muted">
              <p>
                There&apos;s no login — your alerts and tracker belong to this key. Keep a copy; paste it on another
                device to pick up where you left off. Links in alert emails already carry access.
              </p>
              <code className="block break-all border border-line bg-paper px-3 py-2 font-mono text-xs text-body">
                {key ?? "…"}
              </code>
              <div className="flex gap-2">
                <input
                  className={inputClass}
                  value={restoreValue}
                  onChange={(e) => setRestoreValue(e.target.value)}
                  placeholder="Paste a key"
                />
                <button type="button" className="btn btn-ghost shrink-0" onClick={() => restore(restoreValue)}>
                  Use
                </button>
              </div>
            </div>
          )}
        </div>
      </aside>

      <section className="min-w-0">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-line">
          <div className="flex">
            {(["NEW", "APPROVED", "SKIPPED"] as const).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setTab(s)}
                className={`relative px-4 py-3 font-mono text-[11px] uppercase tracking-[0.16em] transition ${
                  tab === s ? "text-ink" : "text-muted hover:text-ink"
                }`}
              >
                {s === "NEW" ? "Inbox" : s === "APPROVED" ? "Approved" : "Skipped"} · {counts[s]}
                {tab === s && <span className="absolute inset-x-0 -bottom-px h-0.5 bg-accent" />}
              </button>
            ))}
          </div>
          <Link href="/tracker" className="label hover:text-ink">
            Tracker →
          </Link>
        </div>

        {error && (
          <div role="alert" className="mt-4 border border-accent/40 bg-accent-soft px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {ready && shown.length === 0 && (
          <div className="mt-6">
            <EmptyState title={tab === "NEW" ? "Inbox zero" : "Nothing here yet"}>
              {watches.length === 0
                ? "Create an alert on the left. Postings from the last week that fit it show up here straight away."
                : tab === "NEW"
                  ? "New matches arrive as the job lists update."
                  : null}
            </EmptyState>
          </div>
        )}

        <div key={tab} className="border-line">
          {shown.map((m, i) => (
            <div key={m.id} className="animate-rise" style={{ animationDelay: `${Math.min(i, 12) * 22}ms` }}>
              <PostingRow
                posting={m.posting}
                trailing={
                  tab === "NEW" ? (
                    <span className="flex gap-2">
                      <button type="button" className="btn btn-primary !px-4 !py-2" disabled={busy} onClick={() => decide(m, true)}>
                        Approve
                      </button>
                      <button type="button" className="btn btn-ghost !px-4 !py-2" disabled={busy} onClick={() => decide(m, false)}>
                        Skip
                      </button>
                    </span>
                  ) : tab === "APPROVED" ? (
                    <Link href={`/jobs/${m.posting.id}`} className="label text-accent-2 hover:text-ink">
                      Continue →
                    </Link>
                  ) : (
                    <button type="button" className="label hover:text-ink" disabled={busy} onClick={() => decide(m, true)}>
                      Approve instead
                    </button>
                  )
                }
              />
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-2">
      <span className="label">{label}</span>
      {children}
      {hint && <span className="block text-xs text-muted">{hint}</span>}
    </label>
  );
}
