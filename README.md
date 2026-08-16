# LinkForge

A URL shortener with click analytics: a Spring Boot API over PostgreSQL and Redis with JWT auth,
and a React/TypeScript dashboard. The application is live on a free Render web service backed by
Neon PostgreSQL; the larger AWS architecture remains an undeployed design in
[`PROJECT_SPEC.md`](PROJECT_SPEC.md).

> **`resume_eligible: no`.** Nothing here goes on a resume, the portfolio site, or an application
> until Mayank can run it and explain, unaided, the request flow, one design choice, and one
> failure mode.

## Status against the build order

| # | Step | State |
|---|------|-------|
| 1 | Core API — `POST /api/links`, `GET /{code}` redirect, Postgres | **Done** |
| 2 | Click logging + analytics (`GET /api/links/{code}/stats`) | **Done**, minus geo |
| 3 | Auth (users, JWT, per-user links) | **Done** |
| 4 | Redis cache for `code -> URL` | **Done**; latency measurement inconclusive |
| 5 | React + TypeScript frontend | **Done** |
| 6 | Dockerize + CI | **Done**; GitHub Actions passing |
| 7 | AWS deployment | **Written and validated, never deployed** |
| 8 | Hardening (rate limits, SSRF, load numbers) | Scheme allowlist only |

## Live deployment

**LinkForge is live at <https://linkforge-cxfk.onrender.com>.** Render builds the root Dockerfile,
which packages the React dashboard inside the Spring Boot application so the UI and API share one
origin. Neon provides the persistent PostgreSQL database. The hosted free tier runs without Redis;
local Docker Compose retains Redis for cache behavior and benchmarking.

The Render service sleeps after 15 minutes without traffic, so the first request after an idle
period can take about a minute. The deployment was smoke-tested through registration, URL creation,
redirect, and database persistence on August 15, 2026.

## The cache result: inconclusive, and that is the honest answer

Seven paired benchmark runs **could not measure a reliable effect from the Redis cache on this
machine.** Run-to-run variance is larger than the difference between the two configurations, and
the sign is not even stable — two runs of the identical command gave the cache +10% throughput and
then −63%.

Large improvements did show up (up to +2171% throughput, p99 225 ms → 6 ms), but only in runs where
the laptop was simultaneously compiling and running other benchmarks. With the host quiet, the
uncached arm does 11,000–17,000 req/s at a p95 under 5 ms and the cache is neutral to slightly
negative. Those early figures measured how the two configurations degrade under **host CPU
starvation**, not a property of the cache, and they are not quotable.

What is established: the cache demonstrably serves the redirects (hundreds of thousands of hits,
~1 miss per run, from server-side counters), and a warm indexed single-row Postgres lookup over a
pooled connection is already fast enough here that a Redis round trip does not reliably beat it.
The real argument for caching a redirect is architectural — it removes the redirect's dependence on
the connection pool — and **no measurement here demonstrates that**; showing it needs a test that
constrains the database on purpose. Every run, the reasoning, and what a trustworthy measurement
would require: [`bench/RESULTS.md`](bench/RESULTS.md).

## Run it

```bash
docker compose up --build
```

**The app is at <http://localhost:3000>.** Postgres on 5432, Redis on 6379, the API on 8080.
Flyway creates the schema on first boot.

nginx serves the built frontend and proxies `/api` to the API container, so the browser only ever
sees one origin — which is why there is no CORS configuration anywhere in this project, and so no
chance of shipping a permissive one. The Vite dev server (`npm --prefix web run dev`, port 5173)
proxies the same way.

### The API on its own

Register (which logs you in — there is no reason to make a client immediately post again):

```bash
curl -s -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"email":"alice@example.com","password":"correct horse battery"}'
```

Shorten a URL with the token from that response:

```bash
curl -s -X POST http://localhost:8080/api/links -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"url":"https://example.com/some/long/path"}'
```

```json
{
  "code": "4C92",
  "shortUrl": "http://localhost:8080/4C92",
  "longUrl": "https://example.com/some/long/path?utm=demo",
  "createdAt": "2026-08-15T02:30:51.822152462Z"
}
```

