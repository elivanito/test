package com.inditex.suppliers.application.port.out;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.domain.vo.SustainabilityRating;

/**
 * Read-model port. Implementations run a single SQL query that filters,
 * scores, applies the small-supplier bonus and paginates entirely in the
 * database, so the dataset (100k–1M suppliers) is never loaded in memory.
 *
 * <p>Two pagination modes are supported through the sealed {@link Pagination} type:
 * <ul>
 *   <li>{@link Pagination.Offset} — legacy LIMIT/OFFSET; returns {@code total}.</li>
 *   <li>{@link Pagination.Keyset} — opaque {@link Cursor}; returns {@code nextCursor}
 *       and omits {@code total} (intentional, to avoid full scans at scale).</li>
 * </ul>
 *
 * <p>Sealed-type discrimination gives <em>exhaustiveness checking</em> at compile
 * time in the adapter, eliminating the "if(offset != null) else" pattern.</p>
 */
public interface PotentialSupplierQuery {

    PotentialSupplierPage findPotential(Filter filter);

    /** Back-compat shorthand for offset-based pagination without filters. */
    default PotentialSupplierPage findPotential(long rate, int limit, int offset) {
        return findPotential(new Filter(rate, limit, new Pagination.Offset(offset), null, null));
    }

    /**
     * Immutable input contract for the query.
     *
     * @param rate       minimum annual turnover threshold (€).
     * @param limit      page size (1..MAX).
     * @param pagination Offset or Keyset, decided at the caller.
     * @param country    optional ISO-3166-1 alpha-2 filter.
     * @param maxRating  optional inclusive upper bound on sustainability rating
     *                   (e.g. {@code B} returns only A and B).
     */
    record Filter(
            long rate,
            int limit,
            Pagination pagination,
            String country,
            SustainabilityRating maxRating
    ) {}

    /** Two-variant ADT to model the pagination strategy explicitly. */
    sealed interface Pagination {
        record Offset(int offset) implements Pagination {}
        /** {@code cursor == null} means "first page". */
        record Keyset(Cursor cursor) implements Pagination {}
    }

    /**
     * Keyset position. Decoded from / encoded into the opaque base64 cursor
     * exchanged with API clients.
     */
    record Cursor(double score, long duns) {}
}


