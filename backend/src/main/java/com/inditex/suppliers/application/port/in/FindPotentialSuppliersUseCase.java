package com.inditex.suppliers.application.port.in;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.domain.vo.SustainabilityRating;

public interface FindPotentialSuppliersUseCase {

    /**
     * Input contract for the read use-case.
     *
     * <p>When {@code cursor != null}, the query runs in keyset mode and
     * {@code offset} is ignored. When {@code cursor == null}, offset mode is used.</p>
     *
     * @param rate       minimum annual turnover threshold (€).
     * @param limit      page size (1..MAX).
     * @param offset     offset for legacy pagination; 0 by default.
     * @param cursor     opaque base64 cursor for keyset mode; null on first call.
     * @param country    optional ISO-3166-1 alpha-2 filter.
     * @param maxRating  optional inclusive upper bound on sustainability rating.
     */
    record Query(
            long rate,
            int limit,
            int offset,
            String cursor,
            String country,
            SustainabilityRating maxRating
    ) {
        public Query(long rate, int limit, int offset) {
            this(rate, limit, offset, null, null, null);
        }
    }

    PotentialSupplierPage find(Query query);
}

