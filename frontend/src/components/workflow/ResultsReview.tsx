"use client";

import { useState } from "react";
import type { MatchResult, TokenUsage } from "@/lib/api";
import { MatchCard } from "@/components/MatchCard";
import { CheckIcon, LinkIcon } from "@/components/icons";
import { PrimaryButton, ReviewFooter, TextButton } from "./ReviewFooter";

export function ResultsReview({
  judgements,
  acceptedIds,
  usage,
  llmModel,
  busy,
  onApprove,
  onBack,
}: {
  judgements: MatchResult[];
  acceptedIds: number[];
  usage: TokenUsage | null;
  llmModel: string | null;
  busy: boolean;
  onApprove: (ids: number[]) => void;
  onBack: () => void;
}) {
  const [accepted, setAccepted] = useState(() => new Set(acceptedIds));
  const toggle = (id: number) =>
    setAccepted((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section aria-labelledby="results-title">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="results-title" className="font-display text-2xl font-semibold">
            Gemini&apos;s verdict — your call
          </h2>
          <p className="mt-3 text-sm text-muted">Keep the ones you&apos;d actually talk about in an interview.</p>
        </div>
        <TextButton onClick={onBack} disabled={busy}>
          ← Edit shortlist (re-runs Gemini)
        </TextButton>
      </div>

      <ol className="mt-6 space-y-5">
        {judgements.map((m) => {
          const on = accepted.has(m.repoId);
          return (
            <li key={m.repoId}>
              <MatchCard
                match={m}
                dimmed={!on}
                actions={
                  <button
                    type="button"
                    onClick={() => toggle(m.repoId)}
                    disabled={busy}
                    aria-pressed={on}
                    className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-semibold transition ${
                      on ? "border-moss bg-moss-soft text-moss" : "border-line bg-card text-muted hover:text-ink"
                    }`}
                  >
                    {on ? (
                      <>
                        <CheckIcon className="size-4" /> Keep
                      </>
                    ) : (
                      "Skipped"
                    )}
                  </button>
                }
              />
            </li>
          );
        })}
      </ol>

      {usage && (
        <p className="mt-4 text-xs text-muted">
          {llmModel ?? "Gemini"} read {usage.promptTokens.toLocaleString()} tokens, thought for{" "}
          {usage.thinkingTokens.toLocaleString()} and wrote {usage.outputTokens.toLocaleString()}.
        </p>
      )}

      <ReviewFooter
        summary={
          <>
            <strong className="text-ink">{accepted.size}</strong> of {judgements.length} picks kept
          </>
        }
      >
        <PrimaryButton
          disabled={busy || accepted.size === 0}
          onClick={() => onApprove(judgements.filter((m) => accepted.has(m.repoId)).map((m) => m.repoId))}
        >
          <LinkIcon className="size-4" />
          {busy ? "Saving…" : "Save & get a shareable link"}
        </PrimaryButton>
      </ReviewFooter>
    </section>
  );
}
