"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { describeError, getJobSources, searchJobs, type JobKind, type JobSource, type PostingPage } from "@/lib/api";
import { EmptyState, PostingRow, timeAgo } from "@/components/jobs/ui";

const PAGE = 30;
const SINCE = [
  { value: 0, label: "Any time" },
  { value: 1, label: "24 hours" },
  { value: 3, label: "3 days" },
  { value: 7, label: "Week" },
  { value: 30, label: "Month" },
];

const inputClass =
  "w-full border border-line bg-card px-4 py-3 text-base outline-none transition placeholder:text-muted/70 focus:border-accent";

export function JobFeed() {
  const [q, setQ] = useState("");
  const [location, setLocation] = useState("");
  const [kind, setKind] = useState<JobKind | "">("");
  const [sinceDays, setSinceDays] = useState(7);
  const [offset, setOffset] = useState(0);
  const [page, setPage] = useState<PostingPage | null>(null);
  const [sources, setSources] = useState<JobSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const debounce = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    getJobSources().then(setSources).catch(() => setSources([]));
  }, []);

  useEffect(() => {
    if (debounce.current) clearTimeout(debounce.current);
    debounce.current = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        setPage(await searchJobs({ q, location, kind, sinceDays, offset, limit: PAGE }));
      } catch (err) {
        setError(describeError(err));
      } finally {
        setLoading(false);
      }
    }, 250);
  }, [q, location, kind, sinceDays, offset]);

  // Any filter change starts from the first page again.
  function filter<T>(set: (v: T) => void) {
    return (v: T) => {
      set(v);
      setOffset(0);
    };
  }

  const alertHref = `/alerts?${new URLSearchParams({
    ...(q ? { keywords: q } : {}),
    ...(location ? { locations: location } : {}),
    ...(kind ? { kinds: kind } : {}),
  })}`;
  const lastChecked = sources.map((s) => s.lastCheckedAt).filter(Boolean).sort().at(-1);

  return (
    <div className="grid gap-10 lg:grid-cols-[300px_1fr]">
      <aside className="space-y-7 lg:sticky lg:top-24 lg:self-start">
        <div className="space-y-3">
          <label htmlFor="jf-q" className="label">
            Role or company
          </label>
          <input
            id="jf-q"
            value={q}
            onChange={(e) => filter(setQ)(e.target.value)}
            placeholder="backend intern, java…"
            className={inputClass}
          />
        </div>
        <div className="space-y-3">
          <label htmlFor="jf-loc" className="label">
            Location
          </label>
          <input
            id="jf-loc"
            value={location}
            onChange={(e) => filter(setLocation)(e.target.value)}
            placeholder="Remote, Seattle, India…"
            className={inputClass}
          />
        </div>
        <fieldset className="space-y-3">
          <legend className="label">Type</legend>
          <div className="flex flex-wrap gap-2">
            {(
              [
                ["", "All"],
                ["INTERNSHIP", "Internships"],
                ["NEW_GRAD", "New grad"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => filter(setKind)(value)}
                className={`border px-3 py-2 font-mono text-[11px] uppercase tracking-[0.14em] transition ${
                  kind === value ? "border-accent bg-accent-soft text-ink" : "border-line text-muted hover:text-ink"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </fieldset>
        <fieldset className="space-y-3">
          <legend className="label">Listed within</legend>
          <div className="flex flex-wrap gap-2">
            {SINCE.map((s) => (
              <button
                key={s.value}
                type="button"
                onClick={() => filter(setSinceDays)(s.value)}
                className={`border px-3 py-2 font-mono text-[11px] uppercase tracking-[0.14em] transition ${
                  sinceDays === s.value ? "border-accent bg-accent-soft text-ink" : "border-line text-muted hover:text-ink"
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
        </fieldset>

        <Link href={alertHref} className="btn btn-primary w-full">
          Alert me on new matches
        </Link>

        <div className="border-t border-line pt-5">
          <p className="label">Sources</p>
          <ul className="mt-3 space-y-2 text-sm">
            {sources.map((s) => (
              <li key={s.fullName} className="flex items-center justify-between gap-3">
                <a
                  href={`https://github.com/${s.fullName}`}
                  target="_blank"
                  rel="noreferrer"
                  className="truncate text-body hover:text-accent-2"
                >
                  {s.fullName}
                </a>
                <span className={`font-mono text-[11px] ${s.lastError ? "text-amber" : "text-muted"}`}>
                  {s.lastError ? "error" : s.lastCount}
                </span>
              </li>
            ))}
          </ul>
          {lastChecked && <p className="label mt-3">Checked {timeAgo(lastChecked)}</p>}
        </div>
      </aside>

      <section className="min-w-0">
        <div className="mb-4 flex items-baseline justify-between gap-4">
          <p className="label">
            {page ? `${page.total.toLocaleString()} open roles` : "Loading…"}
            {loading && page ? " · updating" : ""}
          </p>
          {page && page.total > PAGE && (
            <p className="label">
              {offset + 1}–{Math.min(offset + PAGE, page.total)}
            </p>
          )}
        </div>

        {error && (
          <div role="alert" className="mb-4 border border-accent/40 bg-accent-soft px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {page && page.items.length === 0 && !loading && (
          <EmptyState title="Nothing matches">
            {sources.every((s) => !s.lastCheckedAt)
              ? "The lists haven't been read yet — the first sync runs within a few hours of deploying."
              : "Loosen a filter, or save this search as an alert and we'll email you when one appears."}
          </EmptyState>
        )}

        {page && page.items.length > 0 && (
          <div className={`border-t border-line transition-opacity ${loading ? "opacity-60" : ""}`}>
            {page.items.map((p, i) => (
              <div key={p.id} className="animate-rise" style={{ animationDelay: `${Math.min(i, 12) * 22}ms` }}>
                <PostingRow posting={p} trailing={<span className="text-muted transition group-hover:translate-x-1 group-hover:text-accent-2">→</span>} />
              </div>
            ))}
          </div>
        )}

        {page && page.total > PAGE && (
          <div className="mt-6 flex justify-between gap-4">
            <button
              type="button"
              className="btn btn-ghost"
              disabled={offset === 0}
              onClick={() => {
                setOffset(Math.max(0, offset - PAGE));
                window.scrollTo({ top: 0, behavior: "smooth" });
              }}
            >
              ← Newer
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={offset + PAGE >= page.total}
              onClick={() => {
                setOffset(offset + PAGE);
                window.scrollTo({ top: 0, behavior: "smooth" });
              }}
            >
              Older →
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
