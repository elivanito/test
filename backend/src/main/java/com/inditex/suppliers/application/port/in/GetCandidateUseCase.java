package com.inditex.suppliers.application.port.in;

import com.inditex.suppliers.domain.model.Candidate;

public interface GetCandidateUseCase {
    Candidate get(long duns);
}
