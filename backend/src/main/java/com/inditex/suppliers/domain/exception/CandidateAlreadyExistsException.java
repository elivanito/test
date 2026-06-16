package com.inditex.suppliers.domain.exception;

public class CandidateAlreadyExistsException extends DomainException {
    public CandidateAlreadyExistsException() {
        super("Candidate already exists");
    }
}
