-- Transactional Outbox
--
-- Records domain events in the same DB transaction as the aggregate change
-- (atomic write) and a separate poller publishes them to the message broker.
-- This sidesteps the dual-write problem (DB + Kafka) without distributed
-- transactions. Until a real broker is wired, the publisher just marks rows
-- as PUBLISHED so the table is a verifiable audit trail.

CREATE TABLE outbox_events (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- The publisher scans for PENDING rows in occurred_at order — keep it cheap.
CREATE INDEX ix_outbox_pending ON outbox_events (status, occurred_at)
    WHERE status = 'PENDING';
