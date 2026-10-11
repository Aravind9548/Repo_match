"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * The five stages of applying for one job, with the one you're in lit.
 *
 * Every page in the pipeline belongs to exactly one stage, so this answers "where am I and what comes next" without
 * each page having to explain itself. Stages you can start from are links; the ones that only exist inside a
 * particular application are not.
 */
const STAGES = [
  { id: "find", label: "Find", href: "/jobs", hint: "Browse and save searches" },
  { id: "decide", label: "Decide", href: "/jobs", hint: "Read it, approve or skip" },
  { id: "prepare", label: "Prepare", href: null, hint: "Tailor the resume, draft the answers" },
  { id: "apply", label: "Apply", href: null, hint: "Open the form and send it" },
  { id: "track", label: "Track", href: "/tracker", hint: "What came back, and what's next" },
] as const;

export type Stage = (typeof STAGES)[number]["id"];

/** Which stage a path belongs to, so a page never has to declare it twice. */
export function stageOf(pathname: string): Stage | null {
  if (pathname.startsWith("/jobs/")) return "decide";
  if (pathname.startsWith("/jobs")) return "find";
  if (pathname.startsWith("/tailor")) return "prepare";
  if (pathname.startsWith("/tracker/")) return "apply";
  if (pathname.startsWith("/tracker")) return "track";
  return null;
}

export function FlowStrip() {
  const pathname = usePathname();
  const current = stageOf(pathname);
  if (!current) {
    return null;
  }
  const currentIndex = STAGES.findIndex((s) => s.id === current);

  return (
    <nav aria-label="Where you are" className="border-b border-line bg-card/40">
      <ol className="mx-auto flex max-w-[1400px] items-stretch gap-px overflow-x-auto px-5 sm:px-10">
        {STAGES.map((stage, i) => {
          const active = stage.id === current;
          const done = i < currentIndex;
          const body = (
            <>
              <span className="flex items-center gap-2">
                <span
                  aria-hidden
                  className={`size-1.5 shrink-0 rounded-full ${
                    active ? "bg-accent" : done ? "bg-moss" : "bg-line-strong"
                  }`}
                />
                <span className={active ? "text-ink" : done ? "text-body" : "text-muted"}>{stage.label}</span>
              </span>
              <span className="mt-0.5 hidden text-[10px] normal-case tracking-normal text-muted md:block">
                {stage.hint}
              </span>
            </>
          );
          const className = `flex shrink-0 flex-col py-2.5 pr-6 font-mono text-[11px] uppercase tracking-[0.14em] transition ${
            active ? "" : "hover:text-ink"
          }`;
          return (
            <li key={stage.id} aria-current={active ? "step" : undefined}>
              {stage.href ? (
                <Link href={stage.href} className={className}>
                  {body}
                </Link>
              ) : (
                <span className={className}>{body}</span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/** The one thing to do next, stated as a button rather than left to the navigation. */
export function NextStep({ href, label, hint }: { href: string; label: string; hint?: string }) {
  return (
    <div className="mt-12 border-t border-line pt-8">
      <p className="label">What&apos;s next</p>
      <div className="mt-3 flex flex-wrap items-center gap-5">
        <Link href={href} className="btn btn-primary !px-6 !py-3">
          {label} →
        </Link>
        {hint && <p className="text-sm text-muted">{hint}</p>}
      </div>
    </div>
  );
}
