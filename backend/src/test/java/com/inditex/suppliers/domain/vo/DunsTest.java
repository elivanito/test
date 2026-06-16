package com.inditex.suppliers.domain.vo;

import com.inditex.suppliers.domain.exception.InvalidDunsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DunsTest {

    @Test
    void accepts_9_digit_values() {
        assertThat(Duns.of(100_000_000L).value()).isEqualTo(100_000_000L);
        assertThat(Duns.of(999_999_999L).value()).isEqualTo(999_999_999L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 99_999_999L, 1_000_000_000L, -1})
    void rejects_out_of_range(long v) {
        assertThatThrownBy(() -> Duns.of(v)).isInstanceOf(InvalidDunsException.class);
    }
}
