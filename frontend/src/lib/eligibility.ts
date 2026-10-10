/**
 * Reads the hard requirements out of a job ad and compares them with what the person has told us.
 *
 * Deterministic on purpose: no model, no guessing. It only reports a mismatch it can quote the ad for, and it only
 * compares against details the person typed themselves. Anything it cannot read, it says nothing about — a missed
 * mismatch costs an application, a wrongly claimed one costs a job they could have had.
 */

export type EligibilityNote = {
  /** "blocked" when the ad's own words rule the person out; "check" when it is worth reading before applying. */
  level: "blocked" | "check";
  text: string;
  /** The ad's wording this rests on. */
  quote: string;
};

const YEAR = /\b(20\d\d)\b/g;

function quoteAround(text: string, index: number, span = 150): string {
  const start = Math.max(0, index - 40);
  return text.slice(start, Math.min(text.length, start + span)).replace(/\s+/g, " ").trim();
}

/** Graduation years the ad asks for: "2025 & 2026 passouts", "batch of 2026", "graduating in 2027". */
export function wantedGraduationYears(jd: string): { years: number[]; quote: string } | null {
  const text = jd ?? "";
  const phrase =
    /((?:batch|passout|pass[- ]out|graduat\w*|class)[^.\n]{0,80})|([^.\n]{0,60}(?:batch|passouts?|graduates?|graduating)[^.\n]{0,40})/gi;
  let best: { years: number[]; quote: string } | null = null;
  for (const m of text.matchAll(phrase)) {
    const window = m[0];
    const years = [...window.matchAll(YEAR)].map((y) => Number(y[1])).filter((y) => y >= 2020 && y <= 2035);
    if (years.length && (!best || years.length > best.years.length)) {
      best = { years: [...new Set(years)].sort(), quote: quoteAround(text, m.index ?? 0) };
    }
  }
  return best;
}

/** Years of experience the ad requires: "4+ years", "minimum 3 years of experience". */
export function wantedExperience(jd: string): { years: number; quote: string } | null {
  const m = (jd ?? "").match(/(?:minimum\s+(?:of\s+)?)?(\d{1,2})\s*\+?\s*(?:-\s*\d{1,2}\s*)?years?\s+(?:of\s+)?(?:relevant\s+|professional\s+|industry\s+|hands[- ]on\s+)?(?:work\s+)?experience/i);
  if (!m) return null;
  const years = Number(m[1]);
  return years >= 1 && years <= 20 ? { years, quote: quoteAround(jd, m.index ?? 0) } : null;
}

/** The year in whatever the person wrote in their details ("B.Tech CSE, May 2027"). */
export function graduationYearOf(graduation: string): number | null {
  const years = [...(graduation ?? "").matchAll(YEAR)].map((m) => Number(m[1]));
  return years.length ? Math.max(...years) : null;
}

/**
 * What to tell the person before they spend an evening on this application.
 *
 * Nothing is returned unless the ad states a requirement AND they have given the matching detail: silence here
 * means "not checked", never "you qualify".
 */
export function eligibilityNotes(jd: string, profile: { graduation?: string } | null): EligibilityNote[] {
  const notes: EligibilityNote[] = [];
  if (!jd || jd.length < 40) return notes;

  const wantedYears = wantedGraduationYears(jd);
  const mine = graduationYearOf(profile?.graduation ?? "");
  if (wantedYears && mine) {
    if (!wantedYears.years.includes(mine)) {
      const list = wantedYears.years.join(" and ");
      notes.push({
        level: "blocked",
        text: `This asks for ${list} graduates. Your details say ${mine}.`,
        quote: wantedYears.quote,
      });
    }
  } else if (wantedYears && !mine) {
    notes.push({
      level: "check",
      text: `This asks for ${wantedYears.years.join(" and ")} graduates. Add your graduation year to your details and this gets checked for you.`,
      quote: wantedYears.quote,
    });
  }

  const experience = wantedExperience(jd);
  if (experience && experience.years >= 2) {
    notes.push({
      level: experience.years >= 4 ? "blocked" : "check",
      text: `This asks for ${experience.years}+ years of experience.`,
      quote: experience.quote,
    });
  }

  return notes;
}
