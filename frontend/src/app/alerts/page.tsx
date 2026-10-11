import { redirect } from "next/navigation";

/** Saved searches live with the rest of finding a role now. Old links still work. */
export default function AlertsPage() {
  redirect("/jobs?tab=saved");
}
