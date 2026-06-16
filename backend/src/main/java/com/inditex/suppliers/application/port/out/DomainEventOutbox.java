package com.inditex.suppliers.application.port.out;

/**
 * Output port for the transactional Outbox.
 *
 * <p>Application services append events through this port within the same
 * transaction as the aggregate change. A separate publisher in the infrastructure
 * layer reads the table and ships events to downstream consumers, providing
 * <em>at-least-once</em> delivery without distributed transactions.</p>
 *
 * <p>The payload is a JSON string; the application layer doesn't depend on any
 * serializer. Adapters can pick Jackson, kotlinx.serialization, plain
 * {@code String.format}, etc.</p>
 */
public interface DomainEventOutbox {

    /**
     * Append an event to the outbox. MUST be called from within an active
     * transaction so the row is committed atomically with the aggregate.
     *
     * @param aggregateType e.g. {@code "Candidate"} or {@code "Supplier"}.
     * @param aggregateId   e.g. the DUNS as a string.
     * @param eventType     e.g. {@code "CandidateAccepted"}.
     * @param payloadJson   pre-serialized JSON document.
     */
    void append(String aggregateType, String aggregateId, String eventType, String payloadJson);
}
