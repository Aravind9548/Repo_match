"use client";

import { useRef, useState } from "react";
import {
  addResumeLine,
  describeError,
  gapIdeas,
  phraseResumeLine,
  removeResumeLine,
  type GapIdeas,
  type Phrasing,
  type TailorRun,
} from "@/lib/api";

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
  const [error, setError] = useState<string | null>(null);
  const box = useRef<HTMLTextAreaElement>(null);

  const items = sections.find((s) => s.title === section)?.itemTitles ?? [];
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
          {ideas.questions.length > 0 && (
            <div>
              <p className="label">Questions, not answers</p>
              <ul className="mt-2 space-y-1.5">
                {ideas.questions.map((q, i) => (
                  <li key={i} className="text-sm text-body">
                    · {q}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {ideas.drafts.length > 0 ? (
            <div>
              <p className="label">From work you already listed</p>
              <p className="mt-1.5 text-xs leading-relaxed text-muted">
                Each of these only restates something your resume or a repository already says. Use one as a
                starting point and change it until it&apos;s how you&apos;d put it — and drop it if it isn&apos;t
                really true.
              </p>
              <ul className="mt-3 space-y-3">
                {ideas.drafts.map((d, i) => (
                  <li key={i} className="border border-line bg-paper p-3">
                    <p className="text-sm text-body">{d.text}</p>
                    <p className="label mt-1.5">
                      from your {d.source === "github" ? d.repo || "repository" : "resume"}
                    </p>
                    <p className="mt-1 text-xs italic leading-relaxed text-muted">&ldquo;{d.grounded_on}&rdquo;</p>
                    <button
                      type="button"
                      className="label mt-2 text-accent-2 hover:text-ink"
                      onClick={() => {
                        setText(d.text);
                        setPhrasing(null);
                        setError(null);
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
              questions above are the better route — or leave this gap alone.
            </p>
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
            setText(e.target.value);
            setPhrasing(null);
            setError(null);
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
            <span className="label">Which entry</span>
            <select
              className="mt-1.5 w-full border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
              value={itemTitle}
              onChange={(e) => setItemTitle(e.target.value)}
            >
              <option value="">the first one</option>
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

/** The lines the person has added so far, with a way to take one back out. */
export function AddedLines({ run, trackerKey, onChange }: {
  run: TailorRun;
  trackerKey: string;
  onChange: (next: TailorRun) => void;
}) {
  const [busy, setBusy] = useState(false);
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
            <div className="min-w-0">
              <p className="text-sm text-body">{a.text}</p>
              <p className="label mt-1">
                {a.section}
                {a.item_title ? ` · ${a.item_title}` : ""}
                {a.gap ? ` · answers "${a.gap}"` : ""}
              </p>
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
