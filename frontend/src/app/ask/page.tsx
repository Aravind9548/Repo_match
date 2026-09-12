import type { Metadata } from "next";
import { AskConsole } from "@/components/AskConsole";

export const metadata: Metadata = { title: "Ask your codebase · RepoMatch" };

export default async function AskPage({ searchParams }: { searchParams: Promise<{ username?: string }> }) {
  const { username } = await searchParams;
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <div className="max-w-3xl">
        <span className="chip">Optional · ask your codebase</span>
        <h1 className="display mt-7 text-[clamp(2rem,4.4vw,3.4rem)]">
          Interviewers ask how it works.
          <br />
          <span className="text-accent">Now your repo answers.</span>
        </h1>
        <p className="mt-6 text-lg leading-relaxed text-body">
          Point RepoMatch at one repository and ask anything about it. Answers cite the files and line ranges they came
          from — and if the code doesn&apos;t say it, neither does the answer. Separate from matching; nothing here runs
          during a normal run.
        </p>
      </div>

      <div className="mt-14">
        <AskConsole initialUsername={username ?? ""} />
      </div>
    </div>
  );
}
