package com.inditex.suppliers.application.dto;

import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.SustainabilityRating;

/**
 * Read-model projection for a single potential supplier row.
 * Carries primitives because it crosses adapter boundaries and the
 * score is already computed by the database.
 */
public record PotentialSupplierView(
        long duns,
        String name,
        String country,
        long annualTurnover,
        SustainabilityRating sustainabilityRating,
        SupplierStatus status,
        double score
) {}
