"use client";

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-2xl font-bold">Something went wrong</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">
        {error.message || "We couldn't load this page. The backend may be offline."}
      </p>
      <button onClick={reset} className="mt-6 font-medium underline">
        Try again
      </button>
    </div>
  );
}
