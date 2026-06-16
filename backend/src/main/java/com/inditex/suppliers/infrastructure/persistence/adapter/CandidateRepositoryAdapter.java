package com.inditex.suppliers.infrastructure.persistence.adapter;

import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.infrastructure.persistence.entity.CandidateStateJpa;
import com.inditex.suppliers.infrastructure.persistence.mapper.PersistenceMapper;
import com.inditex.suppliers.infrastructure.persistence.repository.CandidateJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CandidateRepositoryAdapter implements CandidateRepository {

    private final CandidateJpaRepository jpa;

    public CandidateRepositoryAdapter(CandidateJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Candidate> findActiveByDuns(Duns duns) {
        return jpa.findByDunsAndState(duns.value(), CandidateStateJpa.PENDING)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Candidate> findByDuns(Duns duns) {
        return jpa.findById(duns.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public boolean existsActiveByDuns(Duns duns) {
        return jpa.existsByDunsAndState(duns.value(), CandidateStateJpa.PENDING);
    }

    @Override
    public void save(Candidate candidate) {
        jpa.save(PersistenceMapper.toEntity(candidate));
    }

    @Override
    public void deleteByDuns(Duns duns) {
        jpa.deleteById(duns.value());
    }
}
