# LinkForge — Full-Stack URL Shortener with Analytics (Backend + Frontend + AWS)

> **Build spec, August 14, 2026.** A SWE-focused, full-stack project for backend/frontend
> internship applications. Covers a JVM backend language, React/TypeScript frontend, and a
> real AWS deployment in one defensible repo. Same honesty rules as the ML projects. Name is a
> placeholder (LinkForge, Shortr, Trimly...).

**Honesty gate:** nothing on the resume/site/application until Mayank can run it and explain,
unaided, the request flow, one design choice, and one failure mode. `resume_eligible: no`
until then. Building with Claude Code is fine — but you must understand what it produces,
especially concurrency and caching, because interviewers probe exactly there.

---

## 1. Why a URL shortener

It looks simple but exercises everything a backend/frontend intern is expected to know:

- **Backend:** REST API design, a database, caching, unique-ID generation, redirects, auth.
- **Frontend:** forms, API calls, auth state, a small dashboard with charts.
- **Cloud:** a public deployment with a real domain, a managed database, CI/CD.
- **Scale story:** reads (redirects) vastly outnumber writes — a natural reason to add a Redis
  cache and to talk about read/write ratios in an interview.

Small enough to finish; deep enough to defend.

## 2. What it does (one sentence)

Users shorten a long URL into a short code, visiting the short code redirects to the original,
and a dashboard shows click analytics (counts over time, referrers, basic geo).

## 3. Architecture

```
   React + TypeScript SPA  ───────────►  Spring Boot REST API
   (create link, dashboard)                 │
                                            ├── PostgreSQL (RDS)   links, clicks, users
                                            ├── Redis (ElastiCache) code → URL cache
                                            └── JWT auth
                                            │
   GET /{code}  ──► look up (cache→DB) ──► 301 redirect + async click log
                                            │
   Deployed on AWS (ECS Fargate or EC2), CI/CD via GitHub Actions
```

## 4. Tech stack (chosen to match backend SWE job descriptions)

- **Backend:** **Java + Spring Boot** (you already know Java; Spring Boot is on a huge share of
  backend JDs), Spring Web + Spring Data JPA, Spring Security (JWT).
  - *Alternative:* Go (net/http or Gin) if you'd rather learn Go — say the word and I'll reslot.
- **Database:** PostgreSQL (AWS RDS in prod, Docker locally), Flyway migrations.
- **Cache:** Redis (AWS ElastiCache in prod) — cache `code → long URL` for fast redirects.
- **Frontend:** React + **TypeScript** + Vite, a charts lib (Recharts), fetch/axios.
- **Cloud:** AWS — ECS Fargate *or* EC2 for the API, RDS Postgres, ElastiCache Redis, S3 +
  CloudFront for the static frontend, Route 53 for a domain (optional).
- **Ops:** Docker + docker-compose (local), GitHub Actions CI/CD, tests (JUnit + a little
  React Testing Library).

New skills: Spring Boot, JPA, Redis caching, React/TypeScript, and a real AWS deployment —
exactly the keywords backend/frontend intern postings list.

## 5. Build order (each step ships something demoable)

1. **Core API.** `POST /links` (create short code), `GET /{code}` (301 redirect). Store in
   Postgres. Decide the code scheme (base62 of an ID, or a random 7-char code with a
   uniqueness check) and be ready to explain the trade-off. *Deliverable:* shorten + redirect
   works locally.
2. **Click logging + analytics.** On each redirect, record a click (timestamp, referrer, IP→
   coarse geo) **without slowing the redirect** (async/queue). `GET /links/{code}/stats`.
   *Deliverable:* a stats endpoint returning real counts.
3. **Auth.** User accounts, JWT, so links belong to a user. *Deliverable:* per-user link list.
4. **Redis cache.** Cache `code → URL`; measure redirect latency with cache vs without.
   *Deliverable:* a before/after latency number — your interview story.
5. **Frontend.** React + TS: create-link form, a dashboard listing links with a clicks-over-
   time chart. *Deliverable:* a working UI against the API.
6. **Dockerize + CI.** docker-compose for local (api + postgres + redis), GitHub Actions
   running tests on every push. *Deliverable:* green CI.
7. **Deploy to AWS.** API on ECS Fargate/EC2, RDS Postgres, ElastiCache Redis, frontend on
   S3+CloudFront. *Deliverable:* a **live URL** you can put on the resume.
8. **Harden (optional but strong).** Rate limiting, input validation (reject non-URLs / SSRF),
   a health check, and basic load test numbers (requests/sec).

## 6. Honest metrics to claim (fill after building)

- Redirect latency with vs without the Redis cache (p50/p95).
- Requests/sec under a simple load test (e.g. with `hey` or `k6`).
- Test count (JUnit + frontend) and green CI.
- Live deployment URL + the AWS services used.

## 7. Resume bullets — TEMPLATE (fill after building)

> Built **LinkForge**, a full-stack URL shortener: **Spring Boot** REST API with **PostgreSQL**
> and **JWT** auth, a **React/TypeScript** dashboard, deployed on **AWS** (ECS/RDS/ElastiCache,
> S3+CloudFront) with GitHub Actions CI/CD.

> Cached `code → URL` lookups in **Redis**, cutting redirect p95 latency from **[x]ms to [y]ms**;
> logged clicks asynchronously so analytics never blocked the redirect path; **[T]** tests
> green in CI. Live at **[url]**.

## 8. Guardrails specific to this project

- **Validate URLs** (scheme allowlist) to avoid open-redirect / SSRF — a real security bullet
  and a common interview follow-up.
- Keep the redirect path fast: click logging must be **async**, never inline blocking.
- Watch AWS cost: use the free tier, and **tear down** or set a budget alarm so a live demo
  doesn't cost you. A stopped/低-traffic deployment is fine for the resume.
- Be able to explain: the code-generation scheme, the read/write ratio, and why the cache
  helps. If you can't, the gate isn't closed.

## 9. First session checklist

1. Spin up `docker-compose` with Spring Boot + Postgres + Redis locally.
2. Implement `POST /links` and `GET /{code}` end to end — prove shorten + redirect.
3. Then follow build order 3→7. Ask me (or Claude Code) to scaffold any step; I can write the
   Spring controllers, the React dashboard, or the AWS deploy config.

---

### Companion project (for the C/C++ + systems half)

I'll spec this separately when you're ready — my recommendation for backend SWE recruiting is
a **multithreaded HTTP server in C++** (sockets + a thread pool, benchmarked requests/sec). It
pairs perfectly with LinkForge: LinkForge shows you can *use* a web stack, the C++ server shows
you understand what's *underneath* one. Just say "spec the C++ server" and I'll write it.
