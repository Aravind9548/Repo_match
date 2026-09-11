import type { ComponentType, SVGProps } from "react";
import type { WorkflowStep } from "@/lib/api";
import {
  BarsIcon,
  CheckIcon,
  DocIcon,
  GithubIcon,
  HandIcon,
  LinkIcon,
  PenIcon,
  SparkleIcon,
} from "@/components/icons";

export type Busy = "fetch" | "rank" | "judge" | "save";
type NodeStatus = "done" | "waiting" | "running" | "pending" | "future";

type CanvasNode = {
  label: string;
  note: string;
  kind: "auto" | "review" | "future";
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  paid?: boolean;
};

/** The pipeline, n8n-style: square nodes run on their own, round ones wait for you. */
const PIPELINE: CanvasNode[] = [
  { label: "Fetch repos", note: "from GitHub", kind: "auto", icon: GithubIcon },
  { label: "Pick repos", note: "you decide", kind: "review", icon: HandIcon },
  { label: "Rank", note: "embeddings", kind: "auto", icon: BarsIcon },
  { label: "Shortlist", note: "you decide", kind: "review", icon: HandIcon },
  { label: "Gemini judge", note: "reads READMEs", kind: "auto", icon: SparkleIcon, paid: true },
  { label: "Accept picks", note: "you decide", kind: "review", icon: HandIcon },
  { label: "Save", note: "shareable link", kind: "auto", icon: LinkIcon },
  { label: "Resume bullets", note: "coming soon", kind: "future", icon: PenIcon },
  { label: "Tailor LaTeX", note: "coming soon", kind: "future", icon: DocIcon },
];

const GATE_INDEX: Record<WorkflowStep, number> = {
  REVIEW_REPOS: 1,
  REVIEW_SHORTLIST: 3,
  REVIEW_RESULTS: 5,
  DONE: 7,
};

const BUSY_INDEX: Record<Busy, number> = { fetch: 0, rank: 2, judge: 4, save: 6 };

/** Status of every node given where the workflow is (null = not started) and what's running. */
export function statusesFor(step: WorkflowStep | null, busy: Busy | null): NodeStatus[] {
  return PIPELINE.map((node, i) => {
    if (node.kind === "future") return "future";
    if (busy) {
      const b = BUSY_INDEX[busy];
      return i < b ? "done" : i === b ? "running" : "pending";
    }
    if (step === null) return "pending";
    const gate = GATE_INDEX[step];
    return i < gate ? "done" : i === gate ? "waiting" : "pending";
  });
}

const BOX: Record<NodeStatus, string> = {
  done: "border-moss bg-moss-soft text-moss",
  waiting: "border-accent bg-accent-soft text-accent ring-4 ring-accent/15 motion-safe:animate-pulse",
  running: "border-ink bg-card text-ink",
  pending: "border-line bg-card text-muted",
  future: "border-dashed border-line bg-card/60 text-muted opacity-70",
};

function Connector({ from, to }: { from: NodeStatus; to: NodeStatus }) {
  const tone =
    to === "running"
      ? "stroke-ink connector-running"
      : from === "done" && to !== "pending" && to !== "future"
        ? "stroke-moss"
        : "stroke-line";
  return (
    <svg viewBox="0 0 48 56" preserveAspectRatio="none" className="h-14 w-8 shrink-0" aria-hidden>
      <path
        d="M2 28 C 16 18, 32 38, 46 28"
        fill="none"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeDasharray={to === "future" ? "3 5" : undefined}
        className={tone}
      />
    </svg>
  );
}

export function WorkflowCanvas({ statuses, caption }: { statuses: NodeStatus[]; caption?: string }) {
  return (
    <figure className="rounded-2xl border border-line bg-sunken/60 [background-image:radial-gradient(var(--dot)_1.2px,transparent_1.2px)] [background-size:18px_18px]">
      <div className="overflow-x-auto">
        <ol className="flex min-w-max items-start px-5 py-5 sm:px-6">
          {PIPELINE.map((node, i) => {
            const status = statuses[i];
            const Icon = node.icon;
            return (
              <li key={node.label} className="flex items-start">
                {i > 0 && <Connector from={statuses[i - 1]} to={status} />}
                <div className="flex w-[5.25rem] flex-col items-center text-center">
                  <div
                    className={`relative flex size-14 items-center justify-center border-2 ${
                      node.kind === "review" ? "rounded-full" : "rounded-2xl"
                    } ${BOX[status]}`}
                  >
                    {status === "done" ? <CheckIcon className="size-6" /> : <Icon className="size-6" />}
                    {status === "running" && (
                      <span className="absolute -inset-1.5 rounded-[1.25rem] border-2 border-transparent border-t-accent motion-safe:animate-spin" />
                    )}
                    {node.paid && (
                      <span className="absolute -right-2 -top-2 rounded-full border border-amber/30 bg-amber-soft px-1.5 text-[10px] font-semibold text-amber">
                        paid
                      </span>
                    )}
                  </div>
                  <span className="mt-2 text-xs font-semibold leading-tight">{node.label}</span>
                  <span className="font-hand text-[15px] leading-tight text-muted">
                    {status === "waiting" ? "waiting for you" : status === "running" ? "working…" : node.note}
                  </span>
                </div>
              </li>
            );
          })}
        </ol>
      </div>
      {caption && (
        <figcaption className="border-t border-line px-5 py-2.5 text-xs text-muted sm:px-6">{caption}</figcaption>
      )}
    </figure>
  );
}
