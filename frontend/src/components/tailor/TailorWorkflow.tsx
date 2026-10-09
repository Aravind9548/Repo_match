"use client";

import Link from "next/link";

import { AtsCheck } from "@/components/tailor/AtsCheck";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  buildTailored,
  describeError,
  getTailorRun,
  rewindTailor,
  suggestEdits,
  tailorDownloadUrl,
  type ResumeData,
  type SuggestOptions,
  type TailorRun,
  type TailorStep,
} from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";

/** Resume strings carry **bold** and [label](url) markers; show them as text, not symbols. */
function plain(s: string) {
  return (s ?? "").replace(/\[([^\]]+)\]\((https?:\/\/|mailto:)[^)\s]+\)/g, "$1").replace(/\*\*/g, "");
}

function Rich({ text }: { text: string }) {
  const parts = (text ?? "").split(/(\*\*.+?\*\*)/g);
  return (
    <>
      {parts.map((p, i) =>
        p.startsWith("**") && p.endsWith("**") && p.length > 4 ? <strong key={i}>{plain(p)}</strong> : <span key={i}>{plain(p)}</span>,
      )}
    </>
  );
}

const STEPS: { id: TailorStep; label: string; hint: string }[] = [
  { id: "REVIEW_RESUME", label: "Check", hint: "Resume as read + job description" },
  { id: "REVIEW_EDITS", label: "Choose edits", hint: "Keep only what's true and helps" },
  { id: "DONE", label: "PDF", hint: "LaTeX compiled, ready to send" },
];

export function TailorWorkflow({ id }: { id: string }) {
  const { key, ready } = useTrackerKey();
  const [run, setRun] = useState<TailorRun | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!key) return;
    try {
      setRun(await getTailorRun(key, id));
    } catch (err) {
      setError(describeError(err));
    }
  }, [key, id]);

  useEffect(() => {
    if (ready) {
      if (key) load();
      else setError("This tailoring run belongs to another device. Open it from the device you started on.");
    }
  }, [ready, key, load]);

  async function act(label: string, fn: () => Promise<TailorRun>) {
    setBusy(label);
    setError(null);
    try {
      setRun(await fn());
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  if (!run) {
    return error ? <div className="border border-amber/40 bg-amber-soft px-5 py-4">{error}</div> : <p className="label">Loading…</p>;
  }

  const stepIndex = STEPS.findIndex((s) => s.id === run.step);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="label">Tailoring for</p>
          <h1 className="display mt-3 text-[clamp(1.6rem,3vw,2.4rem)]">{run.jobTitle ?? "This job"}</h1>
          <p className="mt-2 text-lg text-body">
            {run.company}
            {run.resume && <span className="text-muted"> · from {run.resume.filename}</span>}
          </p>
        </div>
        {run.applicationId && (
          <Link href={`/tracker/${run.applicationId}`} className="label hover:text-ink">
            ← Application
          </Link>
        )}
      </div>

      {/* Stepper: finished steps are clickable to go back, like the Match workflow. */}
      <ol className="grid grid-cols-3 border border-line">
        {STEPS.map((s, i) => {
          const done = i < stepIndex;
          const current = i === stepIndex;
          return (
            <li key={s.id} className="border-r border-line last:border-r-0">
              <button
                type="button"
                disabled={!done || !!busy}
                onClick={() => act("Going back…", () => rewindTailor(key!, run.id, s.id))}
                className={`block h-full w-full px-4 py-3 text-left transition ${
                  current ? "bg-accent text-white" : done ? "bg-accent-soft text-ink hover:bg-card" : "text-muted"
                }`}
              >
                <span className="font-mono text-[11px] uppercase tracking-[0.16em]">
                  {done ? "✓ " : `${i + 1} · `}
                  {s.label}
                </span>
                <span className={`mt-1 hidden text-xs sm:block ${current ? "text-white/80" : "text-muted"}`}>{s.hint}</span>
              </button>
            </li>
          );
        })}
      </ol>

      {error && (
        <div role="alert" className="border border-amber/40 bg-amber-soft px-4 py-3 text-sm text-body">
          {error}
        </div>
      )}

      <div key={run.step} className="animate-rise">
        {run.step === "REVIEW_RESUME" && (
          <ReviewResume
            run={run}
            busy={busy}
            onSuggest={(opts) =>
              act(opts.githubUsername ? "Reading the job, your resume and your GitHub…" : "Reading the job and your resume…", () =>
                suggestEdits(key!, run.id, opts),
              )
            }
          />
        )}
        {run.step === "REVIEW_EDITS" && (
          <ReviewEdits run={run} busy={busy} onBuild={(ids, keepOrder) => act("Building LaTeX and compiling your PDF…", () => buildTailored(key!, run.id, { acceptedIds: ids, keepOrder }))} />
        )}
        {run.step === "DONE" && key && (
          <Done
            run={run}
            keyValue={key}
            busy={busy}
            onBack={() => act("Going back…", () => rewindTailor(key, run.id, "REVIEW_EDITS"))}
            onRun={setRun}
          />
        )}
      </div>
    </div>
  );
}

