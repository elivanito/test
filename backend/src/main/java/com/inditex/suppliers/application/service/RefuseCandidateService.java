package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.RefuseCandidateUseCase;
import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import com.inditex.suppliers.application.util.DunsLookup;
import com.inditex.suppliers.domain.model.Candidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefuseCandidateService implements RefuseCandidateUseCase {

    private final CandidateRepository candidates;
    private final SupplierMetricsPort metrics;

    public RefuseCandidateService(CandidateRepository candidates, SupplierMetricsPort metrics) {
        this.candidates = candidates;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public void refuse(long dunsValue) {
        Candidate candidate = DunsLookup.requireActiveCandidate(candidates, dunsValue);
        candidate.refuse();
        candidates.save(candidate);
        metrics.candidateRefused();
    }
}
