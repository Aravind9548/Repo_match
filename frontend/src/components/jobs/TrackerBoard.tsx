"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { addApplication, describeError, listApplications, type Application } from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";
import { BOARD_COLUMNS, EmptyState, STATUS_META, timeAgo } from "@/components/jobs/ui";

const inputClass =
  "w-full border border-line bg-paper px-3 py-2.5 text-sm outline-none transition placeholder:text-muted/70 focus:border-accent";

function dueLabel(due: string) {
  const days = Math.round((new Date(`${due}T00:00:00`).getTime() - new Date().setHours(0, 0, 0, 0)) / 86_400_000);
  if (days < 0) return { text: `${-days}d overdue`, urgent: true };
  if (days === 0) return { text: "due today", urgent: true };
  return { text: `due in ${days}d`, urgent: days <= 2 };
}

export function TrackerBoard() {
  const { key, ready } = useTrackerKey({ create: true });
  const [apps, setApps] = useState<Application[] | null>(null);
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState({ company: "", role: "", applyUrl: "" });
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!key) return;
    try {
      setApps(await listApplications(key));
    } catch (err) {
      setError(describeError(err));
    }
  }, [key]);

  useEffect(() => {
    if (ready) load();
  }, [ready, load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    try {
      await addApplication(key!, { ...draft, applyUrl: draft.applyUrl || undefined });
      setDraft({ company: "", role: "", applyUrl: "" });
      setAdding(false);
      await load();
    } catch (err) {
      setError(describeError(err));
    }
  }

  const isClosed = (a: Application) => ["OFFER", "REJECTED", "GHOSTED", "WITHDRAWN"].includes(a.status);
  const upcoming = (apps ?? [])
    .filter((a) => a.nextStepDue && !isClosed(a))
    .sort((a, b) => (a.nextStepDue! < b.nextStepDue! ? -1 : 1))
    .slice(0, 4);

  const active = (apps ?? []).filter((a) => !isClosed(a)).length;
  const responded = (apps ?? []).filter((a) => ["ASSESSMENT", "INTERVIEW", "OFFER", "REJECTED"].includes(a.status)).length;
  const submitted = (apps ?? []).filter((a) => a.appliedAt).length;

  return (
    <div className="space-y-10">
      <div className="grid gap-px border border-line bg-line sm:grid-cols-2 lg:grid-cols-4">
        {[
          ["Active", active],
          ["Submitted", submitted],
          ["Heard back", submitted ? `${Math.round((responded / submitted) * 100)}%` : "—"],
          ["Deadlines", upcoming.length],
        ].map(([k, v]) => (
          <div key={k} className="bg-paper px-6 py-5">
            <p className="label">{k}</p>
            <p className="display mt-3 text-3xl">{v}</p>
          </div>
        ))}
      </div>

      {upcoming.length > 0 && (
        <div className="border border-amber/30 bg-amber-soft px-5 py-4">
          <p className="label !text-amber">Coming up</p>
          <ul className="mt-3 space-y-2">
            {upcoming.map((a) => {
              const d = dueLabel(a.nextStepDue!);
              return (
                <li key={a.id} className="flex flex-wrap items-baseline gap-x-3 text-sm">
                  <Link href={`/tracker/${a.id}`} className="text-ink hover:text-accent-2">
                    {a.nextStep}
                  </Link>
                  <span className="text-muted">{a.company}</span>
                  <span className={`font-mono text-[11px] ${d.urgent ? "text-amber" : "text-muted"}`}>{d.text}</span>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="label">{apps ? `${apps.length} applications` : "Loading…"}</p>
        <div className="flex gap-3">
          <Link href="/jobs" className="btn btn-ghost !px-5 !py-2.5">
            Find roles
          </Link>
          <button type="button" className="btn btn-primary !px-5 !py-2.5" onClick={() => setAdding(!adding)}>
            {adding ? "Close" : "+ Add one found elsewhere"}
          </button>
        </div>
      </div>

      {adding && (
        <form onSubmit={add} className="animate-rise grid gap-3 border border-line bg-card p-5 md:grid-cols-[1fr_1fr_1.4fr_auto]">
          <input className={inputClass} placeholder="Company" required value={draft.company} onChange={(e) => setDraft({ ...draft, company: e.target.value })} />
          <input className={inputClass} placeholder="Role" required value={draft.role} onChange={(e) => setDraft({ ...draft, role: e.target.value })} />
          <input className={inputClass} placeholder="Application link (optional)" value={draft.applyUrl} onChange={(e) => setDraft({ ...draft, applyUrl: e.target.value })} />
          <button type="submit" className="btn btn-primary !py-2.5">
            Add
          </button>
        </form>
      )}

      {error && (
        <div role="alert" className="border border-accent/40 bg-accent-soft px-4 py-3 text-sm">
          {error}
        </div>
      )}

      {apps && apps.length === 0 && (
        <EmptyState title="No applications yet">
          Approve a role from the <Link href="/jobs" className="text-accent-2 hover:underline">job feed</Link> or your{" "}
          <Link href="/jobs?tab=saved" className="text-accent-2 hover:underline">saved search</Link> and it shows up here.
        </EmptyState>
      )}

      {apps && apps.length > 0 && (
        <div className="-mx-5 overflow-x-auto px-5 pb-2 sm:mx-0 sm:px-0">
          <div className="grid min-w-[1100px] grid-cols-5 gap-4">
            {BOARD_COLUMNS.map((col) => {
              const items = apps.filter((a) => col.statuses.includes(a.status));
              return (
                <div key={col.title} className="border border-line bg-card/50">
                  <div className="flex items-center justify-between border-b border-line px-4 py-3">
                    <span className="flex items-center gap-2">
                      <span className="status-dot" style={{ background: STATUS_META[col.statuses[0]].color }} />
                      <span className="label !text-body">{col.title}</span>
                    </span>
                    <span className="font-mono text-[11px] text-muted">{items.length}</span>
                  </div>
                  <ul className="space-y-2 p-2">
                    {items.map((a, i) => (
                      <li key={a.id} className="animate-rise" style={{ animationDelay: `${i * 30}ms` }}>
                        <Link
                          href={`/tracker/${a.id}`}
                          className="group block border border-line bg-paper p-3 transition hover:-translate-y-0.5 hover:border-accent"
                        >
                          <p className="truncate text-sm text-ink group-hover:text-accent-2">{a.company}</p>
                          <p className="mt-1 line-clamp-2 text-xs text-muted">{a.role}</p>
                          <div className="mt-3 flex flex-wrap items-center gap-2">
                            {col.statuses.length > 1 && (
                              <span className="font-mono text-[10px] uppercase tracking-[0.14em]" style={{ color: STATUS_META[a.status].color }}>
                                {STATUS_META[a.status].label}
                              </span>
                            )}
                            {a.nextStepDue && (
                              <span className={`font-mono text-[10px] ${dueLabel(a.nextStepDue).urgent ? "text-amber" : "text-muted"}`}>
                                {dueLabel(a.nextStepDue).text}
                              </span>
                            )}
                            <span className="ml-auto font-mono text-[10px] text-muted">{timeAgo(a.updatedAt)}</span>
                          </div>
                        </Link>
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
