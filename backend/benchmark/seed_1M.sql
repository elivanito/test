-- Synthetic 1M-row dataset for benchmarking the potential-suppliers query.
--
-- NOT a Flyway migration: this file is opt-in. Run it manually with:
--   docker exec -i suppliers-db psql -U suppliers -d suppliers < backend/benchmark/seed_1M.sql
--
-- The data distribution mimics the real domain:
--   * 20 countries (ISO codes, weighted: ES/PT/FR/IT/DE more common, long tail).
--   * Turnover log-normal between 50k and 50M.
--   * Sustainability rating distribution A:15% B:25% C:35% D:20% E:5%.
--   * 8% ON_PROBATION, 2% DISQUALIFIED, rest ACTIVE.
--
-- Verify with:
--   EXPLAIN (ANALYZE, BUFFERS) <the potential-suppliers SQL>
-- before and after each migration to quantify the win.

TRUNCATE suppliers;

INSERT INTO suppliers (duns, name, country, annual_turnover, sustainability_rating, status)
SELECT
    100000000 + g                                                                AS duns,
    'Supplier #' || g                                                            AS name,
    (ARRAY['ES','PT','FR','IT','DE','GB','NL','BE','PL','CZ',
           'AT','CH','SE','DK','NO','FI','IE','GR','HU','RO'])
        [1 + (g % 20)]                                                           AS country,
    -- Log-normal turnover, integer euros, between ~50k and ~50M.
    GREATEST(50000, (50000 * exp((random() * 6.9)))::BIGINT)                     AS annual_turnover,
    (ARRAY['A','A','A','B','B','B','B','B','C','C','C','C','C','C','C',
           'D','D','D','D','E'])
        [1 + ((g * 31) % 20)]                                                    AS sustainability_rating,
    CASE
        WHEN (g % 50) = 0 THEN 'DISQUALIFIED'
        WHEN (g % 12) = 0 THEN 'ON_PROBATION'
        ELSE 'ACTIVE'
    END                                                                          AS status
FROM generate_series(1, 1000000) AS g;

-- Refresh planner stats so EXPLAIN reflects the real distribution.
ANALYZE suppliers;

-- Sanity check
SELECT count(*) AS total,
       count(*) FILTER (WHERE status = 'ACTIVE')        AS active,
       count(*) FILTER (WHERE status = 'ON_PROBATION')  AS on_probation,
       count(*) FILTER (WHERE status = 'DISQUALIFIED')  AS disqualified
FROM suppliers;
