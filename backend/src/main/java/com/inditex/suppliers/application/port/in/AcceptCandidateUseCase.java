package com.inditex.suppliers.application.port.in;

import com.inditex.suppliers.domain.vo.SustainabilityRating;

public interface AcceptCandidateUseCase {
    void accept(long duns, SustainabilityRating rating);
}
