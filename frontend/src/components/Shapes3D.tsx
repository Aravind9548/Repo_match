import type { CSSProperties, ReactNode } from "react";

type Tone = "accent" | "blue" | "moss" | "amber" | "card";

const mix = (tone: Tone, pct: number, toward: "black" | "white") =>
  `color-mix(in oklab, var(--${tone}) ${pct}%, ${toward})`;

/**
 * A real CSS 3D box (six faces, preserve-3d). Faces are shaded from one theme color, so it follows
 * light/dark mode. `spin` animates it; otherwise it sits at `rotate`.
 */
export function Cuboid({
  w,
  h,
  d,
  tone,
  spin,
  rotate = "rotateX(-22deg) rotateY(-32deg)",
  duration,
  front,
}: {
  w: number;
  h: number;
  d: number;
  tone: Tone;
  spin?: "y" | "tumble";
  rotate?: string;
  duration?: string;
  front?: ReactNode;
}) {
  const edge = "1.5px solid color-mix(in oklab, var(--ink) 22%, transparent)";
  const faces: { style: CSSProperties; content?: ReactNode }[] = [
    { style: { width: w, height: h, transform: `translateZ(${d / 2}px)`, background: `var(--${tone})` }, content: front },
    { style: { width: w, height: h, transform: `rotateY(180deg) translateZ(${d / 2}px)`, background: mix(tone, 62, "black") } },
    { style: { width: d, height: h, left: (w - d) / 2, transform: `rotateY(90deg) translateZ(${w / 2}px)`, background: mix(tone, 76, "black") } },
    { style: { width: d, height: h, left: (w - d) / 2, transform: `rotateY(-90deg) translateZ(${w / 2}px)`, background: mix(tone, 76, "black") } },
    { style: { width: w, height: d, top: (h - d) / 2, transform: `rotateX(90deg) translateZ(${h / 2}px)`, background: mix(tone, 78, "white") } },
    { style: { width: w, height: d, top: (h - d) / 2, transform: `rotateX(-90deg) translateZ(${h / 2}px)`, background: mix(tone, 55, "black") } },
  ];
  return (
    <div className="scene3d" style={{ width: w, height: h }} aria-hidden>
      <div
        className={`cuboid ${spin === "y" ? "spin-y" : spin === "tumble" ? "tumble" : ""}`}
        style={{ width: w, height: h, transform: spin ? undefined : rotate, animationDuration: duration }}
      >
        {faces.map((f, i) => (
          <div key={i} className="cuboid-face overflow-hidden" style={{ ...f.style, border: edge }}>
            {f.content}
          </div>
        ))}
      </div>
    </div>
  );
}

/** Slowly spinning boxes parked in the wide-screen side gutters. */
export function FloatingShapes() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-[-2] hidden 2xl:block">
      <div className="bob absolute left-[3.5%] top-[20%]">
        <Cuboid w={86} h={86} d={86} tone="blue" spin="y" duration="18s" />
      </div>
      <div className="bob absolute left-[6%] top-[64%]" style={{ animationDelay: "-2.5s" }}>
        <Cuboid w={124} h={44} d={62} tone="accent" spin="tumble" duration="34s" />
      </div>
      <div className="bob absolute right-[5%] top-[28%]" style={{ animationDelay: "-4s" }}>
        <Cuboid w={50} h={132} d={50} tone="moss" spin="y" duration="22s" />
      </div>
      <div className="bob absolute right-[8%] top-[72%]" style={{ animationDelay: "-1s" }}>
        <Cuboid w={58} h={58} d={58} tone="amber" spin="tumble" duration="26s" />
      </div>
    </div>
  );
}

const BLOCKS: { label: string; tone: Tone; x: number; y: number }[] = [
  { label: "rag-agent", tone: "accent", x: 4, y: 34 },
  { label: "kv-store", tone: "blue", x: 22, y: 150 },
  { label: "auth-service", tone: "moss", x: 0, y: 268 },
];

function ResumeFace() {
  return (
    <div className="relative size-full p-4">
      <div className="flex items-center gap-2">
        <span className="size-7 rounded-full bg-sunken ring-1 ring-line" />
        <span className="space-y-1">
          <span className="block h-1.5 w-20 rounded-full bg-ink/80" />
          <span className="block h-1.5 w-12 rounded-full bg-ink/30" />
        </span>
      </div>
      <span className="mt-4 block h-1 w-full rounded-full bg-ink/15" />
      <span className="mt-1.5 block h-1 w-4/5 rounded-full bg-ink/15" />
      <div className="mt-4 rounded-md bg-accent-soft p-2 ring-1 ring-accent/40">
        <span className="block font-mono text-[9px] font-bold uppercase tracking-widest text-accent">projects</span>
        <span className="mt-1.5 block h-1 w-full rounded-full bg-ink/60" />
        <span className="mt-1 block h-1 w-5/6 rounded-full bg-ink/40" />
        <span className="mt-1 block h-1 w-4/6 rounded-full bg-ink/40" />
      </div>
      <span className="mt-4 block h-1 w-full rounded-full bg-ink/15" />
      <span className="mt-1.5 block h-1 w-3/4 rounded-full bg-ink/15" />
      <span className="mt-1.5 block h-1 w-5/6 rounded-full bg-ink/15" />
      <span className="mt-4 block h-1 w-2/3 rounded-full bg-ink/15" />
      <span className="mt-1.5 block h-1 w-full rounded-full bg-ink/15" />
    </div>
  );
}

/** Hero: repo blocks floating toward a resume slab, in real 3D. */
export function HeroScene() {
  return (
    <div aria-hidden className="relative mx-auto h-[400px] w-full max-w-[520px] select-none">
      <svg viewBox="0 0 520 400" className="absolute inset-0 size-full">
        {[70, 188, 300].map((y, i) => (
          <path
            key={y}
            d={`M175 ${y} C 230 ${y}, 250 ${150 + i * 40}, 300 ${150 + i * 40}`}
            fill="none"
            strokeWidth="2.5"
            strokeLinecap="round"
            className={`connector-running ${i === 2 ? "stroke-[#3a4a8c]" : "stroke-[#1fe0ff]"}`}
          />
        ))}
      </svg>

      <div className="bob absolute right-2 top-4" style={{ animationDuration: "9s" }}>
        <Cuboid w={200} h={280} d={14} tone="card" rotate="rotateX(6deg) rotateY(-26deg)" front={<ResumeFace />} />
      </div>

      {BLOCKS.map((b, i) => (
        <div key={b.label} className="bob absolute" style={{ left: b.x, top: b.y, animationDelay: `${-i * 1.7}s` }}>
          <Cuboid
            w={150}
            h={54}
            d={46}
            tone={b.tone}
            rotate="rotateX(-20deg) rotateY(26deg)"
            front={
              <div className="flex size-full flex-col justify-center gap-1.5 px-3">
                <span className={`font-mono text-[13px] font-bold ${b.tone === "blue" ? "text-white" : "text-[#031029]"}`}>{b.label}</span>
                <span className="flex gap-1">
                  <span className={`h-1 w-8 rounded-full ${b.tone === "blue" ? "bg-white/80" : "bg-[#031029]/70"}`} />
                  <span className={`h-1 w-5 rounded-full ${b.tone === "blue" ? "bg-white/45" : "bg-[#031029]/40"}`} />
                </span>
              </div>
            }
          />
        </div>
      ))}

      <span className="absolute bottom-0 right-10 rotate-[-4deg] font-hand text-2xl text-[#1fe0ff]">
        only the good ones make it →
      </span>
    </div>
  );
}
