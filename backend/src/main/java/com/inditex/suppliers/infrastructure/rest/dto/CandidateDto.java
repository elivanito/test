package com.inditex.suppliers.infrastructure.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CandidateDto(
        @NotNull @PositiveOrZero Long annualTurnover,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String country,
        @NotNull @Min(100_000_000L) @Max(999_999_999L) Long duns,
        @NotBlank String name
) {}