Follow it (`-i` shows the redirect instead of following it):

```bash
curl -i http://localhost:8080/4C92
```

```
HTTP/1.1 302
Location: https://example.com/some/long/path?utm=demo
Cache-Control: no-store
```

Then read the analytics (owner only):

```bash
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/links/4C96/stats?days=7"
```

```json
{
  "code": "4C96",
  "longUrl": "https://example.com/analytics-demo",
  "totalClicks": 6,
  "uniqueVisitors": 3,
  "firstClickAt": "2026-08-15T02:56:40.164826Z",
  "lastClickAt": "2026-08-15T02:56:40.284996Z",
  "windowDays": 7,
  "clicksByDay": [
    {"day": "2026-08-09", "clicks": 0},
    "... one entry per day, zero-filled ...",
    {"day": "2026-08-15", "clicks": 6}
  ],
  "topReferrers": [
    {"referrer": "news.ycombinator.com", "clicks": 2},
    {"referrer": "reddit.com", "clicks": 2},
    {"referrer": "direct", "clicks": 2}
  ]
}
```

That output is a real run: six visits, of which two came from `www.reddit.com` and `reddit.com`
(collapsed into one row), two were direct, and the six client addresses fell into three /24s.

Shed and failed clicks are counted, since a number that is silently too low is worse than an
error:

```bash
curl -s http://localhost:8080/actuator/metrics/linkforge.clicks.rejected
```

| Metric | Meaning |
|---|---|
| `linkforge.clicks.recorded` | Clicks written to the database. |
| `linkforge.clicks.rejected` | Clicks dropped because the write queue was full. |
| `linkforge.clicks.failed` | Clicks that reached a worker but could not be written. |
| `linkforge.cache.hits` | Redirect lookups answered by Redis, including cached absences. |
| `linkforge.cache.misses` | Lookups that had to read Postgres. |
| `linkforge.cache.errors` | Redis operations that failed and fell through to Postgres. |

## AWS

[`infra/`](infra/) holds OpenTofu/Terraform for the whole stack — VPC, ALB, ECS Fargate, RDS
Postgres, ElastiCache Redis, S3 + CloudFront, Secrets Manager, a GitHub OIDC deploy role, and a
budget alarm — plus [`infra/README.md`](infra/README.md), which is the runbook.

> **Nothing in this AWS directory has been deployed.** The live application uses Render and Neon;
> no AWS account was involved. The config is written, `tofu fmt` clean, schema-validated against
> the AWS provider, and graph-checked for
> cycles. That catches wrong attribute names and missing arguments; it does not catch what only a
> real `apply` will. Until it runs, **"deployed on AWS" cannot go on a resume** — the services are
> a design, not an achievement.

Two things worth knowing before spending anything:

- **It costs roughly $25–55/month**, mostly ALB and Fargate, and less with free-tier credits on
  RDS and ElastiCache. There is deliberately **no NAT gateway** — that alone would have been about
  $32/month — so tasks run in public subnets with public IPs and a security group that admits only
  the load balancer. The budget alarm exists because the realistic failure mode here is a
  forgotten stack, not an outage.
- **CloudFront's default behaviour is the API, not the bucket.** The root path belongs to short
  codes, and a short code has no prefix to write a path pattern for, so only `/index.html`,
  `/assets/*` and `/favicon.ico` are carved out to S3. One hostname also means no CORS, exactly as
  nginx arranges locally.

[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) is manual-trigger only. It
authenticates with **OIDC, so there is no AWS access key in the repository** — a long-lived key in
CI never expires and leaks through logs — builds and pushes the image tagged with the commit SHA
(never `latest`, so a rollback can name what it is rolling back to), rolls the ECS service, waits
for stability, publishes the frontend, invalidates the CDN, and smoke-tests the health endpoint.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request:

| Job | What it proves |
|---|---|
| `api` | All 97 backend tests, against real Postgres and Redis via Testcontainers. |
| `web` | `npm ci`, typecheck, 18 frontend tests, production build. |
| `stack` | `docker compose up` boots, then register → shorten → redirect → stats round-trips, and nginx serves the SPA and proxies `/api`. |

