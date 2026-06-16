# Inditex Supplier Management — Solution

> Technical challenge for the Tech Lead Full-Stack Engineer position at Inditex.

> 🌐 **Language / Idioma**: [English](#1-how-to-run) · [Español](#es-1-cómo-ejecutar)

## 1. How to run

The whole solution is orchestrated with **Docker Compose**. No local JDK / Node / DB installation is required.

```bash
docker compose up --build
```

Wait for the `backend` healthcheck to go green (≈ 30 s the first time). Then open:

| Service             | URL                                | Purpose                                |
|---------------------|-------------------------------------|----------------------------------------|
| Frontend (Angular)  | http://localhost:4200               | Potential Suppliers dashboard          |
| Backend (Spring)    | http://localhost:8080                | REST API                               |
| Backend health      | http://localhost:8080/actuator/health |                                      |
| Country mock        | http://localhost:8088                | WireMock — `GET /countries/{code}`     |
| PostgreSQL          | localhost:5432                       | DB `suppliers` / user `suppliers`      |

The frontend talks to the backend via the relative path `/api/*`, proxied by nginx to the `backend` container on the docker-compose network.

### Stop / reset

```bash
docker compose down              # stop containers
docker compose down -v           # also wipe the DB volume
```

### Run locally (without Docker)

- **Backend**: `cd backend && mvn spring-boot:run` (needs a Postgres on `localhost:5432` matching `application.yml` and the country mock on `:8088`).
- **Frontend**: `cd frontend && npm install && npm start` (opens on http://localhost:4200; uses the proxy to reach `localhost:8080`).
- **Tests** (backend): `cd backend && mvn test` — 38 unit tests + 3 Testcontainers ITs (the ITs auto-skip if Docker is unavailable).

## 2. Tech stack

- **Backend**: Java 25, Spring Boot 4.0, Spring Web, Spring Data JPA + JDBC, Flyway, Resilience4j (Circuit Breaker), Caffeine cache, Jakarta Validation, PostgreSQL driver.
- **Frontend**: Angular 18 standalone (signals + `@for/@if` control flow), TypeScript 5.5, served by nginx in production.
- **DB**: PostgreSQL 16.
- **External country service**: provided as a WireMock mock (`countryservice/mappings/*.json`).
- **Tests**: JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL).

## 3. Architecture — hexagonal / DDD

```
backend/src/main/java/com/inditex/suppliers
├── domain                       # PURE — no Spring, no JPA
│   ├── model                    # Candidate, Supplier, statuses
│   ├── vo                       # Duns, CountryCode, AnnualTurnover, Score, SustainabilityRating
│   └── exception                # Business exceptions extending DomainException
├── application
│   ├── port.in                  # Use case interfaces (CreateCandidateUseCase, …)
│   ├── port.out                 # CandidateRepository, SupplierRepository,
│   │                            # CountryGateway, PotentialSupplierQuery
│   ├── dto                      # Cross-boundary records (PotentialSupplierPage/View)
│   └── service                  # Transactional use case implementations
└── infrastructure
    ├── rest                     # Controllers, DTOs, RestMapper, GlobalExceptionHandler
    ├── persistence
    │   ├── entity               # JPA entities (decoupled from domain)
    │   ├── repository           # Spring Data JPA repositories
    │   ├── adapter              # Implementations of application output ports
    │   ├── query                # PotentialSupplierJdbcQuery (read-model SQL)
    │   └── mapper               # PersistenceMapper (domain ↔ entity)
    ├── client.country           # RestClient adapter + cache + circuit breaker
    └── config                   # WebConfig (CORS)
```

**Dependency direction**: `infrastructure` → `application` → `domain`. The domain never imports anything outside `java.*`. JPA entities are intentionally separate from domain aggregates and are mapped via `PersistenceMapper`.

### Why these boundaries

- The challenge mixes two very different workloads:
  - command-side use cases (small aggregates, transactional, rich invariants) → modelled as aggregates with behaviour (`Candidate.accept(...)`, `Supplier.ban()`);
  - a read-side that needs to project, score and paginate **up to a million rows** → modelled as a separate read port (`PotentialSupplierQuery`) implemented with a single SQL statement, never going through the JPA model.
- Splitting these workloads keeps the domain free from `OFFSET/LIMIT/score` concerns and keeps the read-model free from the cost of hydrating aggregates.

## 4. Business rules — where each rule lives

| Rule                                                                                             | Enforced in                                                              |
|--------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| DUNS is a 9-digit number                                                                         | `Duns` VO                                                                |
| Country is ISO 3166-1 alpha-2 (normalised to uppercase)                                          | `CountryCode` VO                                                         |
| Annual turnover ≥ 0                                                                              | `AnnualTurnover` VO                                                      |
| `applyCandidate`: no DUNS already banned                                                         | `CreateCandidateService` (calls `SupplierRepository.isBanned`)           |
| `applyCandidate`: no active candidacy nor existing supplier with the same DUNS                   | `CreateCandidateService`                                                 |
| `acceptCandidate`: turnover ≥ 1,000,000 €                                                        | `Candidate.accept(...)` (`AnnualTurnover.meetsAcceptanceThreshold`)      |
| `acceptCandidate`: country not in the non-approved list                                          | Use case fetches via `CountryGateway`, passes flag to `Candidate.accept` |
| Rating A/B → Active, C/D/E → On Probation                                                        | `SustainabilityRating.leadsToActiveStatus()` + `Supplier.fromAcceptedCandidate` |
| `banSupplier`: only suppliers On Probation can be banned                                         | `Supplier.ban()` (uses `SupplierStatus.canBeBanned`)                     |
| FSM `Restrict`: Active → On Probation (not exposed by API — see §9)                              | `Supplier.restrict()` (uses `SupplierStatus.canBeRestricted`)            |
| FSM `Promote`:  On Probation → Active (not exposed by API — see §9)                              | `Supplier.promote()`  (uses `SupplierStatus.canBePromoted`)              |
| API exposes `Active` for both ACTIVE and ON_PROBATION                                            | `RestMapper.publicStatus`                                                |
| Potential suppliers: `turnover > rate`, excludes Disqualified                                    | `PotentialSupplierJdbcQuery` (WHERE clause)                              |
| Score = `turnover × 0.1 × rating_constant × (1.25 if among 2 lowest unique turnovers per country else 1.0)` | `PotentialSupplierJdbcQuery` (window function in CTE)                    |
| Sorted by score desc, paginated (limit 1–10, offset ≥ 0, rate ≥ 250)                             | `FindPotentialSuppliersService` (validates) + SQL `ORDER BY / LIMIT / OFFSET` |

### The `+25%` bonus — why a window function

The bonus depends on the two **lowest unique turnover values per country** computed over the full supplier population (not the page being returned). With 1M rows this is the only operation that cannot be done in-memory. The SQL uses:

```sql
DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover ASC)
```

on the `DISTINCT (country, annual_turnover)` set, so suppliers tied on the lowest turnover for a country all share rank 1 and all get the bonus. The README example (`200k,200k,200k,210k,250k` → first 4 get the bonus, the 5th does not) is verified end-to-end in `PotentialSupplierJdbcQueryIT`.

## 5. HTTP status code mapping (`GlobalExceptionHandler`)

| Situation                                                       | Status | Body                                          |
|-----------------------------------------------------------------|--------|-----------------------------------------------|
| Bean Validation, malformed JSON, invalid VO (DUNS/country/etc.) | 400    | `{ "info": "<reason>" }`                      |
| Candidate / Supplier not found                                  | 404    | (empty)                                       |
| Already-exists / banned-DUNS / not-pending / cannot-ban         | 409    | `{ "info": "<reason>" }`                      |
| Country banned, turnover insufficient, country unknown          | 422    | `{ "info": "<reason>" }`                      |
| Anything else                                                   | 500    | `{ "info": "Internal error" }`                |

Mapping rationale:
- **404** for missing entities → standard.
- **409** for state-conflict-by-id (already exists, wrong lifecycle state).
- **422** for business rules that depend on *external* data (country status, turnover threshold) so the client knows the request is well-formed but rejected by domain.

## 6. Resilience, concurrency & performance

- **Caffeine cache** (`@Cacheable("countries")`, 10 min, 256 entries) in front of the country gateway: repeated lookups during a batch of acceptances reuse the same upstream result.
- **Resilience4j Circuit Breaker** wrapping the country adapter (50 % failure threshold, 30 s open window). When open, calls fail fast with `CallNotPermittedException` (currently mapped to 500 — acceptable while the upstream is recovering).
- **Optimistic concurrency**: both aggregates carry an opaque `version` token (round-tripped by the persistence adapter). JPA entities have `@Version`, so a stale write triggers `OptimisticLockingFailureException`, which `GlobalExceptionHandler` maps to **`409 Conflict`** with an actionable message. The token is part of the domain as a black box, not a persistence detail; this keeps the hexagon intact while preserving last-write detection across transactions.
- **PostgreSQL indexes** (Flyway `V1`): `idx_suppliers_country_turnover` (for the per-country DENSE_RANK), `idx_suppliers_status` (for the active filter), `idx_candidates_state` (for pending lookups).
- **No N+1**: the potential-suppliers endpoint runs **one** SQL statement (plus one COUNT only when the page is empty, to keep `pagination.total` exact).

### Observability

- **Prometheus endpoint** exposed at `GET /actuator/prometheus` via `micrometer-registry-prometheus`. HTTP latency histograms (`http.server.requests`) are enabled with SLO buckets at 50ms / 100ms / 250ms / 500ms / 1s, ready for a Grafana dashboard. Common tag `application=inditex-suppliers` lets multiple instances share a registry.
- **Correlation id propagation**: `CorrelationIdFilter` (highest precedence) reads `X-Correlation-Id` from the request (or generates a UUID), stores it in SLF4J `MDC`, and echoes it back on the response. The logback pattern (`%X{correlationId:-}`) renders it on every line. Cleanup is `finally`-guarded to avoid leaking into the thread pool.

### Architectural guard-rails

The hexagonal dependency rule is enforced by **ArchUnit** at test time (`HexagonalArchitectureTest`), with six rules: layered architecture (infrastructure → application → domain), and explicit prohibitions of Spring, JPA, web and Jackson imports inside the domain. A junior PR that accidentally imports `jakarta.persistence.Entity` in a domain aggregate fails the build, not the code review.

## 7. Frontend

Single-page Angular 18 dashboard built around the `findPotentialSuppliers` endpoint.

Implemented features:

- **Form** with `rate` validation (must be ≥ 250).
- **Server-side pagination** (5 or 10 per page) plus client-side filtering:
  - search by name or DUNS,
  - filter by country (dynamic list, taken from the current page),
  - filter by sustainability rating.
- **Client-side sorting** on every column.
- **Currency formatting** with `es-ES` locale.
- **State management** purely via Angular signals — no NgRx (overkill here).
- **States**: idle (no search yet), loading, empty, error (with message from the API).
- Accessible markup: explicit labels, `aria-live` for result counts, keyboard-navigable buttons.

## 8. Testing

```bash
cd backend && mvn test
```

- **41** domain/application unit tests covering value objects, aggregates (`Candidate`, `Supplier` — including the full FSM round-trip), and all use case services (mocking the output ports). The re-application flow after `REFUSED` preserves the optimistic-lock token, with a dedicated test.
- **26** controller slice tests (`@WebMvcTest` + `MockMvc`) covering every endpoint × every relevant HTTP status (`201/204/200/400/404/409/422`). Validates the wiring of `GlobalExceptionHandler`, the `RestMapper.publicStatus` collapse (`ON_PROBATION` → `"Active"`), the `OptimisticLockingFailureException → 409` mapping, and parameter validation (`@Min/@Max/@Pattern`).
- **6** ArchUnit rules enforcing the hexagonal dependency rule and prohibiting Spring / JPA / web imports in the domain.
- **3** correlation-id filter tests covering header propagation, UUID fallback and MDC cleanup on exception.
- **3** Testcontainers integration tests in `PotentialSupplierJdbcQueryIT` exercising the read-model SQL against a real PostgreSQL. They reproduce the README bonus example and verify pagination/filtering. They auto-skip when no Docker daemon is available, so `mvn test` works in any environment.

**Backend total: 84 tests pass green, 3 skipped (Docker-dependent IT).**

Backend test pyramid: unit (domain + services) > controller slices (HTTP contract) > narrow integration (SQL against real Postgres) > architecture rules (compile-time invariants).

### Frontend tests

```bash
cd frontend && npm test
```

Runs Karma + Jasmine in **ChromeHeadless** in ~1 s (no watch). 24 specs across three suites:

- `PotentialSuppliersService` (3 specs): verifies that the service emits the correct HTTP query, maps the `{ info: ... }` API error shape into a thrown `Error`, and falls back to the raw HTTP message for non-API errors. Uses `provideHttpClientTesting` to assert request URL, params and method.
- `PotentialSuppliersComponent` (19 specs): isolates the component from HTTP via a hand-rolled `FakeApi`. Covers rate validation, the search happy path, three filter axes (name, DUNS, country, rating), the derived `availableCountries` set, sort behaviour (default direction per column, toggle, numeric vs lexicographic), pagination (`hasNext/PrevPage`, `currentPage`, `totalPages`, `goToPage`, `changeLimit`), `clearFilters`, the error branch and the offset-reset on re-search.
- `AppComponent` (2 specs): smoke test for the page header and the embedding of the feature component.

**Frontend total: 24 specs pass green.**

## 9. Design decisions / trade-offs

- **JPA entities ≠ domain aggregates.** Yes, this is more code; it keeps the domain JPA-free and lets us evolve persistence independently. The mapping layer (`PersistenceMapper`) is purely procedural and trivial to read.
- **Single Maven module** with package-level layering, enforced by **ArchUnit** at test time (`HexagonalArchitectureTest`). A multi-module Gradle/Maven build would enforce boundaries at compile time but multiplies the configuration surface; the ArchUnit suite gives most of the safety at a fraction of the cost.
- **JdbcTemplate** (not JPA) for the read-model. Window functions and projection don't map cleanly to JPA and would force a much heavier round-trip. The SQL is short, in one place, and tested.
- **`SupplierStatus.ON_PROBATION` is internal.** The API collapses it into `"Active"`, but the domain needs the distinction to allow banning. The `RestMapper.publicStatus()` is the single point where this collapse happens.
- **The full FSM (`wiki/iop-techtest-fsm-supplier.png`) is modelled in the domain.** That includes the two transitions `Restrict` (Active → On Probation) and `Promote` (On Probation → Active), which are *not* exposed in the OpenAPI contract (`itx-iop_tech-supplier_flow-main-openapi3_1.yaml` only defines `apply / accept / refuse / ban`). The domain is the source of truth for the lifecycle, while the REST surface is a curated projection. Adding the missing endpoints (e.g. `/suppliers/{duns}/restrict`, `/promote`) is a trivial controller method when the contract permits it; the aggregate already enforces the invariants and is fully covered by `SupplierTest`.
- **Read-model totals.** When a page is empty (paging past the last page) the window-function COUNT yields no rows, so we fall back to a tiny `SELECT COUNT(*)` query to keep `pagination.total` exact. The extra query is a single integer fetch.
- **Country 404 → 422.** A 404 from the country service is treated as a domain error (`CountryUnknownException`), not as "country is fine"; otherwise an unknown country code would silently pass the banned-country check.
- **Frontend filters are client-side** (over the current page) because no filter parameters exist in the OpenAPI contract for `/suppliers/potential`. Pagination remains server-side, so the dataset stays bounded.

## 10. What is intentionally out of scope

- Authentication / authorisation (the brief has no security requirement).
- Distributed tracing (only Prometheus metrics + correlation id are wired; adding OTel exporters is one extra dependency).
- i18n in the frontend (currency uses `es-ES`, copy is in English).
- End-to-end Playwright suite (Karma unit suite is in place; e2e is the natural next step).

## 11. Backlog / next steps

1. Contract tests (Spring Cloud Contract / Pact) against the OpenAPI spec.
2. OpenTelemetry exporter + Grafana / Tempo for full distributed tracing.
3. Server-side filters for `/suppliers/potential` (country, rating) once the contract supports them.
4. Playwright e2e smoke on top of the existing Karma unit suite.
5. Replace offset pagination with keyset pagination for deep pages in the 1M-row case.

## 12. Example session

```bash
# Stack up
docker compose up --build -d

# Apply as a candidate
curl -X POST http://localhost:8080/api/v1/candidates \
  -H 'Content-Type: application/json' \
  -d '{"duns":123456789,"name":"Acme","country":"ES","annualTurnover":1500000}'
# → 201 Created

# Accept with rating A → becomes Active
curl -X POST http://localhost:8080/api/v1/candidates/123456789/accept \
  -H 'Content-Type: application/json' \
  -d '{"sustainabilityRating":"A"}'
# → 204 No Content

# Query potential suppliers for an order of 1k € (offset mode)
curl 'http://localhost:8080/api/v1/suppliers/potential?rate=1000&limit=10&offset=0'
# Or with server-side filters and keyset pagination:
curl 'http://localhost:8080/api/v1/suppliers/potential?rate=1000&limit=10&country=ES&maxRating=B&cursor='
# → 200 OK with paginated, scored list

# OpenAPI live docs:
#   http://localhost:8080/swagger-ui.html
#   http://localhost:8080/v3/api-docs
```

## 13. Iteration 2 — architecture review follow-ups

This section captures the second-pass hardening done after the initial review.
Every item references the file that implements it.

### A. Keyset pagination, sealed types, signed cursors
- `application.port.out.PotentialSupplierQuery` now exposes a **sealed `Pagination` ADT** (`Offset | Keyset`) so the SQL adapter pattern-matches exhaustively — no nullable discriminant.
- `application.dto.PotentialSupplierPage.total` is a **nullable `Long`** and the REST `PaginationDto` is annotated `@JsonInclude(NON_NULL)`: keyset responses simply omit `total` instead of carrying a `-1` sentinel.
- `application.util.CursorCodec` produces **versioned (`v1:`), HMAC-SHA256-signed** cursors. Tampering or downgrading is rejected. Key sourced from `suppliers.cursor.key` (env in prod).
- `application.util.PotentialSupplierLimits` is the **single source of truth** for `MIN_RATE`/`MIN_LIMIT`/`MAX_LIMIT`; both controller annotations and service validation reference it.

### B. Versioned API
All `@RestController` paths are now under `/api/v1/...`. Actuator stays at root (it is operational, not a public contract).

### C. Honest complexity note
`PotentialSupplierJdbcQuery` documents that the keyset strategy is *not* O(log n)
in the strict sense because `score` is a runtime expression and no index can
back the comparison. The keyset still wins over deep `OFFSET` paging by
avoiding the skip cost and per-page `COUNT(*)`. The production-grade fix
(generated column with expression index, or a refreshed materialized view) is
**ADR-005 — Score materialisation** in the backlog.

### D. Security hook
`infrastructure.config.SecurityConfig` ships two filter chains:
- **Default (dev / docker)** — `permitAll`, CSRF off. Matches the brief.
- **`prod` profile** — OAuth2 Resource Server with JWT; method/path matchers
  for `SCOPE_suppliers:read` and `SCOPE_suppliers:write`. Activated by
  `SPRING_PROFILES_ACTIVE=prod` + `OIDC_ISSUER_URI` env. **ADR-006 — Auth model**.

### E. Resilience — fail-closed fallback
`CountryGatewayAdapter` now has an explicit `fallbackMethod` that **treats
the upstream as banning everyone** when the circuit is open. This is a
compliance-aware default (false negative beats false positive); the alternative
fail-open with a `PENDING_REVIEW` state is **ADR-007**. A counter
`country_service_fallback_total` exposes how often the fallback fires so it
can be alerted on.

### F. Business metrics
`SupplierMetricsPort` (out port) + `MicrometerSupplierMetricsAdapter` emit:
`suppliers_transitions_total{type=candidate_created|candidate_accepted|candidate_refused|supplier_banned}`.
Wired from the application services so batch/event-consumer paths get the
same telemetry as the HTTP path.

### G. Distributed tracing
Micrometer Tracing + OpenTelemetry OTLP exporter on the classpath. Endpoint
defaults to `http://localhost:4318/v1/traces` in dev and is mandatory in
`prod`. Sampling: 100% in dev, 10% head-sample in prod (tail-based sampling
expected at the collector).

### H. Transactional outbox
- Migration `V2__outbox.sql` introduces `outbox_events` with a partial index
  on `status = 'PENDING'`.
- `application.port.out.DomainEventOutbox` is appended within the same
  transaction as the aggregate change in `AcceptCandidateService` and
  `BanSupplierService`.
- `infrastructure.persistence.outbox.OutboxPublisher` is a `@Scheduled` poller
  using `FOR UPDATE SKIP LOCKED` for safe horizontal scaling. Gated by
  `suppliers.outbox.enabled=true` so unit tests never see it.

### I. ArchUnit — additional rules
- No setters in `domain.model`.
- No Jakarta Validation or JPA in `domain`.
- Every `domain.exception.*Exception` must extend `DomainException`.
- REST DTOs cannot leak into `domain` or `application`.
- `*Service` classes live exclusively in `application.service`.

### J. Profiles & secrets
- `application-docker.yml` (profile `docker`, activated by docker-compose) holds the container-network settings (datasource host, country-service URL).
- `application-prod.yml` requires every secret via env placeholder (no defaults).
- `flyway.clean-disabled: true` in prod.
- Error responses do not leak messages or stack traces in prod.

### K. Frontend
`PotentialSuppliersService` targets `/api/v1`, forwards optional
`country`/`maxRating` filters and supports keyset pagination through `cursor`.
The wire-level rule is enforced in `potential-suppliers.service.spec.ts`:
either `offset` or `cursor` is on the request — never both.

## 13bis. Performance & scalability for the 100k–1M scale

The challenge calls out a supplier volume in the 100k–1M range as an evaluation
criterion. The implementation choices below were made with that scale in mind
and **the benchmark folder ships the tooling to verify them**, not just claim them.

### Diagnosis — where the cost is on the hot query

`/api/v1/suppliers/potential` is the workload that scales with row count. The
SQL inside `PotentialSupplierJdbcQuery.SCORED_CTE` has three potentially
expensive sub-steps:

1. `SELECT DISTINCT country, annual_turnover` → hash aggregate over the full
   table. At 1M rows it dominates the latency budget on a cold cache.
2. `DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover)` → sort
   per country group; cheap once distinct turnovers are materialised.
3. `ORDER BY score DESC, duns ASC` with `score` being a runtime expression →
   **no index can back this ORDER BY**. Postgres must materialise all
   eligible rows and sort them. The cost grows linearly with the number of
   eligible rows.

### Iteration-2 mitigations (already implemented)

| # | Change | File | Effect |
|---|---|---|---|
| 1 | Partial covering index on eligible suppliers | `V4__suppliers_perf_indexes.sql` | Removes the seq-scan on the filter; smaller than full index because DISQUALIFIED rows are excluded |
| 2 | `rating_multiplier` as STORED generated column | `V5__suppliers_rating_multiplier.sql` | Drops the per-row CASE in the score expression; mapping becomes a single column read |
| 3 | Keyset pagination (already done in iter-1) | `PotentialSupplierJdbcQuery.KEYSET_SQL` | Avoids OFFSET skip cost and the per-page `COUNT(*) OVER ()` |
| 4 | `Cache-Control: max-age=30, public` on `/potential` | `SuppliersController.potential` | A CDN / reverse proxy collapses repeated identical queries (back/forward, debounced typing) — zero DB hits |
| 5 | `@Transactional(readOnly = true)` on all read paths | `FindPotentialSuppliersService`, `Get*Service` | Lets Spring route reads to a replica when `AbstractRoutingDataSource` is wired later (no code change needed) |
| 6 | HikariCP sized with formula, not magic numbers | `application-prod.yml` | `(cores*2)+spindles` → 10 connections per 4-vCPU pod, with leak detection and lifetime caps |
| 7 | Synthetic 1M-row seed + k6 load test + EXPLAIN script | `backend/benchmark/` | The performance claim becomes testable, not narrative |

### Iteration-3 wins (designed, not yet implemented)

These require more invasive changes and are deliberately left as ADRs so they
can be reviewed before merging.

#### ADR-005 — Materialised view for `country_turnover_rank`

The DISTINCT+DENSE_RANK over the whole `suppliers` table is recomputed on
every page. The rank only changes when a supplier's turnover changes, which
in this domain is **monthly at most**. Replacing the CTE with a materialised
view refreshed on a schedule (or after a known DDL event) cuts the per-query
cost dramatically:

```sql
CREATE MATERIALIZED VIEW mv_country_turnover_rank AS
SELECT country, annual_turnover,
       DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover) AS turnover_rank
FROM (SELECT DISTINCT country, annual_turnover FROM suppliers) d
WITH DATA;

CREATE UNIQUE INDEX ON mv_country_turnover_rank (country, annual_turnover);

-- Refresh strategy options (decision pending):
--   1) REFRESH MATERIALIZED VIEW CONCURRENTLY mv_country_turnover_rank;  every 10 min via @Scheduled
--   2) Trigger-based incremental refresh on supplier INSERT/UPDATE (more code, fresher data)
```

Expected impact: removes the dominant cost. The query becomes a join between
the eligible-supplier index and a small (<10k rows) ranked table.

#### ADR-006 — `score` as STORED generated column + expression index

The remaining cost after ADR-005 is the runtime `ORDER BY score DESC`. If
`score` were stored, an index `(score DESC, duns ASC)` would let Postgres
serve the first page with an Index Scan and zero sorting:

```sql
ALTER TABLE suppliers
    ADD COLUMN score NUMERIC GENERATED ALWAYS AS (
        annual_turnover * 0.1 * rating_multiplier
        -- bonus would require a trigger or the MV approach above
    ) STORED;

CREATE INDEX ix_suppliers_score ON suppliers (score DESC, duns ASC)
    WHERE status <> 'DISQUALIFIED';
```

Caveat: the country-rank bonus (1.25 if the supplier is in the two cheapest
turnovers of its country) cannot live in a generated column because it
depends on **other rows**. Two ways out:

- Pre-compute `is_in_bonus_tier` via a periodic UPDATE driven by the
  materialised view (ADR-005). Then `score` becomes a true generated column.
- Two indexes: one for `score_without_bonus DESC` (cheap, exact) and resolve
  the bonus at query time only for the few candidates that survive the index
  scan (top-N retrieval). Requires query rewrite.

Pending a benchmark to decide which one wins.

#### ADR-007 — Read replica routing

The application already separates reads (`readOnly=true`) from writes. Adding
a second `DataSource` and `AbstractRoutingDataSource` lets every
`/api/v1/suppliers/potential` request hit the replica. Zero changes in the
application layer.

### How a reviewer can validate the claim

```bash
# Boot the stack and seed 1M rows
docker compose up -d
docker exec -i $(docker compose ps -q db) \
    psql -U suppliers -d suppliers < backend/benchmark/seed_1M.sql

# Capture latencies
docker run --rm -i --network host grafana/k6 run - \
    < backend/benchmark/load.js

# Validate the planner picked the right index
docker exec -i $(docker compose ps -q db) \
    psql -U suppliers -d suppliers < backend/benchmark/explain.sql
```

SLO proposal — wired as k6 thresholds, so a regression fails the run:

| Scenario          | p95     | p99     |
|-------------------|---------|---------|
| offset_shallow    | <200 ms | <400 ms |
| offset_deep       | <800 ms | —       |
| keyset            | <200 ms | <400 ms |

## 14. Flyway conventions

- **Naming**: `V{n}__{kebab-case-description}.sql`. Production migrations also
  prepend a ticket id: `V12__SUP-345_add_score_column.sql`.
- **Indexes on large tables**: always `CREATE INDEX CONCURRENTLY`; mark
  migration as repeatable / out-of-transaction (`-- Flyway: ignoreMissingMigrations`
  patterns documented per migration when needed).
- **No destructive changes** in a single deploy. Schema evolution follows the
  expand → migrate → contract pattern (add column → backfill → switch reads → drop).
- **Reversibility**: a `Uxx__*.sql` undo script accompanies every
  non-trivial migration (requires Flyway Teams in production; the file is
  kept regardless so the rollback path is explicit on the PR).
- `spring.flyway.clean-disabled: true` in `application-prod.yml` to forbid
  accidental schema wipes.

## 15. ADRs referenced from code

| ADR | Title                                        | Status   |
|-----|----------------------------------------------|----------|
| 001 | JPA entities ≠ domain aggregates             | Accepted |
| 002 | Single-module Maven + ArchUnit boundaries    | Accepted |
| 003 | Read port separate from write repository     | Accepted |
| 004 | Offset pagination kept for back-compat       | Accepted |
| 005 | Score materialisation (generated col / view) | Proposed |
| 006 | Auth model: OAuth2 Resource Server in prod   | Accepted |
| 007 | Country-service fallback: fail-closed        | Accepted |
| 008 | Transactional outbox for domain events       | Accepted |

---

# 🇪🇸 Versión en español

> Reto técnico para la posición de Tech Lead Full-Stack Engineer en Inditex.

<a id="es-1-cómo-ejecutar"></a>

## 1. Cómo ejecutar

Toda la solución se orquesta con **Docker Compose**. No hace falta instalar JDK, Node ni la base de datos localmente.

```bash
docker compose up --build
```

Esperar a que el healthcheck del `backend` esté en verde (≈ 30 s la primera vez). Luego:

- **Frontend**: http://localhost:4200
- **API**: http://localhost:8080/api/v1
- **OpenAPI UI**: http://localhost:8080/swagger-ui.html
- **Actuator** (health/metrics/prometheus): http://localhost:8080/actuator

Para desarrollo local sin Docker:
- **Backend**: `cd backend && mvn spring-boot:run`
- **Frontend**: `cd frontend && npm install && npm start` (proxy a `localhost:8080`)
- **Tests** (backend): `cd backend && mvn test` — 88 unit + 11 IT con Testcontainers (los IT se saltan automáticamente si no hay Docker).

## 2. Stack tecnológico

- **Backend**: Java 25, Spring Boot 4.0, Spring Web, Spring Data JPA + JDBC, Flyway, Resilience4j (circuit breaker + retry + bulkhead), Caffeine cache, Jakarta Validation, Spring Security + OAuth2 Resource Server, Micrometer + OTLP (Prometheus + tracing), driver PostgreSQL.
- **Frontend**: Angular 18 standalone (signals + control flow `@for/@if`), TypeScript 5.5, servido por nginx en producción.
- **Base de datos**: PostgreSQL 16 con esquema gestionado por Flyway.
- **Servicio externo de países**: provisto como mock WireMock (`countryservice/mappings/*.json`).
- **Tests**: JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL), ArchUnit (11 reglas).

