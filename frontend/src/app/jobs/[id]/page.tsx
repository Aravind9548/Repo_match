import type { Metadata } from "next";
import Link from "next/link";
import { JobDecision } from "@/components/jobs/JobDecision";

export const metadata: Metadata = { title: "Posting · RepoMatch" };

export default async function PostingPage({ params }: PageProps<"/jobs/[id]">) {
  const { id } = await params;
  return (
    <div className="mx-auto max-w-[1100px] px-5 py-12 sm:px-10 sm:py-16">
      <Link href="/jobs" className="label hover:text-ink">
        ← Job feed
      </Link>
      <div className="mt-8">
        <JobDecision postingId={Number(id)} />
      </div>
    </div>
  );
}
