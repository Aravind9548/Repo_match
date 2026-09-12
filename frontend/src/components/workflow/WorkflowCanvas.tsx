import type { ComponentType, ReactNode, SVGProps } from "react";
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
type NodeStatus = "done" | "current" | "running" | "pending" | "future";

type CanvasNode = {
  label: string;
  note: string;
  kind: "auto" | "review" | "future";
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  /** The review gate you land on when you click this node to go back. */
  gate: WorkflowStep | null;
  paid?: boolean;
};

/** The pipeline, n8n-style: indigo squares run on their own, cyan circles wait for you. */
const PIPELINE: CanvasNode[] = [
  { label: "Fetch repos", note: "from GitHub", kind: "auto", icon: GithubIcon, gate: "REVIEW_REPOS" },
  { label: "Pick repos", note: "you decide", kind: "review", icon: HandIcon, gate: "REVIEW_REPOS" },
  { label: "Rank", note: "embeddings", kind: "auto", icon: BarsIcon, gate: "REVIEW_SHORTLIST" },
  { label: "Shortlist", note: "you decide", kind: "review", icon: HandIcon, gate: "REVIEW_SHORTLIST" },
  { label: "Gemini judge", note: "reads READMEs", kind: "auto", icon: SparkleIcon, gate: "REVIEW_RESULTS", paid: true },
  { label: "Accept picks", note: "you decide", kind: "review", icon: HandIcon, gate: "REVIEW_RESULTS" },
  { label: "Save", note: "shareable link", kind: "auto", icon: LinkIcon, gate: null },
  { label: "Resume bullets", note: "coming soon", kind: "future", icon: PenIcon, gate: null },
  { label: "Tailor LaTeX", note: "coming soon", kind: "future", icon: DocIcon, gate: null },
];

const STEP_COUNT = PIPELINE.filter((n) => n.kind !== "future").length;
const STEP_ORDER: WorkflowStep[] = ["REVIEW_REPOS", "REVIEW_SHORTLIST", "REVIEW_RESULTS", "DONE"];

const GATE_INDEX: Record<WorkflowStep, number> = {
  REVIEW_REPOS: 1,
  REVIEW_SHORTLIST: 3,
  REVIEW_RESULTS: 5,
  DONE: STEP_COUNT,
};

const BUSY_INDEX: Record<Busy, number> = { fetch: 0, rank: 2, judge: 4, save: 6 };

/** Status of every node given where the workflow is (null = not started yet) and what's running. */
export function statusesFor(step: WorkflowStep | null, busy: Busy | null): NodeStatus[] {
  return PIPELINE.map((node, i) => {
    if (node.kind === "future") return "future";
    if (busy) {
      const b = BUSY_INDEX[busy];
      return i < b ? "done" : i === b ? "running" : "pending";
    }
    if (step === null) return i === 0 ? "current" : "pending";
    const gate = GATE_INDEX[step];
    return i < gate ? "done" : i === gate ? "current" : "pending";
  });
}

function boxClass(node: CanvasNode, status: NodeStatus) {
  const shape = node.kind === "review" ? "rounded-full" : "rounded-2xl";
  switch (status) {
    case "done":
      return `${shape} ${node.kind === "review" ? "border-accent bg-accent text-on-accent" : "border-blue bg-blue text-white"}`;
    case "current":
      return `${shape} scale-110 border-accent bg-card text-accent ring-2 ring-accent/40`;
    case "running":
      return `${shape} scale-110 border-blue bg-card text-blue ring-2 ring-blue/40`;
    case "pending":
      return `${shape} border-line bg-card text-muted`;
    case "future":
      return `${shape} border-dashed border-line bg-transparent text-muted opacity-60`;
  }
}

function Connector({ from, to }: { from: NodeStatus; to: NodeStatus }) {
  let tone = "stroke-line";
  let dash: string | undefined = "3 6";
  if (to === "running") {
    tone = "stroke-blue connector-running";
    dash = undefined;
  } else if (from === "done" && (to === "done" || to === "current")) {
    tone = to === "current" ? "stroke-accent" : "stroke-blue";
    dash = undefined;
  }
  return (
    <svg viewBox="0 0 48 56" preserveAspectRatio="none" className="h-14 w-8 shrink-0" aria-hidden>
      <path
        d="M2 28 C 16 18, 32 38, 46 28"
        fill="none"
        strokeWidth={dash ? 2 : 3}
        strokeLinecap="round"
        strokeDasharray={dash}
        className={tone}
      />
    </svg>
  );
}

/** A node is a button when clicking it can take you back to its gate. */
function NodeShell({ onClick, label, children }: { onClick?: () => void; label: string; children: ReactNode }) {
  const base = "group flex w-[5.25rem] flex-col items-center rounded-xl text-center";
  return onClick ? (
    <button
      type="button"
      onClick={onClick}
      title={`Go back to “${label}”`}
      aria-label={`Go back to ${label}`}
      className={`${base} cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-accent`}
    >
      {children}
    </button>
  ) : (
    <div className={base}>{children}</div>
  );
}

