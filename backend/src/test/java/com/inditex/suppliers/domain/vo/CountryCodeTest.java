package com.inditex.suppliers.domain.vo;

import com.inditex.suppliers.domain.exception.InvalidCountryCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryCodeTest {

    @Test
    void normalizes_to_uppercase() {
        assertThat(CountryCode.of("es").value()).isEqualTo("ES");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"E", "ESP", "1A", "  "})
    void rejects_invalid(String s) {
        assertThatThrownBy(() -> CountryCode.of(s)).isInstanceOf(InvalidCountryCodeException.class);
    }
}
