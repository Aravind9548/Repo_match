import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-2xl font-bold">Analysis not found</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">This link may be mistyped, or the analysis no longer exists.</p>
      <Link href="/analyze" className="mt-6 inline-block font-medium underline">
        Run a new analysis
      </Link>
    </div>
  );
}
