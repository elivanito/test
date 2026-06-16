// k6 load test for /api/v1/suppliers/potential.
//
// Usage:
//   1. Seed the DB once: see benchmark/seed_1M.sql
//   2. docker run --rm -i --network host grafana/k6 run - < backend/benchmark/load.js
//
// What this measures:
//   * p50/p95/p99 latency at the documented 100k-1M scale.
//   * Throughput ceiling before the DB pool saturates (HikariCP active gauge).
//   * Cache-Control behaviour on repeated queries (HTTP/304 not used here yet,
//     but the 30s public TTL should show up at any reverse proxy).
//
// Thresholds are the SLO proposal — failing them fails CI.
//
// Scenarios:
//   * "offset_shallow": typical UI use (offset 0-30). Expected fast.
//   * "offset_deep":    pagination beyond offset 1000. Demonstrates why keyset wins.
//   * "keyset":         walk pages via cursor. Should remain flat regardless of depth.

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export const options = {
    scenarios: {
        offset_shallow: {
            executor: 'constant-arrival-rate',
            rate: 50, timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 20, maxVUs: 100,
            exec: 'offsetShallow',
        },
        offset_deep: {
            executor: 'constant-arrival-rate',
            rate: 10, timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 10, maxVUs: 50,
            exec: 'offsetDeep',
        },
        keyset: {
            executor: 'constant-arrival-rate',
            rate: 30, timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 15, maxVUs: 60,
            exec: 'keysetWalk',
        },
    },
    thresholds: {
        // SLO proposal — adjust after first run captures the baseline.
        'http_req_duration{scenario:offset_shallow}': ['p(95)<200', 'p(99)<400'],
        'http_req_duration{scenario:offset_deep}':    ['p(95)<800'],
        'http_req_duration{scenario:keyset}':         ['p(95)<200', 'p(99)<400'],
        'http_req_failed':                            ['rate<0.001'],
    },
};

export function offsetShallow() {
    const offset = Math.floor(Math.random() * 30) * 10;
    const r = http.get(`${BASE}/suppliers/potential?rate=500000&limit=10&offset=${offset}`);
    check(r, { 'status 200': res => res.status === 200 });
    sleep(0.05);
}

export function offsetDeep() {
    const offset = 1000 + Math.floor(Math.random() * 5000);
    const r = http.get(`${BASE}/suppliers/potential?rate=500000&limit=10&offset=${offset}`);
    check(r, { 'status 200': res => res.status === 200 });
    sleep(0.05);
}

export function keysetWalk() {
    let cursor = '';
    for (let i = 0; i < 5; i++) {
        const url = `${BASE}/suppliers/potential?rate=500000&limit=10&cursor=${encodeURIComponent(cursor)}`;
        const r = http.get(url);
        check(r, { 'status 200': res => res.status === 200 });
        if (r.status !== 200) break;
        const body = r.json();
        cursor = body.pagination.nextCursor || '';
        if (!cursor) break;
    }
    sleep(0.1);
}
