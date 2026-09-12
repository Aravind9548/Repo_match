import type { Metadata } from "next";
import { AnalyzeExperience } from "@/components/AnalyzeExperience";

export const metadata: Metadata = { title: "New match · RepoMatch" };

export default async function AnalyzePage({ searchParams }: { searchParams: Promise<{ username?: string }> }) {
  const { username } = await searchParams;
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <div className="max-w-2xl">
        <span className="chip">Step one of a few</span>
        <h1 className="display mt-7 text-[clamp(2rem,4vw,3rem)]">Match your repos to a job</h1>
        <p className="mt-5 text-lg leading-relaxed text-body">
          We fetch your public repositories first. After that, every step pauses for you to review before anything moves
          on — and nothing is charged to Gemini until you approve the shortlist.
        </p>
      </div>
      <div className="mt-12">
        <AnalyzeExperience initialUsername={username ?? ""} />
      </div>
    </div>
  );
}