// --- Gate 1 -----------------------------------------------------------------------------------------------------

function ReviewResume({
  run,
  busy,
  onSuggest,
}: {
  run: TailorRun;
  busy: string | null;
  onSuggest: (opts: SuggestOptions) => void;
}) {
  const [resume, setResume] = useState<ResumeData | null>(run.resumeData);
  const [jd, setJd] = useState(run.jobDescription ?? "");
  const [edited, setEdited] = useState(false);
  const [github, setGithub] = useState(run.githubUsername ?? "");
  const [instructions, setInstructions] = useState(run.instructions ?? "");
  const [onePage, setOnePage] = useState(run.onePage ?? true);
  const [model, setModel] = useState<"pro" | "flash">(run.model ?? "pro");

  // Remember the GitHub username across runs on this device.
  useEffect(() => {
    if (!run.githubUsername) {
      try {
        const saved = localStorage.getItem("repomatch.github");
        if (saved) setGithub(saved);
      } catch {
        // storage unavailable: the field just starts empty
      }
    }
  }, [run.githubUsername]);

  const CHIPS = [
    "Keep my formatting and section order",
    "Don't change the skills section",
    "Emphasise backend work",
    "Emphasise AI/ML work",
    "Keep every metric exactly as written",
  ];
  const addChip = (c: string) =>
    setInstructions((cur) => (cur.includes(c) ? cur : cur.trim() ? `${cur.trim()}\n${c}` : c));

  function submit() {
    try {
      if (github.trim()) localStorage.setItem("repomatch.github", github.trim());
    } catch {
      // ignore
    }
    onSuggest({ resume: edited ? resume : null, jobDescription: jd, githubUsername: github.trim(), instructions, onePage, model });
  }

  function setBullet(si: number, ii: number, bi: number, value: string) {
    if (!resume) return;
    const next = structuredClone(resume);
    next.sections[si].items[ii].bullets[bi] = value;
    setResume(next);
    setEdited(true);
  }

  function setLine(si: number, li: number, value: string) {
    if (!resume) return;
    const next = structuredClone(resume);
    next.sections[si].lines[li] = value;
    setResume(next);
    setEdited(true);
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1.1fr_1fr]">
      <section>
        <div className="flex items-baseline justify-between">
          <p className="label">Your resume, as we read it</p>
          <p className="text-xs text-muted">Fix anything misread — edits are matched to this text.</p>
        </div>
        <div className="mt-4 max-h-[70vh] space-y-6 overflow-y-auto border border-line bg-card p-5">
          {resume ? (
            <>
              <div>
                <p className="text-xl text-ink">{resume.name}</p>
                {resume.headline && <p className="text-sm text-body">{resume.headline}</p>}
                <p className="mt-1 text-xs text-muted">{resume.contact.join(" · ")}</p>
              </div>
              {resume.sections.map((s, si) => (
                <div key={si}>
                  <p className="font-mono text-[11px] uppercase tracking-[0.18em] text-accent-2">{s.title}</p>
                  {s.items.map((it, ii) => (
                    <div key={ii} className="mt-3">
                      <p className="text-sm text-ink">
                        {plain(it.title)}
                        {it.links?.map((l) => (
                          <a key={l.url} href={l.url} target="_blank" rel="noreferrer" className="ml-2 text-xs text-accent-2 hover:underline">
                            {l.label} ↗
                          </a>
                        ))}
                        {it.date && <span className="float-right text-muted">{it.date}</span>}
                      </p>
                      {(it.subtitle || it.location) && (
                        <p className="text-xs italic text-muted">{[it.subtitle, it.location].filter(Boolean).join(" · ")}</p>
                      )}
                      <ul className="mt-1.5 space-y-1">
                        {it.bullets.map((b, bi) => (
                          <li key={bi} className="flex gap-2">
                            <span className="mt-2 text-muted">•</span>
                            <textarea
                              value={b}
                              title="**bold** and [label](url) keep their formatting"
                              rows={Math.max(1, Math.ceil(b.length / 70))}
                              onChange={(e) => setBullet(si, ii, bi, e.target.value)}
                              className="w-full resize-none border border-transparent bg-transparent px-1.5 py-1 text-sm leading-relaxed text-body outline-none transition hover:border-line focus:border-accent focus:bg-paper"
                            />
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                  {s.lines.map((line, li) => (
                    <input
                      key={li}
                      value={line}
                      onChange={(e) => setLine(si, li, e.target.value)}
                      className="mt-1.5 block w-full border border-transparent bg-transparent px-1.5 py-1 text-sm text-body outline-none hover:border-line focus:border-accent focus:bg-paper"
                    />
                  ))}
                </div>
              ))}
            </>
          ) : (
            <p className="text-muted">Couldn&apos;t read this resume.</p>
          )}
        </div>
      </section>

      <section className="flex flex-col">
        <p className="label">Job description</p>
        <p className="mt-2 text-xs text-muted">
          {run.jobDescription ? "Fetched from the posting. Trim anything irrelevant, or paste a better one." : "We couldn't fetch it automatically — paste it from the posting."}
        </p>
        <textarea
          value={jd}
          onChange={(e) => setJd(e.target.value)}
          placeholder="Paste the job description here…"
          className="mt-3 min-h-80 flex-1 border border-line bg-card p-4 text-sm leading-relaxed text-body outline-none focus:border-accent"
        />
        <div className="mt-6 space-y-5 border border-line bg-card p-5">
          <label className="block">
            <span className="label">Your GitHub username</span>
            <input
              value={github}
              onChange={(e) => setGithub(e.target.value)}
              placeholder="e.g. Aravind9548 — optional"
              className="mt-2 w-full border border-line bg-paper px-3 py-2.5 text-sm text-ink outline-none focus:border-accent"
            />
            <span className="mt-1.5 block text-xs text-muted">
              Your repos most relevant to this job are read too: details from their READMEs can strengthen bullets (each
              quotes the repo), missing project links are added, and a better-fitting project can be suggested.
            </span>
          </label>

          <div>
            <span className="label">Your instructions</span>
            <textarea
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              placeholder="Anything the edits must respect…"
              className="mt-2 min-h-20 w-full border border-line bg-paper p-3 text-sm text-body outline-none focus:border-accent"
            />
            <div className="mt-2 flex flex-wrap gap-1.5">
              {CHIPS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => addChip(c)}
                  className="border border-line px-2.5 py-1 text-xs text-muted transition hover:border-accent hover:text-ink"
                >
                  + {c}
                </button>
              ))}
            </div>
          </div>

          <label className="flex cursor-pointer items-start gap-3">
            <input type="checkbox" className="mt-1 accent-[var(--accent)]" checked={onePage} onChange={() => setOnePage(!onePage)} />
            <span className="text-sm text-body">
              Keep it to one page
              <span className="block text-xs text-muted">Edits won&apos;t lengthen it, and the PDF is tightened automatically if it runs over.</span>
            </span>
          </label>

          <div>
            <span className="label">Model</span>
            <div className="mt-2 grid grid-cols-2 gap-2">
              {(
                [
                  ["pro", "Gemini 3.1 Pro", "Most careful · ~30–60s"],
                  ["flash", "Gemini 2.5 Flash", "Faster, cheaper · ~10–20s"],
                ] as const
              ).map(([id, name, hint]) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => setModel(id)}
                  className={`border p-3 text-left transition ${model === id ? "border-accent bg-accent-soft" : "border-line hover:border-line-strong"}`}
                >
                  <span className="block text-sm text-ink">{name}</span>
                  <span className="mt-0.5 block text-xs text-muted">{hint}</span>
                </button>
              ))}
            </div>
          </div>

          <button type="button" className="btn btn-primary w-full" disabled={!!busy || jd.trim().length < 40 || !resume} onClick={submit}>
            {busy ?? "Suggest edits →"}
          </button>
          <p className="label text-center">
            One AI call · edits may only use your resume{github.trim() ? " and your repos" : ""} — anything else is flagged
          </p>
        </div>
      </section>
    </div>
  );
}

