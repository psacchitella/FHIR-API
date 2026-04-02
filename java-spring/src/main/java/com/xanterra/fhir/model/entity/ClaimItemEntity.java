package com.xanterra.fhir.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claim_items")
public class ClaimItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private ClaimEntity claim;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private String productOrServiceCode; // CPT/HCPCS code

    private String productOrServiceDisplay;

    private LocalDate servicedDate;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal netAmount;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ClaimEntity getClaim() { return claim; }
    public void setClaim(ClaimEntity claim) { this.claim = claim; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public String getProductOrServiceCode() { return productOrServiceCode; }
    public void setProductOrServiceCode(String productOrServiceCode) { this.productOrServiceCode = productOrServiceCode; }
    public String getProductOrServiceDisplay() { return productOrServiceDisplay; }
    public void setProductOrServiceDisplay(String productOrServiceDisplay) { this.productOrServiceDisplay = productOrServiceDisplay; }
    public LocalDate getServicedDate() { return servicedDate; }
    public void setServicedDate(LocalDate servicedDate) { this.servicedDate = servicedDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
}
