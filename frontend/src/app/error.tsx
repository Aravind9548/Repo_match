"use client";

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="mx-auto max-w-md px-5 py-28 text-center">
      <span className="chip">Something broke</span>
      <h1 className="display mt-7 text-3xl">That didn&apos;t work</h1>
      <p className="mt-4 text-body">{error.message || "We couldn't load this page. The backend may be offline."}</p>
      <button onClick={reset} className="btn btn-ghost mt-8">
        Try again
      </button>
    </div>
  );
}
