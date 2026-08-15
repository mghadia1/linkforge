# Redirect latency: cache off vs cache on

Measured August 14, 2026, with `bench/run.sh`. Seven paired runs.

## The finding

**This benchmark cannot measure the effect of the Redis cache on this machine.** Run-to-run
variance is larger than the difference between the two configurations, and the *sign* of the
difference is not stable: two runs of the identical command gave the cache +10% throughput and
then −63%.

No latency improvement should be claimed from this data — not on a resume, not on the portfolio
site, not in an interview. What can be said is written at the bottom of this file.

## Every run

All runs: 30 s, k6 in a container on the compose network, both arms rebuilt from an empty database
and empty Redis, 200-request warmup. In every "cache on" arm the server reported ~1 cache miss and
the rest hits, so the cached arm genuinely was served from Redis.

### Host busy (builds and other benchmark runs competing for CPU)

| # | clicks | VUs | | off | on | change |
|---|---|---|---|---|---|---|
| B1 | off | 20 | throughput | 663/s | 15,062/s | **+2171%** |
| | | | p95 | 35.62 ms | 3.06 ms | −91% |
| | | | p99 | 224.92 ms | 6.00 ms | −97% |
| B2 | off | 20 | throughput | 2,577/s | 9,456/s | **+267%** |
| | | | p95 | 24.45 ms | 5.35 ms | −78% |
| B3 | off | 4 | throughput | 9,572/s | 5,954/s | **−38%** |
| | | | p95 | 0.79 ms | 1.40 ms | +76% |
| B4 | on | 20 | throughput | 944/s | 1,801/s | **+91%** |
| | | | p95 | 62.08 ms | 30.60 ms | −51% |

### Host quiet

| # | clicks | VUs | | off | on | change |
|---|---|---|---|---|---|---|
| Q1 | on | 20 | throughput | 11,188/s | 12,342/s | **+10%** |
| | | | p95 | 4.81 ms | 3.74 ms | −22% |
| Q2 | off | 20 | throughput | 17,114/s | 14,794/s | **−14%** |
| | | | p95 | 2.70 ms | 2.97 ms | +10% |
| Q3 | on | 20 | throughput | 7,478/s | 2,735/s | **−63%** |
| | | | p95 | 7.84 ms | 20.47 ms | +161% |

## Why the early numbers were wrong

B1, B2 and B4 were run while the machine was also compiling, running test suites and running other
benchmark arms. Under that contention the uncached arm collapsed — 663 req/s, a p99 of 225 ms and
a worst case of 18.7 seconds for a query that takes well under a millisecond — while the cached arm
held up. That looks like a spectacular cache win, and it was reported as one.

It is not. Q1, Q2 and Q3 are the same commands on the same code with the machine otherwise idle,
and the uncached arm does 11,000–17,000 req/s with a p95 under 5 ms. The "improvement" in B1–B4 was
the two configurations degrading differently under **host CPU starvation**, not a steady-state
property of the cache. A number that only appears when the load generator is fighting the database
for CPU is a measurement of the laptop, not of LinkForge.

Q1 versus Q3 is the clearest evidence: identical command, identical build, host quiet both times,
and the cache goes from +10% throughput to −63%. Q3's cached arm being three times slower than its
uncached arm is unexplained and was not chased down.

## What can honestly be said

- The cache **works**: ~1 miss and hundreds of thousands of hits per run, verified from server-side
  counters in every cached arm, and `CachedRedirectIntegrationTest` pins the behaviour.
- On this hardware, **a warm indexed single-row Postgres lookup over a pooled connection is already
  fast enough that a Redis round trip does not reliably beat it.** The clearest single data point is
  B3 at 4 concurrent users — comfortably under the 10-connection pool — where the cache cost 38%
  throughput. Q2 agrees on a quiet host at 20 users.
- The *reason* to cache a redirect is architectural rather than per-request speed: it removes the
  redirect's dependence on the database connection pool, so redirects survive the database being
  slow, saturated, or briefly unavailable. `RedisLinkCacheTest` shows the reverse dependency is not
  introduced — Redis being down degrades to a database read rather than an error.
- **That architectural claim is not demonstrated by any measurement here.** Demonstrating it needs a
  test that constrains the database on purpose — a small connection pool, an injected query delay,
  or a database restart mid-run — not a general load test on a laptop.

## What it would take to get a real number

1. A quiet, dedicated machine, with the load generator **off-box** — ideally the step 7 AWS
   deployment, not a laptop running all five processes.
2. Arms alternated and repeated (off, on, off, on, …) at least five times each, reporting medians
   and spread, so ordering and drift are visible.
3. A scenario where the database is the actual constraint, since that is the condition the cache
   exists for.

Until then this file records an inconclusive result, which is the honest outcome.

## Environment

- Apple Silicon laptop, Docker Desktop 29.2.1, all containers plus the load generator on one host.
- API: Spring Boot 3.5.5 on Temurin 21, default Hikari pool (10 connections).
- Postgres 16-alpine, Redis 7-alpine, k6 (grafana/k6:latest).
- B1–B4 predate JWT authentication; Q1–Q3 include it. The redirect path is public either way, but
  the security filter chain does run on it, so the two groups are not perfectly comparable — which
  matters far less than the variance described above.
