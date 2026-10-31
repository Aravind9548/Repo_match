# Security probe

RepoMatch has no accounts. Whoever holds a tracker key owns that key's data, and every query is supposed to be
constrained by the key's SHA-256. That single rule is the whole security model — so it is the thing worth testing,
and a generic scanner has to guess at it.

`probe.py` doesn't guess. It creates two disposable accounts, treats one as an attacker, and tries to reach the
other's data by every route the API offers: reading, enumerating ids, editing, deleting, forging a signed email
link, and calling the scheduled run without its secret. It deletes both accounts when it finishes.

```bash
python security/probe.py                        # the deployed backend
python security/probe.py http://localhost:8081  # a local one
```

No model, no subscription, no dependencies beyond the standard library (`certifi` is used for its CA bundle when
installed). Exit code 1 means something failed.

## What a failure means

| Check | Why it matters |
|---|---|
| Cross-account read | One person reading another's applications, resumes or drafted answers. The worst thing this app could do. |
| Id enumeration | Numeric ids are guessable; only the owner's own may come back. |
| Cross-account edit/delete | Worse than reading: it destroys someone's work. |
| Forged signed link | Alert emails carry `s.<hash>.<sig>`. A forgeable signature is a master key. |
| Cron secret | The scheduled run spends Gemini calls. |
| Unknown origin | A hostile page must not be able to read the API with a visitor's key. |

## Fixed by this probe

`/api/tailor/applications/{id}/runs` used to answer **200 with an empty list** for an application you don't own,
where every other endpoint answers 404. No content leaked, but an empty list still confirms the id exists. It now
checks ownership first and answers 404 like the rest.

## What this does not cover

Dependency vulnerabilities, the AI service (private, reachable only by the backend), the frontend, and anything
needing a browser. For those, run an actual scanner — `strix-agent` is open source and works with Vertex AI
credits, so it costs tokens rather than a subscription.
