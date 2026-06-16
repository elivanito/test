package com.inditex.suppliers.domain.exception;

public class CandidateNotFoundException extends DomainException {
    public CandidateNotFoundException(long duns) {
        super("Candidate not found: " + duns);
    }
}
