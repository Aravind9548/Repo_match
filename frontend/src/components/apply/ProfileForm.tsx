"use client";

import { useEffect, useState } from "react";
import {
  describeError,
  getProfile,
  saveProfile,
  EMPTY_PROFILE,
  type ApplicantProfile,
  type ProfileExtra,
} from "@/lib/api";
import { useTrackerKey } from "@/lib/trackerKey";

const inputClass =
  "w-full border border-line bg-paper px-3 py-2.5 text-sm outline-none transition placeholder:text-muted/70 focus:border-accent";

type Field = { key: keyof ApplicantProfile; label: string; placeholder: string; hint?: string };

/** The questions every application form asks, in the order forms tend to ask them. */
const FIELDS: Field[][] = [
  [
    { key: "fullName", label: "Full name", placeholder: "As it appears on your resume" },
    { key: "email", label: "Email", placeholder: "you@example.com" },
    { key: "phone", label: "Phone", placeholder: "+91 …" },
    { key: "location", label: "Based in", placeholder: "Hyderabad, India" },
  ],
  [
    { key: "githubUrl", label: "GitHub", placeholder: "https://github.com/…" },
    { key: "linkedinUrl", label: "LinkedIn", placeholder: "https://linkedin.com/in/…" },
    { key: "portfolioUrl", label: "Portfolio or site", placeholder: "https://…" },
  ],
  [
    {
      key: "workAuthorization",
      label: "Work authorisation",
      placeholder: "Indian citizen, no sponsorship needed",
      hint: "Answer it the way you'd answer the form.",
    },
    { key: "noticePeriod", label: "Availability / notice", placeholder: "Can start June 2026" },
    { key: "graduation", label: "Graduation", placeholder: "B.Tech CSE, May 2027" },
    { key: "expectedSalary", label: "Expected pay", placeholder: "Open / as per company norms" },
  ],
];

export function ProfileForm() {
  const { key, ready } = useTrackerKey();
  const [form, setForm] = useState<ApplicantProfile>(EMPTY_PROFILE);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !key) return;
    getProfile(key)
      .then((p) => p && setForm({ ...EMPTY_PROFILE, ...p, extras: p.extras ?? [] }))
      .catch((err) => setError(describeError(err)));
  }, [ready, key]);

  function set<K extends keyof ApplicantProfile>(k: K, v: ApplicantProfile[K]) {
    setForm((f) => ({ ...f, [k]: v }));
    setSaved(null);
  }

  function setExtra(i: number, patch: Partial<ProfileExtra>) {
    set(
      "extras",
      form.extras.map((e, j) => (j === i ? { ...e, ...patch } : e)),
    );
  }

  async function submit() {
    if (!key) return;
    setBusy(true);
    setError(null);
    try {
      const next = await saveProfile(key, {
        ...form,
        extras: form.extras.filter((e) => e.label.trim() && e.value.trim()),
      });
      setForm({ ...EMPTY_PROFILE, ...next, extras: next.extras ?? [] });
      setSaved(new Date().toLocaleTimeString());
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  if (ready && !key) {
    return (
      <div role="alert" className="border border-accent/40 bg-accent-soft px-5 py-4">
        Open the link from your email, or paste your key under Find → Saved searches — your details are stored against it.
      </div>
    );
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[1fr_320px]">
      <div className="min-w-0 space-y-10">
        {FIELDS.map((group, gi) => (
          <div key={gi} className="grid gap-5 sm:grid-cols-2">
            {group.map((f) => (
              <label key={String(f.key)} className="block">
                <span className="label">{f.label}</span>
                <input
                  className={`${inputClass} mt-2`}
                  value={(form[f.key] as string) ?? ""}
                  onChange={(e) => set(f.key, e.target.value as ApplicantProfile[typeof f.key])}
                  placeholder={f.placeholder}
                />
                {f.hint && <span className="mt-1.5 block text-xs text-muted">{f.hint}</span>}
              </label>
            ))}
          </div>
        ))}

        <div>
          <p className="label">Anything else forms ask you</p>
          <p className="mt-2 text-sm text-muted">
            Your own question-and-answer pairs — &ldquo;How did you hear about us?&rdquo;, a referral name, a
            registration number.
          </p>
          <div className="mt-4 space-y-3">
            {form.extras.map((e, i) => (
              <div key={i} className="flex flex-col gap-3 sm:flex-row">
                <input
                  className={`${inputClass} sm:w-72`}
                  value={e.label}
                  onChange={(ev) => setExtra(i, { label: ev.target.value })}
                  placeholder="Question"
                />
                <input
                  className={inputClass}
                  value={e.value}
                  onChange={(ev) => setExtra(i, { value: ev.target.value })}
                  placeholder="Your answer"
                />
                <button
                  type="button"
                  className="label shrink-0 self-center hover:text-amber"
                  onClick={() =>
                    set(
                      "extras",
                      form.extras.filter((_, j) => j !== i),
                    )
                  }
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
          <button
            type="button"
            className="btn btn-ghost mt-4 !py-2.5"
            onClick={() => set("extras", [...form.extras, { label: "", value: "" }])}
          >
            Add a question
          </button>
        </div>
      </div>

      <aside className="space-y-5 lg:sticky lg:top-24 lg:self-start">
        <div className="border border-accent/40 bg-card p-5">
          <p className="label">Where this is used</p>
          <p className="mt-2 text-sm text-body">
            Every apply kit starts from these answers, and the drafted answers are allowed to lean on them. Nothing
            here is sent anywhere on its own.
          </p>
          <button type="button" className="btn btn-primary mt-5 w-full !py-2.5" disabled={busy} onClick={submit}>
            {busy ? "Saving…" : "Save my details"}
          </button>
          {saved && <p className="mt-3 text-sm text-muted">Saved at {saved}.</p>}
          {form.updatedAt && !saved && (
            <p className="mt-3 text-sm text-muted">Last changed {new Date(form.updatedAt).toLocaleString()}.</p>
          )}
          {error && (
            <p role="alert" className="mt-3 text-sm text-amber">
              {error}
            </p>
          )}
        </div>
      </aside>
    </div>
  );
}
