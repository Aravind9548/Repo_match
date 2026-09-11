"use client";

import { useState } from "react";
import type { RankedOption } from "@/lib/api";
import { SparkleIcon } from "@/components/icons";
import { PrimaryButton, ReviewFooter, TextButton } from "./ReviewFooter";

const MAX_JUDGED = 15;

/** Rough spend for one Gemini 3.1 Pro judge call (~$0.08 for 12 repos), shown before the human commits. */
const estimateCost = (n: number) => (0.02 + n * 0.005).toFixed(2);

export function ShortlistReview({
  ranking,
  busy,
  onApprove,
  onBack,
}: {
  ranking: RankedOption[];
  busy: boolean;
  onApprove: (ids: number[]) => void;
  onBack?: () => void;
}) {
  const [picked, setPicked] = useState(() => new Set(ranking.filter((r) => r.shortlisted).map((r) => r.id)));
  const sims = ranking.map((r) => r.similarity);
  const min = Math.min(...sims);
  const span = Math.max(...sims) - min || 1;
  const tooMany = picked.size > MAX_JUDGED;

  const toggle = (id: number) =>
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section aria-labelledby="shortlist-title">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="shortlist-title" className="font-display text-2xl font-semibold">
            Who gets a closer look?
          </h2>
          <p className="mt-1 max-w-2xl font-hand text-xl text-muted">
            Gemini will read the README of every repo you tick — thin ones start unticked, but trust your gut
          </p>
        </div>
        {onBack && (
          <TextButton onClick={onBack} disabled={busy}>
            ← Change repo selection
          </TextButton>
        )}
      </div>

      <ol className="mt-6 divide-y divide-line overflow-hidden rounded-2xl border border-line bg-card">
        {ranking.map((r, i) => {
          const on = picked.has(r.id);
          const rel = Math.round(((r.similarity - min) / span) * 100);
          return (
            <li key={r.id}>
              <label className={`flex cursor-pointer items-center gap-4 px-4 py-3 transition hover:bg-sunken/60 ${on ? "" : "opacity-60"}`}>
                <input
                  type="checkbox"
                  checked={on}
                  onChange={() => toggle(r.id)}
                  disabled={busy}
                  className="size-4 shrink-0 accent-[var(--accent)]"
                />
                <span className="w-6 shrink-0 text-right font-display text-lg text-muted tabular-nums">{i + 1}</span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="truncate font-medium">{r.name}</span>
                    {!r.hasReadme && (
                      <span className="shrink-0 rounded-full bg-amber-soft px-2 py-0.5 text-xs font-medium text-amber">no README</span>
                    )}
                  </span>
                  <span className="mt-1.5 block h-1.5 w-full max-w-xs overflow-hidden rounded-full bg-sunken">
                    <span className="block h-full rounded-full bg-ink/70" style={{ width: `${Math.max(4, rel)}%` }} />
                  </span>
                </span>
                <span className="shrink-0 text-xs text-muted tabular-nums" title="Cosine similarity to the job description">
                  {r.similarity.toFixed(3)}
                </span>
              </label>
            </li>
          );
        })}
      </ol>

      <ReviewFooter
        summary={
          tooMany ? (
            <span className="font-medium text-accent">Gemini can judge up to {MAX_JUDGED} repos at once — untick a few.</span>
          ) : (
            <>
              <strong className="text-ink">{picked.size}</strong> shortlisted · est.{" "}
              <strong className="text-ink">${estimateCost(picked.size)}</strong> of Gemini credits
            </>
          )
        }
      >
        <PrimaryButton
          tone="accent"
          disabled={busy || picked.size === 0 || tooMany}
          onClick={() => onApprove(ranking.filter((r) => picked.has(r.id)).map((r) => r.id))}
        >
          <SparkleIcon className="size-4" />
          {busy ? "Gemini is reading…" : "Ask Gemini to judge"}
        </PrimaryButton>
      </ReviewFooter>
    </section>
  );
}
