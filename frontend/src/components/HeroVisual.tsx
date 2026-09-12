/**
 * Glowing wireframe cube beside the hero headline. Drawn as SVG rather than CSS 3D so the
 * edges always read as a cube (a CSS box can rotate face-on and look like a flat square).
 */
export function NeonCube({ className = "", size = 300 }: { className?: string; size?: number }) {
  // Isometric-ish projection of a unit cube, plus the ring from the reference.
  const front = [
    [70, 105],
    [210, 78],
    [210, 218],
    [70, 245],
  ];
  const back = [
    [118, 140],
    [246, 116],
    [246, 240],
    [118, 264],
  ];
  const poly = (pts: number[][]) => pts.map((p) => p.join(",")).join(" ");

  return (
    <div aria-hidden className={`bob ${className}`} style={{ width: size, height: size }}>
      <svg viewBox="0 0 300 300" className="size-full" style={{ filter: "drop-shadow(0 0 22px rgba(37,99,255,0.55))" }}>
        <ellipse cx="158" cy="182" rx="92" ry="30" fill="none" stroke="rgba(37,99,255,0.35)" strokeWidth="1" />
        <polygon points={poly(back)} fill="none" stroke="rgba(37,99,255,0.55)" strokeWidth="1.2" />
        {back.map((p, i) => (
          <line key={i} x1={front[i][0]} y1={front[i][1]} x2={p[0]} y2={p[1]} stroke="rgba(37,99,255,0.55)" strokeWidth="1.2" />
        ))}
        <polygon points={poly(front)} fill="rgba(37,99,255,0.05)" stroke="#4d7dff" strokeWidth="1.6" />
      </svg>
    </div>
  );
}

// Deterministic scatter so server and client render identically.
function seeded(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

const rand = seeded(20260912);
const POINTS = Array.from({ length: 46 }, () => ({ x: rand() * 1440, y: rand() * 760, r: rand() * 1.6 + 0.6 }));
const LINKS = POINTS.flatMap((p, i) =>
  POINTS.slice(i + 1)
    .filter((q) => Math.hypot(p.x - q.x, p.y - q.y) < 165)
    .map((q) => ({ x1: p.x, y1: p.y, x2: q.x, y2: q.y })),
);

/** Faint constellation mesh behind dark sections. */
export function Constellation({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 1440 760" preserveAspectRatio="xMidYMid slice" aria-hidden className={className}>
      {LINKS.map((l, i) => (
        <line key={i} x1={l.x1} y1={l.y1} x2={l.x2} y2={l.y2} stroke="rgba(91,140,255,0.18)" strokeWidth="0.7" />
      ))}
      {POINTS.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r={p.r} fill="rgba(120,165,255,0.5)" />
      ))}
    </svg>
  );
}
