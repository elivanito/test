package com.inditex.suppliers.domain.model;

/**
 * Lifecycle state of a candidacy.
 *
 * PENDING — application submitted, awaiting supervisor decision.
 * REFUSED — application refused; the candidate may re-apply.
 *
 * An accepted candidacy is not a state here: it becomes a Supplier.
 */
public enum CandidateState {
    PENDING,
    REFUSED;

    public boolean isPending() {
        return this == PENDING;
    }
}
