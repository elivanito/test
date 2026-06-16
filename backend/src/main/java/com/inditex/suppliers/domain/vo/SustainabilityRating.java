package com.inditex.suppliers.domain.vo;

/**
 * Sustainability rating assigned by the supervisor upon acceptance.
 * A is best, E is worst. Carries the score multiplier used by the
 * potential-suppliers calculation.
 */
public enum SustainabilityRating {
    A(1.0),
    B(0.75),
    C(0.5),
    D(0.25),
    E(0.1);

    private final double constant;

    SustainabilityRating(double constant) {
        this.constant = constant;
    }

    public double constant() {
        return constant;
    }

    /** A or B → supplier becomes Active. C/D/E → On Probation. */
    public boolean leadsToActiveStatus() {
        return this == A || this == B;
    }
}
