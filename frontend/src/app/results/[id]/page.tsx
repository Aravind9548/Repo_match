import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { MatchCard } from "@/components/MatchCard";
import { ApiError, getAnalysis, type Analysis } from "@/lib/api";

export const metadata: Metadata = { title: "Results · RepoMatch" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function ResultsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!UUID.test(id)) notFound();

  let analysis: Analysis;
  try {
    analysis = await getAnalysis(id);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }

  const { account, matches, otherRepos, warnings } = analysis;
  const login = account?.login;

  return (
    <div className="mx-auto max-w-3xl">
      <header className="flex items-center gap-4">
        {account?.avatarUrl && (
          <Image
            src={account.avatarUrl}
            alt=""
            width={56}
            height={56}
            className="size-14 rounded-full ring-1 ring-zinc-200 dark:ring-zinc-800"
          />
        )}
        <div className="min-w-0">
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
            Top {matches.length} projects{login ? ` for ${login}` : ""}
          </h1>
          <p className="mt-1 text-sm text-zinc-500">
            {analysis.reposAnalyzed} repos analyzed · {new Date(analysis.createdAt).toLocaleString()}
          </p>
        </div>
      </header>

      <details className="mt-6 rounded-xl border border-zinc-200 bg-white px-4 py-3 dark:border-zinc-800 dark:bg-zinc-900">
        <summary className="cursor-pointer text-sm font-medium">Job description</summary>
        <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">
          {analysis.jobDescription}
        </p>
      </details>

      {warnings.length > 0 && (
        <div role="status" className="mt-6 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
          {warnings.join(" ")}
        </div>
      )}

      <ol className="mt-8 space-y-5">
        {matches.map((m) => (
          <li key={m.repoId}>
            <MatchCard match={m} />
          </li>
        ))}
      </ol>

      {otherRepos.length > 0 && (
        <details className="mt-8">
          <summary className="cursor-pointer text-sm font-medium text-zinc-600 dark:text-zinc-400">
            Also considered ({otherRepos.length})
          </summary>
          <ol className="mt-3 divide-y divide-zinc-100 rounded-xl border border-zinc-200 bg-white text-sm dark:divide-zinc-800 dark:border-zinc-800 dark:bg-zinc-900">
            {otherRepos.map((r) => (
              <li key={r.repoId} className="flex items-center justify-between gap-4 px-4 py-2.5">
                <span className="min-w-0 truncate">
                  <span className="mr-3 tabular-nums text-zinc-400">{r.rank}</span>
                  <a href={r.url} target="_blank" rel="noreferrer" className="hover:underline">
                    {r.name}
                  </a>
                </span>
                <span className="shrink-0 tabular-nums text-zinc-500">{r.similarity.toFixed(2)}</span>
              </li>
            ))}
          </ol>
        </details>
      )}

      <div className="mt-10 flex flex-wrap items-center justify-between gap-4 border-t border-zinc-200 pt-6 text-sm dark:border-zinc-800">
        <Link
          href={login ? `/analyze?username=${encodeURIComponent(login)}` : "/analyze"}
          className="rounded-lg bg-zinc-900 px-4 py-2 font-medium text-white hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
        >
          Try another job description
        </Link>
        <p className="text-xs text-zinc-500">
          Ranked with {analysis.embeddingModel}
          {analysis.llmModel ? ` + ${analysis.llmModel}` : ""}. Reasons cite only your repo content.
        </p>
      </div>
    </div>
  );
}
