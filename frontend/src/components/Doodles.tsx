// Hand-drawn marks that make the page feel sketched rather than templated.

/** A loose marker underline to sit under a highlighted word. */
export function Squiggle({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 220 18" preserveAspectRatio="none" aria-hidden className={className}>
      <path
        d="M3 12c22-7 45-9 70-6 20 2 34 7 58 5 26-2 46-8 86-6"
        fill="none"
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
      />
    </svg>
  );
}

/** A curly arrow pointing down-right, for margin notes. */
export function CurlyArrow({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 80 60" aria-hidden className={className}>
      <path
        d="M6 8c18-6 38 2 34 16-3 10-18 8-14-2 5-12 30-8 40 18"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
      />
      <path d="M58 32l8 9 2-12" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** A hand-drawn circle to ring a number or badge. */
export function Scribble({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 100 60" preserveAspectRatio="none" aria-hidden className={className}>
      <path
        d="M52 6C26 4 6 14 6 30s20 25 46 24 42-10 42-25S76 5 44 8"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  );
}
