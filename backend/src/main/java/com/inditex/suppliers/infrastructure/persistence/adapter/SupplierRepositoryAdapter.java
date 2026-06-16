package com.inditex.suppliers.infrastructure.persistence.adapter;

import com.inditex.suppliers.application.port.out.SupplierRepository;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.infrastructure.persistence.entity.SupplierStatusJpa;
import com.inditex.suppliers.infrastructure.persistence.mapper.PersistenceMapper;
import com.inditex.suppliers.infrastructure.persistence.repository.SupplierJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SupplierRepositoryAdapter implements SupplierRepository {

    private final SupplierJpaRepository jpa;

    public SupplierRepositoryAdapter(SupplierJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Supplier> findByDuns(Duns duns) {
        return jpa.findById(duns.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByDuns(Duns duns) {
        return jpa.existsById(duns.value());
    }

    @Override
    public boolean isBanned(Duns duns) {
        return jpa.existsByDunsAndStatus(duns.value(), SupplierStatusJpa.DISQUALIFIED);
    }

    @Override
    public void save(Supplier supplier) {
        jpa.save(PersistenceMapper.toEntity(supplier));
    }
}