## 3. Arquitectura — hexagonal / DDD

```
backend/src/main/java/com/inditex/suppliers
├── domain          # agregados, VOs y excepciones — POJO puro, sin Spring ni JPA
├── application     # casos de uso, puertos in/out, DTOs, util (CursorCodec, Limits)
└── infrastructure  # adapters: REST, JPA, JDBC read-model, cliente country-service,
                    # outbox, configuración Spring, observabilidad
```

**Principios clave:**
- El dominio no conoce a Spring, Jakarta Validation, JPA ni Jackson. **ArchUnit lo verifica en tiempo de compilación** (`HexagonalArchitectureTest` — 11 reglas).
- Los puertos de entrada (`*UseCase`) son la API pública; los servicios de aplicación los implementan.
- Los puertos de salida (`CandidateRepository`, `SupplierRepository`, `CountryGateway`, `PotentialSupplierQuery`, `DomainEventOutbox`, `SupplierMetricsPort`) abstraen la infraestructura.
- **Lectura y escritura separadas (CQRS ligero)**:
  - lado de escritura → JPA + agregados,
  - lado de lectura → un puerto separado (`PotentialSupplierQuery`) con un único statement SQL nativo, sin pasar por el modelo JPA.
- Esto mantiene al dominio libre de las preocupaciones de `OFFSET/LIMIT/score` y al modelo de lectura libre del coste de hidratar agregados.

