package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetSupplierServiceTest {

    private SupplierRepository suppliers;
    private GetSupplierService service;

    @BeforeEach
    void setup() {
        suppliers = mock(SupplierRepository.class);
        service = new GetSupplierService(suppliers);
    }

    @Test
    void returns_supplier_when_found() {
        Supplier expected = Supplier.rehydrate(Duns.of(123_456_789L), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(2_000_000L),
                SustainabilityRating.A, SupplierStatus.ACTIVE);
        when(suppliers.findByDuns(any())).thenReturn(Optional.of(expected));

        Supplier result = service.get(123_456_789L);

        assertThat(result.duns()).isEqualTo(Duns.of(123_456_789L));
        assertThat(result.status()).isEqualTo(SupplierStatus.ACTIVE);
    }

    @Test
    void throws_when_not_found() {
        when(suppliers.findByDuns(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(123_456_789L))
                .isInstanceOf(SupplierNotFoundException.class);
    }
}
