"use client";

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <p className="font-hand text-2xl text-accent">well, that didn&apos;t work</p>
      <h1 className="mt-1 font-display text-3xl font-semibold">Something went wrong</h1>
      <p className="mt-2 text-muted">{error.message || "We couldn't load this page. The backend may be offline."}</p>
      <button onClick={reset} className="mt-6 font-semibold underline underline-offset-4">
        Try again
      </button>
    </div>
  );
}