## 4. Reglas de negocio — dónde vive cada una

| Regla                                                                                                | Aplicada en                                                              |
|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| DUNS es un entero de 9 dígitos                                                                       | `Duns` (VO) — falla rápido al construir                                  |
| País es ISO 3166-1 alpha-2                                                                           | `CountryCode` (VO)                                                       |
| Facturación anual ≥ 0                                                                                | `AnnualTurnover` (VO)                                                    |
| Un candidato REFUSED puede re-aplicar                                                                | `CreateCandidateService` + factoría `Candidate.reapplyFromRefused`       |
| Un proveedor BANNED rechaza nuevas candidaturas                                                      | `CreateCandidateService` (chequea `SupplierRepository.isBanned`)         |
| Aceptación con facturación < 1 M € exige rating ≥ B → `ON_PROBATION`                                 | `Candidate.accept` (FSM en el agregado)                                  |
| Rating < B con facturación < 1 M € → excepción de dominio                                            | `Candidate.accept`                                                       |
| País baneado → el candidato pasa a proveedor en estado `DISQUALIFIED`                                | `Candidate.accept(banned=true)`                                          |
| Sólo se puede banear un proveedor `ON_PROBATION`                                                     | `Supplier.ban` — invariante del agregado                                 |
| `score = facturación × 0.1 × rating_mult × bonus(1.25 si rank ≤ 2)`                                  | SQL en `PotentialSupplierJdbcQuery` (más `rating_multiplier` materializado) |
| El bonus aplica a las **dos facturaciones únicas más bajas por país**                                | `DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover)`      |
| Sólo proveedores **no DISQUALIFIED** son elegibles                                                   | Filtro WHERE en el CTE + índice parcial                                  |

