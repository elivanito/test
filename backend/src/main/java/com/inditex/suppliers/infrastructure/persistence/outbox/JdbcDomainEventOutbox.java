package com.inditex.suppliers.infrastructure.persistence.outbox;

import com.inditex.suppliers.application.port.out.DomainEventOutbox;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC adapter that appends domain events to the {@code outbox_events} table.
 *
 * <p>Marked {@link Propagation#MANDATORY}: invocation outside an existing
 * transaction is a programming error (would defeat the whole point of the
 * outbox pattern). Spring will throw at runtime if misused.</p>
 */
@Component
public class JdbcDomainEventOutbox implements DomainEventOutbox {

    private static final String INSERT_SQL = """
            INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
            VALUES (:aggregateType, :aggregateId, :eventType, CAST(:payload AS JSONB))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDomainEventOutbox(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        jdbc.update(INSERT_SQL, new MapSqlParameterSource()
                .addValue("aggregateType", aggregateType)
                .addValue("aggregateId",   aggregateId)
                .addValue("eventType",     eventType)
                .addValue("payload",       payloadJson));
    }
}