`stack` exists because the other two cannot fail on the things that actually break a deployment. A
wrong environment variable, a bad nginx proxy rule, or a container that cannot reach Postgres
passes every unit and integration test and still ships broken. It also covers "do the Dockerfiles
build", so there is no separate image job.

The workflow first passed on GitHub on August 15, 2026. The repository is standalone, so workflow
paths are rooted exactly where the local commands expect them.

### The Docker API version, again

CI asks the runner's daemon which API version it speaks and passes it to Maven, rather than
trusting the `1.44` default in `pom.xml`. Every daemon advertises a `MinAPIVersion` and answers a
bare HTTP 400 below it, which Testcontainers reports only as "Could not find a valid Docker
environment" — so a runner-image upgrade could otherwise break the build with an error message
that points nowhere near the cause.

## Benchmark

```bash
bench/run.sh 30s 20            # realistic: click logging on
CLICKS=false bench/run.sh 30s 20   # isolates the redirect read path
```

Each arm starts from an empty database and Redis, rebuilds, warms up, then runs k6 in a container
on the compose network. Results and interpretation: [`bench/RESULTS.md`](bench/RESULTS.md).

## Tests

```bash
cd api && ./mvnw test
```

```bash
npm --prefix web run test
```

**115 tests, all green as of August 14, 2026.** 97 on the backend: 52 unit tests covering the code
scheme, the URL allowlist, request-metadata extraction, the click recorder's failure paths and the
cache's behaviour when Redis misbehaves; and 45 integration tests that boot the app against a real
Postgres and a real Redis via Testcontainers, including 12 on authentication and 6 on ownership
isolation. **A running Docker daemon is required.** Plus 18 frontend tests on the API client's
error handling and the components that have logic worth breaking. No local Maven install is needed — `./mvnw` fetches its own — but
`JAVA_HOME` must point at a JDK 21, which is what this was built and run against:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Create an account. `201` with a token, `409` if the address is taken, `400` if invalid. |
| `POST` | `/api/auth/login` | — | `200` with a token, `401` otherwise. |
| `POST` | `/api/links` | Bearer | Create a short link owned by the caller. `201`, or `400` if the URL is rejected. |
| `GET` | `/api/links?page=0&size=20` | Bearer | The caller's links, newest first. `size` clamped to 1–100. |
| `GET` | `/api/links/{code}` | Bearer | Link metadata. `404` if unknown **or owned by someone else**. |
| `GET` | `/api/links/{code}/stats?days=30` | Bearer | Click analytics, owner only. `days` clamped to 1–365. |
| `GET` | `/{code}` | — | The redirect. Public, always. `302`, or `404` if unknown. |
| `GET` | `/actuator/health` | — | Open, so container and load-balancer probes work. |
| `GET` | `/actuator/metrics` | Bearer | The counters below. |

## Design notes (the interview material)

**Short codes are base62 of a Postgres sequence.** `nextval('link_code_seq')` is unique by
construction, so the create path has no collision check and no retry loop, and concurrent creates
cannot collide — `concurrentCreatesGetDistinctCodes` asserts exactly that. The cost is that codes
are sequential: they are guessable, and they leak roughly how many links exist. The alternative,
a random 7-character code, hides both but needs a uniqueness check and a retry on collision. The
sequence is the right trade for links that are public by design; it would be the wrong one for
private links. The sequence starts at 1,000,000 so the first code is four characters rather than
one.

**The redirect is `302`, not the `301` in the spec's diagram.** A 301 is cached by the browser
indefinitely: the second visit to a link never reaches the service. That is faster, but it makes
click counts undercount badly and makes a link's target impossible to change. Since analytics is
half the point of this project, every visit has to be observable, so the redirect is `302` with
`Cache-Control: no-store`.

**The management API lives under `/api`, not at `/links`.** The root path belongs to short codes.
If creation were at `/links`, the day the sequence produced the code `links` that link would be
permanently unreachable — a bug that appears years after the code is written. Reserving a prefix
removes the whole class.

