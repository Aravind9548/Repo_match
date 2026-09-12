import Link from "next/link";
import { Constellation, NeonCube } from "@/components/HeroVisual";

const PIPELINE = [
  { n: "01", title: "Fetch all public repos", body: "Your GitHub account is read in full — READMEs, languages and topics — and cached so it costs nothing twice." },
  { n: "02", title: "Filter unwanted repos", body: "Forks, coursework and dead experiments come out before anything is scored. You decide what counts." },
  { n: "03", title: "Parse job description", body: "The posting is read as one document — must-haves, nice-to-haves and the stack it keeps repeating." },
  { n: "04", title: "Embedding rank", body: "Vector embeddings score every repo against the JD — fast, free, and surprisingly accurate." },
  { n: "05", title: "Review shortlist", body: "Before spending a cent, you approve which repos advance to the Gemini deep-read stage." },
  { n: "06", title: "Gemini AI review", body: "Gemini reads each README in full — citing your actual words to explain relevance, not hallucinating." },
  { n: "07", title: "You decide", body: "Final approval is always yours. Accept, reject, or go back a step — the AI advises, you command." },
  { n: "08", title: "Shareable link", body: "Your matched repo list saves to a URL you can paste anywhere — GitHub, LinkedIn, applications." },
  { n: "09", title: "Resume bullets", body: "Coming soon — resume bullets and LaTeX export drafted from your matched repositories.", soon: true },
];

const CHECKPOINTS = [
  {
    n: "1",
    title: "Filter your repos",
    body: "Before any AI runs, you decide which repositories enter the pipeline. Remove forks, side experiments, or anything you wouldn't present to a recruiter.",
  },
  {
    n: "2",
    title: "Approve the shortlist",
    body: "After embedding-based ranking, you review the top matches before the paid Gemini step runs. No surprise bills — you control what gets analyzed.",
  },
  {
    n: "3",
    title: "Final curation",
    body: "Gemini cites your actual README content. You read its reasoning, accept what's right, and cut what isn't. The AI advises. You decide.",
  },
];

const STACK = [
  { label: "Backend API", value: "Spring Boot" },
  { label: "ML service", value: "FastAPI" },
  { label: "Deep analysis", value: "Gemini 3.1 Pro" },
  { label: "Data layer", value: "PostgreSQL" },
  { label: "Frontend", value: "Next.js" },
  { label: "Embeddings", value: "gemini-embedding-001" },
];

const FREE_TIER = [
  "Fetch all public repos",
  "Filter unwanted repos",
  "Parse job description",
  "Embedding-based ranking",
  "Shortlist review",
];

const PAID_TIER = [
  "Deep README analysis",
  "Verbatim citations",
  "Relevance explanations",
  "Final curation control",
  "Shareable results link",
];

