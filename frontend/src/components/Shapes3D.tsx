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
  const edge = "1px solid color-mix(in oklab, var(--ink) 18%, transparent)";
  const faces: { style: CSSProperties; content?: ReactNode }[] = [
    { style: { width: w, height: h, transform: `translateZ(${d / 2}px)`, background: `var(--${tone})` }, content: front },
    { style: { width: w, height: h, transform: `rotateY(180deg) translateZ(${d / 2}px)`, background: mix(tone, 62, "black") } },
    { style: { width: d, height: h, left: (w - d) / 2, transform: `rotateY(90deg) translateZ(${w / 2}px)`, background: mix(tone, 78, "black") } },
    { style: { width: d, height: h, left: (w - d) / 2, transform: `rotateY(-90deg) translateZ(${w / 2}px)`, background: mix(tone, 78, "black") } },
    { style: { width: w, height: d, top: (h - d) / 2, transform: `rotateX(90deg) translateZ(${h / 2}px)`, background: mix(tone, 82, "white") } },
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

/** A few quiet, slowly turning blocks in the wide-screen side margins. */
export function FloatingShapes() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-[-2] hidden opacity-60 2xl:block">
      <div className="bob absolute left-[3.5%] top-[62%]">
        <Cuboid w={80} h={80} d={80} tone="blue" spin="y" duration="26s" />
      </div>
      <div className="bob absolute left-[6%] top-[86%]" style={{ animationDelay: "-2.5s" }}>
        <Cuboid w={110} h={40} d={56} tone="card" spin="tumble" duration="40s" />
      </div>
      <div className="bob absolute right-[5%] top-[66%]" style={{ animationDelay: "-4s" }}>
        <Cuboid w={46} h={120} d={46} tone="card" spin="y" duration="30s" />
      </div>
      <div className="bob absolute right-[8%] top-[88%]" style={{ animationDelay: "-1s" }}>
        <Cuboid w={54} h={54} d={54} tone="blue" spin="tumble" duration="34s" />
      </div>
    </div>
  );
}
