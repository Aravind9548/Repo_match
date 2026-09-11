import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { WorkflowRunner } from "@/components/workflow/WorkflowRunner";
import { ApiError, getWorkflow, type Workflow } from "@/lib/api";

export const metadata: Metadata = { title: "Workflow · RepoMatch" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function WorkflowPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!UUID.test(id)) notFound();

  let workflow: Workflow;
  try {
    workflow = await getWorkflow(id);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
  return <WorkflowRunner initial={workflow} />;
}
