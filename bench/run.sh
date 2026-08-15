#!/usr/bin/env bash
# Measures redirect latency with the Redis cache off, then on.
#
# Both arms run the same build, the same database, the same warmup and the same load. The only
# difference between them is LINKFORGE_CACHE_ENABLED, which swaps RedisLinkCache for a no-op.
# That is the point: anything else that differed would make the comparison meaningless.
#
# Usage: bench/run.sh [duration] [vus]
set -euo pipefail

DURATION="${1:-30s}"
VUS="${2:-20}"
# CLICKS=false isolates the redirect read path. With click logging on, every redirect also queues
# an insert, so Postgres is under heavy write load in BOTH arms and the difference the cache makes
# to the read is buried under it. Run it both ways and report both.
CLICKS="${CLICKS:-true}"
WARMUP_REQUESTS=200

cd "$(dirname "$0")/.."
PROJECT="$(basename "$PWD")"
NETWORK="${PROJECT}_default"
BASE="http://localhost:8080"

log() { printf '\n\033[1m%s\033[0m\n' "$*"; }

wait_for_health() {
  for _ in $(seq 1 60); do
    if curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "API did not become healthy" >&2
  exit 1
}

# Prints the counter value, or null when the meter does not exist — the cache counters are only
# registered when the cache is enabled, and "n/a" would not be valid JSON.
metric() {
  curl -s -H "Authorization: Bearer $TOKEN" "$BASE/actuator/metrics/$1" 2>/dev/null \
    | python3 -c "
import sys, json
try:
    print(int(json.load(sys.stdin)['measurements'][0]['value']))
except Exception:
    print('null')
"
}

run_arm() {
  local cache_enabled="$1"
  log "=== cache enabled: $cache_enabled ==="

  # Both arms must start from an empty database. Each arm writes hundreds of thousands of click
  # rows, so without this reset the second arm measures a much larger clicks table than the
  # first and the comparison reports insert cost, not cache benefit.
  docker compose down -v >/dev/null 2>&1 || true
  LINKFORGE_CACHE_ENABLED="$cache_enabled" LINKFORGE_CLICKS_ENABLED="$CLICKS" \
    docker compose up -d --build >/dev/null 2>&1
  wait_for_health

  # Creating a link needs a token. The redirect being measured stays public, so the token is only
  # used for this setup call and never appears in the load itself.
  local code
  # Exported, because reading /actuator/metrics now needs a token too.
  TOKEN=$(curl -s -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"bench-$RANDOM@example.com\",\"password\":\"correct horse battery\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  code=$(curl -s -X POST "$BASE/api/links" -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"url":"https://example.com/benchmark-target"}' \
    | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')
  if [ -z "$code" ]; then
    echo "could not create a link to benchmark" >&2
    exit 1
  fi

  # Warm both the Postgres buffer cache and (when enabled) Redis, so the measurement is of the
  # steady state rather than of a cold start.
  for _ in $(seq 1 "$WARMUP_REQUESTS"); do
    curl -s -o /dev/null "$BASE/$code"
  done

  # --quiet suppresses k6's progress bar, which otherwise interleaves with the JSON on stdout.
  # The grep is a second guard: take the summary line, whatever else appears.
  local summary
  summary=$(docker run --rm -i --network "$NETWORK" \
    -e "CODE=$code" -e "BASE_URL=http://api:8080" -e "VUS=$VUS" -e "DURATION=$DURATION" \
    grafana/k6:latest run --quiet \
      --summary-trend-stats="avg,min,med,max,p(95),p(99)" \
      - < bench/redirect-latency.js 2>/dev/null \
    | tr -d '\r' | grep -m1 '^{')

  echo "$summary" > "/tmp/linkforge-bench-$cache_enabled.json"
  # Cache hits are recorded so the comparison can be checked rather than trusted: if the "cache
  # on" arm shows no hits, it was quietly reading Postgres and the whole result is void.
  # Dropped clicks are reported for the same reason — the redirect staying fast while analytics
  # shed load is a result, not a footnote.
  cat > "/tmp/linkforge-bench-$cache_enabled-server.json" <<EOF
{"clicks_recorded": $(metric linkforge.clicks.recorded),
 "clicks_rejected": $(metric linkforge.clicks.rejected),
 "cache_hits": $(metric linkforge.cache.hits),
 "cache_misses": $(metric linkforge.cache.misses)}
EOF
  echo "$summary"
}

log "LinkForge redirect latency benchmark — ${DURATION}, ${VUS} VUs, click logging: ${CLICKS}"
docker compose down -v >/dev/null 2>&1 || true
run_arm false
run_arm true

log "=== results ==="
python3 - <<'PY'
import json

def load(name):
    with open(f"/tmp/linkforge-bench-{name}.json") as f:
        return json.load(f)

def server(name):
    with open(f"/tmp/linkforge-bench-{name}-server.json") as f:
        return json.load(f)

off, on = load("false"), load("true")
print(f"{'':<22}{'cache off':>14}{'cache on':>14}{'change':>14}")
for label, key in [("p50 (ms)", "p50"), ("p95 (ms)", "p95"), ("p99 (ms)", "p99"),
                   ("mean (ms)", "avg"), ("max (ms)", "max"), ("requests/sec", "rps")]:
    a, b = off[key], on[key]
    delta = (b - a) / a * 100 if a else 0
    print(f"{label:<22}{a:>14.2f}{b:>14.2f}{delta:>13.1f}%")
print(f"{'requests':<22}{off['requests']:>14}{on['requests']:>14}")
print(f"{'failed':<22}{off['failed']:>14}{on['failed']:>14}")
so, sn = server("false"), server("true")
def show(v):
    return "-" if v is None else str(v)

for label, key in [("cache hits", "cache_hits"), ("cache misses", "cache_misses"),
                   ("clicks recorded", "clicks_recorded"), ("clicks rejected", "clicks_rejected")]:
    print(f"{label:<22}{show(so[key]):>14}{show(sn[key]):>14}")
PY

log "Tear down with: docker compose down -v"
