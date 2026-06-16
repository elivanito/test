package com.inditex.suppliers.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration: allows the Angular dev server (localhost:4200) and any
 * origin in development. In docker compose the frontend talks via nginx
 * reverse-proxy so CORS is not strictly required, but it keeps `ng serve`
 * usable against a locally running backend.
 *
 * <p>The versioned URL prefix {@code /api/v1} is applied at each {@code @RestController}
 * to keep slice tests ({@code @WebMvcTest}) using the same paths as production.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Single source of truth for the public API base path. */
    public static final String API_V1 = "/api/v1";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4200", "http://localhost")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*");
    }
}
