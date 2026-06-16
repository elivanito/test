package com.inditex.suppliers.domain.exception;

public class CountryUnknownException extends DomainException {
    public CountryUnknownException(String countryCode) {
        super("Country " + countryCode + " is unknown to the country information service");
    }
}
