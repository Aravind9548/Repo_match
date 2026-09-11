import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <p className="font-hand text-2xl text-accent">wrong turn</p>
      <h1 className="mt-1 font-display text-3xl font-semibold">Page not found</h1>
      <p className="mt-2 text-muted">That page doesn&apos;t exist — or the link has a typo.</p>
      <Link href="/" className="mt-6 inline-block font-semibold underline underline-offset-4">
        Back to the start
      </Link>
    </div>
  );
}
