import type { Metadata } from "next";
import { TrackerBoard } from "@/components/jobs/TrackerBoard";
import { PageHeader } from "@/components/jobs/ui";
import { NextStep } from "@/components/FlowStrip";

export const metadata: Metadata = { title: "Tracker · RepoMatch" };

export default function TrackerPage() {
  return (
    <div className="mx-auto max-w-[1500px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Applications" title="Every role you approved." accent="Start to finish.">
        Approving a posting starts an application here. Open one to tailor your resume to it, read the PDF back the
        way a parser will, and draft this job&apos;s answers — then send it and record what comes back. Deadlines are
        reminded before they&apos;re due, and quiet applications nudged after two weeks.
      </PageHeader>
      <div className="mt-14">
        <TrackerBoard />
      </div>

      <NextStep
        href="/apply/profile"
        label="Fill in your details once"
        hint="Name, phone, work authorisation, graduation — every apply kit starts from these."
      />
    </div>
  );
}
