package com.inditex.suppliers.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void uses_incoming_header_when_present() throws ServletException, IOException {
        String incoming = "abc-123";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, incoming);
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (a, b) -> captured.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(req, res, chain);

        assertThat(captured.get()).isEqualTo(incoming);
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(incoming);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull(); // cleaned up
    }

    @Test
    void generates_a_uuid_when_no_header() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> captured = new AtomicReference<>();
        filter.doFilter(req, res, (a, b) -> captured.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        // Valid UUID
        UUID.fromString(captured.get());
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(captured.get());
    }

    @Test
    void cleans_mdc_even_when_chain_throws() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (a, b) -> { throw new ServletException("boom"); };

        try {
            filter.doFilter(req, res, chain);
        } catch (Exception expected) {
            // ignored
        }
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
