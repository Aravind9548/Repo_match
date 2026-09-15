"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { addResume, deleteResume, describeError, listResumes, startTailor, type ResumeDoc } from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";
import { timeAgo } from "@/components/jobs/ui";

const ACCEPT = ".pdf,.docx,.tex,.txt,.md";

function readBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "");
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

/** Gate 0: choose the resume — every time, with the last one ready to reuse. */
export function TailorStart({ applicationId, postingId }: { applicationId?: number; postingId?: number }) {
  const router = useRouter();
  const { key, ready } = useTrackerKey({ create: true });
  const [saved, setSaved] = useState<ResumeDoc[]>([]);
  const [chosen, setChosen] = useState<number | null>(null);
  const [mode, setMode] = useState<"saved" | "upload" | "paste">("upload");
  const [file, setFile] = useState<File | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    if (!key) return;
    try {
      const list = await listResumes(key);
      setSaved(list);
      if (list.length > 0) {
        setMode("saved");
        setChosen(list[0].id);
      }
    } catch (err) {
      setError(describeError(err));
    }
  }, [key]);

  useEffect(() => {
    if (ready) load();
  }, [ready, load]);

  async function go() {
    if (!key) return;
    setError(null);
    try {
      let resumeId = chosen;
      if (mode !== "saved") {
        setBusy("Reading your file…");
        const doc =
          mode === "upload" && file
            ? await addResume(key, { filename: file.name, dataBase64: await readBase64(file) })
            : await addResume(key, { text });
        resumeId = doc.id;
      }
      if (resumeId == null) return;
      const doc = saved.find((d) => d.id === resumeId);
      setBusy(doc?.read ? "Starting…" : "Reading your resume into sections… (about 20 seconds)");
      const run = await startTailor(key, { applicationId, postingId, resumeId });
      router.push(`/tailor/${run.id}`);
    } catch (err) {
      setError(describeError(err));
      setBusy(null);
    }
  }

  const canGo =
    !busy && key && ((mode === "saved" && chosen != null) || (mode === "upload" && file) || (mode === "paste" && text.trim().length >= 80));

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap gap-2">
        {(
          [
            ["saved", `Saved resumes${saved.length ? ` · ${saved.length}` : ""}`],
            ["upload", "Upload a file"],
            ["paste", "Paste text"],
          ] as const
        ).map(([m, label]) => (
          <button
            key={m}
            type="button"
            disabled={m === "saved" && saved.length === 0}
            onClick={() => setMode(m)}
            className={`border px-4 py-2.5 font-mono text-[11px] uppercase tracking-[0.14em] transition disabled:opacity-40 ${
              mode === m ? "border-accent bg-accent-soft text-ink" : "border-line text-muted hover:text-ink"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div key={mode} className="animate-rise">
        {mode === "saved" && (
          <ul className="space-y-2">
            {saved.map((d) => (
              <li key={d.id}>
                <label
                  className={`flex cursor-pointer items-center gap-4 border p-4 transition ${
                    chosen === d.id ? "border-accent bg-accent-soft" : "border-line bg-card hover:border-line-strong"
                  }`}
                >
                  <input type="radio" name="resume" className="accent-[var(--accent)]" checked={chosen === d.id} onChange={() => setChosen(d.id)} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-ink">{d.filename}</span>
                    <span className="label mt-1 block">
                      {d.kind === "TEX" ? "LaTeX — your design is kept" : "Text"} · added {timeAgo(d.createdAt)}
                      {d.read ? " · already read" : ""}
                    </span>
                  </span>
                  <button
                    type="button"
                    className="label hover:text-amber"
                    onClick={async (e) => {
                      e.preventDefault();
                      if (!key || !window.confirm(`Delete "${d.filename}"?`)) return;
                      await deleteResume(key, d.id);
                      await load();
                    }}
                  >
                    Delete
                  </button>
                </label>
              </li>
            ))}
          </ul>
        )}

        {mode === "upload" && (
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragging(false);
              if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0]);
            }}
            onClick={() => input.current?.click()}
            className={`flex cursor-pointer flex-col items-center justify-center border border-dashed px-6 py-16 text-center transition ${
              dragging ? "border-accent bg-accent-soft" : "border-line-strong bg-card hover:border-accent"
            }`}
          >
            <input
              ref={input}
              type="file"
              accept={ACCEPT}
              className="hidden"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <p className="text-lg text-ink">{file ? file.name : "Drop your resume here, or click to choose"}</p>
            <p className="label mt-3">PDF · DOCX · TEX · TXT — up to 3 MB</p>
            <p className="mt-4 max-w-md text-sm text-muted">
              Upload the .tex if you have it: your own layout is kept and only the wording changes.
            </p>
          </div>
        )}

        {mode === "paste" && (
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Paste your resume — plain text or LaTeX source."
            className="min-h-72 w-full border border-line bg-card p-4 font-mono text-sm text-body outline-none focus:border-accent"
          />
        )}
      </div>

      {error && (
        <div role="alert" className="border border-amber/40 bg-amber-soft px-4 py-3 text-sm text-body">
          {error}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6">
        <p className="text-sm text-muted">
          {busy ?? "Next: check how your resume was read, and the job description, before any edits are suggested."}
        </p>
        <button type="button" className="btn btn-primary" disabled={!canGo} onClick={go}>
          {busy ? "Working…" : "Continue →"}
        </button>
      </div>
    </div>
  );
}
