package com.inditex.suppliers.domain.exception;

public class InsufficientTurnoverException extends DomainException {
    public InsufficientTurnoverException() {
        super("Annual turnover is below the acceptance threshold (1,000,000 €)");
    }
}
