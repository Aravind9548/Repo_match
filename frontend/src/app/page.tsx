import Link from "next/link";

const STEPS = [
  {
    title: "Read every repo",
    body: "We pull your public GitHub repositories — READMEs, languages and topics — and build a profile of each project.",
  },
  {
    title: "Rank against the job",
    body: "Embeddings shortlist the projects closest to the job description, then Gemini judges each one against the actual requirements.",
  },
  {
    title: "Explain with evidence",
    body: "Every recommendation comes with a reason and quotes from your own repo. Nothing is invented — unverifiable claims are dropped.",
  },
];

export default function Home() {
  return (
    <div>
      <section className="mx-auto max-w-3xl py-6 text-center sm:py-12">
        <h1 className="text-4xl font-bold tracking-tight text-balance sm:text-5xl">
          Which of your projects should go on this resume?
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-zinc-600 text-pretty dark:text-zinc-400">
          Paste a job description and your GitHub username. RepoMatch ranks your repositories for that role and
          tells you why each one fits.
        </p>
        <Link
          href="/analyze"
          className="mt-8 inline-block rounded-lg bg-zinc-900 px-6 py-3 font-medium text-white shadow-sm hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
        >
          Match my GitHub to a job
        </Link>
      </section>

      <section aria-labelledby="how" className="mt-12">
        <h2 id="how" className="sr-only">
          How it works
        </h2>
        <ol className="grid gap-4 sm:grid-cols-3">
          {STEPS.map((step, i) => (
            <li
              key={step.title}
              className="rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900"
            >
              <span className="font-mono text-sm text-zinc-400">0{i + 1}</span>
              <h3 className="mt-2 font-semibold">{step.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-zinc-600 dark:text-zinc-400">{step.body}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
