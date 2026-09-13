import type { Metadata } from "next";
import { JobFeed } from "@/components/jobs/JobFeed";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "Job feed · RepoMatch" };

export default function JobsPage() {
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Step 1 · discover" title="Every open role" accent="from GitHub's job lists.">
        RepoMatch reads the community-maintained internship and new-grad lists on GitHub a few times a day, strips the
        tracking links and drops what&apos;s closed. Filter it here, or save the filter as an alert and get emailed when
        something new fits.
      </PageHeader>
      <div className="mt-14">
        <JobFeed />
      </div>
    </div>
  );
}
