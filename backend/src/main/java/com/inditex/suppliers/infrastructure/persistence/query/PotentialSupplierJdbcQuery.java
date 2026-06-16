package com.inditex.suppliers.infrastructure.persistence.query;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.dto.PotentialSupplierView;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery;
import com.inditex.suppliers.application.util.CursorCodec;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the potential-suppliers page entirely in SQL:
 *
 *   1. Compute, per country, the two lowest UNIQUE annual_turnover values
 *      (DENSE_RANK over distinct (country, turnover) pairs).
 *   2. Join back, filter by eligibility (turnover > rate, status <> DISQUALIFIED,
 *      optional country and maxRating server-side filters).
 *   3. Compute score = turnover * 0.1 * rating_constant * bonus(1.25 if rank <= 2 else 1.0).
 *   4. Order by score DESC, duns ASC.
 *   5. Two pagination strategies (dispatched on a sealed ADT, not a nullable flag):
 *      - <b>Offset</b>: LIMIT/OFFSET + window COUNT(*) for {@code total}.
 *      - <b>Keyset</b>: WHERE (score, duns) lexicographically beyond the cursor,
 *        fetch {@code limit + 1} rows, drop the extra to detect {@code hasNext}.
 *        No COUNT(*) per page.
 *
 * <h3>Honest complexity note</h3>
 * <p>The {@code score} column is a runtime expression, not a stored column, so no
 * btree index can back the {@code score < :cursorScore} predicate. The keyset SQL
 * still has to scan the eligible CTE; its real cost is
 * {@code O(eligible_rows · log limit)} not {@code O(log n)}. The reason to prefer
 * keyset here is that <b>it does not get worse for deep pages</b> (no OFFSET
 * skip cost) and avoids the per-page COUNT(*).</p>
 *
 * <p>The production-grade fix is to materialize the score: either as a generated
 * column with an expression index, or as a refreshed materialized view. Filed as
 * follow-up — see ADR-005 in SOLUTION.md.</p>
 */
@Component
public class PotentialSupplierJdbcQuery implements PotentialSupplierQuery {

    /**
     * Shared CTE: scored eligible suppliers, after applying the server-side filters.
     * The bonus is computed against ALL suppliers of the country regardless of the
     * filters (business rule: country-wide turnover ranking).
     */
    private static final String SCORED_CTE = """
            WITH country_turnover_rank AS (
                SELECT country,
                       annual_turnover,
                       DENSE_RANK() OVER (PARTITION BY country ORDER BY annual_turnover ASC) AS turnover_rank
                FROM (SELECT DISTINCT country, annual_turnover FROM suppliers) AS distinct_turnovers
            ),
            scored AS (
                SELECT s.duns,
                       s.name,
                       s.country,
                       s.annual_turnover,
                       s.sustainability_rating,
                       s.status,
                       -- rating_multiplier is a STORED generated column (see V5)
                       -- The country-rank bonus is the only CASE evaluated per row.
                       (s.annual_turnover * 0.1
                            * s.rating_multiplier
                            * CASE WHEN ctr.turnover_rank <= 2 THEN 1.25 ELSE 1.0 END
                       ) AS score
                FROM suppliers s
                JOIN country_turnover_rank ctr
                  ON ctr.country = s.country
                 AND ctr.annual_turnover = s.annual_turnover
                WHERE s.status <> 'DISQUALIFIED'
                  AND s.annual_turnover > :rate
                  AND (:country IS NULL OR s.country = :country)
                  AND (:maxRating IS NULL OR s.sustainability_rating <= :maxRating)
            )
            """;

    private static final String OFFSET_SQL = SCORED_CTE + """
            SELECT duns, name, country, annual_turnover, sustainability_rating, status, score,
                   COUNT(*) OVER () AS total_count
            FROM scored
            ORDER BY score DESC, duns ASC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = SCORED_CTE + """
            SELECT COUNT(*) AS total_count FROM scored
            """;

    /**
     * Keyset SQL. The strict lexicographic comparison
     * {@code (score, -duns) < (cursorScore, -cursorDuns)} expressed as:
     * <pre>score &lt; cursorScore OR (score = cursorScore AND duns &gt; cursorDuns)</pre>
     * matches the {@code ORDER BY score DESC, duns ASC} contract.
     */
    private static final String KEYSET_SQL = SCORED_CTE + """
            SELECT duns, name, country, annual_turnover, sustainability_rating, status, score
            FROM scored
            WHERE (:cursorScore IS NULL
                   OR score < :cursorScore
                   OR (score = :cursorScore AND duns > :cursorDuns))
            ORDER BY score DESC, duns ASC
            LIMIT :limitPlusOne
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final CursorCodec cursorCodec;

    public PotentialSupplierJdbcQuery(NamedParameterJdbcTemplate jdbc, CursorCodec cursorCodec) {
        this.jdbc = jdbc;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public PotentialSupplierPage findPotential(Filter f) {
        MapSqlParameterSource baseParams = new MapSqlParameterSource()
                .addValue("rate", f.rate())
                .addValue("country", f.country())
                .addValue("maxRating", f.maxRating() == null ? null : f.maxRating().name());

        // Exhaustive pattern match — compiler enforces both branches.
        return switch (f.pagination()) {
            case Pagination.Offset o -> runOffset(f, o.offset(), baseParams);
            case Pagination.Keyset  k -> runKeyset(f, k.cursor(), baseParams);
        };
    }

    private PotentialSupplierPage runOffset(Filter f, int offset, MapSqlParameterSource params) {
        params.addValue("limit", f.limit())
              .addValue("offset", offset);

        List<PotentialSupplierView> rows = new ArrayList<>(f.limit());
        long[] total = { 0L };

        jdbc.query(OFFSET_SQL, params, rs -> {
            rows.add(mapRow(rs));
            total[0] = rs.getLong("total_count");
        });

        if (rows.isEmpty()) {
            Long count = jdbc.queryForObject(COUNT_SQL, params, Long.class);
            total[0] = count == null ? 0L : count;
        }
        return PotentialSupplierPage.ofOffset(rows, f.limit(), offset, total[0]);
    }

    private PotentialSupplierPage runKeyset(Filter f, Cursor c, MapSqlParameterSource params) {
        params.addValue("cursorScore", c == null ? null : c.score())
              .addValue("cursorDuns",  c == null ? null : c.duns())
              .addValue("limitPlusOne", f.limit() + 1);

        List<PotentialSupplierView> rows = new ArrayList<>(f.limit() + 1);
        jdbc.query(KEYSET_SQL, params,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> rows.add(mapRow(rs)));

        // If we fetched limit+1, the extra row tells us a next page exists; its
        // predecessor (the row we keep last) becomes the next cursor.
        String nextCursor = null;
        if (rows.size() > f.limit()) {
            rows.remove(rows.size() - 1); // drop the sentinel
            PotentialSupplierView last = rows.get(rows.size() - 1);
            nextCursor = cursorCodec.encode(new Cursor(last.score(), last.duns()));
        }
        return PotentialSupplierPage.ofKeyset(rows, f.limit(), nextCursor);
    }

    private static PotentialSupplierView mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PotentialSupplierView(
                rs.getLong("duns"),
                rs.getString("name"),
                rs.getString("country"),
                rs.getLong("annual_turnover"),
                SustainabilityRating.valueOf(rs.getString("sustainability_rating")),
                SupplierStatus.valueOf(rs.getString("status")),
                rs.getDouble("score")
        );
    }
}

