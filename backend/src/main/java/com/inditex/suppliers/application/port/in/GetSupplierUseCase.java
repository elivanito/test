package com.inditex.suppliers.application.port.in;

import com.inditex.suppliers.domain.model.Supplier;

public interface GetSupplierUseCase {
    Supplier get(long duns);
}
