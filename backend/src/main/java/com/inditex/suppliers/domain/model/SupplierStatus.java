package com.inditex.suppliers.domain.model;

/**
 * Internal supplier status. Note that "ACTIVE" and "ON_PROBATION" are exposed
 * as the same status ("Active") through the API — this distinction lives in
 * the domain only because only on-probation suppliers can be banned.
 */
public enum SupplierStatus {
    ACTIVE,
    ON_PROBATION,
    DISQUALIFIED;

    public boolean canBeBanned() {
        return this == ON_PROBATION;
    }

    /** FSM transition {@code Active --Restrict--> On Probation}. */
    public boolean canBeRestricted() {
        return this == ACTIVE;
    }

    /** FSM transition {@code On Probation --Promote--> Active}. */
    public boolean canBePromoted() {
        return this == ON_PROBATION;
    }

    public boolean isDisqualified() {
        return this == DISQUALIFIED;
    }
}
