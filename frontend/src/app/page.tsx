import Link from "next/link";
import { Constellation, NeonCube } from "@/components/HeroVisual";
import { CountUp, Reveal } from "@/components/Reveal";

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

const FREE_TIER = ["Fetch all public repos", "Filter unwanted repos", "Parse job description", "Embedding-based ranking", "Shortlist review"];
const PAID_TIER = ["Deep README analysis", "Verbatim citations", "Relevance explanations", "Final curation control", "Shareable results link"];

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
          <Reveal>
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
              <Link href="/analyze" className="btn btn-primary transition hover:-translate-y-0.5">
                Match my repos
              </Link>
              <Link href="#how-it-works" className="btn btn-ghost transition hover:-translate-y-0.5 hover:border-accent">
                See how it works
              </Link>
            </div>

            <dl className="mt-16 grid max-w-2xl grid-cols-2 border-t border-line pt-8 sm:grid-cols-3">
              <div className="pr-6">
                <dt className="font-display text-4xl font-extrabold text-ink">
                  <CountUp to={1} prefix="~" suffix="min" />
                </dt>
                <dd className="label mt-2">Full pipeline</dd>
              </div>
              <div className="border-line pl-6 sm:border-l">
                <dt className="font-display text-4xl font-extrabold text-ink">Free</dt>
                <dd className="label mt-2">Until Gemini step</dd>
              </div>
              <div className="border-line pl-6 sm:border-l">
                <dt className="font-display text-4xl font-extrabold text-ink">
                  <CountUp to={3} suffix="×" />
                </dt>
                <dd className="label mt-2">Manual checkpoints</dd>
              </div>
            </dl>
          </Reveal>

          <Reveal delay={180} className="hidden lg:block">
            <NeonCube className="mx-auto" />
          </Reveal>
        </div>
      </section>

      {/* ── pipeline ─────────────────────────────────────────────────── */}
      <section id="how-it-works" className="mx-auto max-w-[1400px] px-5 py-24 sm:px-10">
        <Reveal>
          <span className="chip">The pipeline</span>
          <h2 className="display mt-7 text-[clamp(2rem,4vw,3.2rem)]">
            Nine steps from
            <br />
            <span className="text-accent">repo to résumé.</span>
          </h2>
        </Reveal>

        <div className="mt-14 grid border-l border-t border-line sm:grid-cols-2 lg:grid-cols-3">
          {PIPELINE.map((step, i) => (
            <Reveal key={step.n} delay={(i % 3) * 90}>
              <article className="group relative h-full border-b border-r border-line p-8 transition hover:bg-card">
                <span className="absolute left-0 top-0 h-0 w-px bg-accent transition-all duration-500 group-hover:h-full" />
                {step.soon && <span className="label absolute right-6 top-6 border border-line px-2 py-1">Soon</span>}
                <span className="font-mono text-xs text-accent">{step.n}</span>
                <h3 className="display mt-5 text-lg">{step.title}</h3>
                <p className="mt-3 text-sm leading-relaxed text-muted">{step.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ── checkpoints ──────────────────────────────────────────────── */}
      <section id="checkpoints" className="relative isolate overflow-hidden border-y border-line bg-card/40">
        <Constellation className="absolute inset-0 -z-10 size-full opacity-40" />
        <div className="mx-auto max-w-[1400px] px-5 py-24 sm:px-10">
          <Reveal>
            <span className="chip-teal">Human control</span>
            <h2 className="display mt-7 text-[clamp(2rem,4.4vw,3.4rem)]">
              Three checkpoints.
              <br />
              <span className="text-teal">Always your call.</span>
            </h2>
            <p className="mt-6 max-w-2xl text-lg leading-relaxed text-body">
              The pipeline pauses three times for your input. These aren&apos;t confirmations — they&apos;re real
              decisions that shape the output.
            </p>
          </Reveal>

          <div className="mt-14 grid gap-6 lg:grid-cols-3">
            {CHECKPOINTS.map((c, i) => (
              <Reveal key={c.n} delay={i * 120}>
                <article className="h-full border border-line bg-paper/60 p-8 transition hover:border-teal/50">
                  <div className="flex items-center gap-4">
                    <span className="flex size-9 items-center justify-center rounded-full border border-teal/50 bg-teal-soft font-mono text-sm text-teal">
                      {c.n}
                    </span>
                    <span className="label text-teal">Checkpoint {c.n}</span>
                  </div>
                  <h3 className="display mt-7 text-xl">{c.title}</h3>
                  <p className="mt-4 text-sm leading-relaxed text-muted">{c.body}</p>
                </article>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ── tech stack ───────────────────────────────────────────────── */}
      <section id="tech-stack" className="mx-auto grid max-w-[1400px] items-center gap-16 px-5 py-24 sm:px-10 lg:grid-cols-2">
        <Reveal>
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
          <Link href="/analyze" className="btn mt-10 bg-teal text-[#03202a] transition hover:-translate-y-0.5">
            Try RepoMatch
          </Link>
        </Reveal>

        <div className="grid grid-cols-2 border-l border-t border-line">
          {STACK.map((s, i) => (
            <Reveal key={s.label} delay={(i % 2) * 90}>
              <div className="h-full border-b border-r border-line p-7 transition hover:bg-card">
                <div className="label text-teal">{s.label}</div>
                <div className="display mt-3 text-base">{s.value}</div>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ── pricing ──────────────────────────────────────────────────── */}
      <section id="pricing" className="border-y border-line bg-card/40">
        <div className="mx-auto max-w-[1400px] px-5 py-24 text-center sm:px-10">
          <Reveal>
            <span className="chip">Pricing</span>
            <h2 className="display mx-auto mt-7 max-w-3xl text-[clamp(2rem,4.4vw,3.4rem)]">Free until it gets smart</h2>
            <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-body">
              Every step before Gemini analysis is completely free. You only pay when Gemini reads your READMEs — and you
              approve that step.
            </p>
          </Reveal>

          <div className="mx-auto mt-14 grid max-w-4xl text-left lg:grid-cols-2">
            <Reveal>
              <div className="h-full border border-line p-10">
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
            </Reveal>
            <Reveal delay={140}>
              <div className="h-full border border-accent/60 bg-accent-soft p-10">
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
            </Reveal>
          </div>
        </div>
      </section>

      {/* ── ask your codebase ────────────────────────────────────────── */}
      <section className="mx-auto max-w-[1400px] px-5 py-24 sm:px-10">
        <Reveal>
          <div className="grid items-center gap-12 border border-line bg-card p-10 sm:p-14 lg:grid-cols-[1.3fr_1fr]">
            <div>
              <span className="chip">Optional</span>
              <h2 className="display mt-7 text-[clamp(1.8rem,3.4vw,2.8rem)]">
                Ask your codebase
                <br />
                <span className="text-accent">before they do.</span>
              </h2>
              <p className="mt-6 max-w-xl leading-relaxed text-body">
                Index one repository and ask anything about it — how it works, what you&apos;d demo, where the weak spots
                are. Every answer cites the files and line ranges it came from.
              </p>
              <Link href="/ask" className="btn btn-primary mt-9 transition hover:-translate-y-0.5">
                Open the console
              </Link>
            </div>
            <div className="border border-line bg-paper p-6 font-mono text-xs leading-relaxed text-muted">
              <p className="text-accent-2">you › how does the agent decide a service is unhealthy?</p>
              <p className="mt-4 text-body">
                The monitor polls /health and /metrics, flags a service after two consecutive bad polls, then wakes the
                diagnostic loop.
              </p>
              <p className="mt-4 text-accent-2">monitor/monitor.py · lines 73-89</p>
            </div>
          </div>
        </Reveal>
      </section>

      {/* ── closing ──────────────────────────────────────────────────── */}
      <section className="relative isolate overflow-hidden border-t border-line">
        <Constellation className="absolute inset-0 -z-10 size-full opacity-60" />
        <div
          aria-hidden
          className="absolute left-1/2 top-1/2 -z-10 size-[760px] -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{ background: "radial-gradient(circle, rgba(37,99,255,0.14) 0%, transparent 62%)" }}
        />
        <div className="mx-auto max-w-[1400px] px-5 py-28 text-center sm:px-10">
          <Reveal>
            <span className="chip">Get started</span>
            <h2 className="display mx-auto mt-7 max-w-4xl text-[clamp(2.2rem,5vw,4rem)] text-glow">
              Match your repos
              <br />
              <span className="text-accent-2">in under a minute.</span>
            </h2>
            <p className="mx-auto mt-6 max-w-xl text-lg leading-relaxed text-body">
              Free to start. No sign-up required. Paste a job description and your GitHub username — the rest is
              automatic.
            </p>
            <Link href="/analyze" className="btn btn-primary mt-10 !px-10 !py-5 transition hover:-translate-y-0.5">
              Launch RepoMatch
            </Link>
          </Reveal>
        </div>
      </section>
    </div>
  );
}