export default function Home() {
  return (
    <div>
      {/* ── hero ─────────────────────────────────────────────────────── */}
      <section className="relative isolate overflow-hidden border-b border-line">
        <Constellation className="absolute inset-0 -z-10 size-full opacity-70" />
        <div
          aria-hidden
          className="absolute left-1/2 top-1/2 -z-10 size-[900px] -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{ background: "radial-gradient(circle, rgba(37,99,255,0.16) 0%, transparent 62%)" }}
        />

        <div className="mx-auto grid max-w-[1400px] items-center gap-16 px-5 py-24 sm:px-10 sm:py-32 lg:grid-cols-[1.15fr_auto]">
          <div>
            <span className="chip">AI-powered resume intelligence</span>
            <h1 className="display mt-8 text-[clamp(2.6rem,6.4vw,5.2rem)] text-glow">
              Put the right
              <br />
              <span className="text-accent">projects</span>
              <br />
              on your resume.
            </h1>
            <p className="mt-8 max-w-xl text-lg leading-relaxed text-body">
              Paste a job description. Enter your GitHub username. RepoMatch reads every repository and tells you — with
              citations — which projects a recruiter actually wants to see.
            </p>

            <div className="mt-10 flex flex-wrap gap-4">
              <Link href="/analyze" className="btn btn-primary">
                Match my repos
              </Link>
              <Link href="#how-it-works" className="btn btn-ghost">
                See how it works
              </Link>
            </div>

            <dl className="mt-16 grid max-w-2xl grid-cols-2 border-t border-line pt-8 sm:grid-cols-3">
              {[
                { v: "~1min", k: "Full pipeline" },
                { v: "Free", k: "Until Gemini step" },
                { v: "3×", k: "Manual checkpoints" },
              ].map((m, i) => (
                <div key={m.k} className={i > 0 ? "border-line pl-6 sm:border-l" : "pr-6"}>
                  <dt className="font-display text-4xl font-extrabold text-ink">{m.v}</dt>
                  <dd className="label mt-2">{m.k}</dd>
                </div>
              ))}
            </dl>
          </div>

          <NeonCube className="mx-auto hidden lg:block" />
        </div>
      </section>

      {/* ── pipeline ─────────────────────────────────────────────────── */}
      <section id="how-it-works" className="mx-auto max-w-[1400px] px-5 py-24 sm:px-10">
        <span className="chip">The pipeline</span>
        <h2 className="display mt-7 text-[clamp(2rem,4vw,3.2rem)]">
          Nine steps from
          <br />
          <span className="text-accent">repo to résumé.</span>
        </h2>

        <div className="mt-14 grid border-l border-t border-line sm:grid-cols-2 lg:grid-cols-3">
          {PIPELINE.map((step) => (
            <article key={step.n} className="relative border-b border-r border-line p-8 transition hover:bg-card">
              {step.soon && <span className="label absolute right-6 top-6 border border-line px-2 py-1">Soon</span>}
              <span className="font-mono text-xs text-accent">{step.n}</span>
              <h3 className="display mt-5 text-lg">{step.title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-muted">{step.body}</p>
            </article>
          ))}
        </div>
      </section>

      {/* ── checkpoints ──────────────────────────────────────────────── */}
      <section id="checkpoints" className="relative isolate overflow-hidden border-y border-line bg-card/40">
        <Constellation className="absolute inset-0 -z-10 size-full opacity-40" />
        <div className="mx-auto max-w-[1400px] px-5 py-24 sm:px-10">
          <span className="chip-teal">Human control</span>
          <h2 className="display mt-7 text-[clamp(2rem,4.4vw,3.4rem)]">
            Three checkpoints.
            <br />
            <span className="text-teal">Always your call.</span>
          </h2>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-body">
            The pipeline pauses three times for your input. These aren&apos;t confirmations — they&apos;re real decisions
            that shape the output.
          </p>

          <div className="mt-14 grid gap-6 lg:grid-cols-3">
            {CHECKPOINTS.map((c) => (
              <article key={c.n} className="border border-line bg-paper/60 p-8">
                <div className="flex items-center gap-4">
                  <span className="flex size-9 items-center justify-center rounded-full border border-teal/50 bg-teal-soft font-mono text-sm text-teal">
                    {c.n}
                  </span>
                  <span className="label text-teal">Checkpoint {c.n}</span>
                </div>
                <h3 className="display mt-7 text-xl">{c.title}</h3>
                <p className="mt-4 text-sm leading-relaxed text-muted">{c.body}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* ── tech stack ───────────────────────────────────────────────── */}
      <section id="tech-stack" className="mx-auto grid max-w-[1400px] items-center gap-16 px-5 py-24 sm:px-10 lg:grid-cols-2">
        <div>
          <span className="chip-teal">Built with</span>
          <h2 className="display mt-7 text-[clamp(2rem,4vw,3.2rem)]">
            Enterprise-grade
            <br />
            <span className="text-teal">tech stack</span>
          </h2>
          <p className="mt-6 max-w-xl text-lg leading-relaxed text-body">
            A Java Spring Boot backend for reliability, a Python FastAPI service for the AI work, Gemini for deep language
            understanding, and PostgreSQL for persistence — deployed on Cloud Run and Vercel.
          </p>
          <Link href="/analyze" className="btn mt-10 bg-teal text-[#03202a]">
            Try RepoMatch
          </Link>
        </div>

        <div className="grid grid-cols-2 border-l border-t border-line">
          {STACK.map((s) => (
            <div key={s.label} className="border-b border-r border-line p-7">
              <div className="label text-teal">{s.label}</div>
              <div className="display mt-3 text-base">{s.value}</div>
            </div>
          ))}
        </div>
      </section>

      {/* ── pricing ──────────────────────────────────────────────────── */}
      <section id="pricing" className="border-y border-line bg-card/40">
        <div className="mx-auto max-w-[1400px] px-5 py-24 text-center sm:px-10">
          <span className="chip">Pricing</span>
          <h2 className="display mx-auto mt-7 max-w-3xl text-[clamp(2rem,4.4vw,3.4rem)]">Free until it gets smart</h2>
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-body">
            Every step before Gemini analysis is completely free. You only pay when Gemini reads your READMEs — and you
            approve that step.
          </p>

          <div className="mx-auto mt-14 grid max-w-4xl text-left lg:grid-cols-2">
            <div className="border border-line p-10">
              <div className="label">Free tier</div>
              <div className="display mt-4 text-5xl">$0</div>
              <p className="mt-3 text-sm text-muted">Steps 01–05 inclusive</p>
              <ul className="mt-8 space-y-3">
                {FREE_TIER.map((f) => (
                  <li key={f} className="flex items-start gap-3 text-sm text-body">
                    <span className="mt-1.5 size-1.5 shrink-0 bg-accent" />
                    {f}
                  </li>
                ))}
              </ul>
            </div>

            <div className="border border-accent/60 bg-accent-soft p-10">
              <div className="label text-accent-2">Gemini analysis</div>
              <div className="display mt-4 text-4xl">Pay-as-you-go</div>
              <p className="mt-3 text-sm text-muted">About $0.08 per analysis — you approve before it runs</p>
              <ul className="mt-8 space-y-3">
                {PAID_TIER.map((f) => (
                  <li key={f} className="flex items-start gap-3 text-sm text-body">
                    <span className="mt-1.5 size-1.5 shrink-0 bg-accent" />
                    {f}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* ── closing ──────────────────────────────────────────────────── */}
      <section className="relative isolate overflow-hidden">
        <Constellation className="absolute inset-0 -z-10 size-full opacity-60" />
        <div
          aria-hidden
          className="absolute left-1/2 top-1/2 -z-10 size-[760px] -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{ background: "radial-gradient(circle, rgba(37,99,255,0.14) 0%, transparent 62%)" }}
        />
        <div className="mx-auto max-w-[1400px] px-5 py-28 text-center sm:px-10">
          <span className="chip">Get started</span>
          <h2 className="display mx-auto mt-7 max-w-4xl text-[clamp(2.2rem,5vw,4rem)] text-glow">
            Match your repos
            <br />
            <span className="text-accent-2">in under a minute.</span>
          </h2>
          <p className="mx-auto mt-6 max-w-xl text-lg leading-relaxed text-body">
            Free to start. No sign-up required. Paste a job description and your GitHub username — the rest is automatic.
          </p>
          <Link href="/analyze" className="btn btn-primary mt-10 !px-10 !py-5">
            Launch RepoMatch
          </Link>
        </div>
      </section>
    </div>
  );
}
