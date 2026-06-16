-- Validation of the planner choices on the hot query.
-- Run after seed_1M.sql.

-- Shallow offset (typical UI).
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
WITH country_turnover_rank AS (
    SELECT country, annual_turnover,
           DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover ASC) AS turnover_rank
    FROM (SELECT DISTINCT country, annual_turnover FROM suppliers) d
),
scored AS (
    SELECT s.duns, s.name, s.country, s.annual_turnover, s.sustainability_rating, s.status,
           (s.annual_turnover * 0.1 * s.rating_multiplier
            * CASE WHEN ctr.turnover_rank <= 2 THEN 1.25 ELSE 1.0 END) AS score
    FROM suppliers s
    JOIN country_turnover_rank ctr ON ctr.country = s.country AND ctr.annual_turnover = s.annual_turnover
    WHERE s.status <> 'DISQUALIFIED' AND s.annual_turnover > 500000
)
SELECT *, COUNT(*) OVER () FROM scored ORDER BY score DESC, duns ASC LIMIT 10 OFFSET 0;

-- Deep offset (where the OFFSET tax shows up).
EXPLAIN (ANALYZE, BUFFERS)
WITH country_turnover_rank AS (
    SELECT country, annual_turnover,
           DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover ASC) AS turnover_rank
    FROM (SELECT DISTINCT country, annual_turnover FROM suppliers) d
),
scored AS (
    SELECT s.duns, (s.annual_turnover * 0.1 * s.rating_multiplier
                    * CASE WHEN ctr.turnover_rank <= 2 THEN 1.25 ELSE 1.0 END) AS score
    FROM suppliers s
    JOIN country_turnover_rank ctr ON ctr.country = s.country AND ctr.annual_turnover = s.annual_turnover
    WHERE s.status <> 'DISQUALIFIED' AND s.annual_turnover > 500000
)
SELECT * FROM scored ORDER BY score DESC, duns ASC LIMIT 10 OFFSET 5000;

-- Pure index check — should hit ix_suppliers_eligible.
EXPLAIN (ANALYZE, BUFFERS)
SELECT duns FROM suppliers
WHERE status <> 'DISQUALIFIED' AND country = 'ES' AND sustainability_rating <= 'B' AND annual_turnover > 500000
LIMIT 100;
