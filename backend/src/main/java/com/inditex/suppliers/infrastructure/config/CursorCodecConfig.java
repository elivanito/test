package com.inditex.suppliers.infrastructure.config;

import com.inditex.suppliers.application.util.CursorCodec;
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
 * the {@code application-prod.yml} forces a real value.</p>
 */
@Configuration
public class CursorCodecConfig {

    @Bean
    public CursorCodec cursorCodec(
            @Value("${suppliers.cursor.key:dev-only-cursor-key-change-me}") String key) {
        return new CursorCodec(key.getBytes(StandardCharsets.UTF_8));
    }
}
