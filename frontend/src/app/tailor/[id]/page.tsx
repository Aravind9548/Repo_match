import type { Metadata } from "next";
import { TailorWorkflow } from "@/components/tailor/TailorWorkflow";

export const metadata: Metadata = { title: "Tailor your resume · RepoMatch" };

export default async function TailorRunPage({ params }: PageProps<"/tailor/[id]">) {
  const { id } = await params;
  return (
    <div className="mx-auto max-w-[1300px] px-5 py-12 sm:px-10 sm:py-14">
      <TailorWorkflow id={id} />
    </div>
  );
}