## 5. Mapping de códigos HTTP (`GlobalExceptionHandler`)

| Situación                                                       | Status | Body                                          |
|-----------------------------------------------------------------|--------|-----------------------------------------------|
| Validación de input (bean validation, formato)                  | 400    | `{ info: "<mensaje>" }`                       |
| Recurso no encontrado por id                                    | 404    | `{ info: "..." }`                             |
| Conflicto de identidad (ya existe, estado FSM inválido)         | 409    | `{ info: "..." }`                             |
| Regla de negocio rechazada por dato externo (país, facturación) | 422    | `{ info: "..." }`                             |
| Circuit breaker abierto / upstream caído                        | 503    | (vía fallback fail-closed en candidates)      |
| Inesperado                                                      | 500    | `{ info: "Internal error" }`                  |

**Decisión consciente**: 422 (no 400) para reglas que dependen de **datos externos** (status del país, umbral de facturación). El cliente debe saber que la petición está bien formada pero el dominio la rechaza.

## 6. Resiliencia, concurrencia y rendimiento

- **Caffeine cache** (`@Cacheable("countries")`, 10 min, 256 entradas) delante del gateway de países: durante una aceptación masiva, las lookups repetidas reutilizan la respuesta upstream.
- **Resilience4j Circuit Breaker** envolviendo el adapter del country-service (umbral 50% de fallos, ventana abierta 30 s). Cuando se abre, el **fallback es fail-closed**: trata cada país como baneado y rechaza la aceptación. Esta es una decisión compliance-aware (mejor un falso negativo que un falso positivo en un dominio regulado). Métrica `country_service_fallback_total` para alertar.
- **Optimistic locking** (`@Version` en los agregados) — dos aceptaciones simultáneas del mismo candidato no pueden ganar las dos.
- **Paginación keyset firmada** en `/api/v1/suppliers/potential` — el cursor lleva HMAC-SHA256 (`v1:`), prefijado por versión. La rotación de clave invalida todos los cursores activos, que es justo lo deseado en una rotación de secretos.
- **Outbox transaccional** (`outbox_events`) — los eventos de dominio se escriben en la misma transacción que el cambio del agregado, y un poller `@Scheduled` con `FOR UPDATE SKIP LOCKED` los publica de forma asíncrona (preparado para horizontal scaling).
- **Tracing distribuido** vía Micrometer + OpenTelemetry OTLP. Sampling 100% en dev, 10% head-sampling en prod.

