package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.application.port.out.CountryGateway;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.exception.CountryBannedException;
import com.inditex.suppliers.domain.exception.InsufficientTurnoverException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
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

class AcceptCandidateServiceTest {

    private CandidateRepository candidates;
    private SupplierRepository suppliers;
    private CountryGateway countries;
    private AcceptCandidateService service;

    @BeforeEach
    void setup() {
        candidates = mock(CandidateRepository.class);
        suppliers = mock(SupplierRepository.class);
        countries = mock(CountryGateway.class);
        service = new AcceptCandidateService(candidates, suppliers, countries,
                mock(com.inditex.suppliers.application.port.out.SupplierMetricsPort.class),
                mock(com.inditex.suppliers.application.port.out.DomainEventOutbox.class));
    }

    private Candidate pending(long turnover, String country) {
        return Candidate.apply(Duns.of(123_456_789L), "Acme",
                CountryCode.of(country), AnnualTurnover.of(turnover));
    }

    @Test
    void accepts_and_persists_active_supplier() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(pending(1_500_000L, "ES")));
        when(countries.isBanned(any())).thenReturn(false);

        service.accept(123_456_789L, SustainabilityRating.A);

        verify(candidates).deleteByDuns(any());
        ArgumentCaptor<Supplier> saved = ArgumentCaptor.forClass(Supplier.class);
        verify(suppliers).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(SupplierStatus.ACTIVE);
    }

    @Test
    void rating_C_results_in_on_probation() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(pending(1_500_000L, "ES")));
        when(countries.isBanned(any())).thenReturn(false);

        service.accept(123_456_789L, SustainabilityRating.C);

        ArgumentCaptor<Supplier> saved = ArgumentCaptor.forClass(Supplier.class);
        verify(suppliers).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(SupplierStatus.ON_PROBATION);
    }

    @Test
    void fails_when_country_is_banned() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(pending(1_500_000L, "NG")));
        when(countries.isBanned(any())).thenReturn(true);

        assertThatThrownBy(() -> service.accept(123_456_789L, SustainabilityRating.A))
                .isInstanceOf(CountryBannedException.class);
    }

    @Test
    void fails_when_turnover_below_threshold() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(pending(999_000L, "ES")));
        when(countries.isBanned(any())).thenReturn(false);

        assertThatThrownBy(() -> service.accept(123_456_789L, SustainabilityRating.A))
                .isInstanceOf(InsufficientTurnoverException.class);
    }

    @Test
    void fails_when_no_pending_candidacy() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(123_456_789L, SustainabilityRating.A))
                .isInstanceOf(CandidateNotFoundException.class);
    }
}
