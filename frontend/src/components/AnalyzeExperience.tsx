"use client";

import { useState } from "react";
import { AnalyzeForm } from "./AnalyzeForm";
import { statusesFor, WorkflowCanvas } from "./workflow/WorkflowCanvas";

/** Analyze page body: the canvas lights up the first node while repos are being fetched. */
export function AnalyzeExperience({ initialUsername }: { initialUsername: string }) {
  const [pending, setPending] = useState(false);
  return (
    <div className="space-y-10">
      <WorkflowCanvas statuses={statusesFor(null, pending ? "fetch" : null)} />
      <div className="max-w-2xl rounded-3xl border border-line bg-card/90 p-5 shadow-riso sm:p-8">
        <AnalyzeForm initialUsername={initialUsername} onPendingChange={setPending} />
      </div>
    </div>
  );
}
