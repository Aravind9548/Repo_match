"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  buildApplyKit,
  describeError,
  getApplyKit,
  tailorDownloadUrl,
  type ApplicantProfile,
  type ApplyKit,
  type KitAnswer,
} from "@/lib/api";

/** The standing answers worth copying straight into a form, in the order forms ask. */
const PROFILE_ROWS: { key: keyof ApplicantProfile; label: string }[] = [
  { key: "fullName", label: "Name" },
  { key: "email", label: "Email" },
  { key: "phone", label: "Phone" },
  { key: "location", label: "Based in" },
  { key: "githubUrl", label: "GitHub" },
  { key: "linkedinUrl", label: "LinkedIn" },
  { key: "portfolioUrl", label: "Portfolio" },
  { key: "workAuthorization", label: "Work authorisation" },
  { key: "noticePeriod", label: "Availability" },
  { key: "graduation", label: "Graduation" },
  { key: "expectedSalary", label: "Expected pay" },
];

function Copy({ text, label = "Copy" }: { text: string; label?: string }) {
  const [done, setDone] = useState(false);
  return (
    <button
      type="button"
      className="label shrink-0 text-accent-2 transition hover:text-ink"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setDone(true);
          setTimeout(() => setDone(false), 1600);
        } catch {
          setDone(false);
        }
      }}
    >
      {done ? "Copied" : label}
    </button>
  );
}

function grounding(a: KitAnswer) {
  return a.groundedOn ?? a.grounded_on ?? "";
}

/**
 * Everything needed to fill one application: the tailored PDF, your standing answers, and this job's answers
 * drafted from your resume and repositories. Nothing is submitted — you open the form and press submit yourself.
 */
export function ApplyKitPanel({ id, applyUrl, trackerKey }: { id: number; applyUrl: string | null; trackerKey: string }) {
  const [kit, setKit] = useState<ApplyKit | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [github, setGithub] = useState("");

  useEffect(() => {
    getApplyKit(trackerKey, id)
      .then((k) => {
        setKit(k);
        if (k.profile?.githubUrl) {
          setGithub(k.profile.githubUrl.replace(/\/+$/, "").split("/").pop() ?? "");
        }
      })
      .catch((err) => setError(describeError(err)));
  }, [trackerKey, id]);

  async function draft() {
    setBusy(true);
    setError(null);
    try {
      setKit(await buildApplyKit(trackerKey, id, { githubUsername: github.trim() || undefined, model: "pro" }));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  const answers = kit?.answers ?? [];
  const profileRows = kit?.profile
    ? PROFILE_ROWS.map((r) => ({ ...r, value: (kit.profile![r.key] as string) ?? "" })).filter((r) => r.value.trim())
    : [];
  const allText = [
    ...profileRows.map((r) => `${r.label}: ${r.value}`),
    ...answers.map((a) => `${a.question}\n${a.answer}`),
  ].join("\n\n");

  return (
    <div className="border border-line bg-card p-5">
      <div className="flex items-baseline justify-between gap-3">
        <p className="label">Apply kit</p>
        {allText && <Copy text={allText} label="Copy all" />}
      </div>
      <p className="mt-2 text-sm text-body">
        Your answers in one place, so the form takes a minute. You open it and submit it — RepoMatch never does.
      </p>

      <div className="mt-4 flex flex-wrap gap-3">
        {applyUrl && (
          <a href={applyUrl} target="_blank" rel="noreferrer" className="btn btn-primary !px-5 !py-2.5">
            Open the form ↗
          </a>
        )}
        {kit?.hasPdf && kit.tailorRunId && (
          <a href={tailorDownloadUrl(trackerKey, kit.tailorRunId, "pdf")} className="btn btn-ghost !px-5 !py-2.5">
            Tailored PDF ↓
          </a>
        )}
      </div>

      {profileRows.length > 0 ? (
        <dl className="mt-5 divide-y divide-line border-t border-line">
          {profileRows.map((r) => (
            <div key={String(r.key)} className="flex items-start justify-between gap-3 py-2.5">
              <dt className="label shrink-0 pt-0.5">{r.label}</dt>
              <dd className="flex min-w-0 items-start gap-3">
                <span className="truncate text-sm text-body">{r.value}</span>
                <Copy text={r.value} />
              </dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="mt-5 border-t border-line pt-5 text-sm text-muted">
          <Link href="/apply/profile" className="text-accent-2 hover:text-ink">
            Fill in your details once
          </Link>{" "}
          and they&apos;ll be here for every application.
        </p>
      )}

      <div className="mt-6 border-t border-line pt-5">
        <p className="label">This job&apos;s questions</p>
        {answers.length === 0 && (
          <p className="mt-2 text-sm text-muted">
            Why this company, what you&apos;d bring, your strongest project — drafted from your resume and your
            repositories, grounded in both. One Gemini call.
          </p>
        )}

        {answers.length > 0 && (
          <ul className="mt-4 space-y-5">
            {answers.map((a, i) => (
              <li key={i} className="animate-rise" style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}>
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-sm font-medium text-ink">{a.question}</p>
                  <Copy text={a.answer} />
                </div>
                <p className="mt-1.5 whitespace-pre-wrap text-sm text-body">{a.answer}</p>
                {grounding(a) && <p className="label mt-1.5">From: {grounding(a)}</p>}
                {a.unsupported && (
                  <p className="mt-1.5 text-xs text-amber">
                    Read this one before you send it — it uses words that appear nowhere in your resume, repositories
                    or details{a.new_terms?.length ? ` (${a.new_terms.join(", ")})` : ""}.
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}

        <label className="mt-5 block">
          <span className="label">GitHub username</span>
          <input
            className="mt-2 w-full border border-line bg-paper px-3 py-2.5 text-sm outline-none transition placeholder:text-muted/70 focus:border-accent"
            value={github}
            onChange={(e) => setGithub(e.target.value)}
            placeholder="So the answers can quote your repositories"
          />
        </label>
        <button type="button" className="btn btn-primary mt-4 w-full !py-2.5" disabled={busy} onClick={draft}>
          {busy ? "Drafting…" : answers.length ? "Draft again" : "Draft the answers"}
        </button>
        {kit?.createdAt && (
          <p className="mt-2 text-xs text-muted">
            Drafted {new Date(kit.createdAt).toLocaleString()}
            {kit.model ? ` · ${kit.model}` : ""}.
          </p>
        )}
      </div>

      {error && (
        <p role="alert" className="mt-4 text-sm text-amber">
          {error}
        </p>
      )}
    </div>
  );
}
