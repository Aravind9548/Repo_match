import type { ReactNode } from "react";

/** Sticky action bar at the bottom of each review panel: what you're approving + the button. */
export function ReviewFooter({ summary, children }: { summary: ReactNode; children: ReactNode }) {
  return (
    <div className="sticky bottom-3 z-10 mt-8 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-line bg-card/95 px-4 py-3 shadow-lg shadow-ink/5 backdrop-blur sm:px-5">
      <div className="text-sm text-muted">{summary}</div>
      <div className="flex flex-wrap items-center gap-3">{children}</div>
    </div>
  );
}

export function PrimaryButton({
  children,
  tone = "accent",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "accent" | "blue" }) {
  // Cyan = a human approval; indigo = hands work to the (paid) machine.
  const colors = tone === "blue" ? "bg-blue text-white shadow-glow" : "bg-accent text-on-accent shadow-glow-accent";
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 ${colors}`}
    >
      {children}
    </button>
  );
}

export function TextButton(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className="text-sm font-medium text-muted underline decoration-line underline-offset-4 hover:text-ink disabled:opacity-50"
    />
  );
}
