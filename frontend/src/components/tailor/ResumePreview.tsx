"use client";

import { useCallback, useEffect, useState } from "react";
import { describeError, editResumeLine, previewResume, type ResumeData, type TailorRun } from "@/lib/api";

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

/** One line of the resume: reads as text, becomes a textarea when clicked, saves on blur. */
function Line({ text, section, onSave, edited }: {
  text: string;
  section: string;
  onSave: (before: string, after: string, section: string) => Promise<void>;
  edited: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(text);

  useEffect(() => {
    setDraft(text);
  }, [text]);

  if (!editing) {
    return (
      <button
        type="button"
        onClick={() => setEditing(true)}
        title="Click to correct this line"
        className={`block w-full cursor-text text-left text-sm leading-relaxed transition hover:bg-accent-soft ${
          edited ? "border-l-2 border-accent pl-2 text-ink" : "text-body"
        }`}
      >
        <Rich text={text} />
      </button>
    );
  }
  return (
    <textarea
      autoFocus
      className="w-full resize-y border border-accent bg-paper px-2 py-1.5 text-sm leading-relaxed outline-none"
      rows={Math.max(2, Math.ceil(draft.length / 70))}
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={async () => {
        setEditing(false);
        if (draft.trim() !== text.trim()) {
          await onSave(text, draft.trim(), section);
        }
      }}
    />
  );
}

/**
 * The resume as it would be built — every accepted edit, swap, link and line the person added, applied.
 *
 * Any line can be corrected here by clicking it. A correction is stored as the person's own, outranks the model's
 * wording for that line, and survives re-running the suggestions.
 */
export function ResumePreview({ run, trackerKey, acceptedIds, onChange, defaultOpen = false }: {
  run: TailorRun;
  trackerKey: string;
  acceptedIds?: number[];
  onChange?: (next: TailorRun) => void;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const [resume, setResume] = useState<ResumeData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const ids = acceptedIds ? acceptedIds.join(",") : "";
  const load = useCallback(async () => {
    try {
      const res = await previewResume(trackerKey, run.id, acceptedIds);
      setResume(res.resume);
    } catch (err) {
      setError(describeError(err));
    }
    // acceptedIds is an array rebuilt each render; its contents are what matter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trackerKey, run.id, ids]);

  useEffect(() => {
    if (open) void load();
  }, [open, load, run.updatedAt]);

  const editedLines = new Set((run.manualEdits ?? []).map((e) => e.after.trim()));

  async function save(before: string, after: string, section: string) {
    setSaving(true);
    setError(null);
    try {
      const next = await editResumeLine(trackerKey, run.id, { section, before, after });
      onChange?.(next);
      await load();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="border border-line bg-card p-5">
      <button type="button" className="label hover:text-ink" onClick={() => setOpen(!open)}>
        {open ? "▾" : "▸"} The final resume{run.manualEdits?.length ? ` · ${run.manualEdits.length} of your own corrections` : ""}
      </button>

      {open && (
        <div className="animate-rise mt-4">
          <p className="text-xs leading-relaxed text-muted">
            Everything you kept, applied. Click any line to correct it in your own words — what you type wins over
            what the model wrote, and stays through a rebuild.
          </p>

          {!resume ? (
            <p className="label mt-4">Loading…</p>
          ) : (
            <div className="mt-5 space-y-6 border-t border-line pt-5">
              <div>
                <p className="text-lg text-ink">{resume.name}</p>
                {resume.headline && (
                  <p className="mt-1 text-sm text-body">
                    <Rich text={resume.headline} />
                  </p>
                )}
                {resume.contact.length > 0 && <p className="label mt-1">{resume.contact.map(plain).join(" · ")}</p>}
              </div>

              {resume.sections.map((section) => (
                <section key={section.title}>
                  <p className="label border-b border-line pb-1">{section.title}</p>
                  {section.lines.map((line, i) => (
                    <div key={`l${i}`} className="mt-2">
                      <Line text={line} section={section.title} onSave={save} edited={editedLines.has(line.trim())} />
                    </div>
                  ))}
                  {section.items.map((item, i) => (
                    <div key={`i${i}`} className="mt-3">
                      <div className="flex flex-wrap items-baseline justify-between gap-2">
                        <p className="text-sm text-ink">
                          {plain(item.title)}
                          {item.subtitle ? <span className="text-muted"> · {plain(item.subtitle)}</span> : ""}
                        </p>
                        <p className="label">{[item.date, item.location].filter(Boolean).join(" · ")}</p>
                      </div>
                      {item.links.length > 0 && (
                        <p className="label mt-0.5">{item.links.map((l) => l.label).join(" · ")}</p>
                      )}
                      <ul className="mt-1.5 space-y-1.5">
                        {item.bullets.map((b, j) => (
                          <li key={j} className="flex gap-2">
                            <span className="mt-[7px] size-1 shrink-0 bg-line-strong" />
                            <div className="min-w-0 flex-1">
                              <Line text={b} section={section.title} onSave={save} edited={editedLines.has(b.trim())} />
                            </div>
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </section>
              ))}
            </div>
          )}

          {saving && <p className="label mt-4">Saving…</p>}
          {error && (
            <p role="alert" className="mt-4 text-sm text-amber">
              {error}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
