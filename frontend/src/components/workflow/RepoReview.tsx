"use client";

import Image from "next/image";
import { useState } from "react";
import { repoImageUrl, type RepoOption } from "@/lib/api";
import { ArrowRightIcon, CheckIcon, StarIcon } from "@/components/icons";
import { PrimaryButton, ReviewFooter, TextButton } from "./ReviewFooter";

export function RepoReview({
  repos,
  busy,
  onApprove,
}: {
  repos: RepoOption[];
  busy: boolean;
  onApprove: (ids: number[]) => void;
}) {
  const [selected, setSelected] = useState(() => new Set(repos.filter((r) => r.selected).map((r) => r.id)));

  const toggle = (id: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const selectWhere = (pred: (r: RepoOption) => boolean) => setSelected(new Set(repos.filter(pred).map((r) => r.id)));

  return (
    <section aria-labelledby="pick-title">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="pick-title" className="font-display text-2xl font-semibold">
            Which repos should count?
          </h2>
          <p className="mt-1 font-hand text-xl text-muted">
            untick coursework, experiments — anything you wouldn&apos;t show a recruiter
          </p>
        </div>
        <div className="flex flex-wrap gap-4">
          <TextButton onClick={() => selectWhere((r) => !r.fork)} disabled={busy}>
            All (no forks)
          </TextButton>
          <TextButton onClick={() => selectWhere((r) => r.hasReadme && !r.fork)} disabled={busy}>
            Only with README
          </TextButton>
          <TextButton onClick={() => setSelected(new Set())} disabled={busy}>
            None
          </TextButton>
        </div>
      </div>

      <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {repos.map((repo) => {
          const on = selected.has(repo.id);
          return (
            <li key={repo.id}>
              <label
                className={`group block h-full cursor-pointer overflow-hidden rounded-2xl border bg-card transition ${
                  on ? "border-accent shadow-md shadow-accent/10 ring-1 ring-accent" : "border-line opacity-70 hover:opacity-100"
                }`}
              >
                <div className="relative aspect-[2/1] overflow-hidden border-b border-line bg-sunken">
                  <Image
                    src={repoImageUrl(repo.fullName)}
                    alt=""
                    width={600}
                    height={300}
                    unoptimized
                    className={`size-full object-cover transition ${on ? "" : "grayscale"}`}
                  />
                  <span
                    className={`absolute right-3 top-3 flex size-7 items-center justify-center rounded-full border-2 ${
                      on ? "border-accent bg-accent text-white" : "border-line bg-card/90 text-transparent"
                    }`}
                  >
                    <CheckIcon className="size-4" />
                  </span>
                </div>
                <input
                  type="checkbox"
                  className="sr-only"
                  checked={on}
                  onChange={() => toggle(repo.id)}
                  disabled={busy}
                  aria-label={`Include ${repo.name}`}
                />
                <div className="p-4">
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate font-semibold">{repo.name}</span>
                    {repo.stars > 0 && (
                      <span className="flex shrink-0 items-center gap-1 text-xs text-muted">
                        <StarIcon className="size-3.5" />
                        {repo.stars}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 line-clamp-2 min-h-10 text-sm text-muted">
                    {repo.description ?? <span className="italic">No description</span>}
                  </p>
                  <div className="mt-3 flex flex-wrap items-center gap-1.5 text-xs">
                    {repo.languages.slice(0, 3).map((l) => (
                      <span key={l} className="rounded-full bg-sunken px-2 py-0.5 text-muted">
                        {l}
                      </span>
                    ))}
                    {repo.fork && <span className="rounded-full bg-sunken px-2 py-0.5 text-muted">fork</span>}
                    {!repo.hasReadme && (
                      <span className="rounded-full bg-amber-soft px-2 py-0.5 font-medium text-amber">no README</span>
                    )}
                  </div>
                </div>
              </label>
            </li>
          );
        })}
      </ul>

      <ReviewFooter
        summary={
          <>
            <strong className="text-ink">{selected.size}</strong> of {repos.length} repos selected ·{" "}
            <span className="font-hand text-lg">ranking is free</span>
          </>
        }
      >
        <PrimaryButton disabled={busy || selected.size === 0} onClick={() => onApprove([...selected])}>
          {busy ? "Ranking…" : `Rank ${selected.size} repos`}
          <ArrowRightIcon className="size-4" />
        </PrimaryButton>
      </ReviewFooter>
    </section>
  );
}
