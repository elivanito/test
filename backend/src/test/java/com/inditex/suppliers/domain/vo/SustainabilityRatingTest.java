package com.inditex.suppliers.domain.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class SustainabilityRatingTest {

    @Test
    void a_and_b_lead_to_active_status() {
        assertThat(SustainabilityRating.A.leadsToActiveStatus()).isTrue();
        assertThat(SustainabilityRating.B.leadsToActiveStatus()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SustainabilityRating.class, names = {"C", "D", "E"})
    void c_d_e_do_not_lead_to_active_status(SustainabilityRating rating) {
        assertThat(rating.leadsToActiveStatus()).isFalse();
    }

    @Test
    void constants_are_ordered_descending() {
        assertThat(SustainabilityRating.A.constant()).isGreaterThan(SustainabilityRating.B.constant());
        assertThat(SustainabilityRating.B.constant()).isGreaterThan(SustainabilityRating.C.constant());
        assertThat(SustainabilityRating.C.constant()).isGreaterThan(SustainabilityRating.D.constant());
        assertThat(SustainabilityRating.D.constant()).isGreaterThan(SustainabilityRating.E.constant());
    }

    @ParameterizedTest
    @EnumSource(SustainabilityRating.class)
    void all_constants_are_positive(SustainabilityRating rating) {
        assertThat(rating.constant()).isGreaterThan(0.0);
    }

    @Test
    void a_constant_is_one() {
        assertThat(SustainabilityRating.A.constant()).isEqualTo(1.0);
    }
}
