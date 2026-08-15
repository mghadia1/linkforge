// Measures the redirect path only: GET /{code}, redirects not followed.
//
// Run it through bench/run.sh, which starts the stack twice — once with the cache off and once
// with it on — and reports both. Running this script directly measures whatever the stack
// happens to be configured for, which is how a benchmark ends up quoting the wrong number.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://api:8080';
const CODE = __ENV.CODE;

export const options = {
  scenarios: {
    redirects: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '30s',
    },
  },
};

export default function () {
  // redirects: 0 keeps k6 from chasing the Location header out to the public internet, which
  // would measure example.com rather than LinkForge.
  const res = http.get(`${BASE}/${CODE}`, { redirects: 0 });
  check(res, { 'status is 302': (r) => r.status === 302 });
}

export function handleSummary(data) {
  const duration = data.metrics.http_req_duration.values;
  const requests = data.metrics.http_reqs.values;
  const failed = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.passes : 0;
  return {
    stdout:
      JSON.stringify({
        requests: requests.count,
        rps: requests.rate,
        failed: failed,
        avg: duration.avg,
        p50: duration.med,
        p95: duration['p(95)'],
        p99: duration['p(99)'],
        max: duration.max,
      }) + '\n',
  };
}
