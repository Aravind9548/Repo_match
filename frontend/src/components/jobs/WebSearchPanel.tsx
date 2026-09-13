"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { addApplication, describeError, webSearchJobs, type JobKind, type WebSearchResult } from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";

const STEPS = ["Asking Google", "Reading results", "Opening every link", "Dropping dead ones"];

/**
 * Reaches past the synced boards: Gemini searches Google live, then the backend opens each link before it's shown.
 * Costs a real call, so it never runs on its own.
 */
export function WebSearchPanel({ q, location, kind }: { q: string; location: string; kind: JobKind | "" }) {
  const { key } = useTrackerKey({ create: true });
  const [result, setResult] = useState<WebSearchResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [step, setStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [tracked, setTracked] = useState<Record<string, number>>({});

  useEffect(() => {
    if (!busy) return;
    setStep(0);
    const t = setInterval(() => setStep((s) => Math.min(s + 1, STEPS.length - 1)), 9000);
    return () => clearInterval(t);
  }, [busy]);

  async function run() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await webSearchJobs({ q, location, kind }));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  async function track(url: string, company: string, role: string) {
    if (!key) return;
    try {
      const app = await addApplication(key, { company: company || "Unknown company", role, applyUrl: url });
      setTracked((t) => ({ ...t, [url]: app.id }));
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <div className="border border-line bg-card/60">
      <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-4">
        <div>
          <p className="label">Beyond the feed</p>
          <p className="mt-1 text-sm text-body">
            Search the whole web with Google — company career pages and job sites we don&apos;t sync.
          </p>
        </div>
        <button type="button" onClick={run} disabled={busy} className="btn btn-ghost shrink-0 !px-5 !py-2.5">
          {busy ? "Searching…" : result ? "Search again" : "Search the web ↗"}
        </button>
      </div>

      {busy && (
        <div className="border-t border-line px-5 py-4">
          <div className="h-0.5 w-full overflow-hidden bg-line">
            <div className="h-full bg-accent transition-all duration-[9000ms] ease-linear" style={{ width: `${((step + 1) / STEPS.length) * 100}%` }} />
          </div>
          <p className="label mt-3">
            {STEPS[step]}… <span className="normal-case tracking-normal">about 40 seconds</span>
          </p>
        </div>
      )}

      {error && (
        <p role="alert" className="border-t border-line px-5 py-3 text-sm text-amber">
          {error}
        </p>
      )}

      {result && (
        <div className="border-t border-line">
          <p className="px-5 pt-4 text-xs text-muted">
            Searched for “{result.query}” · {result.jobs.length} found
            {result.dropped > 0 ? ` · ${result.dropped} dead link${result.dropped === 1 ? "" : "s"} dropped` : ""}
          </p>
          {result.jobs.length === 0 && <p className="px-5 py-5 text-sm text-body">Google didn&apos;t turn up a live posting for that.</p>}
          <ul className="mt-2">
            {result.jobs.map((j, i) => (
              <li
                key={j.url}
                className="animate-rise flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-line px-5 py-4"
                style={{ animationDelay: `${i * 40}ms` }}
              >
                <div className="min-w-0 flex-1 basis-64">
                  <p className="truncate text-[15px] text-ink">{j.title}</p>
                  <p className="mt-1 truncate text-sm text-muted">
                    {[j.company, j.location, j.posted].filter(Boolean).join(" · ")}
                  </p>
                </div>
                <span
                  className={`font-mono text-[10px] uppercase tracking-[0.14em] ${j.status === "open" ? "text-moss" : "text-amber"}`}
                  title={j.status === "open" ? "We opened this page and it loaded" : "This site blocks automated checks — open it to confirm"}
                >
                  {j.status === "open" ? "● link works" : "● check it yourself"}
                </span>
                <span className="flex gap-2">
                  <a href={j.url} target="_blank" rel="noreferrer" className="btn btn-ghost !px-4 !py-2">
                    Open ↗
                  </a>
                  {j.postingId ? (
                    <Link href={`/jobs/${j.postingId}`} className="btn btn-primary !px-4 !py-2">
                      In feed
                    </Link>
                  ) : tracked[j.url] ? (
                    <Link href={`/tracker/${tracked[j.url]}`} className="btn btn-primary !px-4 !py-2">
                      Tracked ✓
                    </Link>
                  ) : (
                    <button type="button" className="btn btn-primary !px-4 !py-2" disabled={!key} onClick={() => track(j.url, j.company, j.title)}>
                      Track
                    </button>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
