package com.inditex.suppliers.domain.vo;

import com.inditex.suppliers.domain.exception.InvalidDunsException;

/**
 * Data Universal Numbering System identifier.
 * Exactly 9 digits → range [100_000_000, 999_999_999].
 */
public record Duns(long value) {
    private static final long MIN = 100_000_000L;
    private static final long MAX = 999_999_999L;

    public Duns {
        if (value < MIN || value > MAX) {
            throw new InvalidDunsException(value);
        }
    }

    public static Duns of(long value) {
        return new Duns(value);
    }
}
