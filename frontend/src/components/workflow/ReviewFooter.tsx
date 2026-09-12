import type { ReactNode } from "react";

/** Sticky action bar at the bottom of each review panel: what you're approving + the button. */
export function ReviewFooter({ summary, children }: { summary: ReactNode; children: ReactNode }) {
  return (
    <div className="sticky bottom-4 z-10 mt-8 flex flex-wrap items-center justify-between gap-4 border border-line bg-card/95 px-5 py-4 backdrop-blur">
      <div className="text-sm text-muted">{summary}</div>
      <div className="flex flex-wrap items-center gap-4">{children}</div>
    </div>
  );
}

export function PrimaryButton({
  children,
  tone = "accent",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "accent" | "blue" }) {
  // "blue" marks the step that hands work to the paid model; both sit on the brand blue.
  const colors = tone === "blue" ? "bg-accent text-white" : "bg-accent text-white";
  return (
    <button type="button" {...props} className={`btn ${colors} disabled:cursor-not-allowed disabled:opacity-50`}>
      {children}
    </button>
  );
}

export function TextButton(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className="label border-b border-line pb-1 transition hover:border-accent hover:text-ink disabled:opacity-50"
    />
  );
}
