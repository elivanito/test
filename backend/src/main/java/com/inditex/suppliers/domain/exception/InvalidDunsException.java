package com.inditex.suppliers.domain.exception;

public class InvalidDunsException extends DomainException {
    public InvalidDunsException(long value) {
        super("Invalid DUNS: " + value + " — must be a 9-digit number");
    }
}
