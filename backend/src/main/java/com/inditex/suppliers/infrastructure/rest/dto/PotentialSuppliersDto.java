package com.inditex.suppliers.infrastructure.rest.dto;

import java.util.List;

public record PotentialSuppliersDto(List<PotentialSupplierDto> data, PaginationDto pagination) {}
