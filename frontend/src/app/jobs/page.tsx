import type { Metadata } from "next";
import { JobFeed } from "@/components/jobs/JobFeed";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "Job feed · RepoMatch" };

export default function JobsPage() {
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Step 1 · discover" title="Every open role," accent="straight from the source.">
        RepoMatch reads 88 company career sites (Workday, Greenhouse, Lever, Ashby, SmartRecruiters) and India and US
        internship lists on GitHub every six hours, and runs a daily Google search for internships in India — every link
        checked, dead ones dropped. Type what you want the way you&apos;d say it.
      </PageHeader>
      <div className="mt-14">
        <JobFeed />
      </div>
    </div>
  );
}
