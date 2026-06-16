package com.inditex.suppliers.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "suppliers")
public class SupplierJpaEntity {

    @Id
    @Column(name = "duns")
    private Long duns;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "annual_turnover", nullable = false)
    private Long annualTurnover;

    @Column(name = "sustainability_rating", nullable = false, length = 1)
    @Enumerated(EnumType.STRING)
    private SustainabilityRatingJpa sustainabilityRating;

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private SupplierStatusJpa status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public SupplierJpaEntity() { }

    public SupplierJpaEntity(Long duns, String name, String country, Long annualTurnover,
                             SustainabilityRatingJpa rating, SupplierStatusJpa status,
                             Long version) {
        this.duns = duns;
        this.name = name;
        this.country = country;
        this.annualTurnover = annualTurnover;
        this.sustainabilityRating = rating;
        this.status = status;
        this.version = version;
    }

    public Long getDuns() { return duns; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public Long getAnnualTurnover() { return annualTurnover; }
    public SustainabilityRatingJpa getSustainabilityRating() { return sustainabilityRating; }
    public SupplierStatusJpa getStatus() { return status; }
    public Long getVersion() { return version; }

    public void setDuns(Long duns) { this.duns = duns; }
    public void setName(String name) { this.name = name; }
    public void setCountry(String country) { this.country = country; }
    public void setAnnualTurnover(Long annualTurnover) { this.annualTurnover = annualTurnover; }
    public void setSustainabilityRating(SustainabilityRatingJpa sustainabilityRating) { this.sustainabilityRating = sustainabilityRating; }
    public void setStatus(SupplierStatusJpa status) { this.status = status; }
    public void setVersion(Long version) { this.version = version; }
}
