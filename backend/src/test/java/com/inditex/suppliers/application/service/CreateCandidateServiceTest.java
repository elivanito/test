package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.CreateCandidateUseCase.Command;
import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.CandidateAlreadyExistsException;
import com.inditex.suppliers.domain.exception.SupplierBannedException;
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

class CreateCandidateServiceTest {

    private CandidateRepository candidates;
    private SupplierRepository suppliers;
    private CreateCandidateService service;

    @BeforeEach
    void setup() {
        candidates = mock(CandidateRepository.class);
        suppliers = mock(SupplierRepository.class);
        service = new CreateCandidateService(candidates, suppliers,
                mock(com.inditex.suppliers.application.port.out.SupplierMetricsPort.class));
    }

    @Test
    void creates_a_pending_candidate_when_no_prior_history() {
        when(suppliers.isBanned(any())).thenReturn(false);
        when(suppliers.existsByDuns(any())).thenReturn(false);
        when(candidates.findByDuns(any())).thenReturn(Optional.empty());

        Candidate created = service.create(new Command(123_456_789L, "Acme", "ES", 1_500_000L));

        assertThat(created.duns()).isEqualTo(Duns.of(123_456_789L));
        assertThat(created.state()).isEqualTo(CandidateState.PENDING);
        assertThat(created.version()).isZero();
        verify(candidates).save(any());
    }

    @Test
    void reapplies_when_refused_candidacy_exists_preserving_version() {
        Candidate refused = Candidate.rehydrate(Duns.of(123_456_789L), "Old name",
                CountryCode.of("ES"), AnnualTurnover.of(900_000L),
                CandidateState.REFUSED, 7L);

        when(suppliers.isBanned(any())).thenReturn(false);
        when(suppliers.existsByDuns(any())).thenReturn(false);
        when(candidates.findByDuns(any())).thenReturn(Optional.of(refused));

        service.create(new Command(123_456_789L, "Acme", "ES", 1_500_000L));

        ArgumentCaptor<Candidate> captor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidates).save(captor.capture());
        Candidate saved = captor.getValue();
        assertThat(saved.state()).isEqualTo(CandidateState.PENDING);
        assertThat(saved.version()).isEqualTo(7L); // preserved → optimistic locking still works
        assertThat(saved.name()).isEqualTo("Acme"); // re-application uses the new payload
    }

    @Test
    void rejects_when_duns_belongs_to_banned_supplier() {
        when(suppliers.isBanned(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new Command(123_456_789L, "Acme", "ES", 1_500_000L)))
                .isInstanceOf(SupplierBannedException.class);
    }

    @Test
    void rejects_when_pending_candidacy_exists() {
        Candidate pending = Candidate.rehydrate(Duns.of(123_456_789L), "Old",
                CountryCode.of("ES"), AnnualTurnover.of(1_500_000L),
                CandidateState.PENDING, 3L);
        when(suppliers.isBanned(any())).thenReturn(false);
        when(suppliers.existsByDuns(any())).thenReturn(false);
        when(candidates.findByDuns(any())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.create(new Command(123_456_789L, "Acme", "ES", 1_500_000L)))
                .isInstanceOf(CandidateAlreadyExistsException.class);
    }

    @Test
    void rejects_when_supplier_already_exists_for_duns() {
        when(suppliers.isBanned(any())).thenReturn(false);
        when(suppliers.existsByDuns(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new Command(123_456_789L, "Acme", "ES", 1_500_000L)))
                .isInstanceOf(CandidateAlreadyExistsException.class);
    }
}
