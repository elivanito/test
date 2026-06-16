package com.inditex.suppliers.application.port.out;

import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.vo.Duns;

import java.util.Optional;

public interface SupplierRepository {

    Optional<Supplier> findByDuns(Duns duns);

    boolean existsByDuns(Duns duns);

    boolean isBanned(Duns duns);

    void save(Supplier supplier);
}
