package com.inditex.suppliers.infrastructure.rest.dto;

public record SupplierDto(
        Long annualTurnover,
        String country,
        Long duns,
        String name,
        String sustainabilityRating,
        String status
) {}
