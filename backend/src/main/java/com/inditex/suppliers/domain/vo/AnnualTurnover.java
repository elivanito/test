package com.inditex.suppliers.domain.vo;

import com.inditex.suppliers.domain.exception.InvalidAnnualTurnoverException;

/**
 * Annual turnover, expressed in whole euros (per the OpenAPI contract: integer).
 * Must be non-negative.
 */
public record AnnualTurnover(long euros) {

    public static final long ACCEPTANCE_THRESHOLD = 1_000_000L;

    public AnnualTurnover {
        if (euros < 0) {
            throw new InvalidAnnualTurnoverException(euros);
        }
    }

    public boolean meetsAcceptanceThreshold() {
        return euros >= ACCEPTANCE_THRESHOLD;
    }

    public boolean isGreaterThan(long rate) {
        return euros > rate;
    }

    public static AnnualTurnover of(long euros) {
        return new AnnualTurnover(euros);
    }
}
