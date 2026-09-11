import type { Metadata } from "next";
import { AnalyzeForm } from "@/components/AnalyzeForm";
import { statusesFor, WorkflowCanvas } from "@/components/workflow/WorkflowCanvas";

export const metadata: Metadata = { title: "New match · RepoMatch" };

export default async function AnalyzePage({ searchParams }: { searchParams: Promise<{ username?: string }> }) {
  const { username } = await searchParams;
  return (
    <div className="space-y-10">
      <div className="max-w-2xl">
        <p className="font-hand text-2xl text-accent">step one of a few</p>
        <h1 className="mt-1 font-display text-4xl font-semibold tracking-tight">Match your repos to a job</h1>
        <p className="mt-3 text-muted">
          We&apos;ll fetch your public repositories first. After that, every step pauses for you to review before
          anything moves on.
        </p>
      </div>

      <WorkflowCanvas statuses={statusesFor(null, null)} />

      <div className="max-w-2xl rounded-3xl border border-line bg-card/80 p-5 shadow-sm shadow-ink/5 sm:p-8">
        <AnalyzeForm initialUsername={username ?? ""} />
      </div>
    </div>
  );
}
