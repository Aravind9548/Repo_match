# Information architecture

What each page is for, and why things sit where they do. Change this file when you change the structure — the
reasoning is the part that gets lost.

## Audience and thesis

**Audience.** One person looking for an internship or a new-grad role, usually a student, usually on a phone or a
laptop between other things. They scan; they do not read. They know their own projects well and the hiring process
badly.

**Thesis, one sentence.** RepoMatch takes you from "which of these jobs is worth my evening?" to "I applied, and I
know what happens next" — with your own repositories as the evidence, and you making every decision.

## The spine

Five stages, in the order a person moves through them. Every pipeline page belongs to exactly one, and the flow
strip under the navigation lights the one you are in.

| Stage | Where it happens | The job of that page |
|---|---|---|
| **Find** | `/jobs` | Put the open roles in front of you, and let a search worth repeating be saved. |
| **Decide** | `/jobs/[id]` | Is this worth an evening? Approve it or skip it. |
| **Prepare** | `/tailor/[id]` | Make the resume this job's, read the PDF back like a parser, draft the answers. |
| **Apply** | `/tracker/[id]` | Everything needed to fill the form, and the record that you sent it. |
| **Track** | `/tracker` | What came back, what is due, what has gone quiet. |

Two pages sit beside the spine rather than in it, because they are set up once and then used by every stage:

- `/apply/profile` — **My details.** The answers every form asks. Each apply kit starts here.
- `/repos` — **My repos.** Matching a job description, asking a repository how it works, past matches. This is where
  the evidence tailoring quotes comes from.

## Navigation

Four items, in journey order: **Find · Applications · My repos · My details**.

It was six (Match, Genie, Jobs, Alerts, Tracker, My details), which mixed two different products in one bar and gave
a saved search the same weight as the whole application pipeline. Worse, tailoring — the most valuable thing the
product does — had no navigation presence at all and was reachable only from inside an application.

Decisions behind the four:

- **Alerts folded into Find as a tab.** Saving a search is not a different activity from running one. The old
  `/alerts` URL redirects to `/jobs?tab=saved`, so links in sent emails still work.
- **Match and Genie folded into My repos.** They are one job — understanding your own code — and they feed the
  evidence used in tailoring. `/analyze`, `/ask` and `/history` are unchanged; `/repos` is the way in.
- **Tailoring lives under Applications**, because that is what it is: one application's resume. The nav highlights
  Applications while you are in `/tailor/*`.
- **Web search is not a tab.** It runs on the feed's own query, from inside the results. A tab would mean a second
  search box asking the same question.

## Rules this structure keeps

- **Every page names its next step.** A page that cannot say where it sends you is a dead end. Find → your
  applications; Applications → your details; My details → back to your applications; My repos → find a role.
- **One full explanation, one home.** A feature is explained in full on exactly one page; anywhere else it is a
  teaser with a link. The one shared component rendered on two pages is the apply kit (on the tailoring page, where
  you have just built the PDF, and on the application, which is its durable home). It is the same component, so the
  two cannot drift apart.
- **Stage chips, not step numbers.** The old chips numbered a journey nobody takes — "Step 4 · fill it once" for the
  thing you do first. Each page now names its stage.
