import type { Metadata } from "next";
import Link from "next/link";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "My repos · RepoMatch" };

/** The three things RepoMatch can do with your own code, in one place instead of scattered across the nav. */
const TOOLS = [
  {
    href: "/analyze",
    title: "Match my repos to a job",
    body: "Paste a job description and your GitHub username. Your repositories are ranked by what the job actually asks for, each with evidence quoted from the repo itself — never a score with no reason behind it.",
    cta: "Match a job description",
  },
  {
    href: "/ask",
    title: "Ask a repository how it works",
    body: "Index one repo's source, then ask it questions. Every answer cites the file and line it came from, so you can check it before you repeat it in an interview.",
    cta: "Ask your codebase",
  },
  {
    href: "/history",
    title: "What I've matched before",
    body: "Every match you've run, with its shortlist and reasons kept. Useful when two jobs want the same project described differently.",
    cta: "See past matches",
  },
];

export default function ReposPage() {
  return (
    <div className="mx-auto max-w-[1100px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Your own code" title="Your repositories," accent="as evidence.">
        Tailoring quotes your repositories, so it helps to know what they say. These three tools read your own code —
        nothing here touches a job application until you take it there.
      </PageHeader>

      <div className="mt-14 grid gap-5">
        {TOOLS.map((tool, i) => (
          <Link
            key={tool.href}
            href={tool.href}
            className="animate-rise group border border-line bg-card p-6 transition hover:border-accent"
            style={{ animationDelay: `${i * 60}ms` }}
          >
            <h2 className="text-xl text-ink">{tool.title}</h2>
            <p className="mt-3 max-w-2xl text-sm leading-relaxed text-body">{tool.body}</p>
            <p className="label mt-4 text-accent-2 group-hover:text-ink">{tool.cta} →</p>
          </Link>
        ))}
      </div>

      <div className="mt-12 border-t border-line pt-8">
        <p className="label">What&apos;s next</p>
        <div className="mt-3 flex flex-wrap items-center gap-5">
          <Link href="/jobs" className="btn btn-primary !px-6 !py-3">
            Find a role →
          </Link>
          <p className="text-sm text-muted">
            When you approve one, your repositories become the evidence the tailored resume is built from.
          </p>
        </div>
      </div>
    </div>
  );
}
