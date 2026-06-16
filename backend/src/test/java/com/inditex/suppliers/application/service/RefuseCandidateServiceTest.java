package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.exception.CandidateNotPendingException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.CandidateState;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefuseCandidateServiceTest {

    private CandidateRepository candidates;
    private SupplierMetricsPort metrics;
    private RefuseCandidateService service;

    @BeforeEach
    void setup() {
        candidates = mock(CandidateRepository.class);
        metrics = mock(SupplierMetricsPort.class);
        service = new RefuseCandidateService(candidates, metrics);
    }

    private Candidate pending() {
        return Candidate.apply(Duns.of(123_456_789L), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(1_500_000L));
    }

    @Test
    void refuses_pending_candidate_and_persists() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(pending()));

        service.refuse(123_456_789L);

        ArgumentCaptor<Candidate> captor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidates).save(captor.capture());
        assertThat(captor.getValue().state()).isEqualTo(CandidateState.REFUSED);
    }

    @Test
    void emits_metric_on_refusal() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(pending()));

        service.refuse(123_456_789L);

        verify(metrics).candidateRefused();
    }

    @Test
    void throws_when_no_active_candidate() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refuse(123_456_789L))
                .isInstanceOf(CandidateNotFoundException.class);
    }

    @Test
    void throws_when_candidate_already_refused() {
        Candidate refused = Candidate.rehydrate(Duns.of(123_456_789L), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(1_500_000L),
                CandidateState.REFUSED);
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(refused));

        assertThatThrownBy(() -> service.refuse(123_456_789L))
                .isInstanceOf(CandidateNotPendingException.class);
    }
}
