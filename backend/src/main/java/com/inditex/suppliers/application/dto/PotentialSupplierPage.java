package com.inditex.suppliers.application.dto;

import java.util.List;

/**
 * Read-model result: a page of potential suppliers plus pagination metadata.
 * Lives in application layer because it is the natural shape exchanged
 * between the read port and the use case.
 *
 * <p>{@code total} is {@code null} in keyset mode (we intentionally do not
 * issue a COUNT(*) at scale). {@code nextCursor} is {@code null} in offset
 * mode and when the keyset stream is exhausted.</p>
 */
public record PotentialSupplierPage(
        List<PotentialSupplierView> data,
        int limit,
        int offset,
        Long total,
        String nextCursor
) {
    /** Convenience factory for offset-paginated results (no cursor). */
    public static PotentialSupplierPage ofOffset(List<PotentialSupplierView> data,
                                                 int limit, int offset, long total) {
        return new PotentialSupplierPage(data, limit, offset, total, null);
    }

    /** Convenience factory for keyset-paginated results (no total count). */
    public static PotentialSupplierPage ofKeyset(List<PotentialSupplierView> data,
                                                 int limit, String nextCursor) {
        return new PotentialSupplierPage(data, limit, 0, null, nextCursor);
    }
}