La regla de dependencias hexagonal la verifica **ArchUnit** en cada `mvn test` (`HexagonalArchitectureTest`) con 11 reglas: arquitectura por capas + prohibiciones explícitas de Spring/JPA/web/Jackson en dominio + reglas sobre setters, jerarquía de excepciones, etc. Un PR que importa `jakarta.persistence.Entity` en un agregado **rompe el build**, no la code review.

## 7. Frontend

SPA Angular 18 construida alrededor del endpoint `findPotentialSuppliers`.

- **Standalone components** (sin NgModules), **signals** para estado local, **`@for/@if`** para flujo.
- Tabla con filtrado por país y rating, ordenación por columna, paginación.
- Llama al backend vía `/api/v1` (proxy nginx en prod, proxy de Angular CLI en dev).
- Soporta filtros server-side (`country`, `maxRating`) y paginación **keyset** (`cursor`) — `offset` y `cursor` son mutuamente exclusivos en el wire.
- **Estados**: idle (sin búsqueda aún), loading, vacío, error (con el mensaje del API).
- Marcado accesible: labels explícitos, `aria-live` para el contador de resultados, navegación por teclado.

## 8. Testing

```bash
cd backend && mvn test
```

- 88 tests unitarios y de slice (controllers, servicios, VOs, FSM del agregado).
- 11 ITs con **Testcontainers** (PostgreSQL real, Flyway aplicado): pruebas extremo a extremo del query model y del flujo de aceptación. Se saltan automáticamente si Docker no está disponible.
- 11 reglas **ArchUnit**.
- **Frontend**: 26 specs verdes (Karma + jasmine), incluyendo wire-level del servicio (filtros, cursor, ofuscación de offset cuando hay cursor).

