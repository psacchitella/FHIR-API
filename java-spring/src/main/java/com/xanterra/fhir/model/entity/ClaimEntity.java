package com.xanterra.fhir.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class ClaimEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id", nullable = false)
    private CoverageEntity coverage;

    @Column(nullable = false, length = 20)
    private String status; // active, cancelled, draft, entered-in-error

    @Column(nullable = false, length = 20)
    private String type; // institutional, oral, pharmacy, professional, vision

    @Column(nullable = false, length = 20)
    private String use; // claim, preauthorization, predetermination

    @Column(nullable = false)
    private LocalDate billablePeriodStart;

    @Column(nullable = false)
    private LocalDate billablePeriodEnd;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 3)
    private String currency = "USD";

    private String providerReference;
    private String facilityReference;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClaimItemEntity> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PatientEntity getPatient() { return patient; }
    public void setPatient(PatientEntity patient) { this.patient = patient; }
    public CoverageEntity getCoverage() { return coverage; }
    public void setCoverage(CoverageEntity coverage) { this.coverage = coverage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUse() { return use; }
    public void setUse(String use) { this.use = use; }
    public LocalDate getBillablePeriodStart() { return billablePeriodStart; }
    public void setBillablePeriodStart(LocalDate billablePeriodStart) { this.billablePeriodStart = billablePeriodStart; }
    public LocalDate getBillablePeriodEnd() { return billablePeriodEnd; }
    public void setBillablePeriodEnd(LocalDate billablePeriodEnd) { this.billablePeriodEnd = billablePeriodEnd; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getFacilityReference() { return facilityReference; }
    public void setFacilityReference(String facilityReference) { this.facilityReference = facilityReference; }
    public List<ClaimItemEntity> getItems() { return items; }
    public void setItems(List<ClaimItemEntity> items) { this.items = items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void addItem(ClaimItemEntity item) {
        items.add(item);
        item.setClaim(this);
    }
}
