package com.inditex.suppliers.domain.exception;

/** Root for all business-rule violations originating in the domain. */
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}
