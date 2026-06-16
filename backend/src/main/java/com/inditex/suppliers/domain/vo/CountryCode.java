package com.inditex.suppliers.domain.vo;

import com.inditex.suppliers.domain.exception.InvalidCountryCodeException;

import java.util.Locale;

/**
 * ISO 3166-1 alpha-2 country code (always uppercase).
 */
public record CountryCode(String value) {

    public CountryCode {
        if (value == null || value.length() != 2 || !value.chars().allMatch(Character::isLetter)) {
            throw new InvalidCountryCodeException(value);
        }
        value = value.toUpperCase(Locale.ROOT);
    }

    public static CountryCode of(String value) {
        return new CountryCode(value);
    }
}
