import type { Metadata } from "next";
import { AnalyzeForm } from "@/components/AnalyzeForm";

export const metadata: Metadata = { title: "Analyze · RepoMatch" };

export default async function AnalyzePage({
  searchParams,
}: {
  searchParams: Promise<{ username?: string }>;
}) {
  const { username } = await searchParams;
  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-3xl font-bold tracking-tight">Match your repos to a job</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">
        We read your public repositories and tell you which ones to feature for this role — and why.
      </p>
      <div className="mt-8">
        <AnalyzeForm initialUsername={username ?? ""} />
      </div>
    </div>
  );
}