export function WorkflowCanvas({
  statuses,
  caption,
  sticky = false,
  step = null,
  onGoBack,
  disabled = false,
  demo = false,
}: {
  statuses: NodeStatus[];
  caption?: string;
  /** Pin under the site header so the current step stays visible while reviewing. */
  sticky?: boolean;
  /** Illustration on the landing page — must not read as the visitor's own progress. */
  demo?: boolean;
  /** Where the workflow currently is; finished nodes before it become clickable. */
  step?: WorkflowStep | null;
  onGoBack?: (gate: WorkflowStep) => void;
  disabled?: boolean;
}) {
  const current = statuses.findIndex((s) => s === "current" || s === "running");
  const done = statuses.filter((s) => s === "done").length;
  const progress = Math.round(((done + (current >= 0 ? 0.5 : 0)) / STEP_COUNT) * 100);
  const status =
    current >= 0
      ? `Step ${current + 1} of ${STEP_COUNT} · ${PIPELINE[current].label}`
      : done === STEP_COUNT
        ? "All steps done"
        : "Not started";
  const canGoBackTo = (gate: WorkflowStep | null) =>
    !!onGoBack && !disabled && gate !== null && step !== null && STEP_ORDER.indexOf(gate) < STEP_ORDER.indexOf(step);

  return (
    <figure
      className={`rounded-2xl border border-line [background-image:radial-gradient(var(--dot)_1.2px,transparent_1.2px)] [background-size:18px_18px] ${
        sticky ? "sticky top-14 z-20 bg-sunken/95 shadow-xl shadow-ink/10 backdrop-blur-md" : "bg-sunken/60"
      }`}
    >
      <div className="overflow-x-auto">
        <ol className="flex min-w-max items-start px-5 pb-4 pt-11 sm:px-6">
          {PIPELINE.map((node, i) => {
            const s = statuses[i];
            const Icon = node.icon;
            const live = s === "current" || s === "running";
            const back = s === "done" && canGoBackTo(node.gate);
            return (
              <li key={node.label} className="flex items-start" aria-current={live ? "step" : undefined}>
                {i > 0 && <Connector from={statuses[i - 1]} to={s} />}
                <NodeShell label={node.label} onClick={back ? () => onGoBack!(node.gate!) : undefined}>
                  <div
                    className={`relative flex size-14 items-center justify-center border-2 transition duration-300 ${boxClass(node, s)} ${
                      back ? "group-hover:-translate-y-1 group-hover:ring-2 ring-accent/40" : ""
                    }`}
                  >
                    {live && (
                      <span
                        className={`halo absolute inset-0 border-2 ${node.kind === "review" ? "rounded-full" : "rounded-2xl"} ${
                          s === "running" ? "border-blue" : "border-accent"
                        }`}
                      />
                    )}
                    {s === "done" ? (
                      <>
                        <CheckIcon className={`size-6 ${back ? "group-hover:hidden" : ""}`} />
                        {back && <span className="hidden text-xl font-bold leading-none group-hover:inline">↺</span>}
                      </>
                    ) : (
                      <Icon className="size-6" />
                    )}
                    {s === "running" && (
                      <span className="absolute -inset-1.5 rounded-[1.25rem] border-2 border-transparent border-t-blue motion-safe:animate-spin" />
                    )}
                    {node.paid && (
                      <span className="absolute -right-3 -top-2.5 rounded-full border border-amber/40 bg-amber-soft px-1.5 text-[10px] font-bold text-amber">
                        paid
                      </span>
                    )}
                    {live && (
                      <span
                        className={`absolute -top-9 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider shadow-md ${
                          s === "running" ? "bg-blue text-white" : "bg-accent text-on-accent"
                        }`}
                      >
                        {demo ? "example step" : s === "running" ? "working" : "you are here"}
                        <span
                          className={`absolute left-1/2 top-full -translate-x-1/2 border-x-[5px] border-t-[5px] border-x-transparent ${
                            s === "running" ? "border-t-blue" : "border-t-accent"
                          }`}
                        />
                      </span>
                    )}
                  </div>
                  <span className={`mt-2.5 text-xs font-semibold leading-tight ${live ? "text-ink" : s === "done" ? "text-ink/80" : "text-muted"}`}>
                    {node.label}
                  </span>
                  <span className={`font-mono text-[10px] uppercase leading-tight tracking-wider ${live || back ? "text-accent" : "text-muted"}`}>
                    {back ? (
                      <>
                        <span className="group-hover:hidden">{node.note}</span>
                        <span className="hidden group-hover:inline">go back here</span>
                      </>
                    ) : !demo && s === "current" ? (
                      node.kind === "review" ? "waiting for you" : "start here"
                    ) : s === "running" ? (
                      "working…"
                    ) : (
                      node.note
                    )}
                  </span>
                </NodeShell>
              </li>
            );
          })}
        </ol>
      </div>
      <figcaption className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-line px-5 py-2.5 text-xs sm:px-6">
        {demo && (
          <span className="rounded-full border border-amber/40 bg-amber-soft px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider text-amber">
            Example flow
          </span>
        )}
        <span className="font-semibold text-ink">{demo ? `A run would pause here · ${PIPELINE[current]?.label ?? ""}` : status}</span>
        <span
          className="h-1.5 w-32 overflow-hidden rounded-full bg-card ring-1 ring-line"
          role="progressbar"
          aria-valuenow={progress}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Workflow progress"
        >
          <span className="block h-full rounded-full bg-linear-to-r from-blue to-accent transition-all duration-700" style={{ width: `${progress}%` }} />
        </span>
        <span className="flex items-center gap-3 text-muted">
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-full bg-accent" /> you decide
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-[3px] bg-blue" /> runs itself
          </span>
          {onGoBack && <span className="hidden sm:inline">· click a finished step to go back</span>}
        </span>
        {caption && <span className="text-muted sm:ml-auto">{caption}</span>}
      </figcaption>
    </figure>
  );
}
