import type { Metadata } from "next";
import Link from "next/link";
import { describeError, getHistory, type AnalysisSummary } from "@/lib/api";

export const metadata: Metadata = { title: "History · RepoMatch" };

export default async function HistoryPage({ searchParams }: { searchParams: Promise<{ username?: string }> }) {
  const { username = "" } = await searchParams;
  const login = username.trim();

  let items: AnalysisSummary[] = [];
  let error: string | null = null;
  if (login) {
    try {
      items = await getHistory(login);
    } catch (e) {
      error = describeError(e);
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <p className="font-hand text-2xl text-accent">the paper trail</p>
      <h1 className="mt-1 font-display text-4xl font-semibold tracking-tight">Past matches</h1>

      <form action="/history" className="mt-6 flex flex-wrap gap-3">
        <label htmlFor="history-username" className="sr-only">
          GitHub username
        </label>
        <input
          id="history-username"
          name="username"
          defaultValue={login}
          required
          placeholder="GitHub username"
          className="min-w-0 flex-1 rounded-xl border border-line bg-card px-3.5 py-2.5 shadow-sm outline-none focus:border-ink"
        />
        <button className="rounded-full bg-ink px-5 py-2.5 text-sm font-semibold text-paper hover:bg-accent">Show</button>
      </form>

      {error && <p className="mt-6 rounded-xl border border-accent/30 bg-accent-soft px-4 py-3 text-sm">{error}</p>}

      {login && !error && items.length === 0 && (
        <p className="mt-10 text-center font-hand text-2xl text-muted">no saved matches for {login} yet</p>
      )}

      <ol className="mt-8 space-y-4">
        {items.map((a) => (
          <li key={a.id}>
            <Link
              href={`/results/${a.id}`}
              className="block rounded-2xl border border-line bg-card p-5 shadow-sm shadow-ink/5 transition hover:-translate-y-0.5 hover:shadow-md"
            >
              <p className="text-xs text-muted">
                {new Date(a.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}
              </p>
              <p className="mt-1 line-clamp-2 font-medium">{a.jobDescriptionPreview}</p>
              <ul className="mt-3 flex flex-wrap gap-1.5">
                {a.topRepos.map((name, i) => (
                  <li key={name} className="rounded-full bg-sunken px-2.5 py-0.5 text-xs">
                    <span className="mr-1 text-accent">{i + 1}</span>
                    {name}
                  </li>
                ))}
              </ul>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  );
}
