package com.inditex.suppliers.infrastructure.persistence.mapper;

import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.CandidateState;
import com.inditex.suppliers.domain.model.Supplier;
import com.inditex.suppliers.domain.model.SupplierStatus;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import com.inditex.suppliers.domain.vo.SustainabilityRating;
import com.inditex.suppliers.infrastructure.persistence.entity.CandidateJpaEntity;
import com.inditex.suppliers.infrastructure.persistence.entity.CandidateStateJpa;
import com.inditex.suppliers.infrastructure.persistence.entity.SupplierJpaEntity;
import com.inditex.suppliers.infrastructure.persistence.entity.SupplierStatusJpa;
import com.inditex.suppliers.infrastructure.persistence.entity.SustainabilityRatingJpa;

/** Pure mapping between domain aggregates and JPA entities (and vice-versa). */
public final class PersistenceMapper {

    private PersistenceMapper() {}

    public static Candidate toDomain(CandidateJpaEntity e) {
        return Candidate.rehydrate(
                Duns.of(e.getDuns()),
                e.getName(),
                CountryCode.of(e.getCountry()),
                AnnualTurnover.of(e.getAnnualTurnover()),
                EnumMapper.map(e.getState(), CandidateState.class),
                versionOrZero(e.getVersion())
        );
    }

    public static CandidateJpaEntity toEntity(Candidate c) {
        return new CandidateJpaEntity(
                c.duns().value(),
                EnumMapper.map(c.state(), CandidateStateJpa.class),
                c.name(),
                c.country().value(),
                c.annualTurnover().euros(),
                c.version()
        );
    }

    public static Supplier toDomain(SupplierJpaEntity e) {
        return Supplier.rehydrate(
                Duns.of(e.getDuns()),
                e.getName(),
                CountryCode.of(e.getCountry()),
                AnnualTurnover.of(e.getAnnualTurnover()),
                EnumMapper.map(e.getSustainabilityRating(), SustainabilityRating.class),
                EnumMapper.map(e.getStatus(), SupplierStatus.class),
                versionOrZero(e.getVersion())
        );
    }

    public static SupplierJpaEntity toEntity(Supplier s) {
        return new SupplierJpaEntity(
                s.duns().value(),
                s.name(),
                s.country().value(),
                s.annualTurnover().euros(),
                EnumMapper.map(s.rating(), SustainabilityRatingJpa.class),
                EnumMapper.map(s.status(), SupplierStatusJpa.class),
                s.version()
        );
    }

    private static long versionOrZero(Long version) {
        return version == null ? 0L : version;
    }
}
