package com.inditex.suppliers.domain.vo;

import com.inditex.suppliers.domain.exception.InvalidAnnualTurnoverException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnualTurnoverTest {

    @Test
    void rejects_negative() {
        assertThatThrownBy(() -> AnnualTurnover.of(-1))
                .isInstanceOf(InvalidAnnualTurnoverException.class);
    }

    @Test
    void below_threshold() {
        assertThat(AnnualTurnover.of(999_999L).meetsAcceptanceThreshold()).isFalse();
    }

    @Test
    void at_threshold() {
        assertThat(AnnualTurnover.of(1_000_000L).meetsAcceptanceThreshold()).isTrue();
    }
}
