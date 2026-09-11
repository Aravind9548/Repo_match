import Link from "next/link";
import { CurlyArrow, Squiggle } from "@/components/Doodles";
import { ArrowRightIcon, HandIcon, SparkleIcon } from "@/components/icons";
import { HeroScene } from "@/components/Shapes3D";
import { statusesFor, WorkflowCanvas } from "@/components/workflow/WorkflowCanvas";

const GATES = [
  {
    title: "You pick the repos",
    body: "Coursework, half-finished experiments, that one fork — untick anything you wouldn't put in front of a recruiter.",
    shadow: "shadow-riso-pink",
    tilt: "sm:-rotate-1",
  },
  {
    title: "You shape the shortlist",
    body: "See how every repo ranks against the job before any AI spends a cent. Add the underdog, drop the noise.",
    shadow: "shadow-riso",
    tilt: "sm:translate-y-5 sm:rotate-1",
  },
  {
    title: "You keep the final say",
    body: "Gemini explains each pick with quotes from your own README. Keep the ones you'd happily talk about in an interview.",
    shadow: "shadow-riso-pink",
    tilt: "sm:-rotate-[0.6deg]",
  },
];

export default function Home() {
  return (
    <div className="space-y-20 sm:space-y-28">
      <section className="grid items-center gap-10 lg:grid-cols-[1.1fr_1fr]">
        <div>
          <p className="font-hand text-2xl text-accent">for when every job wants something different</p>
          <h1 className="mt-3 font-display text-5xl font-semibold leading-[1.04] tracking-tight text-balance text-overprint sm:text-6xl">
            Put the{" "}
            <span className="relative inline-block text-blue">
              right
              <Squiggle className="absolute -bottom-2 left-0 h-3 w-full text-accent" />
            </span>{" "}
            projects on your resume.
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-relaxed text-muted text-pretty">
            Paste a job description and your GitHub username. RepoMatch reads every repo, ranks them for that role,
            and tells you why each one fits — backed by what&apos;s actually in your code.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-5">
            <Link
              href="/analyze"
              className="inline-flex items-center gap-2 rounded-full bg-ink px-6 py-3 font-semibold text-paper shadow-riso-pink transition hover:-translate-y-0.5 hover:shadow-riso"
            >
              Match my GitHub to a job
              <ArrowRightIcon className="size-4" />
            </Link>
            <span className="font-hand text-xl text-muted">takes about a minute</span>
          </div>
        </div>
        <HeroScene />
      </section>

      <section aria-labelledby="how">
        <div className="mb-6 flex items-end gap-3">
          <h2 id="how" className="font-display text-3xl font-semibold">
            How it works
          </h2>
          <CurlyArrow className="mb-[-10px] hidden h-10 w-14 text-muted sm:block" />
          <span className="font-hand text-xl text-muted">it waits for you at every pink step</span>
        </div>
        <WorkflowCanvas statuses={statusesFor("REVIEW_SHORTLIST", null)} caption="An example run, paused at the shortlist." />
      </section>

      <section aria-labelledby="control" className="grid gap-8 lg:grid-cols-[1fr_2fr]">
        <div>
          <h2 id="control" className="font-display text-3xl font-semibold text-balance">
            The AI does the reading. <span className="text-accent">You make the calls.</span>
          </h2>
          <p className="mt-3 text-muted">
            Three checkpoints stop the pipeline and wait for you. The paid Gemini step only runs after you approve the
            shortlist — and every reason it gives is checked against your repo before you see it.
          </p>
        </div>
        <ol className="grid gap-5 sm:grid-cols-3">
          {GATES.map((gate, i) => (
            <li key={gate.title} className={`rounded-2xl border border-line bg-card p-5 ${gate.shadow} ${gate.tilt}`}>
              <span
                className={`flex size-10 items-center justify-center rounded-full border-2 ${
                  i === 1 ? "border-blue bg-blue-soft text-blue" : "border-accent bg-accent-soft text-accent"
                }`}
              >
                {i === 2 ? <SparkleIcon className="size-5" /> : <HandIcon className="size-5" />}
              </span>
              <h3 className="mt-4 font-display text-lg font-semibold">{gate.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">{gate.body}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
