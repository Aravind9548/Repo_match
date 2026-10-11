import type { Metadata } from "next";
import { Suspense } from "react";
import { FindTabs } from "@/components/jobs/FindTabs";
import { PageHeader } from "@/components/jobs/ui";
import { NextStep } from "@/components/FlowStrip";

export const metadata: Metadata = { title: "Find a role · RepoMatch" };

export default function JobsPage() {
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Find" title="Every open role," accent="straight from the source.">
        RepoMatch reads 88 company career sites (Workday, Greenhouse, Lever, Ashby, SmartRecruiters) and India and US
        internship lists on GitHub every six hours, and runs a daily Google search for internships in India — every link
        checked, dead ones closed. Browse it, save the search, or ask the open web.
      </PageHeader>
      <div className="mt-14">
        <Suspense fallback={<p className="label">Loading…</p>}>
          <FindTabs />
        </Suspense>
      </div>

      <NextStep
        href="/tracker"
        label="See the ones you approved"
        hint="Approving a posting starts an application: that's where you tailor a resume and send it."
      />
    </div>
  );
}
