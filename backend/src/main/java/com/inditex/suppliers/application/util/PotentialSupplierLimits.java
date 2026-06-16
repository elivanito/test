package com.inditex.suppliers.application.util;

/**
 * Single source of truth for the {@code /suppliers/potential} read-model bounds.
 *
 * <p>Referenced from the controller's {@code @Min}/{@code @Max} annotations and
 * from the application service's defensive validation. Avoids the historical
 * smell of duplicated literals across the two layers diverging silently.</p>
 */
public final class PotentialSupplierLimits {

    /** Minimum value accepted for the {@code rate} parameter (business rule). */
    public static final int MIN_RATE = 250;

    /** Minimum page size. */
    public static final int MIN_LIMIT = 1;

    /**
     * Maximum page size. Capped at 10 to match the published OpenAPI contract
     * ({@code QueryLimit.maximum = 10}), which is the source of truth for the
     * public API. Also protects the database from unbounded scans.
     */
    public static final int MAX_LIMIT = 10;

    private PotentialSupplierLimits() {}
}
