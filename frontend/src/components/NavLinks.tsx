"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export const PRODUCT_NAV = [
  // Four stops, in the order a person actually moves through them.
  { href: "/jobs", label: "Find", match: ["/jobs"] },
  { href: "/tracker", label: "Applications", match: ["/tracker", "/tailor"] },
  { href: "/repos", label: "My repos", match: ["/repos", "/analyze", "/ask", "/workflow", "/results", "/history"] },
  { href: "/apply/profile", label: "My details", match: ["/apply"] },
];

/** Top navigation with a sliding underline under the section you're in. */
export function NavLinks() {
  const pathname = usePathname();
  return (
    <div className="hidden items-center gap-1 lg:flex">
      {PRODUCT_NAV.map((item) => {
        const active = item.match.some((m) => pathname === m || pathname.startsWith(`${m}/`));
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`relative px-4 py-2 font-mono text-[11px] uppercase tracking-[0.2em] transition ${
              active ? "text-ink" : "text-muted hover:text-ink"
            }`}
          >
            {item.label}
            <span
              className={`absolute inset-x-4 -bottom-[13px] h-0.5 origin-left bg-accent transition-transform duration-300 ${
                active ? "scale-x-100" : "scale-x-0"
              }`}
            />
          </Link>
        );
      })}
    </div>
  );
}

/** Horizontal scroller for phones, where the top links are hidden. */
export function MobileNav() {
  const pathname = usePathname();
  return (
    <div className="flex gap-1 overflow-x-auto border-t border-line px-3 lg:hidden">
      {PRODUCT_NAV.map((item) => {
        const active = item.match.some((m) => pathname === m || pathname.startsWith(`${m}/`));
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`shrink-0 border-b-2 px-3 py-2.5 font-mono text-[11px] uppercase tracking-[0.18em] ${
              active ? "border-accent text-ink" : "border-transparent text-muted"
            }`}
          >
            {item.label}
          </Link>
        );
      })}
    </div>
  );
}