// --- Gate 2 -----------------------------------------------------------------------------------------------------

function ReviewEdits({
  run,
  busy,
  onBuild,
}: {
  run: TailorRun;
  busy: string | null;
  onBuild: (ids: number[], keepOrder: boolean) => void;
}) {
  const plan = run.plan!;
  const [kept, setKept] = useState<Set<number>>(new Set(run.acceptedIds));
  const [useOrder, setUseOrder] = useState(plan.reorder.length > 0);
  const toggle = (id: number) =>
    setKept((k) => {
      const next = new Set(k);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
      <section className="min-w-0 space-y-4">
        <div className="border border-line bg-card p-5">
          <p className="label">What we&apos;d change</p>
          <p className="mt-2 leading-relaxed text-body">{plan.summary}</p>
        </div>

        {plan.edits.length === 0 && (
          <div className="border border-dashed border-line-strong p-8 text-center text-body">
            No honest edits to suggest — your resume already reads well for this job. You can still build the PDF.
          </div>
        )}

        {plan.edits.map((e, i) => {
          const on = kept.has(e.id);
          return (
            <label
              key={e.id}
              className={`animate-rise block cursor-pointer border p-5 transition ${
                on ? "border-accent/60 bg-card" : "border-line bg-paper opacity-80 hover:opacity-100"
              }`}
              style={{ animationDelay: `${Math.min(i, 10) * 40}ms` }}
            >
              <div className="flex items-start gap-4">
                <input type="checkbox" className="mt-1 size-4 accent-[var(--accent)]" checked={on} onChange={() => toggle(e.id)} />
                <div className="min-w-0 flex-1">
                  <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-muted">{e.section}</p>
                  <p className="mt-2 text-sm leading-relaxed text-muted line-through decoration-muted/60">
                    <Rich text={e.before} />
                  </p>
                  <p className="mt-1.5 leading-relaxed text-ink">
                    <Rich text={e.after} />
                  </p>
                  <p className="mt-2 text-xs text-accent-2">Why: {e.reason}</p>
                  {e.source === "github" && (
                    <p className="mt-2 border-l-2 border-teal/60 pl-3 text-xs leading-relaxed text-body">
                      From your repo{" "}
                      <a href={e.repo_url} target="_blank" rel="noreferrer" className="text-teal hover:underline">
                        {e.repo} ↗
                      </a>
                      : &ldquo;{e.evidence}&rdquo;
                    </p>
                  )}
                  {e.unsupported && (
                    <p className="mt-3 border border-amber/40 bg-amber-soft px-3 py-2 text-xs leading-relaxed text-body">
                      {e.from_job_ad.length > 0 ? (
                        <>
                          ⚠ <strong>{e.from_job_ad.join(", ")}</strong> comes from the job ad, not your resume. Keep it
                          only if it honestly describes what you did.
                        </>
                      ) : (
                        <>
                          ⚠ Adds <strong>{e.new_terms.join(", ")}</strong>, which isn&apos;t on your resume. Keep it only
                          if it&apos;s true and you can talk about it in an interview.
                        </>
                      )}
                    </p>
                  )}
                </div>
              </div>
            </label>
          );
        })}
        {plan.link_additions?.length > 0 && (
          <div className="border border-line bg-card p-5">
            <p className="label">Add missing GitHub links</p>
            <div className="mt-3 space-y-2">
              {plan.link_additions.map((l) => (
                <label key={l.id} className="flex cursor-pointer items-center gap-3 text-sm text-body">
                  <input type="checkbox" className="accent-[var(--accent)]" checked={kept.has(l.id)} onChange={() => toggle(l.id)} />
                  <span>
                    {l.item_title} →{" "}
                    <a href={l.url} target="_blank" rel="noreferrer" className="text-accent-2 hover:underline">
                      {l.url.replace("https://", "")}
                    </a>
                  </span>
                </label>
              ))}
            </div>
          </div>
        )}

        {plan.swaps?.map((s) => {
          const on = kept.has(s.id);
          return (
            <label key={s.id} className={`block cursor-pointer border p-5 transition ${on ? "border-teal/60 bg-card" : "border-line bg-paper"}`}>
              <div className="flex items-start gap-4">
                <input type="checkbox" className="mt-1 size-4 accent-[var(--accent)]" checked={on} onChange={() => toggle(s.id)} />
                <div className="min-w-0 flex-1">
                  <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-teal">Project swap · {s.section}</p>
                  <p className="mt-2 text-sm text-muted">
                    Replace <span className="line-through">{plain(s.replace_title)}</span> with{" "}
                    <a href={s.repo_url} target="_blank" rel="noreferrer" className="text-teal hover:underline">
                      {s.repo} ↗
                    </a>
                  </p>
                  <div className="mt-3 border border-line bg-paper p-3">
                    <p className="text-sm text-ink">
                      {plain(s.new_item.title)} <span className="text-xs italic text-muted">{s.new_item.subtitle}</span>
                    </p>
                    <ul className="mt-1.5 list-disc space-y-1 pl-5 text-sm text-body">
                      {s.new_item.bullets.map((b, i) => (
                        <li key={i}>{plain(b)}</li>
                      ))}
                    </ul>
                  </div>
                  <p className="mt-2 text-xs text-accent-2">Why: {s.reason}</p>
                  {s.unsupported && (
                    <p className="mt-2 text-xs text-amber">⚠ Uses {s.new_terms.join(", ")}, which the repo doesn&apos;t mention — check before keeping.</p>
                  )}
                </div>
              </div>
            </label>
          );
        })}

        {plan.dropped_edits > 0 && (
          <p className="text-xs text-muted">
            {plan.dropped_edits} suggestion{plan.dropped_edits === 1 ? " was" : "s were"} discarded because they didn&apos;t match text on your resume.
          </p>
        )}
      </section>

      <aside className="space-y-5 lg:sticky lg:top-24 lg:self-start">
        {plan.repos_used?.length > 0 && (
          <div className="border border-line p-4">
            <p className="label">Repos read for this job</p>
            <p className="mt-2 text-xs leading-relaxed text-body">{plan.repos_used.join(" · ")}</p>
          </div>
        )}
        {plan.matched_keywords.length > 0 && (
          <div className="border border-line p-4">
            <p className="label">Already on your resume</p>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {plan.matched_keywords.map((k) => (
                <span key={k} className="border border-moss/40 bg-moss-soft px-2 py-0.5 text-xs text-body">
                  {k}
                </span>
              ))}
            </div>
          </div>
        )}
        {plan.missing_keywords.length > 0 && (
          <div className="border border-line p-4">
            <p className="label">Gaps — not added</p>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {plan.missing_keywords.map((k) => (
                <span key={k} className="border border-amber/40 bg-amber-soft px-2 py-0.5 text-xs text-body">
                  {k}
                </span>
              ))}
            </div>
            <p className="mt-3 text-xs text-muted">The job asks for these and your resume doesn&apos;t show them. Worth a project, not a fib.</p>
          </div>
        )}
        {plan.reorder.length > 0 && (
          <label className="flex cursor-pointer items-start gap-3 border border-line p-4">
            <input type="checkbox" className="mt-1 accent-[var(--accent)]" checked={useOrder} onChange={() => setUseOrder(!useOrder)} />
            <span className="text-sm text-body">
              Put the most relevant items first
              <span className="mt-1 block text-xs text-muted">
                {plan.reorder.map((o) => `${o.section}: ${o.item_titles.join(" → ")}`).join(" · ")}
              </span>
            </span>
          </label>
        )}
        <div className="border border-line bg-card p-5">
          <p className="text-sm text-body">
            {plan.edits.filter((e) => kept.has(e.id)).length} of {plan.edits.length} edits kept
            {plan.swaps?.some((s) => kept.has(s.id)) ? ", a project swap" : ""}
            {plan.link_additions?.some((l) => kept.has(l.id)) ? ", GitHub links" : ""}. Next, they&apos;re applied to{" "}
            {run.resume?.kind === "TEX" ? "your own LaTeX" : "a clean LaTeX template"} and compiled into a PDF.
          </p>
          <button type="button" className="btn btn-primary mt-5 w-full" disabled={!!busy} onClick={() => onBuild([...kept], !useOrder)}>
            {busy ?? "Build my PDF →"}
          </button>
          <p className="label mt-3 text-center">No AI call · about 10–30 seconds</p>
        </div>
      </aside>
    </div>
  );
}

// --- Done -------------------------------------------------------------------------------------------------------

function Done({ run, keyValue, busy, onBack, onRun }: {
  run: TailorRun;
  keyValue: string;
  busy: string | null;
  onBack: () => void;
  onRun: (next: TailorRun) => void;
}) {
  const pdfUrl = useMemo(() => tailorDownloadUrl(keyValue, run.id, "pdf"), [keyValue, run.id]);
  const texUrl = useMemo(() => tailorDownloadUrl(keyValue, run.id, "tex"), [keyValue, run.id]);
  const missed = run.missedEdits.length;

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
      <section className="min-w-0">
        {run.hasPdf ? (
          <iframe title="Tailored resume" src={`${pdfUrl}#view=FitH`} className="h-[80vh] w-full border border-line bg-white" />
        ) : (
          <div className="border border-amber/40 bg-amber-soft p-6">
            <p className="text-ink">The LaTeX didn&apos;t compile.</p>
            <p className="mt-2 text-sm text-body">
              Download the .tex and open it in Overleaf — or go back and drop the last edits.
            </p>
            {run.compileLog && (
              <pre className="mt-4 max-h-64 overflow-auto border border-line bg-paper p-3 font-mono text-[11px] text-muted">{run.compileLog}</pre>
            )}
          </div>
        )}
      </section>

      <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
        <div className="border border-line bg-card p-5">
          <p className="label">Your tailored resume</p>
          <p className="mt-2 text-sm text-body">
            {run.acceptedIds.length} change{run.acceptedIds.length === 1 ? "" : "s"} applied ·{" "}
            {run.usedOwnTemplate ? "your own LaTeX design" : "RepoMatch template"}
            {run.pages ? ` · ${run.pages} page${run.pages === 1 ? "" : "s"}` : ""}
          </p>
          {run.onePage && (run.pages ?? 0) > 1 && (
            <p className="mt-3 text-xs leading-relaxed text-amber">
              Still {run.pages} pages at the tightest setting. Go back and untick a project swap or a long edit, or
              remove a line from your resume at the Check step.
            </p>
          )}
          {run.hasPdf && (
            <a href={pdfUrl} className="btn btn-primary mt-5 w-full">
              Download PDF
            </a>
          )}
          <a href={texUrl} className="btn btn-ghost mt-3 w-full">
            Download .tex
          </a>
          {missed > 0 && (
            <p className="mt-4 text-xs leading-relaxed text-amber">
              {missed} edit{missed === 1 ? "" : "s"} couldn&apos;t be placed in your .tex (the markup around that text differs). Those lines are unchanged.
            </p>
          )}
        </div>
        <AtsCheck run={run} trackerKey={keyValue} onChecked={onRun} />

        <div className="border border-line p-5 text-sm text-body">
          <p>Read it once before sending — you know your work best.</p>
          <div className="mt-4 flex flex-col gap-2">
            <button type="button" className="label text-left hover:text-ink" disabled={!!busy} onClick={onBack}>
              ← Change which edits are kept
            </button>
            {run.applicationId && (
              <Link href={`/tracker/${run.applicationId}`} className="label hover:text-ink">
                Back to the application →
              </Link>
            )}
          </div>
        </div>
      </aside>
    </div>
  );
}
