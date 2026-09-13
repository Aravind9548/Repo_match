import type { Metadata } from "next";
import { TrackerBoard } from "@/components/jobs/TrackerBoard";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "Tracker · RepoMatch" };

export default function TrackerPage() {
  return (
    <div className="mx-auto max-w-[1500px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Step 3 · keep track" title="After you hit submit." accent="Nothing slips.">
        Every approved role, from &ldquo;to apply&rdquo; to the outcome. Record what comes back — an assessment, an
        interview invite, a deadline — and RepoMatch reminds you before it&apos;s due and nudges when a company goes
        quiet.
      </PageHeader>
      <div className="mt-14">
        <TrackerBoard />
      </div>
    </div>
  );
}
