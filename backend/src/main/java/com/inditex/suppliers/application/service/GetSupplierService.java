package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.GetSupplierUseCase;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.application.util.DunsLookup;
import com.inditex.suppliers.domain.model.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSupplierService implements GetSupplierUseCase {

    private final SupplierRepository suppliers;

    public GetSupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public Supplier get(long dunsValue) {
        return DunsLookup.requireSupplier(suppliers, dunsValue);
    }
}
