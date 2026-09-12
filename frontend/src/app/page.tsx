import Image from "next/image";
import Link from "next/link";
import heroCity from "@/assets/hero-city.jpg";
import { CurlyArrow } from "@/components/Doodles";
import { ArrowRightIcon, HandIcon, SparkleIcon } from "@/components/icons";
import { statusesFor, WorkflowCanvas } from "@/components/workflow/WorkflowCanvas";

const GATES = [
  {
    title: "You pick the repos",
    body: "Coursework, half-finished experiments, that one fork — untick anything you wouldn't put in front of a recruiter.",
    human: true,
  },
  {
    title: "You shape the shortlist",
    body: "See how every repo ranks against the job before any AI spends a cent. Add the underdog, drop the noise.",
    human: false,
  },
  {
    title: "You keep the final say",
    body: "Gemini explains each pick with quotes from your own README. Keep the ones you'd happily talk about in an interview.",
    human: true,
  },
];

export default function Home() {
  return (
    <div className="space-y-20 sm:space-y-28">
      {/* Hero over a full-bleed photo, tinted navy; always dark, whatever the theme. */}
      <section className="relative isolate -mt-10 flex min-h-[540px] items-center pb-24 pt-16 sm:-mt-14 sm:min-h-[620px]">
        <div aria-hidden className="absolute inset-y-0 left-1/2 -z-10 w-screen -translate-x-1/2 overflow-hidden">
          <Image
            src={heroCity}
            alt=""
            fill
            priority
            placeholder="blur"
            sizes="100vw"
            className="object-cover object-[center_62%]"
          />
          {/* Light blue tint keeps the photo vivid; the dark fade only sits behind the text. */}
          <div className="absolute inset-0 bg-[#1b3d9e]/20 mix-blend-multiply" />
          <div className="absolute inset-0 bg-linear-to-r from-[#061433]/85 via-[#061433]/35 to-transparent" />
          <div className="absolute inset-x-0 bottom-0 h-28 bg-linear-to-t from-paper to-transparent" />
        </div>

        <div className="max-w-2xl">
          <h1 className="font-display text-5xl font-semibold leading-[1.05] tracking-tight text-balance text-white sm:text-6xl">
            Put the right projects on your resume.
          </h1>
          <p className="mt-5 text-2xl font-semibold text-[#5fd6f2] sm:text-[1.7rem]">Your repos, matched to the job.</p>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#d7e0f0] text-pretty">
            Paste a job description and your GitHub username. RepoMatch reads every repo, ranks them for that role,
            and tells you why each one fits — backed by what&apos;s actually in your code.
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-5">
            <Link
              href="/analyze"
              className="inline-flex items-center gap-2 rounded-full bg-[#3fc6e6] px-6 py-3 font-semibold text-[#04121f] shadow-lg shadow-black/25 transition hover:-translate-y-0.5 hover:bg-[#7dddf3]"
            >
              Match my GitHub to a job
              <ArrowRightIcon className="size-4" />
            </Link>
            <span className="text-sm text-[#b9c6dc]">Takes about a minute. Free until you ask Gemini.</span>
          </div>
        </div>
      </section>

      <section aria-labelledby="how">
        <div className="mb-6 flex items-end gap-3">
          <h2 id="how" className="font-display text-3xl font-semibold">
            How it works
          </h2>
          <CurlyArrow className="mb-[-10px] hidden h-10 w-14 text-muted sm:block" />
          <span className="font-hand text-xl text-muted">it waits for you at every round step</span>
        </div>
        <WorkflowCanvas
          demo
          statuses={statusesFor("REVIEW_SHORTLIST", null)}
          caption="Nobody's repos here yet — start a run to see your own."
        />
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
            <li
              key={gate.title}
              className={`rounded-2xl border border-line bg-card p-5 transition hover:-translate-y-1 ${
                gate.human ? "shadow-glow-accent" : "shadow-glow"
              } ${i === 1 ? "sm:translate-y-5" : ""}`}
            >
              <span
                className={`flex size-10 items-center justify-center rounded-full border-2 ${
                  gate.human ? "border-accent bg-accent-soft text-accent" : "border-blue bg-blue-soft text-blue"
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
