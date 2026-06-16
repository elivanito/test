package com.inditex.suppliers.infrastructure.rest.dto;

public record PotentialSupplierDto(
        Long annualTurnover,
        String country,
        Long duns,
        String name,
        String sustainabilityRating,
        String status,
        Double score
) {}
