package com.inditex.suppliers.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pagination metadata exposed to API clients.
 *
 * <p>In offset mode {@code total} carries the global count and {@code nextCursor}
 * is {@code null}. In keyset mode the server intentionally omits {@code total}
 * (no COUNT(*) at scale) and emits {@code nextCursor} when more pages exist.</p>
 *
 * <p>Both nullable fields are dropped from the wire when {@code null} thanks to
 * {@link JsonInclude} — no sentinel values leak to clients.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationDto(int limit, int offset, Long total, String nextCursor) {}
