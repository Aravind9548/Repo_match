import type { MatchResult } from "@/lib/api";
import { ScoreBar } from "./ScoreBar";

export function MatchCard({ match }: { match: MatchResult }) {
  return (
    <article className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm sm:p-6 dark:border-zinc-800 dark:bg-zinc-900">
      <header className="flex items-start gap-4">
        <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-zinc-900 text-sm font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900">
          {match.rank}
        </span>
        <div className="min-w-0 flex-1">
          <a
            href={match.url}
            target="_blank"
            rel="noreferrer"
            className="break-words text-lg font-semibold hover:underline"
          >
            {match.name}
          </a>
          {match.description && (
            <p className="mt-0.5 text-sm text-zinc-600 dark:text-zinc-400">{match.description}</p>
          )}
        </div>
        <div className="shrink-0 text-right">
          <div className="text-3xl font-bold leading-none tabular-nums">{match.matchScore}</div>
          <div className="mt-1 text-xs uppercase tracking-wide text-zinc-500">match</div>
        </div>
      </header>

      <div className="mt-4">
        <ScoreBar value={match.matchScore} label={`${match.name} match score`} />
      </div>

      {match.reason && <p className="mt-4 leading-relaxed">{match.reason}</p>}

      {match.matchedRequirements.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2" aria-label="Job requirements this project evidences">
          {match.matchedRequirements.map((req) => (
            <li
              key={req}
              className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-800 ring-1 ring-emerald-200 dark:bg-emerald-950 dark:text-emerald-200 dark:ring-emerald-900"
            >
              {req}
            </li>
          ))}
        </ul>
      )}

      {match.evidence.length > 0 && (
        <div className="mt-4">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-zinc-500">From the repo</h3>
          <ul className="mt-2 space-y-1.5">
            {match.evidence.map((quote) => (
              <li
                key={quote}
                className="border-l-2 border-zinc-300 pl-3 font-mono text-sm text-zinc-700 dark:border-zinc-700 dark:text-zinc-300"
              >
                {quote}
              </li>
            ))}
          </ul>
        </div>
      )}

      <footer className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-zinc-100 pt-4 text-xs text-zinc-500 dark:border-zinc-800">
        {match.languages.length > 0 && <span>{match.languages.slice(0, 4).join(" · ")}</span>}
        {match.fitScore !== null && <span>LLM fit {match.fitScore}/100</span>}
        <span>similarity {match.similarity.toFixed(2)}</span>
        {match.stars > 0 && <span>★ {match.stars}</span>}
        {!match.hasReadme && (
          <span className="rounded bg-amber-100 px-1.5 py-0.5 font-medium text-amber-900 dark:bg-amber-950 dark:text-amber-200">
            No README — add one to strengthen this match
          </span>
        )}
      </footer>
    </article>
  );
}
