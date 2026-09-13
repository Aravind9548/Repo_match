import type { Metadata } from "next";
import { Archivo, JetBrains_Mono } from "next/font/google";
import Link from "next/link";
import { MobileNav, NavLinks } from "@/components/NavLinks";
import { ScrollProgress } from "@/components/Reveal";
import "./globals.css";

const archivo = Archivo({ variable: "--font-archivo", subsets: ["latin"], weight: ["400", "500", "600", "700", "800"] });
const jetbrains = JetBrains_Mono({ variable: "--font-jetbrains", subsets: ["latin"], weight: ["400", "500"] });

export const metadata: Metadata = {
  title: "RepoMatch",
  description: "Paste a job description, get back which of your GitHub projects to put on the resume — ranked, with citations.",
};

const FOOTER_NAV = [
  { href: "/#how-it-works", label: "How it works" },
  { href: "/analyze", label: "Match" },
  { href: "/ask", label: "Genie" },
  { href: "/jobs", label: "Jobs" },
  { href: "/alerts", label: "Alerts" },
  { href: "/tracker", label: "Tracker" },
];

function Logo({ size = 30 }: { size?: number }) {
  return (
    <span className="flex items-center justify-center bg-accent" style={{ width: size, height: size }} aria-hidden>
      <svg width={size * 0.6} height={size * 0.6} viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 8h13l-3-3M20 16H7l3 3" />
      </svg>
    </span>
  );
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${archivo.variable} ${jetbrains.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <ScrollProgress />
        <header className="sticky top-0 z-30 border-b border-line bg-paper/90 backdrop-blur">
          <nav className="mx-auto flex h-16 max-w-[1400px] items-center justify-between gap-6 px-5 sm:px-10">
            <Link href="/" className="flex items-center gap-3">
              <Logo />
              <span className="font-display text-lg font-extrabold uppercase tracking-tight">
                Repo<span className="text-accent">Match</span>
              </span>
            </Link>

            <NavLinks />

            <div className="flex items-center gap-3">
              <Link href="/history" className="label hidden hover:text-ink sm:block">
                History
              </Link>
              <Link href="/analyze" className="btn btn-primary !px-5 !py-2.5">
                Try free
              </Link>
            </div>
          </nav>
          <MobileNav />
        </header>

        <main className="flex-1">{children}</main>

        <footer className="border-t border-line">
          <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-6 px-5 py-9 sm:px-10">
            <Link href="/" className="flex items-center gap-3">
              <Logo size={26} />
              <span className="font-display text-base font-extrabold uppercase tracking-tight">
                Repo<span className="text-accent">Match</span>
              </span>
            </Link>
            <div className="flex flex-wrap items-center gap-7">
              {FOOTER_NAV.map((item) => (
                <Link key={item.href} href={item.href} className="label hover:text-ink">
                  {item.label}
                </Link>
              ))}
            </div>
            <span className="label">© 2026 RepoMatch — built by Aravind</span>
          </div>
        </footer>
      </body>
    </html>
  );
}
