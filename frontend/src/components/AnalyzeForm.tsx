"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, createWorkflow, describeError } from "@/lib/api";
import { ArrowRightIcon } from "@/components/icons";

const MIN_JD = 50;
const MAX_JD = 20000;

const inputClass =
  "mt-1.5 block w-full rounded-xl border border-line bg-card px-3.5 py-2.5 text-base shadow-sm outline-none transition placeholder:text-muted/70 focus:border-ink focus:ring-4 focus:ring-ink/5 disabled:opacity-60";

export function AnalyzeForm({
  initialUsername = "",
  onPendingChange,
}: {
  initialUsername?: string;
  onPendingChange?: (pending: boolean) => void;
}) {
  const router = useRouter();
  const [username, setUsername] = useState(initialUsername);
  const [jobDescription, setJobDescription] = useState("");
  const [topK, setTopK] = useState(5);
  const [includeForks, setIncludeForks] = useState(false);
  const [pending, setPendingState] = useState(false);
  const setPending = (p: boolean) => {
    setPendingState(p);
    onPendingChange?.(p);
  };
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setPending(true);
    setError(null);
    setFieldErrors({});
    try {
      // Step 1 only fetches repos (free); every later step waits for the human.
      const workflow = await createWorkflow({
        username: username.trim(),
        jobDescription: jobDescription.trim(),
        topK,
        includeForks,
      });
      router.push(`/workflow/${workflow.id}`);
    } catch (err) {
      setError(describeError(err));
      if (err instanceof ApiError) setFieldErrors(err.problem.fieldErrors ?? {});
      setPending(false);
    }
  }

  const jdLength = jobDescription.trim().length;

  return (
    <form onSubmit={onSubmit} className="space-y-6" aria-busy={pending}>
      <div>
        <label htmlFor="username" className="text-sm font-semibold">
          GitHub username
        </label>
        <div className="relative">
          <span className="pointer-events-none absolute left-3.5 top-1/2 mt-[3px] -translate-y-1/2 text-muted">
            github.com/
          </span>
          <input
            id="username"
            name="username"
            required
            autoComplete="off"
            spellCheck={false}
            pattern="[A-Za-z0-9](?:[A-Za-z0-9\-]{0,38})"
            title="Letters, numbers and hyphens (max 39 characters)"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className={`${inputClass} pl-[6.6rem]`}
            placeholder="octocat"
            disabled={pending}
            aria-invalid={Boolean(fieldErrors.username)}
          />
        </div>
        {fieldErrors.username && <p className="mt-1 text-sm text-accent">Username {fieldErrors.username}</p>}
      </div>

      <div>
        <div className="flex items-baseline justify-between">
          <label htmlFor="jd" className="text-sm font-semibold">
            Job description
          </label>
          <span className={`text-xs tabular-nums ${jdLength > 0 && jdLength < MIN_JD ? "text-accent" : "text-muted"}`}>
            {jdLength < MIN_JD ? `${jdLength}/${MIN_JD} min` : `${jdLength.toLocaleString()} chars`}
          </span>
        </div>
        <textarea
          id="jd"
          name="jobDescription"
          required
          minLength={MIN_JD}
          maxLength={MAX_JD}
          rows={11}
          value={jobDescription}
          onChange={(e) => setJobDescription(e.target.value)}
          className={`${inputClass} resize-y leading-relaxed`}
          placeholder="Paste the whole posting — responsibilities, must-haves and nice-to-haves all help."
          disabled={pending}
          aria-invalid={Boolean(fieldErrors.jobDescription)}
        />
        {fieldErrors.jobDescription && (
          <p className="mt-1 text-sm text-accent">Job description {fieldErrors.jobDescription}</p>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-x-8 gap-y-3 text-sm">
        <label className="flex items-center gap-2">
          Aim for
          <select
            value={topK}
            onChange={(e) => setTopK(Number(e.target.value))}
            disabled={pending}
            className="rounded-lg border border-line bg-card px-2 py-1"
          >
            {[3, 5, 7, 10].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
          projects
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={includeForks}
            onChange={(e) => setIncludeForks(e.target.checked)}
            disabled={pending}
            className="size-4 accent-[var(--accent)]"
          />
          Pre-select forked repos
        </label>
      </div>

      {error && (
        <div role="alert" className="rounded-xl border border-accent/30 bg-accent-soft px-4 py-3 text-sm">
          {error}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-4">
        <button
          type="submit"
          disabled={pending}
          className="inline-flex items-center gap-2 rounded-full bg-ink px-6 py-3 font-semibold text-paper shadow-riso-pink transition hover:-translate-y-0.5 hover:shadow-riso disabled:cursor-wait disabled:opacity-70"
        >
          {pending ? "Fetching your repos…" : "Start — fetch my repos"}
          {!pending && <ArrowRightIcon className="size-4" />}
        </button>
        <p className="font-hand text-lg text-muted">nothing paid happens without your OK</p>
      </div>
    </form>
  );
}
