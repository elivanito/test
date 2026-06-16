package com.inditex.suppliers.domain.exception;

public class CandidateNotPendingException extends DomainException {
    public CandidateNotPendingException(String action) {
        super("Candidate cannot be " + action + ": no pending candidacy");
    }
}
