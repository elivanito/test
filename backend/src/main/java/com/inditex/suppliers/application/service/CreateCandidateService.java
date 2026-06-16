package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.CreateCandidateUseCase;
import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.CandidateAlreadyExistsException;
import com.inditex.suppliers.domain.exception.SupplierBannedException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCandidateService implements CreateCandidateUseCase {

    private final CandidateRepository candidates;
    private final SupplierRepository suppliers;
    private final SupplierMetricsPort metrics;

    public CreateCandidateService(CandidateRepository candidates,
                                  SupplierRepository suppliers,
                                  SupplierMetricsPort metrics) {
        this.candidates = candidates;
        this.suppliers = suppliers;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Candidate create(Command command) {
        Duns duns = Duns.of(command.duns());
        CountryCode country = CountryCode.of(command.country());
        AnnualTurnover turnover = AnnualTurnover.of(command.annualTurnover());

        if (suppliers.isBanned(duns)) {
            throw new SupplierBannedException();
        }
        if (suppliers.existsByDuns(duns)) {
            throw new CandidateAlreadyExistsException();
        }

        // Per the spec, a REFUSED candidacy may re-apply (the PENDING row "comes back"),
        // preserving the optimistic-locking token to detect concurrent edits.
        Candidate candidate = candidates.findByDuns(duns)
                .map(existing -> {
                    if (existing.state().isPending()) {
                        throw new CandidateAlreadyExistsException();
                    }
                    return Candidate.reapplyFromRefused(duns, command.name(), country, turnover,
                            existing.version());
                })
                .orElseGet(() -> Candidate.apply(duns, command.name(), country, turnover));

        candidates.save(candidate);
        metrics.candidateCreated();
        return candidate;
    }
}
