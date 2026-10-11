"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { AlertsConsole } from "@/components/jobs/AlertsConsole";
import { JobFeed } from "@/components/jobs/JobFeed";

/**
 * Finding a role: browsing, and the searches worth repeating.
 *
 * These were two destinations, which made saving a search look like a different activity from running one. It isn't:
 * you search, and when a search is worth repeating you save it. The tab lives in the URL so a link still points at
 * the right one.
 */
const TABS = [
  { id: "browse", label: "Browse", hint: "21,000 open roles, refreshed every six hours. Searching the open web is a button inside the results, using the same query." },
  { id: "saved", label: "Saved searches", hint: "A search worth repeating. New matches reach you here, and by email if you add one." },
] as const;

type TabId = (typeof TABS)[number]["id"];

export function FindTabs() {
  const router = useRouter();
  const params = useSearchParams();
  const raw = params.get("tab");
  const active: TabId = TABS.some((t) => t.id === raw) ? (raw as TabId) : "browse";

  function go(tab: TabId) {
    const next = new URLSearchParams(params.toString());
    if (tab === "browse") next.delete("tab");
    else next.set("tab", tab);
    router.replace(next.toString() ? `/jobs?${next}` : "/jobs", { scroll: false });
  }

  return (
    <div>
      <div role="tablist" aria-label="How to find a role" className="flex gap-px border-b border-line">
        {TABS.map((tab) => {
          const on = tab.id === active;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={on}
              type="button"
              onClick={() => go(tab.id)}
              className={`relative px-5 py-3 text-left font-mono text-[11px] uppercase tracking-[0.16em] transition ${
                on ? "text-ink" : "text-muted hover:text-ink"
              }`}
            >
              {tab.label}
              <span
                className={`absolute inset-x-0 -bottom-px h-0.5 origin-left bg-accent transition-transform duration-300 ${
                  on ? "scale-x-100" : "scale-x-0"
                }`}
              />
            </button>
          );
        })}
      </div>

      <p className="mt-4 text-sm text-muted">{TABS.find((t) => t.id === active)?.hint}</p>

      <div key={active} className="animate-rise mt-8">
        {active === "browse" && <JobFeed />}
        {active === "saved" && <AlertsConsole />}
      </div>
    </div>
  );
}
