import type { Metadata } from "next";
import { TailorStart } from "@/components/tailor/TailorStart";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "Tailor your resume · RepoMatch" };

export default async function TailorNewPage({ searchParams }: PageProps<"/tailor/new">) {
  const p = await searchParams;
  const num = (v: string | string[] | undefined) => (typeof v === "string" && /^\d+$/.test(v) ? Number(v) : undefined);
  return (
    <div className="mx-auto max-w-[1100px] px-5 py-14 sm:px-10 sm:py-16">
      <PageHeader chip="Tailor · step 0" title="Which resume" accent="should we start from?">
        Upload the resume you&apos;d normally send — PDF, Word, LaTeX or plain text — or reuse one you&apos;ve given
        before. Nothing is changed until you approve it, and nothing is ever sent to an employer.
      </PageHeader>
      <div className="mt-12">
        <TailorStart applicationId={num(p.application)} postingId={num(p.posting)} />
      </div>
    </div>
  );
}
