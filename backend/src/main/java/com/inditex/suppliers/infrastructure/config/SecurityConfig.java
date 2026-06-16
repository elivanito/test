package com.inditex.suppliers.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration.
 *
 * <h3>Defaults (dev / local / docker-compose)</h3>
 * <p>All API endpoints are publicly accessible. This keeps the local feedback
 * loop frictionless and matches the challenge brief ("no authentication
 * required"). Actuator endpoints are also open in dev for observability tools.</p>
 *
 * <h3>{@code prod} profile</h3>
 * <p>Activated by {@code SPRING_PROFILES_ACTIVE=prod}. Requires a valid OAuth2
 * JWT (issuer URL configured via {@code spring.security.oauth2.resourceserver
 * .jwt.issuer-uri}) and applies role-based authorization:
 * <ul>
 *   <li>{@code GET /api/v1/suppliers/**} → {@code SCOPE_suppliers:read}</li>
 *   <li>{@code POST /api/v1/candidates/**} and {@code POST /api/v1/suppliers/*&#47;ban}
 *       → {@code SCOPE_suppliers:write}</li>
 * </ul>
 * Actuator's {@code /actuator/health} stays public; everything else under
 * {@code /actuator} requires {@code SCOPE_actuator}.
 * </p>
 *
 * <p>This setup is the <em>hook</em> requested by architecture review: not turned
 * on by default to keep the challenge runnable, but ready to be activated by
 * flipping the profile.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Permissive filter chain for non-production profiles. CSRF is disabled
     * because the API is stateless JSON consumed by the SPA, not browser forms.
     */
    @Bean
    @Profile("!prod")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Production filter chain: OAuth2 resource-server validating JWTs against
     * the configured issuer. The composition root supplies the issuer URI via
     * {@code application-prod.yml}.
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(
            HttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a
                        // Operations
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("SCOPE_actuator")
                        // API
                        .requestMatchers(HttpMethod.GET, "/api/v1/suppliers/**")
                            .hasAuthority("SCOPE_suppliers:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/candidates/**")
                            .hasAuthority("SCOPE_suppliers:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/**")
                            .hasAuthority("SCOPE_suppliers:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**")
                            .hasAuthority("SCOPE_suppliers:write")
                        // OpenAPI is closed in prod
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
