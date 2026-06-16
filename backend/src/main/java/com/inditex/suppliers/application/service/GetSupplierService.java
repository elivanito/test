package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.in.GetSupplierUseCase;
import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.exception.SupplierNotFoundException;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.vo.Duns;
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
        Duns duns = Duns.of(dunsValue);
        return suppliers.findByDuns(duns)
                .orElseThrow(() -> new SupplierNotFoundException(dunsValue));
    }
}