## 9. Decisiones de diseño / trade-offs

- **Entidades JPA ≠ agregados de dominio.** Sí, es más código; pero mantiene el dominio libre de JPA y permite evolucionar la persistencia independientemente. El mapping (`PersistenceMapper`) es procedural y trivial de leer.
- **Maven mono-módulo** con layering por paquetes, verificado por **ArchUnit**. Un build multi-módulo Gradle/Maven impondría límites en tiempo de compilación pero multiplica la superficie de configuración; ArchUnit da la mayor parte de la seguridad a una fracción del coste.
- **404 del country-service → 422.** No se trata como "país OK"; sería una vulnerabilidad de compliance.
- **Filtros del frontend son client-side sobre la página actual** + **filtros server-side** (`country`, `maxRating`) cuando se quieran aplicar a todo el dataset. La paginación es siempre server-side.
- **Fail-closed sobre fail-open** en el fallback del country-service — justificado en ADR-007.
- **`Long total` nullable** + `@JsonInclude(NON_NULL)` en lugar de un sentinel `-1`: en modo keyset, el campo simplemente no aparece en la respuesta.

## 10. Qué queda fuera de alcance deliberadamente

- Autenticación / autorización **activa** (la spec no la pide). El *hook* está: `SecurityConfig` con perfil `prod` que activa OAuth2 Resource Server con scopes — basta con un `SPRING_PROFILES_ACTIVE=prod` y un `OIDC_ISSUER_URI`.
- i18n en el frontend (moneda formateada como `es-ES`, copy en inglés).
- E2E con Playwright (la suite de Karma cubre unit; e2e es el siguiente paso natural).
- Rate limiting (bucket4j), audit log estructurado, SBOM (cyclonedx) — documentados en el backlog.

