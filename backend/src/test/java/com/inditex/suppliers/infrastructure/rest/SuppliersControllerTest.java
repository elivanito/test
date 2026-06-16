package com.inditex.suppliers.infrastructure.rest;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.dto.PotentialSupplierView;
import com.inditex.suppliers.application.port.in.BanSupplierUseCase;
import com.inditex.suppliers.application.port.in.FindPotentialSuppliersUseCase;
import com.inditex.suppliers.application.port.in.GetSupplierUseCase;
import com.inditex.suppliers.domain.exception.SupplierCannotBeBannedException;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuppliersController.class)
@Import({GlobalExceptionHandler.class, com.inditex.suppliers.infrastructure.config.SecurityConfig.class})
class SuppliersControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetSupplierUseCase getUc;
    @MockitoBean BanSupplierUseCase ban;
    @MockitoBean FindPotentialSuppliersUseCase findPotential;

    private static final long DUNS = 123_456_789L;

    private Supplier sample(SupplierStatus status) {
        return Supplier.rehydrate(Duns.of(DUNS), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(2_000_000L),
                SustainabilityRating.B, status, 0L);
    }

    // ---------- GET /suppliers/{duns} ----------

    @Test
    void get_returns_200_and_exposes_on_probation_as_Active() throws Exception {
        when(getUc.get(DUNS)).thenReturn(sample(SupplierStatus.ON_PROBATION));
        mvc.perform(get("/api/v1/suppliers/{duns}", DUNS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duns").value(DUNS))
                .andExpect(jsonPath("$.status").value("Active"));
    }

    @Test
    void get_returns_200_with_Active_for_disqualified() throws Exception {
        // Sanity: disqualified is publicly exposed too — the API contract surfaces it as Disqualified
        when(getUc.get(DUNS)).thenReturn(sample(SupplierStatus.DISQUALIFIED));
        mvc.perform(get("/api/v1/suppliers/{duns}", DUNS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Disqualified"));
    }

    @Test
    void get_returns_404_when_supplier_missing() throws Exception {
        when(getUc.get(DUNS)).thenThrow(new SupplierNotFoundException(DUNS));
        mvc.perform(get("/api/v1/suppliers/{duns}", DUNS))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_returns_400_when_duns_out_of_range() throws Exception {
        mvc.perform(get("/api/v1/suppliers/{duns}", 99))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /suppliers/{duns}/ban ----------

    @Test
    void ban_returns_204_on_success() throws Exception {
        mvc.perform(post("/api/v1/suppliers/{duns}/ban", DUNS))
                .andExpect(status().isNoContent());
    }

    @Test
    void ban_returns_409_when_supplier_cannot_be_banned() throws Exception {
        doThrow(new SupplierCannotBeBannedException()).when(ban).ban(DUNS);
        mvc.perform(post("/api/v1/suppliers/{duns}/ban", DUNS))
                .andExpect(status().isConflict());
    }

    @Test
    void ban_returns_404_when_supplier_missing() throws Exception {
        doThrow(new SupplierNotFoundException(DUNS)).when(ban).ban(DUNS);
        mvc.perform(post("/api/v1/suppliers/{duns}/ban", DUNS))
                .andExpect(status().isNotFound());
    }

    @Test
    void ban_returns_409_on_optimistic_lock_failure() throws Exception {
        doThrow(new OptimisticLockingFailureException("stale")).when(ban).ban(DUNS);
        mvc.perform(post("/api/v1/suppliers/{duns}/ban", DUNS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.info").value(
                        org.hamcrest.Matchers.containsString("Concurrent")));
    }

    // ---------- GET /suppliers/potential ----------

    @Test
    void potential_returns_200_with_paginated_payload() throws Exception {
        PotentialSupplierView view = new PotentialSupplierView(
                DUNS, "Acme", "ES", 2_000_000L,
                SustainabilityRating.A, SupplierStatus.ACTIVE, 250_000.0);
        when(findPotential.find(any())).thenReturn(
                new PotentialSupplierPage(List.of(view), 10, 0, 1L, null));

        mvc.perform(get("/api/v1/suppliers/potential").param("rate", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].duns").value(DUNS))
                .andExpect(jsonPath("$.data[0].status").value("Active"))
                .andExpect(jsonPath("$.data[0].sustainabilityRating").value("A"))
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.offset").value(0));
    }

    @Test
    void potential_returns_400_when_rate_below_minimum() throws Exception {
        mvc.perform(get("/api/v1/suppliers/potential").param("rate", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void potential_returns_400_when_limit_above_maximum() throws Exception {
        mvc.perform(get("/api/v1/suppliers/potential")
                        .param("rate", "1000")
                        .param("limit", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void potential_returns_400_when_country_is_not_iso_alpha_2() throws Exception {
        mvc.perform(get("/api/v1/suppliers/potential")
                        .param("rate", "1000")
                        .param("country", "SPAIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void potential_exposes_nextCursor_in_pagination_payload() throws Exception {
        PotentialSupplierView view = new PotentialSupplierView(
                DUNS, "Acme", "ES", 2_000_000L,
                SustainabilityRating.A, SupplierStatus.ACTIVE, 250_000.0);
        when(findPotential.find(any())).thenReturn(
                PotentialSupplierPage.ofKeyset(List.of(view), 10, "opaque-cursor"));

        mvc.perform(get("/api/v1/suppliers/potential")
                        .param("rate", "1000")
                        .param("country", "ES")
                        .param("maxRating", "B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.nextCursor").value("opaque-cursor"))
                .andExpect(jsonPath("$.pagination.total").doesNotExist());
    }
}
