package com.inditex.suppliers.domain.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreTest {

    @Test
    void accepts_zero() {
        assertThat(Score.of(0).value()).isZero();
    }

    @Test
    void accepts_positive_value() {
        assertThat(Score.of(42.5).value()).isEqualTo(42.5);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.001})
    void rejects_negative(double v) {
        assertThatThrownBy(() -> Score.of(v))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_nan() {
        assertThatThrownBy(() -> Score.of(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_positive_infinity() {
        assertThatThrownBy(() -> Score.of(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_infinity() {
        assertThatThrownBy(() -> Score.of(Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factory_method_round_trips() {
        Score score = Score.of(99.99);
        assertThat(score).isEqualTo(new Score(99.99));
    }
}
