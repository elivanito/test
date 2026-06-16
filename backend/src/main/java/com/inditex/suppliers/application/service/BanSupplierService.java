package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.BanSupplierUseCase;
import com.inditex.suppliers.application.port.out.DomainEventOutbox;
import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.vo.Duns;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BanSupplierService implements BanSupplierUseCase {

    private final SupplierRepository suppliers;
    private final SupplierMetricsPort metrics;
    private final DomainEventOutbox outbox;

    public BanSupplierService(SupplierRepository suppliers,
                              SupplierMetricsPort metrics,
                              DomainEventOutbox outbox) {
        this.suppliers = suppliers;
        this.metrics = metrics;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public void ban(long dunsValue) {
        Duns duns = Duns.of(dunsValue);
        Supplier supplier = suppliers.findByDuns(duns)
                .orElseThrow(() -> new SupplierNotFoundException(dunsValue));
        supplier.ban();
        suppliers.save(supplier);
        outbox.append("Supplier", String.valueOf(dunsValue), "SupplierBanned",
                "{\"duns\":" + dunsValue + "}");
        metrics.supplierBanned();
    }
}
