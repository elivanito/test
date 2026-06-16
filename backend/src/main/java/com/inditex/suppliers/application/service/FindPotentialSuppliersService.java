package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.port.in.FindPotentialSuppliersUseCase;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Cursor;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Filter;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Pagination;
import com.inditex.suppliers.application.util.CursorCodec;
import com.inditex.suppliers.application.util.PotentialSupplierLimits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class FindPotentialSuppliersService implements FindPotentialSuppliersUseCase {

    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    private final PotentialSupplierQuery query;
    private final CursorCodec cursorCodec;

    public FindPotentialSuppliersService(PotentialSupplierQuery query, CursorCodec cursorCodec) {
        this.query = query;
        this.cursorCodec = cursorCodec;
    }

    @Override
    @Transactional(readOnly = true)
    public PotentialSupplierPage find(Query q) {
        if (q.rate() < PotentialSupplierLimits.MIN_RATE) {
            throw new IllegalArgumentException("rate must be >= " + PotentialSupplierLimits.MIN_RATE);
        }
        if (q.limit() < PotentialSupplierLimits.MIN_LIMIT || q.limit() > PotentialSupplierLimits.MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be in ["
                    + PotentialSupplierLimits.MIN_LIMIT + ", " + PotentialSupplierLimits.MAX_LIMIT + "]");
        }
        if (q.offset() < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        String country = null;
        if (q.country() != null && !q.country().isBlank()) {
            country = q.country().toUpperCase();
            if (!COUNTRY.matcher(country).matches()) {
                throw new IllegalArgumentException("country must be ISO 3166-1 alpha-2");
            }
        }

        // null cursor parameter → offset mode. A present-but-blank cursor opts into
        // keyset mode starting from the first page (the client cannot construct one
        // without the server's signature, so empty string is the agreed "give me
        // the first keyset page" convention).
        Pagination pagination;
        if (q.cursor() != null) {
            Cursor cursor = q.cursor().isBlank() ? null : cursorCodec.decode(q.cursor());
            pagination = new Pagination.Keyset(cursor);
        } else {
            pagination = new Pagination.Offset(q.offset());
        }

        return query.findPotential(new Filter(q.rate(), q.limit(), pagination, country, q.maxRating()));
    }
}


