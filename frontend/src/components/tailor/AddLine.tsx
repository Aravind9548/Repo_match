"use client";

import { useRef, useState } from "react";
import {
  addResumeLine,
  describeError,
  gapIdeas,
  phraseResumeLine,
  removeResumeLine,
  type GapDraft,
  type GapIdeas,
  type Phrasing,
  type ResumeData,
  type TailorRun,
} from "@/lib/api";

const squash = (s: string) => (s ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");

/**
 * Where a draft belongs: the entry whose own text it restates.
 *
 * A draft about one project filed under another makes the resume say something false about that other project, so
 * the destination follows the source rather than whatever was last selected.
 */
export function homeOf(resume: ResumeData | null, draft: GapDraft): { section: string; itemTitle: string } | null {
  if (!resume) return null;
  const quote = squash(draft.grounded_on);
  for (const section of resume.sections) {
    for (const item of section.items) {
      const own = squash([item.title, item.subtitle, ...item.bullets].join(" "));
      if (quote.length > 12 && own.includes(quote)) {
        return { section: section.title, itemTitle: item.title };
      }
    }
  }
  // A repository draft belongs with the entry named after that repository.
  const repo = squash(draft.repo);
  if (repo.length > 2) {
    for (const section of resume.sections) {
      for (const item of section.items) {
        const title = squash(item.title);
        if (title && (title.includes(repo) || repo.includes(title))) {
          return { section: section.title, itemTitle: item.title };
        }
      }
    }
  }
  return null;
}

const STOP = new Set(["with", "that", "this", "from", "were", "their", "they", "them", "when", "what", "which",
  "while", "into", "over", "after", "before", "built", "made", "used", "using", "work", "worked", "working",
  "project", "projects", "team", "also", "some", "other", "about", "than", "then", "there", "where", "would",
  "could", "should", "have", "been", "each", "more", "most", "wrote", "write"]);

/** Distinctive words a line and an entry share — the basis for guessing where a typed line belongs. */
function shared(a: string, b: string): string[] {
  const words = (t: string) =>
    new Set((t.toLowerCase().match(/[a-z][a-z0-9+#.]{3,}/g) ?? []).filter((w) => !STOP.has(w)));
  const second = words(b);
  return [...words(a)].filter((w) => second.has(w));
}

/**
 * The entry a typed line most likely belongs to: the one it shares the most distinctive words with.
 *
 * Only a guess, and it says so — it pre-selects the entry and names the words it matched on, so a wrong guess is
 * obvious before the line is added rather than after it is on the resume.
 */
export function bestHome(resume: ResumeData | null, text: string):
  { section: string; itemTitle: string; matched: string[] } | null {
  if (!resume || text.trim().length < 12) return null;
  let best: { section: string; itemTitle: string; matched: string[] } | null = null;
  for (const section of resume.sections) {
    for (const item of section.items) {
      const matched = shared(text, [item.title, item.subtitle, ...item.bullets].join(" "));
      if (matched.length >= 2 && (!best || matched.length > best.matched.length)) {
        best = { section: section.title, itemTitle: item.title, matched };
      }
    }
  }
  return best;
}

/**
 * Answering a gap — in the person's own words, never the job ad's.
 *
 * The job asking for "collaboration" is not a reason to write "collaboration" on a resume. It's a reason to say what
 * you actually did with other people. So this asks for the work, takes the sentence as typed, and will tighten the
 * wording only on request — and hands the sentence straight back if the model tries to add anything to it.
 */
export function AddLine({ run, trackerKey, gap, sections, onDone, onCancel }: {
  run: TailorRun;
  trackerKey: string;
  gap: string;
  sections: { title: string; itemTitles: string[] }[];
  onDone: (next: TailorRun) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState("");
  const [section, setSection] = useState(sections[0]?.title ?? "Projects");
  const [itemTitle, setItemTitle] = useState("");
  const [phrasing, setPhrasing] = useState<Phrasing | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [added, setAdded] = useState(false);
  const [ideas, setIdeas] = useState<GapIdeas | null>(null);
  // Where the draft in the box came from, so filing it somewhere else can be called out.
  const [from, setFrom] = useState<{ section: string; itemTitle: string; label: string } | null>(null);
  // Once the person picks an entry themselves, the guess stops moving it.
  const [pickedByHand, setPickedByHand] = useState(false);
  const [guess, setGuess] = useState<{ itemTitle: string; matched: string[] } | null>(null);
  const [showQuestions, setShowQuestions] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const box = useRef<HTMLTextAreaElement>(null);

  const items = sections.find((s) => s.title === section)?.itemTitles ?? [];
  const goesTo = itemTitle || items[0] || section;
  const misfiled = from && (from.section !== section || (itemTitle || items[0] || "") !== from.itemTitle);
  const value = phrasing?.bullet ?? text;

  async function help() {
    setBusy("Looking…");
    setError(null);
    try {
      const res = await gapIdeas(trackerKey, run.id, gap);
      setIdeas(res.ideas);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  async function tighten() {
    if (text.trim().length < 3) {
      setError("Write something first, then I'll tighten your words — never add to them.");
      box.current?.focus();
      return;
    }
    setBusy("Tightening…");
    setError(null);
    try {
      const res = await phraseResumeLine(trackerKey, run.id, text);
      setPhrasing(res.phrasing);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  async function add() {
    if (value.trim().length < 3) {
      setError("Write the line first — a few words about what you actually did.");
      box.current?.focus();
      return;
    }
    setBusy("Adding…");
    setError(null);
    try {
      const next = await addResumeLine(trackerKey, run.id, { section, itemTitle, text: value, gap });
      setAdded(true);
      onDone(next); // the list below fills in straight away; the box stays long enough to be read
      setTimeout(onCancel, 1400);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="animate-rise mt-3 border border-accent/40 bg-paper p-4">
      <p className="text-sm text-ink">
        The job asks for <strong>{gap}</strong>.
      </p>
      <p className="mt-1 text-xs leading-relaxed text-muted">
        Don&apos;t write the word — write what you did. One honest line about real work says it; the word on its own
        says nothing, and an interviewer will ask.
      </p>

      {!ideas && !added && (
        <button
          type="button"
          className={`btn btn-ghost mt-4 w-full !py-2.5 ${busy === "Looking…" ? "btn-working" : ""}`}
          disabled={!!busy}
          onClick={help}
        >
          {busy === "Looking…" ? "Reading your own work…" : "Help me remember"}
        </button>
      )}

      {ideas && (
        <div className="animate-rise mt-4 space-y-4 border border-line bg-card p-4">
          {ideas.drafts.length > 0 ? (
            <div>
              <p className="label">Suggested lines, from work you already listed</p>
              <p className="mt-1.5 text-xs leading-relaxed text-muted">
                Each only restates something your resume or a repository already says, and names the entry it
                belongs under. Use one as a starting point and change it until it&apos;s how you&apos;d put it —
                and drop it if it isn&apos;t really true.
              </p>
              <ul className="mt-3 space-y-3">
                {ideas.drafts.map((d, i) => (
                  <li key={i} className="border border-line bg-paper p-3">
                    <p className="text-sm text-body">{d.text}</p>
                    <p className="label mt-1.5">
                      → goes under {d.item_title || d.section || "this section"}
                      {d.source === "github" ? ` · from your ${d.repo || "repository"}` : ""}
                    </p>
                    <p className="mt-1 text-xs italic leading-relaxed text-muted">&ldquo;{d.grounded_on}&rdquo;</p>
                    <button
                      type="button"
                      className="label mt-2 text-accent-2 hover:text-ink"
                      onClick={() => {
                        setText(d.text);
                        setPhrasing(null);
                        setError(null);
                        // File it where the work it describes already lives: the entry the draft names, or,
                        // failing that, the entry whose own text it quotes.
                        const home = d.item_title
                          ? { section: d.section, itemTitle: d.item_title }
                          : homeOf(run.resumeData, d);
                        if (home) {
                          setSection(home.section);
                          setItemTitle(home.itemTitle);
                        }
                        setFrom(
                          home
                            ? { ...home, label: d.source === "github" ? d.repo || "that repository" : home.itemTitle }
                            : null,
                        );
                        box.current?.focus();
                      }}
                    >
                      Use this and edit it →
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="text-sm text-body">
              Nothing in your resume or repositories speaks to this, so there&apos;s nothing honest to draft. The
              questions below are the better route — or leave this gap alone.
            </p>
          )}

          {ideas.questions.length > 0 && (
            <div className="border-t border-line pt-3">
              <button
                type="button"
                className="label hover:text-ink"
                onClick={() => setShowQuestions(!showQuestions)}
              >
                {showQuestions || ideas.drafts.length === 0 ? "▾" : "▸"} Questions to jog your memory
              </button>
              {(showQuestions || ideas.drafts.length === 0) && (
                <ul className="animate-rise mt-2 space-y-1.5">
                  {ideas.questions.map((q, i) => (
                    <li key={i} className="text-sm text-body">
                      · {q}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {ideas.dropped > 0 && (
            <p className="text-xs text-amber">
              {ideas.dropped} draft{ideas.dropped === 1 ? " was" : "s were"} discarded for claiming something your
              own work doesn&apos;t say.
            </p>
          )}
        </div>
      )}

      <label className="mt-4 block">
        <span className="label">What you did — your words</span>
        <textarea
          ref={box}
          autoFocus
          className="mt-1.5 min-h-20 w-full resize-y border border-line bg-card px-3 py-2.5 text-sm outline-none transition placeholder:text-muted/70 focus:border-accent"
          value={text}
          onChange={(e) => {
            const next = e.target.value;
            setText(next);
            setPhrasing(null);
            setError(null);
            if (pickedByHand || from) return;
            const home = bestHome(run.resumeData, next);
            setGuess(home ? { itemTitle: home.itemTitle, matched: home.matched } : null);
            if (home) {
              setSection(home.section);
              setItemTitle(home.itemTitle);
            }
          }}
          placeholder="e.g. worked with two classmates on the Raft project, we split the work and reviewed each other's code"
        />
      </label>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <label className="block">
          <span className="label">Put it under</span>
          <select
            className="mt-1.5 w-full border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
            value={section}
            onChange={(e) => {
              setSection(e.target.value);
              setPickedByHand(true);
              setGuess(null);
              setItemTitle("");
            }}
          >
            {sections.map((s) => (
              <option key={s.title} value={s.title}>
                {s.title}
              </option>
            ))}
          </select>
        </label>
        {items.length > 0 && (
          <label className="block">
            <span className="label">Which entry *</span>
            <select
              className="mt-1.5 w-full border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
              value={itemTitle}
              onChange={(e) => {
                setItemTitle(e.target.value);
                setPickedByHand(true);
                setGuess(null);
              }}
            >

              {items.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {phrasing && (
        <div className="mt-3 border border-line bg-card p-3">
          <p className="label">{phrasing.unsupported ? "Kept your words" : "Tightened"}</p>
          <p className="mt-1.5 text-sm text-body">{phrasing.bullet}</p>
          {phrasing.unsupported && (
            <p className="mt-2 text-xs text-amber">
              It tried to add {phrasing.new_terms.join(", ")}, which you didn&apos;t say — so your sentence stands as
              you wrote it.
            </p>
          )}
        </div>
      )}

      <div className="mt-4 border-t border-line pt-3">
        <p className="text-xs text-muted">
          This line will be read as part of <span className="text-ink">{goesTo}</span>.
          {guess && !misfiled && (
            <span className="text-muted">
              {" "}
              Put there because you both mention {guess.matched.slice(0, 3).join(", ")} — change it above if
              that&apos;s the wrong one.
            </span>
          )}
        </p>
        {misfiled && (
          <p className="animate-rise mt-2 text-xs leading-relaxed text-amber">
            ⚠ That draft describes {from?.label}. Putting it under {goesTo} would make your resume say it about{" "}
            {goesTo} instead — change the entry above, or rewrite the line so it&apos;s true of {goesTo}.
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap gap-3">
        <button
          type="button"
          className={`btn btn-primary !px-5 !py-2.5 ${busy === "Adding…" ? "btn-working" : ""}`}
          disabled={!!busy || added}
          onClick={add}
        >
          {added ? "Added ✓" : busy === "Adding…" ? "Adding…" : "Add this line"}
        </button>
        <button
          type="button"
          className={`btn btn-ghost !px-5 !py-2.5 ${busy === "Tightening…" ? "btn-working" : ""}`}
          disabled={!!busy || added || !!phrasing}
          onClick={tighten}
        >
          {busy === "Tightening…" ? "Tightening…" : "Tighten the wording"}
        </button>
        <button type="button" className="label self-center hover:text-ink" onClick={onCancel}>
          {added ? "Close" : "Cancel"}
        </button>
      </div>
      {added ? (
        <p className="animate-rise mt-3 text-sm text-moss">
          Added to {section}
          {itemTitle ? ` · ${itemTitle}` : ""} — open below in the final resume, and in the PDF when you build it.
        </p>
      ) : (
        <p className="label mt-3">Adding costs nothing · tightening is one cheap AI call</p>
      )}
      {error && (
        <p role="alert" className="mt-3 text-sm text-amber">
          {error}
        </p>
      )}
    </div>
  );
}

/** The lines the person has added, with a way to move one to the right entry or take it back out. */
export function AddedLines({ run, trackerKey, onChange }: {
  run: TailorRun;
  trackerKey: string;
  onChange: (next: TailorRun) => void;
}) {
  const [busy, setBusy] = useState(false);

  // Every place a line could live, as "Section ∙ Entry".
  const places: { section: string; itemTitle: string }[] = [];
  for (const section of run.resumeData?.sections ?? []) {
    if (section.items.length === 0) {
      places.push({ section: section.title, itemTitle: "" });
    }
    for (const item of section.items) {
      places.push({ section: section.title, itemTitle: item.title });
    }
  }

  /** Moving is removing and re-adding: the line is the person's text, only its home changes. */
  async function move(index: number, place: { section: string; itemTitle: string }) {
    const line = run.additions[index];
    setBusy(true);
    try {
      await removeResumeLine(trackerKey, run.id, index);
      onChange(await addResumeLine(trackerKey, run.id, {
        section: place.section,
        itemTitle: place.itemTitle,
        text: line.text,
        gap: line.gap,
      }));
    } finally {
      setBusy(false);
    }
  }

  if (!run.additions?.length) {
    return null;
  }
  return (
    <div className="animate-rise border border-moss/40 bg-moss-soft p-4">
      <p className="label">Lines you wrote · {run.additions.length}</p>
      <ul className="mt-3 space-y-3">
        {run.additions.map((a, i) => (
          <li
            key={i}
            className="animate-rise flex items-start justify-between gap-3"
            style={{ animationDelay: `${Math.min(i, 6) * 40}ms` }}
          >
            <div className="min-w-0 flex-1">
              <p className="text-sm text-body">{a.text}</p>
              <p className="label mt-1">
                reads as part of {a.item_title || a.section}
                {a.gap ? ` · answers "${a.gap}"` : ""}
              </p>
              {places.length > 1 && (
                <label className="mt-2 block">
                  <span className="label">Wrong place? Move it</span>
                  <select
                    className="mt-1 w-full border border-line bg-card px-2 py-1.5 text-xs outline-none focus:border-accent"
                    value={`${a.section}||${a.item_title}`}
                    disabled={busy}
                    onChange={(e) => {
                      const [section, itemTitle] = e.target.value.split("||");
                      void move(i, { section, itemTitle });
                    }}
                  >
                    {places.map((pl) => (
                      <option key={`${pl.section}||${pl.itemTitle}`} value={`${pl.section}||${pl.itemTitle}`}>
                        {pl.itemTitle ? `${pl.section} · ${pl.itemTitle}` : pl.section}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
            <button
              type="button"
              className="label shrink-0 hover:text-amber"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                try {
                  onChange(await removeResumeLine(trackerKey, run.id, i));
                } finally {
                  setBusy(false);
                }
              }}
            >
              Remove
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
