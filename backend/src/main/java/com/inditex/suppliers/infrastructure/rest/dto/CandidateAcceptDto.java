package com.inditex.suppliers.infrastructure.rest.dto;

import com.inditex.suppliers.domain.vo.SustainabilityRating;
import jakarta.validation.constraints.NotNull;

public record CandidateAcceptDto(@NotNull SustainabilityRating sustainabilityRating) {}
