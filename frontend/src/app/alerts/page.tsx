import type { Metadata } from "next";
import { AlertsConsole } from "@/components/jobs/AlertsConsole";
import { PageHeader } from "@/components/jobs/ui";

export const metadata: Metadata = { title: "Alerts · RepoMatch" };

export default function AlertsPage() {
  return (
    <div className="mx-auto max-w-[1400px] px-5 py-16 sm:px-10 sm:py-20">
      <PageHeader chip="Step 2 · get told" title="New roles find you." accent="You still decide.">
        Save a search. When a new posting fits it, it lands in your inbox below — and in your email if you add one.
        Approve the ones worth your time; skip the rest.
      </PageHeader>
      <div className="mt-14">
        <AlertsConsole />
      </div>
    </div>
  );
}
