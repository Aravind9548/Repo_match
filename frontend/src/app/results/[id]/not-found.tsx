import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md px-5 py-28 text-center">
      <span className="chip">Nothing here</span>
      <h1 className="display mt-7 text-3xl">Analysis not found</h1>
      <p className="mt-4 text-body">This link may be mistyped, or the analysis no longer exists.</p>
      <Link href="/analyze" className="btn btn-primary mt-8">
        Start a new match
      </Link>
    </div>
  );
}
