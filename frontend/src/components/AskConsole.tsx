"use client";

import { useState } from "react";
import {
  ApiError,
  askRepo,
  describeError,
  getIndexStatus,
  indexRepo,
  listRepos,
  type CodeAnswer,
  type IndexSummary,
  type RepoSummary,
} from "@/lib/api";

const SUGGESTED = [
  "What would you show in a two-minute demo?",
  "Explain the architecture in plain terms.",
  "Where is state kept between runs?",
  "What's the weakest part of this design?",
  "Which tests cover the main flow?",
];

const inputClass =
  "w-full border border-line bg-card px-4 py-3 text-base outline-none transition placeholder:text-muted/70 focus:border-accent disabled:opacity-60";

export function AskConsole({ initialUsername = "" }: { initialUsername?: string }) {
  const [username, setUsername] = useState(initialUsername);
  const [repos, setRepos] = useState<RepoSummary[] | null>(null);
  const [selected, setSelected] = useState<RepoSummary | null>(null);
  const [index, setIndex] = useState<IndexSummary | null>(null);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<CodeAnswer | null>(null);
  const [busy, setBusy] = useState<"repos" | "index" | "ask" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadRepos(e: React.FormEvent) {
    e.preventDefault();
    setBusy("repos");
    setError(null);
    setRepos(null);
    setSelected(null);
    setIndex(null);
    setAnswer(null);
    try {
      const res = await listRepos(username.trim());
      setRepos(res.repos.filter((r) => !r.fork));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  async function pick(repo: RepoSummary) {
    setSelected(repo);
    setAnswer(null);
    setIndex(null);
    setError(null);
    try {
      setIndex(await getIndexStatus(repo.id));
    } catch (err) {
      // 422 simply means "not indexed yet" — that's the normal first visit.
      if (!(err instanceof ApiError) || err.problem.status !== 422) setError(describeError(err));
    }
  }

  async function runIndex() {
    if (!selected) return;
    setBusy("index");
    setError(null);
    try {
      setIndex(await indexRepo(selected.id, Boolean(index)));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  async function ask(q: string) {
    if (!selected || q.trim().length < 5) return;
    setBusy("ask");
    setError(null);
    setAnswer(null);
    try {
      setAnswer(await askRepo(selected.id, q.trim()));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[380px_1fr]">
      {/* picker */}
      <aside className="space-y-6">
        <form onSubmit={loadRepos} className="space-y-3">
          <label htmlFor="ask-username" className="label">
            GitHub username
          </label>
          <div className="flex gap-3">
            <input
              id="ask-username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              pattern="[A-Za-z0-9](?:[A-Za-z0-9\-]{0,38})"
              placeholder="octocat"
              disabled={busy !== null}
              className={inputClass}
            />
            <button type="submit" className="btn btn-ghost shrink-0" disabled={busy !== null}>
              {busy === "repos" ? "…" : "Load"}
            </button>
          </div>
        </form>

        {repos && (
          <div className="border border-line">
            <div className="label border-b border-line px-4 py-3">{repos.length} repositories</div>
            <ul className="max-h-[420px] overflow-y-auto">
              {repos.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => pick(r)}
                    disabled={busy !== null}
                    className={`flex w-full items-center justify-between gap-3 border-b border-line px-4 py-3 text-left transition last:border-b-0 hover:bg-card ${
                      selected?.id === r.id ? "bg-card text-ink" : "text-body"
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm">{r.name}</span>
                      <span className="label mt-1 block">{r.languages.slice(0, 2).join(" · ") || "—"}</span>
                    </span>
                    {selected?.id === r.id && <span className="size-2 shrink-0 bg-accent" />}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </aside>

      {/* console */}
      <section className="min-w-0">
        {!selected && (
          <div className="border border-line bg-card p-10 text-center">
            <p className="label">Pick a repository to begin</p>
            <p className="mt-4 text-body">
              RepoMatch reads the actual source of one repository, then answers questions about it — citing the files and
              line ranges it used.
            </p>
          </div>
        )}

        {selected && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-4 border border-line bg-card px-5 py-4">
              <div>
                <div className="display text-lg">{selected.name}</div>
                <p className="label mt-2">
                  {index
                    ? `${index.fileCount} files · ${index.chunkCount} excerpts · indexed ${new Date(index.indexedAt).toLocaleDateString()}`
                    : "Not indexed yet"}
                </p>
              </div>
              <button type="button" onClick={runIndex} disabled={busy !== null} className="btn btn-ghost">
                {busy === "index" ? "Reading the code…" : index ? "Re-index" : "Index this repo"}
              </button>
            </div>

            {index && (
              <>
                <div className="flex gap-3">
                  <input
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") ask(question);
                    }}
                    placeholder="Ask anything about this repository…"
                    disabled={busy !== null}
                    className={inputClass}
                  />
                  <button
                    type="button"
                    onClick={() => ask(question)}
                    disabled={busy !== null || question.trim().length < 5}
                    className="btn btn-primary shrink-0"
                  >
                    {busy === "ask" ? "Reading…" : "Ask"}
                  </button>
                </div>

                <div className="flex flex-wrap gap-2">
                  {SUGGESTED.map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => {
                        setQuestion(q);
                        ask(q);
                      }}
                      disabled={busy !== null}
                      className="border border-line px-3 py-2 text-left text-xs text-muted transition hover:border-accent hover:text-ink"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </>
            )}

            {error && (
              <div role="alert" className="border border-accent/40 bg-accent-soft px-4 py-3 text-sm">
                {error}
              </div>
            )}

            {answer && (
              <article className="border border-line bg-card p-6 sm:p-8">
                <p className="label">{answer.answered ? "Answer" : "Not in the indexed code"}</p>
                <p className="mt-4 whitespace-pre-line leading-relaxed text-body">{answer.answer}</p>

                {answer.citations.length > 0 && (
                  <div className="mt-7 space-y-3">
                    <h3 className="label">Cited from the repository</h3>
                    {answer.citations.map((c) => (
                      <details key={`${c.path}-${c.startLine}`} className="border border-line bg-paper">
                        <summary className="cursor-pointer px-4 py-3 font-mono text-xs text-accent-2">
                          {c.path} · lines {c.startLine}–{c.endLine}
                        </summary>
                        {c.snippet && (
                          <pre className="overflow-x-auto border-t border-line px-4 py-3 font-mono text-[12px] leading-relaxed text-body">
                            {c.snippet}
                          </pre>
                        )}
                      </details>
                    ))}
                  </div>
                )}

                <footer className="mt-7 flex flex-wrap gap-x-6 gap-y-2 border-t border-line pt-5">
                  <span className="label">{answer.llmModel ?? "Gemini"}</span>
                  <span className="label">{answer.searched.length} excerpts searched</span>
                  {answer.usage && (
                    <span className="label">
                      {answer.usage.promptTokens.toLocaleString()} in / {answer.usage.outputTokens.toLocaleString()} out
                    </span>
                  )}
                </footer>
              </article>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
