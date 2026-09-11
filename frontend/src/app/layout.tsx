import type { Metadata } from "next";
import { Caveat, Fraunces, Geist, Geist_Mono } from "next/font/google";
import Link from "next/link";
import { FloatingShapes } from "@/components/Shapes3D";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
// Soft, slightly wonky serif for headings and a handwriting face for margin notes.
const fraunces = Fraunces({ variable: "--font-fraunces", subsets: ["latin"], axes: ["SOFT", "opsz"] });
const caveat = Caveat({ variable: "--font-caveat", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "RepoMatch",
  description: "Paste a job description, get back which of your GitHub projects to put on the resume — ranked, with reasons.",
};

function Logo() {
  return (
    <svg viewBox="0 0 32 32" className="size-8" aria-hidden>
      <rect x="4" y="4" width="26" height="26" rx="8" className="fill-blue" />
      <rect x="2" y="2" width="26" height="26" rx="8" className="fill-ink" />
      <circle cx="10.5" cy="11" r="3" className="fill-blue" />
      <circle cx="19.5" cy="19" r="3" className="fill-accent" />
      <path d="M12.5 13c3 1 3.5 4 5.5 5" fill="none" className="stroke-paper" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} ${fraunces.variable} ${caveat.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <FloatingShapes />
        <header className="sticky top-0 z-30 h-14 border-b border-line bg-paper/85 backdrop-blur">
          <nav className="mx-auto flex h-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
            <Link href="/" className="flex items-center gap-2.5">
              <Logo />
              <span className="font-display text-xl font-semibold tracking-tight">RepoMatch</span>
            </Link>
            <div className="flex items-center gap-4 text-sm font-medium sm:gap-6">
              <Link href="/history" className="text-muted hover:text-ink">
                History
              </Link>
              <Link
                href="/analyze"
                className="rounded-full bg-accent px-4 py-1.5 font-semibold text-on-accent shadow-glow-accent transition hover:-translate-y-0.5"
              >
                New match
              </Link>
            </div>
          </nav>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6 sm:py-14">{children}</main>
        <footer className="border-t border-line py-8 text-center">
          <p className="font-hand text-xl">made with care (and a lot of READMEs) by Aravind</p>
          <p className="mt-1 text-xs text-muted">Spring Boot · FastAPI · Gemini · PostgreSQL · Next.js</p>
        </footer>
      </body>
    </html>
  );
}
