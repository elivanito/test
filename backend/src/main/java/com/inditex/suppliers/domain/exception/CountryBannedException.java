package com.inditex.suppliers.domain.exception;

public class CountryBannedException extends DomainException {
    public CountryBannedException(String countryCode) {
        super("Country " + countryCode + " is on the non-approved list");
    }
}
