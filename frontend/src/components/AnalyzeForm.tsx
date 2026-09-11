"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { analyze, ApiError, describeError } from "@/lib/api";

const MIN_JD = 50;
const MAX_JD = 20000;

// The backend call is one request; these stages mirror what it does, paced by typical timings.
const STAGES = [
  { after: 0, label: "Syncing your GitHub repositories" },
  { after: 4, label: "Embedding repos and ranking them against the job description" },
  { after: 9, label: "Gemini is reading the shortlist and judging fit" },
  { after: 45, label: "Almost done — writing grounded reasons" },
];

const inputClass =
  "mt-1.5 block w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-base shadow-sm outline-none focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 dark:border-zinc-700 dark:bg-zinc-900 dark:focus:border-zinc-300";

export function AnalyzeForm({ initialUsername = "" }: { initialUsername?: string }) {
  const router = useRouter();
  const [username, setUsername] = useState(initialUsername);
  const [jobDescription, setJobDescription] = useState("");
  const [topK, setTopK] = useState(5);
  const [includeForks, setIncludeForks] = useState(false);
  const [pending, setPending] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!pending) return;
    const start = Date.now();
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - start) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [pending]);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setPending(true);
    setElapsed(0);
    setError(null);
    setFieldErrors({});
    try {
      const result = await analyze({
        username: username.trim(),
        jobDescription: jobDescription.trim(),
        topK,
        includeForks,
      });
      router.push(`/results/${result.id}`);
    } catch (err) {
      setError(describeError(err));
      if (err instanceof ApiError) setFieldErrors(err.problem.fieldErrors ?? {});
      setPending(false);
    }
  }

  const stage = [...STAGES].reverse().find((s) => elapsed >= s.after)!;
  const jdLength = jobDescription.trim().length;

  return (
    <form onSubmit={onSubmit} className="space-y-6" aria-busy={pending}>
      <div>
        <label htmlFor="username" className="text-sm font-medium">
          GitHub username
        </label>
        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 mt-[3px] -translate-y-1/2 text-zinc-400">
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
            className={`${inputClass} pl-[6.5rem]`}
            placeholder="octocat"
            disabled={pending}
            aria-invalid={Boolean(fieldErrors.username)}
          />
        </div>
        {fieldErrors.username && <p className="mt-1 text-sm text-red-600">Username {fieldErrors.username}</p>}
      </div>

      <div>
        <div className="flex items-baseline justify-between">
          <label htmlFor="jd" className="text-sm font-medium">
            Job description
          </label>
          <span className={`text-xs tabular-nums ${jdLength > 0 && jdLength < MIN_JD ? "text-amber-600" : "text-zinc-500"}`}>
            {jdLength < MIN_JD ? `${jdLength}/${MIN_JD} min` : `${jdLength.toLocaleString()} chars`}
          </span>
        </div>
        <textarea
          id="jd"
          name="jobDescription"
          required
          minLength={MIN_JD}
          maxLength={MAX_JD}
          rows={12}
          value={jobDescription}
          onChange={(e) => setJobDescription(e.target.value)}
          className={`${inputClass} resize-y leading-relaxed`}
          placeholder="Paste the full job description — responsibilities, required and nice-to-have skills."
          disabled={pending}
          aria-invalid={Boolean(fieldErrors.jobDescription)}
        />
        {fieldErrors.jobDescription && (
          <p className="mt-1 text-sm text-red-600">Job description {fieldErrors.jobDescription}</p>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
        <label className="flex items-center gap-2 text-sm">
          Show top
          <select
            value={topK}
            onChange={(e) => setTopK(Number(e.target.value))}
            disabled={pending}
            className="rounded-md border border-zinc-300 bg-white px-2 py-1 dark:border-zinc-700 dark:bg-zinc-900"
          >
            {[3, 5, 7, 10].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
          projects
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={includeForks}
            onChange={(e) => setIncludeForks(e.target.checked)}
            disabled={pending}
            className="size-4 accent-zinc-900"
          />
          Include forked repos
        </label>
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-4">
        <button
          type="submit"
          disabled={pending}
          className="rounded-lg bg-zinc-900 px-5 py-2.5 font-medium text-white shadow-sm transition hover:bg-zinc-700 disabled:cursor-wait disabled:opacity-70 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
        >
          {pending ? "Analyzing…" : "Find my best projects"}
        </button>
        {pending ? (
          <p className="flex items-center gap-2 text-sm text-zinc-600 dark:text-zinc-400" aria-live="polite">
            <span className="size-2 animate-pulse rounded-full bg-emerald-500" />
            {stage.label}… <span className="tabular-nums">{elapsed}s</span>
          </p>
        ) : (
          <p className="text-sm text-zinc-500">Usually takes 30–60 seconds.</p>
        )}
      </div>
    </form>
  );
}
