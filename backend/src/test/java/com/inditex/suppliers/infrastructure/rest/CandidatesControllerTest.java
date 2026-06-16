package com.inditex.suppliers.infrastructure.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inditex.suppliers.application.port.in.AcceptCandidateUseCase;
import com.inditex.suppliers.application.port.in.CreateCandidateUseCase;
import com.inditex.suppliers.application.port.in.GetCandidateUseCase;
import com.inditex.suppliers.application.port.in.RefuseCandidateUseCase;
import com.inditex.suppliers.domain.exception.CandidateAlreadyExistsException;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.exception.CandidateNotPendingException;
import com.inditex.suppliers.domain.exception.CountryBannedException;
import com.inditex.suppliers.domain.exception.InsufficientTurnoverException;
import com.inditex.suppliers.domain.exception.SupplierBannedException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import com.inditex.suppliers.infrastructure.rest.dto.CandidateAcceptDto;
import com.inditex.suppliers.infrastructure.rest.dto.CandidateDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CandidatesController.class)
@Import({GlobalExceptionHandler.class, com.inditex.suppliers.infrastructure.config.SecurityConfig.class})
class CandidatesControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @MockitoBean CreateCandidateUseCase create;
    @MockitoBean GetCandidateUseCase getUc;
    @MockitoBean AcceptCandidateUseCase accept;
    @MockitoBean RefuseCandidateUseCase refuse;

    private static final long DUNS = 123_456_789L;

    private Candidate sampleCandidate() {
        return Candidate.rehydrate(Duns.of(DUNS), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(1_500_000L),
                com.inditex.suppliers.domain.model.CandidateState.PENDING, 0L);
    }

    // ---------- POST /candidates ----------

    @Test
    void create_returns_201_with_body() throws Exception {
        when(create.create(any())).thenReturn(sampleCandidate());

        CandidateDto body = new CandidateDto(1_500_000L, "ES", DUNS, "Acme");

        mvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duns").value(DUNS))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.country").value("ES"));
    }

    @Test
    void create_returns_400_when_payload_invalid() throws Exception {
        // missing required fields
        mvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns_400_when_country_pattern_violated() throws Exception {
        CandidateDto body = new CandidateDto(1_500_000L, "ESP", DUNS, "Acme");
        mvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns_409_when_candidate_already_exists() throws Exception {
        when(create.create(any())).thenThrow(new CandidateAlreadyExistsException());
        CandidateDto body = new CandidateDto(1_500_000L, "ES", DUNS, "Acme");
        mvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_returns_409_when_supplier_banned() throws Exception {
        when(create.create(any())).thenThrow(new SupplierBannedException());
        CandidateDto body = new CandidateDto(1_500_000L, "ES", DUNS, "Acme");
        mvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.info").value("Supplier banned"));
    }

    // ---------- GET /candidates/{duns} ----------

    @Test
    void get_returns_404_when_not_found() throws Exception {
        when(getUc.get(DUNS)).thenThrow(new CandidateNotFoundException(DUNS));
        mvc.perform(get("/api/v1/candidates/{duns}", DUNS))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_returns_200_when_found() throws Exception {
        when(getUc.get(DUNS)).thenReturn(sampleCandidate());
        mvc.perform(get("/api/v1/candidates/{duns}", DUNS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duns").value(DUNS));
    }

    @Test
    void get_returns_400_when_duns_out_of_range() throws Exception {
        mvc.perform(get("/api/v1/candidates/{duns}", 42))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /candidates/{duns}/accept ----------

    @Test
    void accept_returns_204_on_success() throws Exception {
        CandidateAcceptDto body = new CandidateAcceptDto(SustainabilityRating.A);
        mvc.perform(post("/api/v1/candidates/{duns}/accept", DUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNoContent());
        verify(accept).accept(eq(DUNS), eq(SustainabilityRating.A));
    }

    @Test
    void accept_returns_404_when_candidate_missing() throws Exception {
        doThrow(new CandidateNotFoundException(DUNS)).when(accept).accept(eq(DUNS), any());
        CandidateAcceptDto body = new CandidateAcceptDto(SustainabilityRating.A);
        mvc.perform(post("/api/v1/candidates/{duns}/accept", DUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void accept_returns_422_when_country_banned() throws Exception {
        doThrow(new CountryBannedException("NG")).when(accept).accept(eq(DUNS), any());
        CandidateAcceptDto body = new CandidateAcceptDto(SustainabilityRating.A);
        mvc.perform(post("/api/v1/candidates/{duns}/accept", DUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void accept_returns_422_when_insufficient_turnover() throws Exception {
        doThrow(new InsufficientTurnoverException()).when(accept).accept(eq(DUNS), any());
        CandidateAcceptDto body = new CandidateAcceptDto(SustainabilityRating.A);
        mvc.perform(post("/api/v1/candidates/{duns}/accept", DUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void accept_returns_409_when_candidate_not_pending() throws Exception {
        doThrow(new CandidateNotPendingException("accepted")).when(accept).accept(eq(DUNS), any());
        CandidateAcceptDto body = new CandidateAcceptDto(SustainabilityRating.A);
        mvc.perform(post("/api/v1/candidates/{duns}/accept", DUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // ---------- POST /candidates/{duns}/refuse ----------

    @Test
    void refuse_returns_204_on_success() throws Exception {
        mvc.perform(post("/api/v1/candidates/{duns}/refuse", DUNS))
                .andExpect(status().isNoContent());
        verify(refuse).refuse(DUNS);
    }

    @Test
    void refuse_returns_404_when_missing() throws Exception {
        doThrow(new CandidateNotFoundException(DUNS)).when(refuse).refuse(DUNS);
        mvc.perform(post("/api/v1/candidates/{duns}/refuse", DUNS))
                .andExpect(status().isNotFound());
    }
}
