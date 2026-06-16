# Performance benchmark

The challenge mentions a supplier volume of **100,000 to 1,000,000**. This
folder ships the tooling to validate the system at that scale.

## Files

- `seed_1M.sql` — generates a synthetic 1 M-row dataset with realistic
  distributions (countries, turnovers log-normal, rating mix, ~2 %
  DISQUALIFIED, ~8 % ON_PROBATION).
- `load.js` — k6 scenarios that exercise the three pagination shapes
  (shallow offset, deep offset, keyset walk) with SLO thresholds wired as
  `pass/fail` conditions.
- `explain.sql` — `EXPLAIN (ANALYZE, BUFFERS)` snippets for the hot query, so
  you can verify the index usage before/after migrations.

## How to run

```bash
# 1) Start the stack
docker compose up -d

# 2) Seed 1 M rows (~25 s on a modern laptop, ~120 MB of table data)
docker exec -i $(docker compose ps -q db) \
    psql -U suppliers -d suppliers < backend/benchmark/seed_1M.sql

# 3) Load test (k6 must be installed locally, or use the container)
docker run --rm -i --network host \
    -e BASE_URL=http://localhost:8080/api/v1 \
    grafana/k6 run - < backend/benchmark/load.js

# 4) Inspect the plan
docker exec -i $(docker compose ps -q db) \
    psql -U suppliers -d suppliers < backend/benchmark/explain.sql
```

## Expected results (proposed SLOs)

| Scenario          | p95     | p99     | Notes                                  |
|-------------------|---------|---------|----------------------------------------|
| offset_shallow    | <200 ms | <400 ms | Typical UI usage                       |
| offset_deep       | <800 ms | —       | OFFSET cost made visible on purpose    |
| keyset            | <200 ms | <400 ms | Flat regardless of depth (the point)   |
| http_req_failed   | <0.1 %  | —       | No 5xx allowed during the run          |

If `offset_deep` is close to `keyset`, the dataset is too small — increase
the seed to 5 M rows.

## What we are validating

1. **Indexes**: `ix_suppliers_eligible` (partial, covering) should be the
   driver of the JOIN. Verify with `EXPLAIN` — look for an Index Scan, not a
   Seq Scan.
2. **Generated column**: `rating_multiplier` is read, not computed.
3. **HikariCP**: under load, the active connections gauge stays under the
   configured `maximum-pool-size` and no `connection-timeout` errors fire.
4. **Cache-Control**: a downstream proxy (nginx/Varnish) would hit the cache
   for ~30 s on repeated identical queries. Without proxy, k6 doesn't see
   the win — note this in the report.
