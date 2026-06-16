package com.inditex.suppliers.application.util;

import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.vo.Duns;

/**
 * Centralises the repeated {@code Duns.of(value) → repo.find(…).orElseThrow(…)}
 * lookup-or-throw pattern used across multiple services.
 */
public final class DunsLookup {

    private DunsLookup() {}

    public static Candidate requireActiveCandidate(CandidateRepository repo, long dunsValue) {
        return repo.findActiveByDuns(Duns.of(dunsValue))
                .orElseThrow(() -> new CandidateNotFoundException(dunsValue));
    }

    public static Supplier requireSupplier(SupplierRepository repo, long dunsValue) {
        return repo.findByDuns(Duns.of(dunsValue))
                .orElseThrow(() -> new SupplierNotFoundException(dunsValue));
    }
}
