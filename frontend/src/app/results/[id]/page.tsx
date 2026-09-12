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
    <div className="mx-auto max-w-[1100px] px-5 py-16 sm:px-10 sm:py-20">
      <header className="flex flex-wrap items-center gap-5">
        {account?.avatarUrl && (
          <Image src={account.avatarUrl} alt="" width={56} height={56} className="size-14 border border-line" />
        )}
        <div className="min-w-0">
          <span className="chip">{login ? `${login}'s shortlist` : "Shortlist"}</span>
          <h1 className="display mt-5 text-[clamp(1.9rem,3.6vw,2.8rem)]">
            {matches.length} projects <span className="text-accent">for this role</span>
          </h1>
          <p className="label mt-3">
            {analysis.reposAnalyzed} repos considered ·{" "}
            {new Date(analysis.createdAt).toLocaleDateString(undefined, { dateStyle: "medium" })}
          </p>
        </div>
      </header>

      <details className="mt-10 border border-line bg-card px-5 py-4">
        <summary className="label cursor-pointer">The job description</summary>
        <p className="mt-4 whitespace-pre-line text-sm leading-relaxed text-muted">{analysis.jobDescription}</p>
      </details>

      {warnings.length > 0 && (
        <div role="status" className="mt-6 border border-amber/40 bg-amber-soft px-4 py-3 text-sm">
          {warnings.join(" ")}
        </div>
      )}

      <ol className="mt-10 grid gap-5">
        {matches.map((m) => (
          <li key={m.repoId}>
            <MatchCard match={m} />
          </li>
        ))}
      </ol>

      {otherRepos.length > 0 && (
        <details className="mt-10 border border-line">
          <summary className="label cursor-pointer px-5 py-4">Also ranked — {otherRepos.length} more</summary>
          <ol className="border-t border-line">
            {otherRepos.map((r) => (
              <li key={r.repoId} className="flex items-center justify-between gap-4 border-b border-line px-5 py-3 last:border-b-0">
                <span className="min-w-0 truncate text-sm">
                  <span className="mr-4 font-mono text-xs text-muted">{String(r.rank).padStart(2, "0")}</span>
                  <a href={r.url} target="_blank" rel="noreferrer" className="hover:text-accent">
                    {r.name}
                  </a>
                </span>
                <span className="font-mono text-xs text-muted">{r.similarity.toFixed(2)}</span>
              </li>
            ))}
          </ol>
        </details>
      )}

      <div className="mt-14 flex flex-wrap items-center justify-between gap-6 border-t border-line pt-8">
        <Link href={login ? `/analyze?username=${encodeURIComponent(login)}` : "/analyze"} className="btn btn-primary">
          Try another job
        </Link>
        <p className="label max-w-sm text-right">
          {analysis.embeddingModel}
          {analysis.llmModel ? ` + ${analysis.llmModel}` : ""} · every quote checked against your repo
        </p>
      </div>
    </div>
  );
}
