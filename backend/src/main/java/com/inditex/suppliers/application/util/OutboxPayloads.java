package com.inditex.suppliers.application.util;

import com.inditex.suppliers.domain.vo.SustainabilityRating;

/**
 * Centralised JSON payload builders for outbox events.
 * Eliminates hand-rolled string concatenation scattered across services.
 */
public final class OutboxPayloads {

    private OutboxPayloads() {}

    public static String candidateAccepted(long duns, SustainabilityRating rating) {
        return "{\"duns\":" + duns + ",\"rating\":\"" + rating.name() + "\"}";
    }

    public static String supplierBanned(long duns) {
        return "{\"duns\":" + duns + "}";
    }
}
