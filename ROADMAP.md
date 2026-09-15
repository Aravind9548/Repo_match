# RepoMatch roadmap

RepoMatch helps a student go from "which of my projects fit this job?" to "I applied, and I know what's next".
Every paid AI step waits for a human click; nothing is ever submitted on the user's behalf.

## Shipped

| Area | What it does |
|---|---|
| **Match** | Paste a JD + GitHub username → repos ranked by embeddings, re-judged by Gemini with verified evidence. Three approval gates with go-back. |
| **Genie** | Index one repo's source, ask questions answered only from its code with file/line citations. Draft a README from the code. |
| **Jobs feed** | ~13k open roles from 3 GitHub lists + 47 company boards (Greenhouse, Lever, Ashby, SmartRecruiters), refreshed every 6h. Query understanding (kind, country, city spellings, synonyms), whole-word matching, facets. |
| **Web search** | On demand: Gemini grounded in Google Search, every link opened before it's shown. |
| **Alerts** | Saved searches using the same matcher as the feed; in-app inbox, optional email digests with signed links. |
| **Tracker** | Approve → choose resume → submit yourself → log what comes back. Deadlines reminded 2 days ahead, quiet applications nudged after 2 weeks. |
| **Resume tailoring** | Upload PDF/DOCX/.tex/text (or reuse a saved one) → check how it was read and the job description (fetched from the posting's source) → accept or reject each proposed edit (edits must match resume text; wording that adds a tool or copies the job ad is flagged and off by default; gaps listed, never inserted) → LaTeX (your own .tex or a clean template) compiled to PDF with Tectonic. |
| **India coverage** | 94 sources: Workday career sites filtered to India, India internship lists on GitHub, 88 company boards, and a daily capped Google discovery that keeps only postings whose page names the company and title. |

## Now — India coverage (this iteration)

Goal: most of the internships a student in India would find by searching, without them searching.

1. **India job lists on GitHub** — `akshat0011/engineering-internship-india`, `FresherFlow/India-Jobs-Internships`,
   `Rohan-droid7341/internship-engine-india`. Parser learns their formats: unnamed apply columns, skill tags inside
   the role cell, and real dates ("12 Sept 2026", "14 Sep 26", "Sep 11, 2026").
2. **Workday career sites** — the system most large employers use (NVIDIA, Micron, HPE, Salesforce, Mastercard,
   Citi, Adobe, Intel, Deutsche Bank…), queried through Workday's public job search for India roles.
3. **More company boards** — Tekion, Atlan, Glance, Zeta Global, Coursera, Toast, Observe.AI, ixigo, Sandisk,
   Western Digital, Grab, Unacademy, Continental.
4. **Daily Google discovery** — a fixed set of grounded Google searches ("summer internship India 2027",
   "winter internship India", per-city variants) runs once a day. Results whose links open are added to the feed
   as their own source and expire if Google stops finding them. Hard-capped queries per day.
5. **Season filter** — summer / winter / off-cycle, read from the title ("Summer 2027", "Winter Intern", "6-month").

## Next

- **Fit score per posting** — embed postings once, rank the feed by similarity to the user's own repos.
- **Apply kit** — per posting: resume PDF, answers to common questions drafted from real repos, the link.
- **Email on** — user adds a Gmail App Password; alerts and reminders go out.
- **Deadline extraction** — read "apply by" dates from posting pages where they exist.

## Later

- GitHub OAuth instead of tracker keys, so data follows the account.
- Inbox sync (Gmail, opt-in) to detect assessment/interview emails and update the tracker automatically.
- Multi-JD comparison and skills-gap analysis across saved postings.
- Recruiter mode: paste a JD, see which candidates' repos fit.

## Won't do

- **Auto-submitting applications.** Every portal differs, most forbid bots, and one wrong auto-filled answer can
  cost a real chance. The product stops at "ready to submit".
- Scraping sites that forbid it (LinkedIn, Naukri, Internshala). Their postings reach the feed only through
  Google results the user can open themselves.