**The chart draws straight lines between days, not a smooth curve.** Recharts' default `monotone`
interpolation bows the line above the days it passes between, which on a zero-filled daily series
draws traffic on days that had none — the first render of this chart showed a gentle ramp across
three days that were all zero. Daily totals are discrete, so the line has to pass through them.

**Chart marks use a colour that was checked, not chosen.** `#e35c12` sits inside the OKLCH
lightness band for a dark surface and clears the chroma floor and contrast checks; the brighter
ember used for buttons does not. Bars in the referrer list are all one colour on purpose: they are
one series measured across categories, and colouring by rank would make the colours change meaning
the moment the ranking did. Length is the encoding.

**The token is in `localStorage`, which is the usual choice and the usual criticism.** Anything
that can run script on this origin can read it, where an HttpOnly cookie could not. The honest
trade is that a cookie would need the CSRF protection the API deliberately does not implement.
Both halves of that trade are written down rather than one of them being quietly picked.

**Tokens are validated by the framework, not by hand.** The app uses Spring Security's OAuth2
resource server, so signature checking, expiry and issuer validation are library code. That is
deliberately the part not to write yourself — a JWT filter that forgets to reject `alg: none`, or
compares signatures with `String.equals`, is a total authentication bypass that still passes every
happy-path test. The app only *issues* tokens (`JwtService`, HS256), and the subject is the user's
id rather than their email so a token survives an address change and no email is scattered
through the logs.

**There is no default signing key, and that is on purpose.** A secret committed to source is a
secret shared by every deployment and readable by anyone with the repository — with it, an
attacker mints a valid token for any account. Unset, the app generates a random key at startup and
logs a warning; tokens then do not survive a restart, which is a visible nuisance in development
and a loud signal in any other environment. A configured secret shorter than 32 bytes stops the
app from starting, because HS256 with a short key can be brute-forced offline from a single
captured token.

**Someone else's link is `404`, never `403`.** A 403 confirms the code exists and belongs to an
account, which is precisely the fact being protected — and since codes are sequential, that answer
can be walked. `LinkOwnershipIntegrationTest` pins this for both metadata and stats. The redirect
itself stays public: ownership controls who reads the analytics, not who may follow the link.

**Login gives one answer for every failure — including in how long it takes.** A wrong password
and an address with no account return the same status and the same message. That is only half the
job: if an unknown address skipped password hashing it would return in microseconds while a real
one paid the full BCrypt cost, and the difference is measurable from outside. `UserService`
verifies against a throwaway hash in the unknown-address branch so both paths cost the same.

**CSRF protection is disabled, and that is correct here.** CSRF defends against the browser
attaching credentials automatically, which is what cookies do. The credential here is an
`Authorization` header that a cross-site request cannot cause the browser to send. Move to
cookie-based sessions and that line has to come back — it is a consequence of the token choice,
not a default that was switched off to make something work.

**Click logging never touches the redirect's critical path.** The redirect handler reads the
headers it needs on the request thread, builds an immutable `ClickEvent`, hands it to a bounded
executor, and returns. It does not wait for the insert, and `ClickRecorder.record` cannot throw.
Two details carry the weight:

- *The request object never crosses a thread boundary.* Servlet containers recycle
  `HttpServletRequest` once the response is committed, so reading a header from a worker thread
  races with the next request and can silently record the wrong referrer.
- *The queue is bounded and rejection aborts.* An unbounded queue does not remove backpressure,
  it converts it into heap growth until the process dies and takes redirects with it.
  `CallerRunsPolicy` would be worse: it pushes the insert back onto the Tomcat thread, which is
  exactly the blocking the async path exists to prevent. So the trade is explicit — **under
  sustained overload LinkForge loses click rows and keeps serving redirects.** Analytics are
  lossy; the redirect is not. `linkforge.clicks.rejected` counts what was lost.

