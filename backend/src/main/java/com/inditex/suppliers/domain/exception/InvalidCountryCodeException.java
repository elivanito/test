package com.inditex.suppliers.domain.exception;

public class InvalidCountryCodeException extends DomainException {
    public InvalidCountryCodeException(String value) {
        super("Invalid country code: '" + value + "' — must be a 2-letter ISO 3166-1 alpha-2 code");
    }
}
