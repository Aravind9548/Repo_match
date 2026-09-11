# RepoMatch

Paste a job description, get back which of your GitHub projects to put on the resume — ranked, with reasons grounded in the repos' own content.

```
[Next.js + TypeScript]  ──REST──▶  [Spring Boot API]  ──▶ GitHub REST API
                                        │   │
                                        │   └──▶ PostgreSQL (repos, profiles, embeddings, analyses)
                                        ▼
                               [FastAPI AI service]  ──▶ Gemini (embeddings + LLM)
```

**How matching works**

1. **Sync** — Spring Boot pulls every public repo (README, languages, topics) and builds a text *profile* per repo. Repos are cached in Postgres; only repos whose `pushed_at` changed are re-downloaded.
2. **Retrieve** — the AI service embeds each profile (`RETRIEVAL_DOCUMENT`) and the JD (`RETRIEVAL_QUERY`) with `gemini-embedding-001` and ranks by cosine similarity. Embeddings are cached per profile hash, so unchanged repos are never re-embedded.
3. **Re-rank + explain (RAG)** — at least 12 candidates go to Gemini (repos with a real README first, since near-empty repos look deceptively similar to keyword-style JDs). Gemini scores fit 0–100 and writes a 1–2 sentence reason citing only the repo's content. Every evidence snippet it returns is verified to exist in the repo before it is shown.
4. **Score** — `0.2 × relative similarity + 0.8 × LLM fit`. If the LLM is unavailable, results fall back to similarity only.

## Repo layout

| Path | What |
|---|---|
| `backend/` | Spring Boot 3.5 / Java 21 — REST API, GitHub client, JPA + Flyway, orchestration |
| `ai-service/` | FastAPI — `/rank` (embed + cosine) and `/explain` (grounded LLM reasons) |
| `frontend/` | Next.js 16 / React 19 / Tailwind v4 — landing, analyze form, results pages |
| `docker-compose.yml` | Postgres (pgvector image) + both services |

## Running locally

Prereqs: Java 21, Maven, Python 3.12, Docker.

```bash
cp .env.example .env          # fill in GOOGLE_CLOUD_PROJECT (+ optional GITHUB_TOKEN)
docker compose -p repomatch up -d postgres     # Postgres on localhost:5434

# AI service (port 8000)
cd ai-service
python -m venv .venv && .venv/Scripts/pip install -r requirements-dev.txt   # or .venv/bin on macOS/Linux
.venv/Scripts/python -m uvicorn app.main:app --port 8000

# Backend (port 8081)
cd backend
mvn spring-boot:run

# Frontend (port 3000) — expects the backend on localhost:8081
cd frontend
npm install
npm run dev
```

Frontend env (optional, e.g. in `frontend/.env.local`): `NEXT_PUBLIC_API_BASE_URL` (browser → API, default `http://localhost:8081`) and `API_BASE_URL` (server components → API, defaults to the public URL). The backend's allowed CORS origins are set with `CORS_ALLOWED_ORIGINS`.

Pages: `/` landing · `/analyze` (username + JD form) · `/results/{id}` (shareable, re-openable result).

Or everything in containers: `docker compose -p repomatch up --build` (set `GCLOUD_ADC_FILE` in `.env` to a service-account key for Vertex).

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/repos/{username}?refresh=false` | Sync + list a user's repos |
| `POST` | `/api/analyze` | `{username, jobDescription, topK?, includeForks?, refresh?}` → ranked matches with reasons |
| `GET` | `/api/analyses/{id}` | Re-open a past analysis |
| `GET` | `/api/analyses?username=` | Recent analyses for a user |

Errors are RFC 7807 problem documents: `404` unknown user, `422` no repos, `429` GitHub rate limit (with `Retry-After`), `503` AI service down.

## Tests

```bash
cd backend && mvn test
cd ai-service && .venv/Scripts/python -m pytest
```
