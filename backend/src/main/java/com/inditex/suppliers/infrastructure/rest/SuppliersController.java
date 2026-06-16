package com.inditex.suppliers.infrastructure.rest;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.port.in.BanSupplierUseCase;
import com.inditex.suppliers.application.port.in.FindPotentialSuppliersUseCase;
import com.inditex.suppliers.application.port.in.GetSupplierUseCase;
import com.inditex.suppliers.application.util.PotentialSupplierLimits;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import com.inditex.suppliers.infrastructure.rest.dto.PotentialSuppliersDto;
import com.inditex.suppliers.infrastructure.rest.dto.SupplierDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
@Validated
@Tag(name = "Suppliers", description = "Active and disqualified suppliers; ranking by score.")
public class SuppliersController {

    private final GetSupplierUseCase get;
    private final BanSupplierUseCase ban;
    private final FindPotentialSuppliersUseCase findPotential;

    public SuppliersController(GetSupplierUseCase get,
                               BanSupplierUseCase ban,
                               FindPotentialSuppliersUseCase findPotential) {
        this.get = get;
        this.ban = ban;
        this.findPotential = findPotential;
    }

    @Operation(summary = "Get a supplier by DUNS")
    @GetMapping("/{duns}")
    public SupplierDto get(@PathVariable @Min(100_000_000L) @Max(999_999_999L) long duns) {
        return RestMapper.toDto(get.get(duns));
    }

    @Operation(summary = "Ban a supplier on probation")
    @PostMapping("/{duns}/ban")
    public ResponseEntity<Void> ban(@PathVariable @Min(100_000_000L) @Max(999_999_999L) long duns) {
        ban.ban(duns);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists potential suppliers, scored and ordered by score DESC.
     *
     * <p>Pagination strategies:
     * <ul>
     *   <li><b>Offset</b>: send {@code offset} (default 0). Response carries {@code total}.</li>
     *   <li><b>Keyset</b> (recommended for deep paging): send the {@code cursor}
     *       returned in {@code pagination.nextCursor}. {@code offset} is ignored
     *       when {@code cursor} is present.</li>
     * </ul>
     */
    @Operation(
            summary = "List potential suppliers (server-side filters + keyset pagination)",
            description = "Filters and scoring run entirely in SQL. Supports both offset (legacy) "
                    + "and keyset (scalable) pagination."
    )
    @GetMapping("/potential")
    public org.springframework.http.ResponseEntity<PotentialSuppliersDto> potential(
            @Parameter(description = "Minimum annual turnover threshold (€). Must be ≥ 250.")
            @RequestParam @Min(PotentialSupplierLimits.MIN_RATE) long rate,

            @Parameter(description = "Page size. 1..10, default 10.")
            @RequestParam(required = false, defaultValue = "10")
            @Min(PotentialSupplierLimits.MIN_LIMIT) @Max(PotentialSupplierLimits.MAX_LIMIT) int limit,

            @Parameter(description = "Offset for legacy pagination. Ignored when 'cursor' is set.")
            @RequestParam(required = false, defaultValue = "0") @PositiveOrZero int offset,

            @Parameter(description = "Opaque keyset cursor returned in pagination.nextCursor.")
            @RequestParam(required = false) String cursor,

            @Parameter(description = "ISO-3166-1 alpha-2 country filter, e.g. ES.")
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z]{2}$") String country,

            @Parameter(description = "Inclusive upper bound on sustainability rating: A..E.")
            @RequestParam(required = false) SustainabilityRating maxRating) {

        PotentialSupplierPage page = findPotential.find(
                new FindPotentialSuppliersUseCase.Query(rate, limit, offset, cursor, country, maxRating));

        // HTTP cache hint: the result is a function of (rate, limit, cursor/offset,
        // country, maxRating). The same caller repeating the same query within
        // a short window is the typical UI pattern (back/forward, debounce).
        // 30s public TTL absorbs that without serving very stale data; downstream
        // CDN/edge can revalidate via the underlying ETag if added later.
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl
                        .maxAge(java.time.Duration.ofSeconds(30))
                        .cachePublic())
                .body(RestMapper.toDto(page));
    }
}

