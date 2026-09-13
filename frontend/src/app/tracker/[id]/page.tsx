import type { Metadata } from "next";
import Link from "next/link";
import { ApplicationDetail } from "@/components/jobs/ApplicationDetail";

export const metadata: Metadata = { title: "Application · RepoMatch" };

export default async function ApplicationPage({ params }: PageProps<"/tracker/[id]">) {
  const { id } = await params;
  return (
    <div className="mx-auto max-w-[1100px] px-5 py-12 sm:px-10 sm:py-16">
      <Link href="/tracker" className="label hover:text-ink">
        ← Tracker
      </Link>
      <div className="mt-8">
        <ApplicationDetail id={Number(id)} />
      </div>
    </div>
  );
}
