import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <p className="font-hand text-2xl text-accent">hmm, nothing here</p>
      <h1 className="mt-1 font-display text-3xl font-semibold">Analysis not found</h1>
      <p className="mt-2 text-muted">This link may be mistyped, or the analysis no longer exists.</p>
      <Link href="/analyze" className="mt-6 inline-block font-semibold underline underline-offset-4">
        Start a new match
      </Link>
    </div>
  );
}
