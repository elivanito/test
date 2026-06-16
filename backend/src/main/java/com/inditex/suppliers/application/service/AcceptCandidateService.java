package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.AcceptCandidateUseCase;
import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.CountryGateway;
import com.inditex.suppliers.application.port.out.DomainEventOutbox;
import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptCandidateService implements AcceptCandidateUseCase {

    private final CandidateRepository candidates;
    private final SupplierRepository suppliers;
    private final CountryGateway countries;
    private final SupplierMetricsPort metrics;
    private final DomainEventOutbox outbox;

    public AcceptCandidateService(CandidateRepository candidates,
                                  SupplierRepository suppliers,
                                  CountryGateway countries,
                                  SupplierMetricsPort metrics,
                                  DomainEventOutbox outbox) {
        this.candidates = candidates;
        this.suppliers = suppliers;
        this.countries = countries;
        this.metrics = metrics;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public void accept(long dunsValue, SustainabilityRating rating) {
        Duns duns = Duns.of(dunsValue);
        Candidate candidate = candidates.findActiveByDuns(duns)
                .orElseThrow(() -> new CandidateNotFoundException(dunsValue));

        boolean banned = countries.isBanned(candidate.country());
        Supplier supplier = candidate.accept(rating, banned);

        candidates.deleteByDuns(duns);
        suppliers.save(supplier);
        outbox.append("Candidate", String.valueOf(dunsValue), "CandidateAccepted",
                "{\"duns\":" + dunsValue + ",\"rating\":\"" + rating.name() + "\"}");
        metrics.candidateAccepted();
    }
}
