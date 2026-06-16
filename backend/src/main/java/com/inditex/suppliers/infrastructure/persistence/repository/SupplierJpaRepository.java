package com.inditex.suppliers.infrastructure.persistence.repository;

import com.inditex.suppliers.infrastructure.persistence.entity.SupplierJpaEntity;
import com.inditex.suppliers.infrastructure.persistence.entity.SupplierStatusJpa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierJpaRepository extends JpaRepository<SupplierJpaEntity, Long> {

    boolean existsByDunsAndStatus(Long duns, SupplierStatusJpa status);
}
