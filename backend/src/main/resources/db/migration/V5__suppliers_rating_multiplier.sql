-- Pre-compute the sustainability_rating → multiplier mapping as a STORED
-- generated column. Saves Postgres from evaluating the CASE expression per row
-- on every potential-suppliers request.
--
-- The mapping is part of the business contract (1.0 / 0.75 / 0.5 / 0.25 / 0.1)
-- and lives nowhere else in the code now — the query uses this column.
-- Changing the mapping requires a migration, which is the right level of
-- ceremony for a domain constant.

ALTER TABLE suppliers
    ADD COLUMN rating_multiplier NUMERIC(4,3) GENERATED ALWAYS AS (
        CASE sustainability_rating
            WHEN 'A' THEN 1.000
            WHEN 'B' THEN 0.750
            WHEN 'C' THEN 0.500
            WHEN 'D' THEN 0.250
            WHEN 'E' THEN 0.100
        END
    ) STORED;
