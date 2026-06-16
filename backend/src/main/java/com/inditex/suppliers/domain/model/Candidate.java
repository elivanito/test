package com.inditex.suppliers.domain.model;

import com.inditex.suppliers.domain.exception.CandidateNotPendingException;
import com.inditex.suppliers.domain.exception.CountryBannedException;
import com.inditex.suppliers.domain.exception.InsufficientTurnoverException;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;

import java.util.Objects;

/**
 * Candidate aggregate root.
 *
 * Represents a supplier application. A candidacy is either PENDING (the active one,
 * exactly one per DUNS) or REFUSED (terminal — re-application is allowed).
 *
 * Acceptance is modelled here as a domain operation that produces a {@link Supplier};
 * the candidate itself is then removed by the use case (an accepted candidacy is not
 * a state, it is a transition out of the aggregate).
 */
public final class Candidate {

    private final Duns duns;
    private final String name;
    private final CountryCode country;
    private final AnnualTurnover annualTurnover;
    private CandidateState state;
    /** See {@code Supplier#version} — opaque optimistic-locking token. */
    private final long version;

    private Candidate(Duns duns, String name, CountryCode country,
                      AnnualTurnover annualTurnover, CandidateState state, long version) {
        this.duns = Objects.requireNonNull(duns);
        this.name = requireName(name);
        this.country = Objects.requireNonNull(country);
        this.annualTurnover = Objects.requireNonNull(annualTurnover);
        this.state = Objects.requireNonNull(state);
        this.version = version;
    }

    /** Factory for a brand-new (pending) candidacy. */
    public static Candidate apply(Duns duns, String name, CountryCode country, AnnualTurnover annualTurnover) {
        return new Candidate(duns, name, country, annualTurnover, CandidateState.PENDING, 0L);
    }

    /**
     * Re-apply: move a previously REFUSED candidacy back to PENDING, preserving the
     * optimistic-locking token. Use case ensures the candidate exists and is REFUSED.
     */
    public static Candidate reapplyFromRefused(Duns duns, String name, CountryCode country,
                                               AnnualTurnover annualTurnover, long version) {
        return new Candidate(duns, name, country, annualTurnover, CandidateState.PENDING, version);
    }

    /** Rehydration factory used by adapters. */
    public static Candidate rehydrate(Duns duns, String name, CountryCode country,
                                      AnnualTurnover annualTurnover, CandidateState state, long version) {
        return new Candidate(duns, name, country, annualTurnover, state, version);
    }

    /** Convenience overload for tests/builders that do not care about the lock token. */
    public static Candidate rehydrate(Duns duns, String name, CountryCode country,
                                      AnnualTurnover annualTurnover, CandidateState state) {
        return rehydrate(duns, name, country, annualTurnover, state, 0L);
    }

    /**
     * Accept this candidacy and produce the resulting supplier.
     * Acceptance preconditions (country not banned, turnover threshold) are enforced here.
     *
     * @param rating       supervisor-assigned sustainability rating
     * @param countryBanned whether the country is currently banned (resolved by use case)
     */
    public Supplier accept(SustainabilityRating rating, boolean countryBanned) {
        if (!state.isPending()) {
            throw new CandidateNotPendingException("accepted");
        }
        if (countryBanned) {
            throw new CountryBannedException(country.value());
        }
        if (!annualTurnover.meetsAcceptanceThreshold()) {
            throw new InsufficientTurnoverException();
        }
        return Supplier.fromAcceptedCandidate(this, rating);
    }

    /** Refuse this candidacy. The candidacy becomes terminal; the candidate may re-apply. */
    public void refuse() {
        if (!state.isPending()) {
            throw new CandidateNotPendingException("refused");
        }
        this.state = CandidateState.REFUSED;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Candidate name must not be blank");
        }
        return name;
    }

    public Duns duns() { return duns; }
    public String name() { return name; }
    public CountryCode country() { return country; }
    public AnnualTurnover annualTurnover() { return annualTurnover; }
    public CandidateState state() { return state; }
    public long version() { return version; }
}
