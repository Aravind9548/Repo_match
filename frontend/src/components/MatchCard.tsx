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
      className={`rounded-2xl border border-line bg-card p-5 transition duration-300 sm:p-6 ${
        dimmed ? "opacity-55" : "hover:-translate-y-0.5 hover:shadow-riso"
      }`}
    >
      <header className="flex items-start gap-4">
        <span className="shrink-0 font-display text-4xl font-semibold leading-none text-accent tabular-nums">
          {match.rank}
        </span>
        <div className="min-w-0 flex-1">
          <a
            href={match.url}
            target="_blank"
            rel="noreferrer"
            className="break-words font-display text-xl font-semibold hover:underline"
          >
            {match.name}
          </a>
          {match.description && <p className="mt-0.5 text-sm text-muted">{match.description}</p>}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          <div className="text-right">
            <span className="font-display text-3xl font-semibold leading-none tabular-nums">{match.matchScore}</span>
            <span className="ml-1 text-xs uppercase tracking-wide text-muted">match</span>
          </div>
          {actions}
        </div>
      </header>

      <div className="mt-4">
        <ScoreBar value={match.matchScore} label={`${match.name} match score`} />
      </div>

      {match.reason && <p className="mt-4 leading-relaxed">{match.reason}</p>}

      {match.matchedRequirements.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2" aria-label="Job requirements this project evidences">
          {match.matchedRequirements.map((req) => (
            <li key={req} className="rounded-full border border-moss/25 bg-moss-soft px-2.5 py-0.5 text-xs font-medium text-moss">
              {req}
            </li>
          ))}
        </ul>
      )}

      {match.evidence.length > 0 && (
        <div className="mt-5 rounded-xl bg-sunken/70 px-4 py-3">
          <h3 className="font-hand text-lg leading-none text-muted">straight from the repo</h3>
          <ul className="mt-2 space-y-1">
            {match.evidence.map((quote) => (
              <li key={quote} className="font-mono text-[13px] text-ink/85">
                “{quote}”
              </li>
            ))}
          </ul>
        </div>
      )}

      <footer className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line pt-4 text-xs text-muted">
        {match.languages.length > 0 && <span>{match.languages.slice(0, 4).join(" · ")}</span>}
        {match.fitScore !== null && <span>LLM fit {match.fitScore}/100</span>}
        <span>similarity {match.similarity.toFixed(2)}</span>
        {match.stars > 0 && <span>★ {match.stars}</span>}
        {!match.hasReadme && (
          <span className="rounded-full bg-amber-soft px-2 py-0.5 font-medium text-amber">
            No README — add one to strengthen this match
          </span>
        )}
      </footer>
    </article>
  );
}
