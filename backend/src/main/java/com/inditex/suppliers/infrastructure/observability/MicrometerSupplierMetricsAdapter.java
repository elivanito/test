package com.inditex.suppliers.infrastructure.observability;

import com.inditex.suppliers.application.port.out.SupplierMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer-backed implementation of {@link SupplierMetricsPort}. Exposes one
 * Prometheus counter family {@code suppliers_transitions_total{type=...}} so that
 * business volume is observable next to the (free) JVM/HTTP signals.
 */
@Component
public class MicrometerSupplierMetricsAdapter implements SupplierMetricsPort {

    private static final String COUNTER = "suppliers.transitions";

    private final MeterRegistry meterRegistry;

    public MicrometerSupplierMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void candidateCreated() {
        meterRegistry.counter(COUNTER, "type", "candidate_created").increment();
    }

    @Override
    public void candidateAccepted() {
        meterRegistry.counter(COUNTER, "type", "candidate_accepted").increment();
    }

    @Override
    public void candidateRefused() {
        meterRegistry.counter(COUNTER, "type", "candidate_refused").increment();
    }

    @Override
    public void supplierBanned() {
        meterRegistry.counter(COUNTER, "type", "supplier_banned").increment();
    }
}
