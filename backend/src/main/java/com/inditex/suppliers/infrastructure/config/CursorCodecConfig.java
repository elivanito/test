package com.inditex.suppliers.infrastructure.config;

import com.inditex.suppliers.application.util.CursorCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Composition root for {@link CursorCodec}.
 *
 * <p>The HMAC key is sourced from the {@code suppliers.cursor.key} property,
 * overridable per-environment (e.g. injected from a secrets manager in prod).
 * A development-only default is provided to keep the local dev loop frictionless;
 * the {@code application-prod.yml} forces a real value via {@code ${CURSOR_HMAC_KEY}}.</p>
 */
@Configuration
public class CursorCodecConfig {

    private static final Logger log = LoggerFactory.getLogger(CursorCodecConfig.class);
    private static final String DEV_DEFAULT_KEY = "insecure-dev-local-only-cursor-hmac-key-32b!";

    @Bean
    public CursorCodec cursorCodec(
            @Value("${suppliers.cursor.key:" + DEV_DEFAULT_KEY + "}") String key) {
        if (DEV_DEFAULT_KEY.equals(key)) {
            log.warn("Using default dev HMAC key for cursor encoding. "
                    + "Set suppliers.cursor.key (or CURSOR_HMAC_KEY) in production.");
        }
        return new CursorCodec(key.getBytes(StandardCharsets.UTF_8));
    }
}
