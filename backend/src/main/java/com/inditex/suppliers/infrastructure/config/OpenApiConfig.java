package com.inditex.suppliers.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi configuration. Produces:
 * <ul>
 *   <li>{@code GET /v3/api-docs} — machine-readable OpenAPI 3.1 JSON.</li>
 *   <li>{@code GET /swagger-ui.html} — interactive UI for local exploration.</li>
 * </ul>
 *
 * <p>Annotated controllers and DTOs are introspected automatically; this bean just
 * adds top-level metadata.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI suppliersOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Inditex Supplier Management API")
                .version("1.0.0")
                .description("""
                        Manages the supplier onboarding life-cycle (Candidate FSM) and
                        the read-model that ranks potential suppliers with server-side
                        filters and keyset pagination scalable to 1M+ rows.
                        """)
                .contact(new Contact().name("Tech Lead Inditex"))
                .license(new License().name("Proprietary")));
    }
}
