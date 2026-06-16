package com.inditex.suppliers.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration restricted to non-production profiles.
 *
 * <p>Allows the Angular dev server (localhost:4200) during local development.
 * In docker-compose the frontend talks via nginx reverse-proxy so CORS is not
 * required; in production the SPA is served from the same origin.</p>
 *
 * <p>The versioned URL prefix {@code /api/v1} is applied at each {@code @RestController}
 * to keep slice tests ({@code @WebMvcTest}) using the same paths as production.</p>
 */
@Configuration
@Profile("!prod")
public class WebConfig implements WebMvcConfigurer {

    /** Single source of truth for the public API base path. */
    public static final String API_V1 = "/api/v1";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200", "http://localhost")
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Correlation-Id", "Accept")
                .maxAge(3600);
    }
}