## 11. Backlog / próximos pasos

1. Tests de contrato (Spring Cloud Contract / Pact) contra la spec OpenAPI del country-service.
2. Materialización del `score` como columna stored + índice — ver ADR-005/006.
3. Playwright e2e encima de la suite Karma existente.
4. Rate limiting con `bucket4j-spring-boot-starter`.
5. Test del outbox con Testcontainers (atomicidad tx + concurrencia con `SKIP LOCKED`).
6. CI/CD: pipeline GitHub Actions con SBOM (cyclonedx) + dependency-check + Trivy.
7. Mutation testing con PIT — saber qué % de mutantes sobreviven, no sólo cuántos tests pasan.

## 12. Sesión de ejemplo

```bash
# Levantar el stack
docker compose up --build -d

# Solicitar como candidato
curl -X POST http://localhost:8080/api/v1/candidates \
  -H 'Content-Type: application/json' \
  -d '{"duns":123456789,"name":"Acme","country":"ES","annualTurnover":1500000}'
# → 201 Created

# Aceptar con rating A → Active
curl -X POST http://localhost:8080/api/v1/candidates/123456789/accept \
  -H 'Content-Type: application/json' \
  -d '{"sustainabilityRating":"A"}'
# → 204 No Content

# Consultar proveedores potenciales para un pedido de 1k € (offset)
curl 'http://localhost:8080/api/v1/suppliers/potential?rate=1000&limit=10&offset=0'

# Lo mismo con filtros server-side y paginación keyset:
curl 'http://localhost:8080/api/v1/suppliers/potential?rate=1000&limit=10&country=ES&maxRating=B&cursor='

# Docs OpenAPI live:
#   http://localhost:8080/swagger-ui.html
#   http://localhost:8080/v3/api-docs
```

## 13. Iteración 2 — mejoras tras revisión arquitectónica

### A. Paginación keyset, sealed types, cursors firmados
- `application.port.out.PotentialSupplierQuery` expone un **ADT sellado `Pagination`** (`Offset | Keyset`); el adapter SQL hace pattern-matching exhaustivo.
- `application.dto.PotentialSupplierPage.total` es **`Long` nullable** y el `PaginationDto` REST lleva `@JsonInclude(NON_NULL)`: las respuestas keyset omiten `total` en lugar de devolver un `-1` como sentinela.
- `application.util.CursorCodec` produce cursors **versionados (`v1:`) y firmados con HMAC-SHA256**. Cualquier manipulación se rechaza. La clave viene de `suppliers.cursor.key` (env en prod).
- `application.util.PotentialSupplierLimits` es la **única fuente de verdad** para `MIN_RATE`/`MIN_LIMIT`/`MAX_LIMIT`; las anotaciones del controller y la validación del servicio la referencian.

### B. API versionada
Todos los `@RestController` están bajo `/api/v1/...`. Actuator se queda en raíz (es operacional, no contrato público).

### C. Nota honesta de complejidad
`PotentialSupplierJdbcQuery` documenta que la estrategia keyset **no es O(log n)** en sentido estricto: `score` es una expresión runtime y ningún índice puede respaldar la comparación. El keyset sigue ganando frente al `OFFSET` profundo porque evita el coste del skip y el `COUNT(*)` por página. La solución production-grade (columna generada con índice, o MV refrescada) está documentada en **ADR-005**.

### D. Hook de seguridad
`infrastructure.config.SecurityConfig` con dos filter chains:
- **Default (dev / docker)** — `permitAll`, CSRF off. Coincide con la spec.
- **Perfil `prod`** — OAuth2 Resource Server con JWT; matchers de método/path para `SCOPE_suppliers:read` y `SCOPE_suppliers:write`. Activado con `SPRING_PROFILES_ACTIVE=prod` + `OIDC_ISSUER_URI`. **ADR-006**.

### E. Resiliencia — fallback fail-closed
`CountryGatewayAdapter` con `fallbackMethod` explícito que **trata el upstream como baneando a todos** cuando el circuit se abre. Decisión compliance-aware. El contador `country_service_fallback_total` permite alertar.

### F. Métricas de negocio
`SupplierMetricsPort` (out port) + `MicrometerSupplierMetricsAdapter` emiten:
`suppliers_transitions_total{type=candidate_created|candidate_accepted|candidate_refused|supplier_banned}`. Cableado desde los servicios de aplicación para que rutas batch / consumers de eventos generen la misma telemetría que la ruta HTTP.

### G. Tracing distribuido
Micrometer Tracing + exporter OpenTelemetry OTLP. Endpoint por defecto en dev: `http://localhost:4318/v1/traces`. Sampling: 100% dev, 10% head-sampling en prod (tail-based esperado en el collector).

### H. Outbox transaccional
- Migración `V3__outbox.sql` con tabla `outbox_events` e índice parcial sobre `status = 'PENDING'`.
- `application.port.out.DomainEventOutbox` se invoca en la misma transacción que el cambio del agregado en `AcceptCandidateService` y `BanSupplierService`.
- `OutboxPublisher` es un `@Scheduled` con `FOR UPDATE SKIP LOCKED` para escalado horizontal. Gateado por `suppliers.outbox.enabled=true`.

### I. ArchUnit — reglas adicionales
- Sin setters en `domain.model`.
- Sin Jakarta Validation ni JPA en `domain`.
- Toda `domain.exception.*Exception` debe extender `DomainException`.
- Los DTOs REST no pueden filtrarse a `domain` ni `application`.
- Las clases `*Service` viven exclusivamente en `application.service`.

