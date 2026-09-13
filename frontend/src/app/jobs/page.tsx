import type { Metadata } from "next";
import { JobFeed } from "@/components/jobs/JobFeed";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "Job feed · RepoMatch" };

export default function JobsPage() {
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Step 1 · discover" title="Every open role," accent="straight from the source.">
        RepoMatch reads company career boards (Greenhouse, Lever, Ashby, SmartRecruiters) and the community job lists on
        GitHub every six hours — the same public listings search engines index, with dead links dropped. Type what you
        want the way you&apos;d say it.
      </PageHeader>
      <div className="mt-14">
        <JobFeed />
      </div>
    </div>
  );
}
