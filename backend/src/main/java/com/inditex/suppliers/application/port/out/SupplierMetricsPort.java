package com.inditex.suppliers.application.port.out;

/**
 * Output port for business telemetry. Application services emit FSM-transition
 * events through this port; the infrastructure layer translates them to a
 * concrete metrics backend (Micrometer/Prometheus today, OTLP tomorrow).
 *
 * <p>Defining a port keeps the application layer agnostic of any monitoring
 * vendor and enforces the hexagonal direction (services depend on this
 * interface, the Micrometer adapter implements it).</p>
 */
public interface SupplierMetricsPort {

    void candidateCreated();

    void candidateAccepted();

    void candidateRefused();

    void supplierBanned();
}
