package com.inditex.suppliers.domain.exception;

public class InvalidAnnualTurnoverException extends DomainException {
    public InvalidAnnualTurnoverException(long value) {
        super("Invalid annual turnover: " + value + " — must be non-negative");
    }
}
