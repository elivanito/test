package com.inditex.suppliers.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * JPA mapping for candidate rows. Identity is the DUNS alone; {@code state}
 * tracks the candidacy lifecycle (PENDING → REFUSED, or REFUSED → PENDING on
 * re-application). Optimistic locking via {@link Version} prevents two
 * concurrent transitions from clobbering each other.
 */
@Entity
@Table(name = "candidates")
public class CandidateJpaEntity {

    @Id
    @Column(name = "duns", nullable = false)
    private Long duns;

    @Column(name = "state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private CandidateStateJpa state;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "annual_turnover", nullable = false)
    private Long annualTurnover;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CandidateJpaEntity() { }

    public CandidateJpaEntity(Long duns, CandidateStateJpa state, String name, String country,
                              Long annualTurnover, Long version) {
        this.duns = duns;
        this.state = state;
        this.name = name;
        this.country = country;
        this.annualTurnover = annualTurnover;
        this.version = version;
    }

    public Long getDuns() { return duns; }
    public CandidateStateJpa getState() { return state; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public Long getAnnualTurnover() { return annualTurnover; }
    public Long getVersion() { return version; }

    public void setDuns(Long duns) { this.duns = duns; }
    public void setState(CandidateStateJpa state) { this.state = state; }
    public void setName(String name) { this.name = name; }
    public void setCountry(String country) { this.country = country; }
    public void setAnnualTurnover(Long annualTurnover) { this.annualTurnover = annualTurnover; }
    public void setVersion(Long version) { this.version = version; }
}
