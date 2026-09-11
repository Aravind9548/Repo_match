/**
 * Sketch of the idea: a few repo cards on the left, loose ink lines carrying the best ones
 * onto a resume sheet on the right. Colors come from theme tokens via Tailwind fill/stroke classes.
 */
export function HeroIllustration({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 520 380" role="img" aria-label="Repository cards connected to a resume" className={className}>
      {/* repo cards */}
      <g transform="rotate(-4 110 80)">
        <rect x="30" y="40" width="170" height="78" rx="14" className="fill-card stroke-ink" strokeWidth="2" />
        <circle cx="54" cy="64" r="8" className="fill-moss-soft stroke-moss" strokeWidth="2" />
        <path d="M70 61h86M70 70h52" className="stroke-ink" strokeWidth="2.5" strokeLinecap="round" />
        <path d="M46 94h36" className="stroke-accent" strokeWidth="5" strokeLinecap="round" />
        <path d="M90 94h28" className="stroke-line" strokeWidth="5" strokeLinecap="round" />
      </g>
      <g transform="rotate(3 120 190)">
        <rect x="40" y="152" width="170" height="78" rx="14" className="fill-card stroke-ink" strokeWidth="2" />
        <circle cx="64" cy="176" r="8" className="fill-accent-soft stroke-accent" strokeWidth="2" />
        <path d="M80 173h86M80 182h40" className="stroke-ink" strokeWidth="2.5" strokeLinecap="round" />
        <path d="M56 206h44" className="stroke-moss" strokeWidth="5" strokeLinecap="round" />
        <path d="M108 206h20" className="stroke-line" strokeWidth="5" strokeLinecap="round" />
      </g>
      <g transform="rotate(-2 110 300)" opacity="0.55">
        <rect x="26" y="264" width="170" height="78" rx="14" className="fill-card stroke-ink" strokeWidth="2" strokeDasharray="5 6" />
        <path d="M50 290h96M50 300h60" className="stroke-muted" strokeWidth="2.5" strokeLinecap="round" />
      </g>

      {/* ink connectors */}
      <path d="M204 76c60-4 70 40 120 44" fill="none" className="stroke-accent" strokeWidth="2.5" strokeLinecap="round" />
      <path d="M212 190c50 6 60-36 112-38" fill="none" className="stroke-accent" strokeWidth="2.5" strokeLinecap="round" />
      <path d="M318 116l8 5-9 4M318 148l8 4-8 5" fill="none" className="stroke-accent" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />

      {/* resume sheet */}
      <g transform="rotate(2 420 190)">
        <rect x="330" y="40" width="160" height="300" rx="10" className="fill-card stroke-ink" strokeWidth="2" />
        <circle cx="358" cy="72" r="12" className="fill-sunken stroke-ink" strokeWidth="2" />
        <path d="M378 68h80M378 80h52" className="stroke-ink" strokeWidth="2.5" strokeLinecap="round" />
        <path d="M348 108h124M348 120h110" className="stroke-line" strokeWidth="3" strokeLinecap="round" />
        {/* highlighted Projects section */}
        <rect x="342" y="138" width="136" height="72" rx="8" className="fill-accent-soft" />
        <path d="M350 152h58" className="stroke-accent" strokeWidth="3" strokeLinecap="round" />
        <path d="M350 168h112M350 180h96M350 194h104" className="stroke-ink" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M348 232h124M348 244h96M348 256h116M348 280h124M348 292h80M348 304h110" className="stroke-line" strokeWidth="3" strokeLinecap="round" />
      </g>

      {/* margin tick */}
      <path d="M470 118l10 12 22-28" fill="none" className="stroke-moss" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
