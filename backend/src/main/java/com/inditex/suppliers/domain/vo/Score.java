package com.inditex.suppliers.domain.vo;

/**
 * Score assigned to a supplier in the context of a potential-suppliers query.
 * Domain-pure value: kept as a double consistently with the OpenAPI schema.
 */
public record Score(double value) {

    public Score {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Score must be a non-negative finite number");
        }
    }

    public static Score of(double value) {
        return new Score(value);
    }
}
