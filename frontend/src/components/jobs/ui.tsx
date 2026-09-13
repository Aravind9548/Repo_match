import Link from "next/link";
import type { ApplicationStatus, Posting } from "@/lib/api";

export const STATUS_META: Record<ApplicationStatus, { label: string; color: string; hint: string }> = {
  APPROVED: { label: "To apply", color: "var(--accent-2)", hint: "Approved — pick a resume and submit" },
  APPLIED: { label: "Applied", color: "var(--teal)", hint: "Submitted, waiting to hear back" },
  ASSESSMENT: { label: "Assessment", color: "var(--amber)", hint: "OA or take-home received" },
  INTERVIEW: { label: "Interview", color: "#b58cff", hint: "Interviews scheduled or in progress" },
  OFFER: { label: "Offer", color: "var(--moss)", hint: "You got it" },
  REJECTED: { label: "Rejected", color: "#ff6b6b", hint: "Not this time" },
  GHOSTED: { label: "No reply", color: "var(--muted)", hint: "Went quiet" },
  WITHDRAWN: { label: "Withdrawn", color: "var(--muted)", hint: "You stepped back" },
};

/** The board's columns, left to right. Closed-out statuses share one column. */
export const BOARD_COLUMNS: { title: string; statuses: ApplicationStatus[] }[] = [
  { title: "To apply", statuses: ["APPROVED"] },
  { title: "Applied", statuses: ["APPLIED"] },
  { title: "Assessment", statuses: ["ASSESSMENT"] },
  { title: "Interview", statuses: ["INTERVIEW"] },
  { title: "Closed", statuses: ["OFFER", "REJECTED", "GHOSTED", "WITHDRAWN"] },
];

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  const meta = STATUS_META[status];
  return (
    <span className="inline-flex items-center gap-2 border border-line px-2.5 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-body">
      <span className="status-dot" style={{ background: meta.color }} />
      {meta.label}
    </span>
  );
}

export function PageHeader({
  chip,
  title,
  accent,
  children,
}: {
  chip: string;
  title: string;
  accent?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="max-w-3xl">
      <span className="chip">{chip}</span>
      <h1 className="display mt-7 text-[clamp(2rem,4.4vw,3.4rem)]">
        {title}
        {accent && (
          <>
            <br />
            <span className="text-accent">{accent}</span>
          </>
        )}
      </h1>
      {children && <div className="mt-6 text-lg leading-relaxed text-body">{children}</div>}
    </div>
  );
}

export function kindLabel(kind: Posting["kind"]) {
  return kind === "INTERNSHIP" ? "Internship" : kind === "NEW_GRAD" ? "New grad" : "Full-time";
}

const COUNTRY_NAMES: Record<string, string> = {
  IN: "India", US: "United States", CA: "Canada", GB: "United Kingdom", DE: "Germany", IE: "Ireland", SG: "Singapore",
  NL: "Netherlands", FR: "France", JP: "Japan", AU: "Australia", PL: "Poland", MX: "Mexico", BR: "Brazil", IL: "Israel",
  AE: "UAE", ES: "Spain", KR: "South Korea", CN: "China", CH: "Switzerland", SE: "Sweden",
};

export function countryName(code: string) {
  return COUNTRY_NAMES[code] ?? code;
}

/** When a posting was listed: the employer's date when known. */
export function listedAt(p: Posting) {
  return p.postedAt ?? p.firstSeenAt;
}

export function timeAgo(iso: string) {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days <= 0) return "today";
  if (days === 1) return "1 day ago";
  if (days < 30) return `${days} days ago`;
  const months = Math.floor(days / 30);
  return months === 1 ? "1 month ago" : `${months} months ago`;
}

export function PostingRow({ posting, trailing }: { posting: Posting; trailing?: React.ReactNode }) {
  return (
    <div className="group relative flex flex-wrap items-center gap-x-6 gap-y-3 border-b border-line px-5 py-4 transition hover:bg-card">
      <span className="absolute inset-y-0 left-0 w-0.5 origin-top scale-y-0 bg-accent transition-transform duration-300 group-hover:scale-y-100" />
      <Link href={`/jobs/${posting.id}`} className="min-w-0 flex-1 basis-72">
        <span className="block truncate text-[15px] text-ink group-hover:text-accent-2">{posting.role}</span>
        <span className="mt-1 block truncate text-sm text-muted">
          {posting.company}
          {posting.location ? ` · ${posting.location}` : ""}
          {posting.remote && !/remote/i.test(posting.location ?? "") ? " · Remote" : ""}
        </span>
      </Link>
      <span className="label hidden sm:block">{kindLabel(posting.kind)}</span>
      <span className="label w-24 text-right">{timeAgo(listedAt(posting))}</span>
      {trailing}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="border border-dashed border-line-strong bg-card/40 px-6 py-14 text-center">
      <p className="label">{title}</p>
      {children && <div className="mx-auto mt-4 max-w-md text-body">{children}</div>}
    </div>
  );
}
