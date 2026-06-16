package com.inditex.suppliers.infrastructure.persistence.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic publisher for the transactional Outbox.
 *
 * <p>Picks up the oldest {@code PENDING} events in batches, "publishes" them
 * (today: marks them as {@code PUBLISHED}; tomorrow: writes to Kafka or SNS),
 * and updates the row in a single statement. {@code FOR UPDATE SKIP LOCKED}
 * lets multiple instances run concurrently without stepping on each other —
 * essential for horizontal scaling.</p>
 *
 * <p>Disabled by default ({@code suppliers.outbox.enabled=false}) so unit and
 * slice tests don't accidentally hammer the DB. Production turns it on via
 * {@code application-prod.yml}.</p>
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "suppliers.outbox.enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private static final String CLAIM_SQL = """
            UPDATE outbox_events
               SET status = 'PUBLISHED', published_at = now()
             WHERE id IN (
                 SELECT id FROM outbox_events
                  WHERE status = 'PENDING'
                  ORDER BY occurred_at ASC
                  LIMIT :batch
                  FOR UPDATE SKIP LOCKED
             )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxPublisher(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Runs every 2s by default. A real deployment would replace the in-row
     * update with an actual broker send and use a 2-step claim → publish →
     * mark pattern with retries for transport failures.
     */
    @Scheduled(fixedDelayString = "${suppliers.outbox.poll-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        int published = jdbc.update(CLAIM_SQL,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("batch", BATCH_SIZE));
        if (published > 0) {
            log.debug("Outbox: published {} event(s)", published);
        }
    }
}
