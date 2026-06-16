package com.inditex.suppliers.infrastructure.rest;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.dto.PotentialSupplierView;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.infrastructure.rest.dto.CandidateDto;
import com.inditex.suppliers.infrastructure.rest.dto.PaginationDto;
import com.inditex.suppliers.infrastructure.rest.dto.PotentialSupplierDto;
import com.inditex.suppliers.infrastructure.rest.dto.PotentialSuppliersDto;
import com.inditex.suppliers.infrastructure.rest.dto.SupplierDto;

/**
 * Domain ↔ REST DTO conversions.
 *
 * Collapses internal SupplierStatus.ACTIVE and ON_PROBATION into the public
 * "Active" value, as mandated by the OpenAPI contract.
 */
public final class RestMapper {

    private RestMapper() {}

    public static CandidateDto toDto(Candidate c) {
        return new CandidateDto(
                c.annualTurnover().euros(),
                c.country().value(),
                c.duns().value(),
                c.name()
        );
    }

    public static SupplierDto toDto(Supplier s) {
        return new SupplierDto(
                s.annualTurnover().euros(),
                s.country().value(),
                s.duns().value(),
                s.name(),
                s.rating().name(),
                publicStatus(s.status())
        );
    }

    public static PotentialSuppliersDto toDto(PotentialSupplierPage page) {
        return new PotentialSuppliersDto(
                page.data().stream().map(RestMapper::toDto).toList(),
                new PaginationDto(page.limit(), page.offset(), page.total(), page.nextCursor())
        );
    }

    public static PotentialSupplierDto toDto(PotentialSupplierView v) {
        return new PotentialSupplierDto(
                v.annualTurnover(),
                v.country(),
                v.duns(),
                v.name(),
                v.sustainabilityRating().name(),
                publicStatus(v.status()),
                v.score()
        );
    }

    private static String publicStatus(SupplierStatus status) {
        return status == SupplierStatus.DISQUALIFIED ? "Disqualified" : "Active";
    }
}
