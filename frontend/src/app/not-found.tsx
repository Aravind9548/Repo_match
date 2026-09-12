import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md px-5 py-28 text-center">
      <span className="chip">404</span>
      <h1 className="display mt-7 text-3xl">Page not found</h1>
      <p className="mt-4 text-body">That page doesn&apos;t exist — or the link has a typo.</p>
      <Link href="/" className="btn btn-ghost mt-8">
        Back to the start
      </Link>
    </div>
  );
}
