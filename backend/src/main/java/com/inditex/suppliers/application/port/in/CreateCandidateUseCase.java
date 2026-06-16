package com.inditex.suppliers.application.port.in;

import com.inditex.suppliers.domain.model.Candidate;

public interface CreateCandidateUseCase {
    record Command(long duns, String name, String country, long annualTurnover) {}

    Candidate create(Command command);
}