**The cache is a lookaside cache that is allowed to fail.** Every Redis call is wrapped: if Redis
is unreachable, `lookup` returns `Unknown` and the redirect falls through to Postgres — slower,
still correct. A cache that can take the service down is not an optimisation, it is a second
thing that can break. The Redis timeout is 200 ms for the same reason: the redirect must never sit
waiting on a cache.

**Absence is cached too, for a shorter time.** Without that, requests for codes that do not exist
skip the cache entirely and land on Postgres every time, so anyone hitting random codes aims their
traffic straight at the database. Negative entries live 30 s against a real entry's 1 h, which
bounds the one staleness this introduces: a code someone probed for just before it was created
stays invisible for at most 30 s.

**Cache values are encoded as `id:url`, not serialized objects.** A serialized Java object ties
every warm entry to the class it was written from, so a field rename turns the cache into a wall
of deserialization errors at the worst possible moment. Only the first colon separates, so URLs
keep theirs. Nothing needs invalidating today because links are immutable — the moment an update
or delete endpoint exists, that stops being true and eviction becomes mandatory.

**Unique visitors are approximate, and the API says so.** The stored address has its last IPv4
octet (or everything below the IPv6 /48) zeroed before it reaches the database, so a household
behind one NAT counts once and one visitor on two networks counts twice. `X-Forwarded-For` is
also client-controlled: it is only trustworthy behind a proxy that overwrites it, which is a
deployment-step problem, not a code-step one.

**The URL allowlist is a security boundary, not input tidiness.** A shortener is an open-redirect
engine by definition; without a scheme allowlist a stored `javascript:` or `file:` target turns
every short link into an attack. Embedded user info (`https://bank.com@evil.example`) is rejected
for the same reason. Blocking private/loopback destinations (SSRF) is *not* done yet — that is
step 8.

## Environment notes

Docker Engine 29 rejects Docker API versions below 1.44, while the docker-java client inside
Testcontainers 1.21.3 still negotiates 1.32 and fails with a bare HTTP 400 ("Could not find a
valid Docker environment"). `pom.xml` pins `api.version=1.44` for the test JVM so this works
without per-machine setup.

## Known gaps

- **No geo.** The spec asked for IP → coarse geo; a real country lookup needs a GeoLite2 database
  (license acceptance plus a download), and guessing a country from an IP without one would be
  fabrication. The anonymized address is stored, so this can be added later without a migration.
- **No rate limiting on login**, so nothing slows down password guessing. Enumeration is defended;
  brute force is not. This is the most significant remaining hole and belongs to step 8.
- Passwords are only length-checked (8 minimum, no composition rules, per NIST SP 800-63B). The
  same guidance says to check candidates against a breached-password list, which is not done.
- Tokens live one hour and cannot be revoked. There is no refresh token, no logout, and no
  denylist: a stolen token is valid until it expires. Short-lived access tokens plus refresh
  tokens are the standard answer and are not built.
- The frontend has no routing library: it is one screen plus a gate, and the selected link is not
  in the URL, so a particular link's analytics cannot be linked to or reloaded into.
- The dashboard does not poll. Clicks that arrive while it is open do not appear until the window
  or the link selection changes.
- Links created before this step have no owner. They still redirect and are invisible to every
  per-user endpoint — correct, but it means the column is nullable and the code has to handle it.
- Stats are computed on every request with no cache, so a popular link aggregates its whole click
  history each time. Fine at this size; it is the next thing to break under load.
- Click writes are one insert per redirect and are the current throughput ceiling — they roughly
  halve peak redirect throughput even with the cache on. Batching them is the next real
  optimisation; the read path is already handled.
- **The AWS stack has never been applied**, so no claim about it is defensible yet: not the
  services, not a live URL, not "deployed with CI/CD". Deploying it once — and tearing it down —
  is what converts it from a design into something sayable.
- **The cache has no measured latency benefit.** The benchmark runs the load generator, the API and
  both datastores on one laptop, and its variance swamps the effect — cache-off throughput ranged
  from 663 to 17,114 req/s across identical commands. No latency claim from it belongs on a resume
  until it is re-run off-box with alternating repeats.
- No rate limiting and no SSRF protection.
- No load-test numbers. Any latency or throughput figure would be invented.
