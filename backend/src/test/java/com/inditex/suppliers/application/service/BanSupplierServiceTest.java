package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.SupplierCannotBeBannedException;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanSupplierServiceTest {

    private Supplier supplier(SupplierStatus status) {
        return Supplier.rehydrate(Duns.of(123_456_789L), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(2_000_000L),
                SustainabilityRating.C, status);
    }

    @Test
    void bans_on_probation_supplier() {
        SupplierRepository repo = mock(SupplierRepository.class);
        when(repo.findByDuns(any())).thenReturn(Optional.of(supplier(SupplierStatus.ON_PROBATION)));
        new BanSupplierService(repo,
                mock(com.inditex.suppliers.application.port.out.SupplierMetricsPort.class),
                mock(com.inditex.suppliers.application.port.out.DomainEventOutbox.class))
                .ban(123_456_789L);
        verify(repo).save(any());
    }

    @Test
    void cannot_ban_active() {
        SupplierRepository repo = mock(SupplierRepository.class);
        when(repo.findByDuns(any())).thenReturn(Optional.of(supplier(SupplierStatus.ACTIVE)));
        assertThatThrownBy(() -> new BanSupplierService(repo,
                mock(com.inditex.suppliers.application.port.out.SupplierMetricsPort.class),
                mock(com.inditex.suppliers.application.port.out.DomainEventOutbox.class))
                .ban(123_456_789L))
                .isInstanceOf(SupplierCannotBeBannedException.class);
    }

    @Test
    void not_found() {
        SupplierRepository repo = mock(SupplierRepository.class);
        when(repo.findByDuns(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new BanSupplierService(repo,
                mock(com.inditex.suppliers.application.port.out.SupplierMetricsPort.class),
                mock(com.inditex.suppliers.application.port.out.DomainEventOutbox.class))
                .ban(123_456_789L))
                .isInstanceOf(SupplierNotFoundException.class);
    }
}
