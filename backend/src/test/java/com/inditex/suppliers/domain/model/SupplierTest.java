package com.inditex.suppliers.domain.model;

import com.inditex.suppliers.domain.exception.SupplierCannotBeBannedException;
import com.inditex.suppliers.domain.exception.SupplierCannotBePromotedException;
import com.inditex.suppliers.domain.exception.SupplierCannotBeRestrictedException;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierTest {

    private Supplier supplier(SupplierStatus status) {
        return Supplier.rehydrate(Duns.of(123_456_789L), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(2_000_000L),
                SustainabilityRating.C, status);
    }

    @Test
    void ban_transitions_on_probation_to_disqualified() {
        Supplier s = supplier(SupplierStatus.ON_PROBATION);
        s.ban();
        assertThat(s.status()).isEqualTo(SupplierStatus.DISQUALIFIED);
    }

    @Test
    void cannot_ban_active_supplier() {
        assertThatThrownBy(() -> supplier(SupplierStatus.ACTIVE).ban())
                .isInstanceOf(SupplierCannotBeBannedException.class);
    }

    @Test
    void cannot_ban_already_disqualified() {
        assertThatThrownBy(() -> supplier(SupplierStatus.DISQUALIFIED).ban())
                .isInstanceOf(SupplierCannotBeBannedException.class);
    }

    // ------- FSM: Restrict (Active -> On Probation) -------

    @Test
    void restrict_transitions_active_to_on_probation() {
        Supplier s = supplier(SupplierStatus.ACTIVE);
        s.restrict();
        assertThat(s.status()).isEqualTo(SupplierStatus.ON_PROBATION);
    }

    @Test
    void cannot_restrict_on_probation_supplier() {
        assertThatThrownBy(() -> supplier(SupplierStatus.ON_PROBATION).restrict())
                .isInstanceOf(SupplierCannotBeRestrictedException.class);
    }

    @Test
    void cannot_restrict_disqualified_supplier() {
        assertThatThrownBy(() -> supplier(SupplierStatus.DISQUALIFIED).restrict())
                .isInstanceOf(SupplierCannotBeRestrictedException.class);
    }

    // ------- FSM: Promote (On Probation -> Active) -------

    @Test
    void promote_transitions_on_probation_to_active() {
        Supplier s = supplier(SupplierStatus.ON_PROBATION);
        s.promote();
        assertThat(s.status()).isEqualTo(SupplierStatus.ACTIVE);
    }

    @Test
    void cannot_promote_active_supplier() {
        assertThatThrownBy(() -> supplier(SupplierStatus.ACTIVE).promote())
                .isInstanceOf(SupplierCannotBePromotedException.class);
    }

    @Test
    void cannot_promote_disqualified_supplier() {
        assertThatThrownBy(() -> supplier(SupplierStatus.DISQUALIFIED).promote())
                .isInstanceOf(SupplierCannotBePromotedException.class);
    }

    // ------- FSM round-trip: Active -> On Probation -> Active -> ... -> Disqualified -------

    @Test
    void full_fsm_round_trip_matches_diagram() {
        Supplier s = supplier(SupplierStatus.ACTIVE);
        s.restrict();
        assertThat(s.status()).isEqualTo(SupplierStatus.ON_PROBATION);
        s.promote();
        assertThat(s.status()).isEqualTo(SupplierStatus.ACTIVE);
        s.restrict();
        s.ban();
        assertThat(s.status()).isEqualTo(SupplierStatus.DISQUALIFIED);
        // Terminal state — no further transitions
        assertThatThrownBy(s::restrict).isInstanceOf(SupplierCannotBeRestrictedException.class);
        assertThatThrownBy(s::promote).isInstanceOf(SupplierCannotBePromotedException.class);
        assertThatThrownBy(s::ban).isInstanceOf(SupplierCannotBeBannedException.class);
    }
}
