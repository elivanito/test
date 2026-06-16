package com.inditex.suppliers.infrastructure.persistence.repository;

import com.inditex.suppliers.infrastructure.persistence.entity.CandidateJpaEntity;
import com.inditex.suppliers.infrastructure.persistence.entity.CandidateStateJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateJpaRepository extends JpaRepository<CandidateJpaEntity, Long> {

    Optional<CandidateJpaEntity> findByDunsAndState(Long duns, CandidateStateJpa state);

    boolean existsByDunsAndState(Long duns, CandidateStateJpa state);
}
