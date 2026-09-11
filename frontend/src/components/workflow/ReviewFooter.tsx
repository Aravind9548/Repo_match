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
  tone = "ink",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "ink" | "accent" }) {
  const colors = tone === "accent" ? "bg-accent text-white hover:brightness-110" : "bg-ink text-paper hover:bg-accent";
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold shadow-sm transition disabled:cursor-not-allowed disabled:opacity-50 ${colors}`}
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
