package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.GetCandidateUseCase;
import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.util.DunsLookup;
import com.inditex.suppliers.domain.model.Candidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCandidateService implements GetCandidateUseCase {

    private final CandidateRepository candidates;

    public GetCandidateService(CandidateRepository candidates) {
        this.candidates = candidates;
    }

    @Override
    @Transactional(readOnly = true)
    public Candidate get(long dunsValue) {
        return DunsLookup.requireActiveCandidate(candidates, dunsValue);
    }
}
