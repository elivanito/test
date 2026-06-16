package com.inditex.suppliers.domain.model;

import com.inditex.suppliers.domain.exception.SupplierCannotBeBannedException;
import com.inditex.suppliers.domain.exception.SupplierCannotBePromotedException;
import com.inditex.suppliers.domain.exception.SupplierCannotBeRestrictedException;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;

import java.util.Objects;

/**
 * Supplier aggregate root.
 *
 * Internally tracks {@link SupplierStatus#ACTIVE}, {@link SupplierStatus#ON_PROBATION}
 * and {@link SupplierStatus#DISQUALIFIED}. The REST API collapses ACTIVE and
 * ON_PROBATION into "Active"; the distinction lives in the domain because only
 * suppliers on probation can be banned.
 */
public final class Supplier {

    private final Duns duns;
    private final String name;
    private final CountryCode country;
    private final AnnualTurnover annualTurnover;
    private final SustainabilityRating rating;
    private SupplierStatus status;
    /**
     * Opaque optimistic-locking token, round-tripped by the persistence adapter.
     * The domain treats it as a black box: {@code 0L} for a brand-new aggregate,
     * whatever the store returned for a rehydrated one. The token survives mutations
     * untouched; the store increments it atomically on save.
     */
    private final long version;

    private Supplier(Duns duns, String name, CountryCode country,
                     AnnualTurnover annualTurnover, SustainabilityRating rating,
                     SupplierStatus status, long version) {
        this.duns = Objects.requireNonNull(duns);
        this.name = Objects.requireNonNull(name);
        this.country = Objects.requireNonNull(country);
        this.annualTurnover = Objects.requireNonNull(annualTurnover);
        this.rating = Objects.requireNonNull(rating);
        this.status = Objects.requireNonNull(status);
        this.version = version;
    }

    static Supplier fromAcceptedCandidate(Candidate candidate, SustainabilityRating rating) {
        SupplierStatus initial = rating.leadsToActiveStatus()
                ? SupplierStatus.ACTIVE
                : SupplierStatus.ON_PROBATION;
        return new Supplier(candidate.duns(), candidate.name(), candidate.country(),
                candidate.annualTurnover(), rating, initial, 0L);
    }

    /** Rehydration factory used by adapters. */
    public static Supplier rehydrate(Duns duns, String name, CountryCode country,
                                     AnnualTurnover annualTurnover, SustainabilityRating rating,
                                     SupplierStatus status, long version) {
        return new Supplier(duns, name, country, annualTurnover, rating, status, version);
    }

    /** Convenience overload for tests/builders that do not care about the lock token. */
    public static Supplier rehydrate(Duns duns, String name, CountryCode country,
                                     AnnualTurnover annualTurnover, SustainabilityRating rating,
                                     SupplierStatus status) {
        return rehydrate(duns, name, country, annualTurnover, rating, status, 0L);
    }

    /** Ban the supplier. Only allowed if currently on probation. */
    public void ban() {
        if (!status.canBeBanned()) {
            throw new SupplierCannotBeBannedException();
        }
        this.status = SupplierStatus.DISQUALIFIED;
    }

    /**
     * FSM transition {@code Active --Restrict--> On Probation}.
     *
     * Not exposed by the OpenAPI contract; modelled at the domain level so the
     * aggregate is a faithful implementation of the supplier state machine and
     * can be driven by future admin / automated processes.
     */
    public void restrict() {
        if (!status.canBeRestricted()) {
            throw new SupplierCannotBeRestrictedException();
        }
        this.status = SupplierStatus.ON_PROBATION;
    }

    /**
     * FSM transition {@code On Probation --Promote--> Active}.
     *
     * Not exposed by the OpenAPI contract; see {@link #restrict()}.
     */
    public void promote() {
        if (!status.canBePromoted()) {
            throw new SupplierCannotBePromotedException();
        }
        this.status = SupplierStatus.ACTIVE;
    }

    public boolean isDisqualified() {
        return status.isDisqualified();
    }

    public Duns duns() { return duns; }
    public String name() { return name; }
    public CountryCode country() { return country; }
    public AnnualTurnover annualTurnover() { return annualTurnover; }
    public SustainabilityRating rating() { return rating; }
    public SupplierStatus status() { return status; }
    public long version() { return version; }
}
