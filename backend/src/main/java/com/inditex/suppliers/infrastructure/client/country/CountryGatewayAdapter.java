package com.inditex.suppliers.infrastructure.client.country;

import com.inditex.suppliers.application.port.out.CountryGateway;
import com.inditex.suppliers.domain.exception.CountryUnknownException;
import com.inditex.suppliers.domain.vo.CountryCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Country-service adapter.
 *
 * <ul>
 *   <li><b>Cached</b> (Caffeine, configured globally in application.yml). Repeated
 *       lookups for the same country code reuse the cached result.</li>
 *   <li><b>Circuit-breaker protected</b> (Resilience4j {@code countryService}). When
 *       the breaker opens, requests short-circuit to {@link #fallback}.</li>
 *   <li><b>404 → {@link CountryUnknownException}</b>, not "not banned": refusing
 *       to silently accept candidates with non-existent ISO codes.</li>
 * </ul>
 *
 * <h3>Fallback policy</h3>
 * <p>The Architecture Decision is <b>fail-closed</b>: if the country-service is
 * unreachable, we treat <em>every</em> country as banned and reject candidate
 * acceptance. Compliance-driven systems prefer false negatives over false
 * positives — admitting a possibly-banned supplier could trigger a regulatory
 * finding. The opposite ("fail-open") would require a {@code PENDING_REVIEW}
 * candidate state and a reconciliation worker; out of scope for this iteration.
 * See ADR-007 in SOLUTION.md.</p>
 *
 * <p>The {@code country_service_fallback_total} counter exposes how often the
 * fallback fires so we can alert on degraded upstream availability.</p>
 */
@Component
public class CountryGatewayAdapter implements CountryGateway {

    private static final Logger log = LoggerFactory.getLogger(CountryGatewayAdapter.class);

    private final RestClient client;
    private final Counter fallbackCounter;

    public CountryGatewayAdapter(RestClient countryRestClient, MeterRegistry meterRegistry) {
        this.client = countryRestClient;
        this.fallbackCounter = Counter.builder("country_service.fallback")
                .description("Times the country-service circuit breaker triggered the fail-closed fallback")
                .register(meterRegistry);
    }

    @Override
    @Cacheable(cacheNames = "countries", key = "#country.value()")
    @CircuitBreaker(name = "countryService", fallbackMethod = "fallback")
    public boolean isBanned(CountryCode country) {
        try {
            CountryResponse response = client.get()
                    .uri("/countries/{code}", country.value())
                    .retrieve()
                    .body(CountryResponse.class);
            return response != null && response.isBanned();
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 404) {
                throw new CountryUnknownException(country.value());
            }
            throw ex;
        }
    }

    /**
     * Fail-closed fallback. Triggered when the circuit is open or the upstream
     * call throws a non-translated error. The signature must mirror the original
     * method plus the {@link Throwable} as last argument (Resilience4j contract).
     *
     * <p>Note: {@link CountryUnknownException} bypasses the fallback because
     * Resilience4j is configured to ignore domain exceptions (see
     * {@code resilience4j.circuitbreaker.instances.countryService.ignoreExceptions}
     * in application.yml). A 404 is a business outcome, not a transport failure.</p>
     */
    @SuppressWarnings("unused")
    private boolean fallback(CountryCode country, Throwable t) {
        if (t instanceof CountryUnknownException cue) {
            throw cue;
        }
        fallbackCounter.increment();
        log.warn("country-service unavailable for code={} cause={}; failing closed (banned)",
                country.value(), t.toString());
        return true; // fail-closed
    }
}
