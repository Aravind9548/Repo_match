import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { MatchCard } from "@/components/MatchCard";
import { Squiggle } from "@/components/Doodles";
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
            width={64}
            height={64}
            className="size-16 rounded-full border-2 border-card shadow ring-1 ring-line"
          />
        )}
        <div className="min-w-0">
          <p className="font-hand text-xl text-accent">{login ? `${login}'s shortlist` : "the shortlist"}</p>
          <h1 className="font-display text-3xl font-semibold tracking-tight text-glow sm:text-4xl">
            Your best{" "}
            <span className="relative inline-block">
              {matches.length}
              <Squiggle className="absolute -bottom-1.5 left-0 h-2.5 w-full text-accent" />
            </span>{" "}
            projects for this role
          </h1>
          <p className="mt-1 text-sm text-muted">
            {analysis.reposAnalyzed} repos considered · {new Date(analysis.createdAt).toLocaleDateString(undefined, { dateStyle: "medium" })}
          </p>
        </div>
      </header>

      <details className="mt-6 rounded-2xl border border-line bg-card px-4 py-3">
        <summary className="cursor-pointer text-sm font-semibold">The job description</summary>
        <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-muted">{analysis.jobDescription}</p>
      </details>

      {warnings.length > 0 && (
        <div role="status" className="mt-6 rounded-xl border border-amber/30 bg-amber-soft px-4 py-3 text-sm">
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
          <summary className="cursor-pointer font-hand text-xl text-muted">
            and the rest, in order ({otherRepos.length})
          </summary>
          <ol className="mt-3 divide-y divide-line overflow-hidden rounded-2xl border border-line bg-card text-sm">
            {otherRepos.map((r) => (
              <li key={r.repoId} className="flex items-center justify-between gap-4 px-4 py-2.5">
                <span className="min-w-0 truncate">
                  <span className="mr-3 tabular-nums text-muted">{r.rank}</span>
                  <a href={r.url} target="_blank" rel="noreferrer" className="hover:underline">
                    {r.name}
                  </a>
                </span>
                <span className="shrink-0 tabular-nums text-muted">{r.similarity.toFixed(2)}</span>
              </li>
            ))}
          </ol>
        </details>
      )}

      <div className="mt-12 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6">
        <Link
          href={login ? `/analyze?username=${encodeURIComponent(login)}` : "/analyze"}
          className="rounded-full bg-ink px-5 py-2.5 text-sm font-semibold text-paper transition hover:bg-accent"
        >
          Try another job description
        </Link>
        <p className="max-w-xs text-right text-xs text-muted">
          Ranked with {analysis.embeddingModel}
          {analysis.llmModel ? ` + ${analysis.llmModel}` : ""}. Every quote is checked against your repo.
        </p>
      </div>
    </div>
  );
}
