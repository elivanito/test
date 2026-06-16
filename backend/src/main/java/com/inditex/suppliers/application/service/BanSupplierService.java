package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.BanSupplierUseCase;
import com.inditex.suppliers.application.port.out.DomainEventOutbox;
import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.application.util.DunsLookup;
import com.inditex.suppliers.application.util.OutboxPayloads;
import com.inditex.suppliers.domain.model.Supplier;
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
        Supplier supplier = DunsLookup.requireSupplier(suppliers, dunsValue);
        supplier.ban();
        suppliers.save(supplier);
        outbox.append("Supplier", String.valueOf(dunsValue), "SupplierBanned",
                OutboxPayloads.supplierBanned(dunsValue));
        metrics.supplierBanned();
    }
}
