/**
 * Night-city backdrop for the landing hero: skyline, lit windows and long-exposure light trails.
 * Drawn as SVG (deterministic, so server and client render the same) — crisp at any size, no photo asset.
 */

const W = 1600;
const H = 760;
const HORIZON = 500;

function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

type Building = { x: number; w: number; h: number };

function skyline(seed: number, minW: number, maxW: number, minH: number, maxH: number): Building[] {
  const r = rng(seed);
  const out: Building[] = [];
  let x = -20;
  while (x < W + 20) {
    const w = minW + r() * (maxW - minW);
    out.push({ x, w, h: minH + r() * (maxH - minH) });
    x += w + r() * 6;
  }
  return out;
}

const FAR = skyline(7, 28, 70, 80, 230);
const NEAR = skyline(42, 46, 120, 110, 330);
const TOWERS: Building[] = [
  { x: 540, w: 36, h: 420 },
  { x: 1050, w: 42, h: 470 },
  { x: 1330, w: 30, h: 390 },
];

const rand = rng(99);
const WINDOWS = [...NEAR, ...TOWERS].flatMap((b) => {
  const cells: { x: number; y: number; warm: boolean; o: number }[] = [];
  for (let y = HORIZON - b.h + 12; y < HORIZON - 12; y += 18) {
    for (let x = b.x + 7; x < b.x + b.w - 8; x += 13) {
      if (rand() < 0.27) cells.push({ x, y, warm: rand() < 0.7, o: 0.45 + rand() * 0.5 });
    }
  }
  return cells;
});
const STARS = Array.from({ length: 90 }, () => ({
  x: rand() * W,
  y: rand() * HORIZON * 0.55,
  r: rand() * 1.3 + 0.3,
  o: rand() * 0.7 + 0.2,
}));

const TRAILS = [
  { d: "M-120 800 C 300 650, 720 600, 1720 560", warm: true, delay: "0s" },
  { d: "M-120 830 C 380 700, 820 645, 1720 600", warm: false, delay: "-2s" },
  { d: "M-120 770 C 260 630, 650 585, 1720 540", warm: false, delay: "-4s" },
  { d: "M-120 870 C 440 735, 910 675, 1720 635", warm: true, delay: "-1s" },
  { d: "M-120 900 C 520 770, 980 700, 1720 665", warm: false, delay: "-3s" },
];

export function CityNight({ className = "" }: { className?: string }) {
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" className={className} aria-hidden>
      <defs>
        <linearGradient id="cn-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#030720" />
          <stop offset="0.5" stopColor="#0a1552" />
          <stop offset="0.75" stopColor="#1a2f8f" />
          <stop offset="1" stopColor="#2b1c66" />
        </linearGradient>
        <radialGradient id="cn-haze" cx="0.5" cy="0.66" r="0.6">
          <stop offset="0" stopColor="#3f7bff" stopOpacity="0.6" />
          <stop offset="1" stopColor="#3f7bff" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="cn-warm" x1="0" x2="1">
          <stop offset="0" stopColor="#ff2d6f" />
          <stop offset="0.6" stopColor="#ff5d3d" />
          <stop offset="1" stopColor="#ffb347" />
        </linearGradient>
        <linearGradient id="cn-cool" x1="0" x2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="0.5" stopColor="#9ff3ff" />
          <stop offset="1" stopColor="#1fe0ff" />
        </linearGradient>
        <linearGradient id="cn-ground" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#081036" />
          <stop offset="1" stopColor="#030618" />
        </linearGradient>
        <filter id="cn-glow" x="-10%" y="-60%" width="120%" height="220%">
          <feGaussianBlur stdDeviation="8" />
        </filter>
      </defs>

      <rect width={W} height={H} fill="url(#cn-sky)" />
      {STARS.map((s, i) => (
        <circle key={i} cx={s.x} cy={s.y} r={s.r} fill="#dbe6ff" opacity={s.o} />
      ))}
      <ellipse cx={W / 2} cy={HORIZON} rx={W * 0.72} ry={270} fill="url(#cn-haze)" />

      {FAR.map((b, i) => (
        <rect key={i} x={b.x} y={HORIZON - b.h} width={b.w} height={b.h} fill="#15287a" opacity="0.8" />
      ))}
      {TOWERS.map((t) => (
        <g key={t.x}>
          <rect x={t.x} y={HORIZON - t.h} width={t.w} height={t.h} fill="#0b1850" />
          <line x1={t.x + t.w / 2} y1={HORIZON - t.h} x2={t.x + t.w / 2} y2={HORIZON - t.h - 46} stroke="#2a3d9a" strokeWidth="2" />
          <circle className="blink" cx={t.x + t.w / 2} cy={HORIZON - t.h - 48} r="3.5" fill="#ff3b5c" />
        </g>
      ))}
      {NEAR.map((b, i) => (
        <rect key={i} x={b.x} y={HORIZON - b.h} width={b.w} height={b.h} fill="#081241" />
      ))}
      {WINDOWS.map((w, i) => (
        <rect key={i} x={w.x} y={w.y} width="5" height="8" rx="1" fill={w.warm ? "#ffcf7a" : "#8eeeff"} opacity={w.o} />
      ))}

      <rect y={HORIZON} width={W} height={H - HORIZON} fill="url(#cn-ground)" />
      {TRAILS.map((t) => (
        <g key={t.d}>
          <path d={t.d} fill="none" stroke={`url(#cn-${t.warm ? "warm" : "cool"})`} strokeWidth="16" opacity="0.55" filter="url(#cn-glow)" />
          <path
            d={t.d}
            fill="none"
            stroke={`url(#cn-${t.warm ? "warm" : "cool"})`}
            strokeWidth="2.4"
            strokeLinecap="round"
            className="trail"
            style={{ animationDelay: t.delay }}
          />
        </g>
      ))}
    </svg>
  );
}
