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
                toDomain(e.getState()),
                e.getVersion() == null ? 0L : e.getVersion()
        );
    }

    public static CandidateJpaEntity toEntity(Candidate c) {
        return new CandidateJpaEntity(
                c.duns().value(),
                toJpa(c.state()),
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
                SustainabilityRating.valueOf(e.getSustainabilityRating().name()),
                toDomain(e.getStatus()),
                e.getVersion() == null ? 0L : e.getVersion()
        );
    }

    public static SupplierJpaEntity toEntity(Supplier s) {
        return new SupplierJpaEntity(
                s.duns().value(),
                s.name(),
                s.country().value(),
                s.annualTurnover().euros(),
                SustainabilityRatingJpa.valueOf(s.rating().name()),
                toJpa(s.status()),
                s.version()
        );
    }

    public static CandidateState toDomain(CandidateStateJpa s) {
        return CandidateState.valueOf(s.name());
    }

    public static CandidateStateJpa toJpa(CandidateState s) {
        return CandidateStateJpa.valueOf(s.name());
    }

    public static SupplierStatus toDomain(SupplierStatusJpa s) {
        return SupplierStatus.valueOf(s.name());
    }

    public static SupplierStatusJpa toJpa(SupplierStatus s) {
        return SupplierStatusJpa.valueOf(s.name());
    }
}