### J. Perfilesdocker.yml` (perfil `docker`, activado por docker-compose) contiene los ajustes de la red de contenedores (host del datasource, URL del country-service).
- `application- y secretos
- `application-prod.yml` exige cada secreto vía placeholder env (sin defaults).
- `flyway.clean-disabled: true` en prod.
- Las respuestas de error no exponen mensajes ni stack traces en prod.

### K. Frontend
`PotentialSuppliersService` apunta a `/api/v1`, reenvía filtros opcionales `country`/`maxRating` y soporta paginación keyset vía `cursor`. La regla wire-level está testeada en `potential-suppliers.service.spec.ts`: o `offset` o `cursor` en la request — nunca ambos.

## 13bis. Performance y escalabilidad para 100k–1M

El reto menciona explícitamente un volumen de **100.000 a 1.000.000** de proveedores como criterio de evaluación. Las decisiones siguientes se tomaron con esa escala en mente y **la carpeta `backend/benchmark/` incluye el utillaje para verificarlo**, no sólo para afirmarlo.

### Diagnóstico — dónde está el coste de la query hot

`/api/v1/suppliers/potential` es la carga que escala con número de filas. La SQL de `PotentialSupplierJdbcQuery.SCORED_CTE` tiene tres sub-pasos potencialmente caros:

1. `SELECT DISTINCT country, annual_turnover` → hash aggregate sobre la tabla entera. A 1M filas, en caché fría es el coste dominante.
2. `DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover)` → sort por grupo de país; barato una vez materializados los distinct.
3. `ORDER BY score DESC, duns ASC` siendo `score` expresión runtime → **ningún índice puede respaldar este ORDER BY**. Postgres tiene que materializar todas las filas elegibles y ordenarlas. El coste crece linealmente con el número de filas elegibles.

### Mitigaciones iteración 2 (ya implementadas)

| # | Cambio | Archivo | Efecto |
|---|---|---|---|
| 1 | Índice parcial covering sobre suppliers elegibles | `V4__suppliers_perf_indexes.sql` | Elimina el seq-scan del filtro; ~30-40% más pequeño que un full index al excluir DISQUALIFIED |
| 2 | `rating_multiplier` como columna generada STORED | `V5__suppliers_rating_multiplier.sql` | Elimina el `CASE` por fila en la expresión del score |
| 3 | Paginación keyset (iter 1) | `PotentialSupplierJdbcQuery.KEYSET_SQL` | Evita el skip-cost del OFFSET y el `COUNT(*) OVER ()` por página |
| 4 | `Cache-Control: max-age=30, public` en `/potential` | `SuppliersController.potential` | Un CDN / reverse proxy colapsa queries idénticas repetidas (back/forward, debounce de teclado) — cero impacto en DB |
| 5 | `@Transactional(readOnly = true)` en todas las rutas de lectura | `FindPotentialSuppliersService`, `Get*Service` | Permite enrutar lecturas a réplica con `AbstractRoutingDataSource` sin cambios en la app |
| 6 | HikariCP dimensionado con fórmula, no número mágico | `application-prod.yml` | `(cores*2)+spindles` → 10 conexiones por pod de 4 vCPU, con leak detection y lifetime cap |
| 7 | Seed sintético 1M filas + load test k6 + EXPLAIN | `backend/benchmark/` | El claim de "1M filas" se vuelve testeable, no narrativo |

### Mejoras iteración 3 (diseñadas, no implementadas)

Cambios más invasivos, deliberadamente dejados como ADRs:

- **ADR-005 — Vista materializada para `country_turnover_rank`** refrescada cada N min. Elimina el coste dominante (DISTINCT+DENSE_RANK sobre 1M filas). La query pasa a ser un join entre el índice de elegibles y una tabla rankeada pequeña.
- **ADR-006 — `score` como columna stored** + índice `(score DESC, duns ASC)`. Habilita Index Scan en el ORDER BY. Caveat real documentado: el bonus depende de **otras filas**, así que requiere o un UPDATE periódico, o dos índices con top-N rewrite. Pendiente benchmark para decidir.
- **ADR-007 — Read replica routing** vía `AbstractRoutingDataSource`. La aplicación ya separa lecturas (`readOnly=true`), así que es **coste cero en código de aplicación**.

### Cómo lo valida un revisor

```bash
docker compose up -d
docker exec -i $(docker compose ps -q db) \
    psql -U suppliers -d suppliers < backend/benchmark/seed_1M.sql
docker run --rm -i --network host grafana/k6 run - < backend/benchmark/load.js
docker exec -i $(docker compose ps -q db) \
    psql -U suppliers -d suppliers < backend/benchmark/explain.sql
```

SLOs comprometidos (cableados como thresholds de k6 → fallan el run si se incumplen):

| Escenario        | p95     | p99     |
|------------------|---------|---------|
| offset_shallow   | <200 ms | <400 ms |
| offset_deep      | <800 ms | —       |
| keyset           | <200 ms | <400 ms |

## 14. Convenciones de Flyway

- **Naming**: `V{n}__{kebab-case-description}.sql`. Las migraciones de producción anteponen el ticket: `V12__SUP-345_add_score_column.sql`.
- **Índices en tablas grandes**: siempre `CREATE INDEX CONCURRENTLY`; marcar la migración como out-of-transaction.
- **Sin cambios destructivos** en un único deploy. La evolución de esquema sigue el patrón expand → migrate → contract (añadir columna → backfill → cambiar reads → drop).
- **Reversibilidad**: cada migración no trivial se acompaña de un `Uxx__*.sql` con el undo. Requiere Flyway Teams en producción; el archivo se mantiene de todas formas para que la ruta de rollback sea explícita en el PR.
- `spring.flyway.clean-disabled: true` en `application-prod.yml` para impedir wipes accidentales.

## 15. ADRs referenciados en el código

| ADR | Título                                                | Estado     |
|-----|-------------------------------------------------------|------------|
| 001 | Entidades JPA ≠ agregados de dominio                  | Aceptado   |
| 002 | Maven mono-módulo + límites con ArchUnit              | Aceptado   |
| 003 | Puerto de lectura separado del repositorio de escritura | Aceptado |
| 004 | Paginación offset mantenida por retrocompatibilidad   | Aceptado   |
| 005 | Materialización del score (columna generada / MV)     | Propuesto  |
| 006 | Modelo de auth: OAuth2 Resource Server en prod        | Aceptado   |
| 007 | Fallback del country-service: fail-closed             | Aceptado   |
| 008 | Outbox transaccional para eventos de dominio          | Aceptado   |

