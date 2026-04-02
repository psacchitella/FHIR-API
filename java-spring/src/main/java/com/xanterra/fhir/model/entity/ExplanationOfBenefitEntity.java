package com.xanterra.fhir.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "explanation_of_benefits")
public class ExplanationOfBenefitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, unique = true)
    private ClaimEntity claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @Column(nullable = false, length = 20)
    private String status; // active, cancelled, draft, entered-in-error

    @Column(nullable = false, length = 30)
    private String outcome; // queued, complete, error, partial

    @Column(nullable = false, length = 20)
    private String type; // institutional, oral, pharmacy, professional, vision

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSubmitted;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalBenefit; // amount insurer pays

    @Column(precision = 10, scale = 2)
    private BigDecimal patientResponsibility; // copay + deductible + coinsurance

    @Column(precision = 10, scale = 2)
    private BigDecimal deductibleApplied;

    @Column(precision = 10, scale = 2)
    private BigDecimal coinsuranceAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal copayAmount;

    private String disposition; // human-readable adjudication outcome

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
    public ClaimEntity getClaim() { return claim; }
    public void setClaim(ClaimEntity claim) { this.claim = claim; }
    public PatientEntity getPatient() { return patient; }
    public void setPatient(PatientEntity patient) { this.patient = patient; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getTotalSubmitted() { return totalSubmitted; }
    public void setTotalSubmitted(BigDecimal totalSubmitted) { this.totalSubmitted = totalSubmitted; }
    public BigDecimal getTotalBenefit() { return totalBenefit; }
    public void setTotalBenefit(BigDecimal totalBenefit) { this.totalBenefit = totalBenefit; }
    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal patientResponsibility) { this.patientResponsibility = patientResponsibility; }
    public BigDecimal getDeductibleApplied() { return deductibleApplied; }
    public void setDeductibleApplied(BigDecimal deductibleApplied) { this.deductibleApplied = deductibleApplied; }
    public BigDecimal getCoinsuranceAmount() { return coinsuranceAmount; }
    public void setCoinsuranceAmount(BigDecimal coinsuranceAmount) { this.coinsuranceAmount = coinsuranceAmount; }
    public BigDecimal getCopayAmount() { return copayAmount; }
    public void setCopayAmount(BigDecimal copayAmount) { this.copayAmount = copayAmount; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
