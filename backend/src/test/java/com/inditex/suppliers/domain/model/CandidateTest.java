package com.inditex.suppliers.domain.model;

import com.inditex.suppliers.domain.exception.CandidateNotPendingException;
import com.inditex.suppliers.domain.exception.CountryBannedException;
import com.inditex.suppliers.domain.exception.InsufficientTurnoverException;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateTest {

    private Candidate fresh(long turnover, String country) {
        return Candidate.apply(Duns.of(123_456_789L), "Acme",
                CountryCode.of(country), AnnualTurnover.of(turnover));
    }

    @Test
    void newly_applied_is_pending() {
        assertThat(fresh(1_500_000L, "ES").state()).isEqualTo(CandidateState.PENDING);
    }

    @Test
    void accept_with_rating_A_creates_active_supplier() {
        Supplier s = fresh(1_500_000L, "ES").accept(SustainabilityRating.A, false);
        assertThat(s.status()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(s.rating()).isEqualTo(SustainabilityRating.A);
    }

    @Test
    void accept_with_rating_C_places_on_probation() {
        Supplier s = fresh(1_500_000L, "ES").accept(SustainabilityRating.C, false);
        assertThat(s.status()).isEqualTo(SupplierStatus.ON_PROBATION);
    }

    @Test
    void cannot_accept_when_country_banned() {
        assertThatThrownBy(() -> fresh(1_500_000L, "ES").accept(SustainabilityRating.A, true))
                .isInstanceOf(CountryBannedException.class);
    }

    @Test
    void cannot_accept_when_turnover_below_threshold() {
        assertThatThrownBy(() -> fresh(999_999L, "ES").accept(SustainabilityRating.A, false))
                .isInstanceOf(InsufficientTurnoverException.class);
    }

    @Test
    void refuse_marks_as_refused() {
        Candidate c = fresh(1_500_000L, "ES");
        c.refuse();
        assertThat(c.state()).isEqualTo(CandidateState.REFUSED);
    }

    @Test
    void cannot_refuse_twice() {
        Candidate c = fresh(1_500_000L, "ES");
        c.refuse();
        assertThatThrownBy(c::refuse).isInstanceOf(CandidateNotPendingException.class);
    }

    @Test
    void cannot_accept_refused_candidate() {
        Candidate c = fresh(1_500_000L, "ES");
        c.refuse();
        assertThatThrownBy(() -> c.accept(SustainabilityRating.A, false))
                .isInstanceOf(CandidateNotPendingException.class);
    }
}
