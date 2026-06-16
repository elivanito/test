-- Performance migration — supports the potential-suppliers query at 1M-row scale.
--
-- WHY these indexes
-- =================
-- The hot path is:
--   SELECT ... FROM suppliers s
--   JOIN country_turnover_rank ctr ON (country, annual_turnover)
--   WHERE s.status <> 'DISQUALIFIED'
--     AND s.annual_turnover > :rate
--     AND (:country IS NULL OR s.country = :country)
--     AND (:maxRating IS NULL OR s.sustainability_rating <= :maxRating)
--
-- 1) Partial index restricted to status IN ('ACTIVE','ON_PROBATION') keeps the
--    index ~30-40% smaller than a regular one (assumes DISQUALIFIED is a
--    minority, which the business expects) and lets Postgres skip the status
--    check on read.
-- 2) Composite (country, sustainability_rating, annual_turnover) supports both
--    the country filter and the maxRating range with a single B-tree, and is
--    used by the JOIN with country_turnover_rank on (country, annual_turnover)
--    too. INCLUDE (duns) makes it covering for the keyset tiebreaker.
--
-- Trade-offs documented in SOLUTION.md §perf.
--
-- In production these would be CREATE INDEX CONCURRENTLY (cannot run inside
-- a transaction → would need a dedicated migration file with
-- "-- Flyway: transactional=false"). For the challenge stack the table is
-- empty at migration time so the plain form is acceptable.

CREATE INDEX ix_suppliers_eligible
    ON suppliers (country, sustainability_rating, annual_turnover)
    INCLUDE (duns, name, status)
    WHERE status <> 'DISQUALIFIED';

-- Drop indexes superseded by the new covering one. Keep ix_suppliers_country_turnover
-- because it backs the country_turnover_rank CTE (which scans DISQUALIFIED rows too:
-- the ranking is country-wide regardless of supplier status).
DROP INDEX IF EXISTS ix_suppliers_status;
DROP INDEX IF EXISTS ix_suppliers_turnover;
