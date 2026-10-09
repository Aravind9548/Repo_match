"use client";

import { useState } from "react";
import { describeError, runAtsCheck, type AtsReport, type TailorRun } from "@/lib/api";

/**
 * The read-back check: the built PDF is re-parsed the way an applicant tracking system would parse it, and the job
 * ad's requirements are counted against what came out of the file.
 *
 * It is deliberately not called a score. No employer's parser is reproduced, and a percentage here predicts nothing
 * — what it can say honestly is "this file is readable, these requirements appear in it, these don't".
 */
export function AtsCheck({ run, trackerKey, onChecked }: {
  run: TailorRun;
  trackerKey: string;
  onChecked: (next: TailorRun) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showText, setShowText] = useState(false);
  const report: AtsReport | null = run.ats ?? null;

  async function check() {
    setBusy(true);
    setError(null);
    try {
      onChecked(await runAtsCheck(trackerKey, run.id, { model: "flash" }));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  if (!run.hasPdf) {
    return null;
  }

  const parse = report?.parse;
  const coverage = report?.coverage ?? null;
  const missing = coverage?.requirements.filter((r) => !r.found) ?? [];
  const found = coverage?.requirements.filter((r) => r.found) ?? [];

  return (
    <div className="border border-line bg-card p-5">
      <p className="label">Before you send it</p>
      {!report ? (
        <>
          <p className="mt-2 text-sm text-body">
            Read the PDF back the way an employer&apos;s parser reads it: is the text machine-readable, does your
            contact detail come out, are the headings recognised — and which of the ad&apos;s requirements actually
            appear in the file.
          </p>
          <button type="button" className="btn btn-primary mt-5 w-full" disabled={busy} onClick={check}>
            {busy ? "Reading it back…" : "Check it against the job"}
          </button>
          <p className="label mt-3 text-center">
            {run.jobDescription && run.jobDescription.length > 40 ? "One AI call · a few seconds" : "No AI call · the parse report only"}
          </p>
        </>
      ) : (
        <div className="mt-3 space-y-5">
          {coverage && coverage.required_total > 0 && (
            <div>
              <div className="flex items-baseline justify-between gap-3">
                <p className="text-sm text-body">
                  <span className="font-mono text-2xl text-ink">{coverage.required_percent}%</span> of what the ad
                  requires appears in the file
                </p>
              </div>
              <div className="mt-2 h-1.5 w-full bg-line">
                <div
                  className="h-full bg-accent transition-[width] duration-700"
                  style={{ width: `${coverage.required_percent}%` }}
                />
              </div>
              <p className="mt-2 text-xs text-muted">
                {coverage.required_found} of {coverage.required_total} required
                {coverage.preferred_total > 0
                  ? `, ${coverage.preferred_found} of ${coverage.preferred_total} preferred`
                  : ""}
                . This counts the ad&apos;s own words against the text a parser pulls out — it isn&apos;t a score any
                employer gives you.
              </p>
            </div>
          )}

          {parse && parse.problems.length > 0 && (
            <div>
              <p className="label">Would trip a parser</p>
              <ul className="mt-2 space-y-2">
                {parse.problems.map((p, i) => (
                  <li key={i} className="text-xs leading-relaxed text-amber">
                    ⚠ {p}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {parse && (
            <div>
              <p className="label">What it read</p>
              <dl className="mt-2 space-y-1 text-xs text-body">
                <div className="flex justify-between gap-3">
                  <dt className="text-muted">Email</dt>
                  <dd>{parse.email || <span className="text-amber">none found</span>}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-muted">Phone</dt>
                  <dd>{parse.phone || <span className="text-amber">none found</span>}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-muted">Links</dt>
                  <dd>{parse.links}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-muted">Headings</dt>
                  <dd>{parse.sections_found.join(", ") || "none"}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-muted">Pages</dt>
                  <dd>{parse.pages}</dd>
                </div>
              </dl>
            </div>
          )}

          {missing.length > 0 && (
            <div>
              <p className="label">The ad asks, the file doesn&apos;t show</p>
              <ul className="mt-2 space-y-2">
                {missing.map((r) => (
                  <li key={r.term} className="border border-amber/40 bg-amber-soft px-3 py-2">
                    <p className="text-sm text-ink">{r.term}</p>
                    {r.quote && <p className="mt-1 text-xs italic text-muted">&ldquo;{r.quote}&rdquo;</p>}
                    {r.importance === "preferred" && <p className="label mt-1">nice to have</p>}
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-muted">
                Nothing is added for you. If you have genuinely done one of these, go back and say so in your own
                words; if you haven&apos;t, it stays missing.
              </p>
            </div>
          )}

          {found.length > 0 && (
            <div>
              <p className="label">Found in the file</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {found.map((r) => (
                  <span
                    key={r.term}
                    title={r.where}
                    className="border border-moss/40 bg-moss-soft px-2 py-0.5 text-xs text-body"
                  >
                    {r.term}
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="border-t border-line pt-4">
            <button type="button" className="label hover:text-ink" onClick={() => setShowText(!showText)}>
              {showText ? "▾" : "▸"} See the plain text a parser gets
            </button>
            {showText && (
              <pre className="animate-rise mt-3 max-h-64 overflow-auto border border-line bg-paper p-3 font-mono text-[11px] leading-relaxed text-muted">
                {report.extracted}
              </pre>
            )}
            <button type="button" className="label mt-3 block hover:text-ink" disabled={busy} onClick={check}>
              {busy ? "Checking…" : "Check again"}
            </button>
            {run.atsCheckedAt && (
              <p className="mt-2 text-xs text-muted">Checked {new Date(run.atsCheckedAt).toLocaleString()}.</p>
            )}
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="mt-4 text-sm text-amber">
          {error}
        </p>
      )}
    </div>
  );
}
