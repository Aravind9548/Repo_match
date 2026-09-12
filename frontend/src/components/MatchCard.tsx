import type { ReactNode } from "react";
import type { MatchResult } from "@/lib/api";
import { ScoreBar } from "./ScoreBar";

export function MatchCard({
  match,
  actions,
  dimmed = false,
}: {
  match: MatchResult;
  /** Extra controls in the card header (e.g. keep/skip during review). */
  actions?: ReactNode;
  dimmed?: boolean;
}) {
  return (
    <article
      className={`border border-line bg-card p-6 transition sm:p-8 ${dimmed ? "opacity-50" : "hover:border-accent/50"}`}
    >
      <header className="flex items-start gap-5">
        <span className="font-mono text-xs text-accent">{String(match.rank).padStart(2, "0")}</span>
        <div className="min-w-0 flex-1">
          <a href={match.url} target="_blank" rel="noreferrer" className="display break-words text-xl hover:text-accent">
            {match.name}
          </a>
          {match.description && <p className="mt-2 text-sm text-muted">{match.description}</p>}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-3">
          <div className="text-right">
            <span className="font-display text-4xl font-extrabold leading-none text-ink tabular-nums">{match.matchScore}</span>
            <span className="label ml-2">match</span>
          </div>
          {actions}
        </div>
      </header>

      <div className="mt-5">
        <ScoreBar value={match.matchScore} label={`${match.name} match score`} />
      </div>

      {match.reason && <p className="mt-6 leading-relaxed text-body">{match.reason}</p>}

      {match.matchedRequirements.length > 0 && (
        <ul className="mt-6 flex flex-wrap gap-2" aria-label="Job requirements this project evidences">
          {match.matchedRequirements.map((req) => (
            <li key={req} className="border border-accent/40 bg-accent-soft px-3 py-1 font-mono text-[11px] uppercase tracking-wider text-accent-2">
              {req}
            </li>
          ))}
        </ul>
      )}

      {match.evidence.length > 0 && (
        <div className="mt-7 border-l-2 border-accent bg-sunken px-5 py-4">
          <h3 className="label">Straight from the repo</h3>
          <ul className="mt-3 space-y-1.5">
            {match.evidence.map((quote) => (
              <li key={quote} className="font-mono text-[13px] leading-relaxed text-body">
                “{quote}”
              </li>
            ))}
          </ul>
        </div>
      )}

      <footer className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-line pt-5">
        {match.languages.length > 0 && <span className="label">{match.languages.slice(0, 4).join(" · ")}</span>}
        {match.fitScore !== null && <span className="label">Fit {match.fitScore}/100</span>}
        <span className="label">Similarity {match.similarity.toFixed(2)}</span>
        {match.stars > 0 && <span className="label">★ {match.stars}</span>}
        {!match.hasReadme && (
          <span className="border border-amber/40 bg-amber-soft px-2 py-1 font-mono text-[11px] uppercase tracking-wider text-amber">
            No README
          </span>
        )}
      </footer>
    </article>
  );
}
