"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { describeError, getJobSources, searchJobs, type JobKind, type JobSource, type PostingPage } from "@/lib/api";
import { EmptyState, PostingRow, countryName, kindLabel, timeAgo } from "@/components/jobs/ui";
import { WebSearchPanel } from "@/components/jobs/WebSearchPanel";

const PAGE = 30;
const SINCE = [
  { value: 0, label: "Any time" },
  { value: 1, label: "24h" },
  { value: 7, label: "Week" },
  { value: 30, label: "Month" },
];
const KINDS: { value: JobKind | ""; label: string }[] = [
  { value: "", label: "All" },
  { value: "INTERNSHIP", label: "Internships" },
  { value: "NEW_GRAD", label: "New grad" },
  { value: "FULL_TIME", label: "Full-time" },
];
const EXAMPLES = ["internship in India", "summer intern india", "winter internship", "SDE intern bangalore", "fresher hyderabad", "ML intern"];

const chip = (on: boolean) =>
  `border px-3 py-2 font-mono text-[11px] uppercase tracking-[0.14em] transition ${
    on ? "border-accent bg-accent-soft text-ink" : "border-line text-muted hover:text-ink"
  }`;

export function JobFeed() {
  const [q, setQ] = useState("");
  const [location, setLocation] = useState("");
  const [kind, setKind] = useState<JobKind | "">("");
  const [sinceDays, setSinceDays] = useState(0);
  const [offset, setOffset] = useState(0);
  const [page, setPage] = useState<PostingPage | null>(null);
  const [sources, setSources] = useState<JobSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const debounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latest = useRef(0);

  const hydrated = useRef(false);

  useEffect(() => {
    getJobSources().then(setSources).catch(() => setSources([]));
    // A shared link (/jobs?q=internship+in+India) opens straight into that search.
    const p = new URLSearchParams(window.location.search);
    if (p.get("q")) setQ(p.get("q")!);
    if (p.get("location")) setLocation(p.get("location")!);
    const k = p.get("kind");
    if (k === "INTERNSHIP" || k === "NEW_GRAD" || k === "FULL_TIME") setKind(k);
    hydrated.current = true;
  }, []);

  // Keep the address bar in step with the search, without adding history entries per keystroke.
  useEffect(() => {
    if (!hydrated.current) return;
    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (location) p.set("location", location);
    if (kind) p.set("kind", kind);
    const next = `${window.location.pathname}${p.size ? `?${p}` : ""}`;
    if (next !== `${window.location.pathname}${window.location.search}`) window.history.replaceState(null, "", next);
  }, [q, location, kind]);

  useEffect(() => {
    if (debounce.current) clearTimeout(debounce.current);
    debounce.current = setTimeout(async () => {
      const call = ++latest.current;
      setLoading(true);
      setError(null);
      try {
        const res = await searchJobs({ q, location, kind, sinceDays, offset, limit: PAGE });
        // Typing fast fires several searches; only the newest one may paint.
        if (call === latest.current) setPage(res);
      } catch (err) {
        if (call === latest.current) setError(describeError(err));
      } finally {
        if (call === latest.current) setLoading(false);
      }
    }, 280);
  }, [q, location, kind, sinceDays, offset]);

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
  const boards = sources.filter((s) => s.type && s.type !== "GITHUB_LIST");
  const lists = sources.filter((s) => !s.type || s.type === "GITHUB_LIST");
  const facets = page?.facets;

  return (
    <div className="space-y-10">
      {/* The one box people actually use. */}
      <div className="border border-line bg-card p-5 sm:p-7">
        <div className="grid gap-3 md:grid-cols-[1fr_280px]">
          <label className="block">
            <span className="label">Search anything</span>
            <input
              value={q}
              onChange={(e) => filter(setQ)(e.target.value)}
              placeholder="internship in India, backend intern bangalore…"
              className="mt-3 w-full border border-line-strong bg-paper px-5 py-4 text-lg text-ink outline-none transition placeholder:text-muted/70 focus:border-accent"
              autoFocus
            />
          </label>
          <label className="block">
            <span className="label">Location</span>
            <input
              value={location}
              onChange={(e) => filter(setLocation)(e.target.value)}
              placeholder="India, Pune, Remote…"
              className="mt-3 w-full border border-line bg-paper px-5 py-4 text-lg text-ink outline-none transition placeholder:text-muted/70 focus:border-accent"
            />
          </label>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2">
          {KINDS.map((k) => {
            const count = k.value ? facets?.kinds[k.value] : undefined;
            return (
              <button key={k.value} type="button" onClick={() => filter(setKind)(k.value)} className={chip(kind === k.value)}>
                {k.label}
                {count !== undefined && <span className="ml-2 text-muted">{count.toLocaleString()}</span>}
              </button>
            );
          })}
          <span className="mx-2 hidden h-5 w-px bg-line sm:block" />
          {SINCE.map((s) => (
            <button key={s.value} type="button" onClick={() => filter(setSinceDays)(s.value)} className={chip(sinceDays === s.value)}>
              {s.label}
            </button>
          ))}
        </div>

        {!q && !location && (
          <div className="mt-5 flex flex-wrap items-center gap-2">
            <span className="label mr-1">Try</span>
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => filter(setQ)(ex)}
                className="border border-line px-3 py-1.5 text-xs text-muted transition hover:border-accent hover:text-ink"
              >
                {ex}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="grid gap-10 lg:grid-cols-[1fr_280px]">
        <section className="min-w-0">
          <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2">
            <p className="text-sm text-body">
              <span className="font-mono text-ink">{page ? page.total.toLocaleString() : "…"}</span>{" "}
              {page?.total === 1 ? "role" : "roles"}
              {page?.understood && (
                <>
                  <span className="text-muted"> · understood as </span>
                  <span className="text-accent-2">{page.understood}</span>
                </>
              )}
              {loading && page && <span className="label ml-3">updating</span>}
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
            <EmptyState title="Nothing matches that exactly">
              <div className="space-y-4">
                {sinceDays > 0 && (
                  <p>
                    <button type="button" className="text-accent-2 hover:underline" onClick={() => filter(setSinceDays)(0)}>
                      Search any time
                    </button>{" "}
                    instead of the last {sinceDays === 1 ? "day" : `${sinceDays} days`}.
                  </p>
                )}
                {kind && facets && Object.keys(facets.kinds).length > 0 && (
                  <p>
                    Without the type filter:{" "}
                    {Object.entries(facets.kinds).map(([k, n], i) => (
                      <span key={k}>
                        {i > 0 && ", "}
                        <button type="button" className="text-accent-2 hover:underline" onClick={() => filter(setKind)(k as JobKind)}>
                          {kindLabel(k as JobKind)} ({n})
                        </button>
                      </span>
                    ))}
                  </p>
                )}
                {facets && Object.keys(facets.countries).length > 0 && (
                  <p>
                    These words match roles in{" "}
                    {Object.entries(facets.countries)
                      .slice(0, 5)
                      .map(([c, n], i) => (
                        <span key={c}>
                          {i > 0 && ", "}
                          <button type="button" className="text-accent-2 hover:underline" onClick={() => filter(setLocation)(countryName(c))}>
                            {countryName(c)} ({n})
                          </button>
                        </span>
                      ))}
                    .
                  </p>
                )}
                <p className="text-muted">Or save it as an alert — you&apos;ll hear when one appears.</p>
              </div>
            </EmptyState>
          )}

          {page && page.items.length > 0 && (
            <div className={`border-t border-line transition-opacity ${loading ? "opacity-60" : ""}`}>
              {page.items.map((p, i) => (
                <div key={p.id} className="animate-rise" style={{ animationDelay: `${Math.min(i, 12) * 22}ms` }}>
                  <PostingRow
                    posting={p}
                    trailing={<span className="text-muted transition group-hover:translate-x-1 group-hover:text-accent-2">→</span>}
                  />
                </div>
              ))}
            </div>
          )}

          {page && (q || location) && (
            <div className="mt-8">
              <WebSearchPanel q={q} location={location} kind={kind} />
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
                ← Previous
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
                More →
              </button>
            </div>
          )}
        </section>

        <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          <Link href={alertHref} className="btn btn-primary w-full">
            Alert me on new matches
          </Link>

          {facets && Object.keys(facets.countries).length > 0 && (
            <div className="border border-line p-4">
              <p className="label">Where these roles are</p>
              <ul className="mt-3 space-y-1.5 text-sm">
                {Object.entries(facets.countries).map(([c, n]) => (
                  <li key={c}>
                    <button
                      type="button"
                      onClick={() => filter(setLocation)(countryName(c))}
                      className="flex w-full items-center justify-between text-body transition hover:text-accent-2"
                    >
                      {countryName(c)}
                      <span className="font-mono text-[11px] text-muted">{n.toLocaleString()}</span>
                    </button>
                  </li>
                ))}
                {facets.remote > 0 && (
                  <li>
                    <button
                      type="button"
                      onClick={() => filter(setLocation)("Remote")}
                      className="flex w-full items-center justify-between text-body transition hover:text-accent-2"
                    >
                      Remote
                      <span className="font-mono text-[11px] text-muted">{facets.remote.toLocaleString()}</span>
                    </button>
                  </li>
                )}
              </ul>
            </div>
          )}

          <details className="border border-line p-4">
            <summary className="label cursor-pointer">
              {sources.length} sources · {boards.length} company career sites · {lists.length} GitHub lists
            </summary>
            <ul className="mt-3 max-h-72 space-y-1.5 overflow-y-auto text-sm">
              {[...lists, ...boards].map((s) => (
                <li key={s.fullName} className="flex items-center justify-between gap-3">
                  <span className="truncate text-body">{s.companyName ?? s.fullName}</span>
                  <span className={`shrink-0 font-mono text-[11px] ${s.lastError ? "text-amber" : "text-muted"}`}>
                    {s.lastError ? "error" : s.lastCount}
                  </span>
                </li>
              ))}
            </ul>
            {lastChecked && <p className="label mt-3">Checked {timeAgo(lastChecked)}</p>}
          </details>
        </aside>
      </div>
    </div>
  );
}
