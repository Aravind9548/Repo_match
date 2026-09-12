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
    <div className="mx-auto max-w-[1100px] px-5 py-16 sm:px-10 sm:py-20">
      <span className="chip">The paper trail</span>
      <h1 className="display mt-7 text-[clamp(2rem,4vw,3rem)]">Past matches</h1>

      <form action="/history" className="mt-10 flex flex-wrap gap-3">
        <label htmlFor="history-username" className="sr-only">
          GitHub username
        </label>
        <input
          id="history-username"
          name="username"
          defaultValue={login}
          required
          placeholder="GitHub username"
          className="min-w-0 flex-1 border border-line bg-card px-4 py-3 text-base outline-none focus:border-accent"
        />
        <button className="btn btn-primary">Show</button>
      </form>

      {error && <p className="mt-8 border border-accent/40 bg-accent-soft px-4 py-3 text-sm">{error}</p>}

      {login && !error && items.length === 0 && (
        <p className="label mt-16 text-center">No saved matches for {login} yet</p>
      )}

      <ol className="mt-10 grid gap-4">
        {items.map((a) => (
          <li key={a.id}>
            <Link href={`/results/${a.id}`} className="block border border-line bg-card p-6 transition hover:border-accent/60">
              <p className="label">{new Date(a.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}</p>
              <p className="mt-3 line-clamp-2 text-body">{a.jobDescriptionPreview}</p>
              <ul className="mt-4 flex flex-wrap gap-2">
                {a.topRepos.map((name, i) => (
                  <li key={name} className="border border-line px-3 py-1 font-mono text-xs text-body">
                    <span className="mr-2 text-accent">{i + 1}</span>
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
