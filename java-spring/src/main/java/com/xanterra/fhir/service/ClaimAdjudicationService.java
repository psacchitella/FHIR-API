package com.xanterra.fhir.service;

import com.xanterra.fhir.event.ClaimEvent;
import com.xanterra.fhir.event.ClaimEventPublisher;
import com.xanterra.fhir.model.entity.ClaimEntity;
import com.xanterra.fhir.model.entity.ExplanationOfBenefitEntity;
import com.xanterra.fhir.repository.ClaimRepository;
import com.xanterra.fhir.repository.ExplanationOfBenefitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core bill-pay adjudication engine.
 *
 * Simulates the X12 837→835 flow in FHIR terms:
 *   Claim (837) → adjudicate → ExplanationOfBenefit (835/EOB)
 *
 * In production this would integrate with a rules engine or payer system.
 * This implementation applies a simplified adjudication model:
 *   - $50 copay per claim
 *   - $500 deductible (tracked per patient, simplified here)
 *   - 80/20 coinsurance after deductible
 */
@Service
public class ClaimAdjudicationService {

    private static final Logger log = LoggerFactory.getLogger(ClaimAdjudicationService.class);

    private static final BigDecimal COPAY = new BigDecimal("50.00");
    private static final BigDecimal DEDUCTIBLE = new BigDecimal("500.00");
    private static final BigDecimal COINSURANCE_RATE = new BigDecimal("0.20"); // patient pays 20%

    private final ClaimRepository claimRepository;
    private final ExplanationOfBenefitRepository eobRepository;
    private final CoverageService coverageService;
    private final ClaimEventPublisher eventPublisher;

    public ClaimAdjudicationService(ClaimRepository claimRepository,
                                     ExplanationOfBenefitRepository eobRepository,
                                     CoverageService coverageService,
                                     ClaimEventPublisher eventPublisher) {
        this.claimRepository = claimRepository;
        this.eobRepository = eobRepository;
        this.coverageService = coverageService;
        this.eventPublisher = eventPublisher;
    }

    public ClaimEntity submitClaim(ClaimEntity claim) {
        claim.setStatus("active");
        ClaimEntity saved = claimRepository.save(claim);

        eventPublisher.publishClaimEvent(new ClaimEvent(
                saved.getId().toString(),
                saved.getPatient().getId().toString(),
                ClaimEvent.EventType.CLAIM_SUBMITTED,
                "Claim submitted: $" + saved.getTotalAmount()));

        return saved;
    }

    public Optional<ClaimEntity> findClaimById(UUID id) {
        return claimRepository.findById(id);
    }

    public List<ClaimEntity> findClaimsByPatient(UUID patientId) {
        return claimRepository.findByPatientId(patientId);
    }

    /**
     * Adjudicate a claim and generate an ExplanationOfBenefit.
     *
     * Flow:
     * 1. Verify coverage eligibility (Redis-cached lookup)
     * 2. Apply copay, deductible, coinsurance
     * 3. Generate EOB with payment breakdown
     * 4. Publish Kafka events for downstream consumers
     */
    @Transactional
    public ExplanationOfBenefitEntity adjudicate(UUID claimId) {
        ClaimEntity claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        if (!"active".equals(claim.getStatus())) {
            throw new IllegalStateException("Claim " + claimId + " is not in active status");
        }

        // Step 1: Check coverage eligibility
        boolean eligible = coverageService.isPatientEligible(claim.getPatient().getId());
        if (!eligible) {
            claim.setStatus("cancelled");
            claimRepository.save(claim);

            eventPublisher.publishClaimEvent(new ClaimEvent(
                    claimId.toString(),
                    claim.getPatient().getId().toString(),
                    ClaimEvent.EventType.CLAIM_DENIED,
                    "No active coverage"));

            ExplanationOfBenefitEntity deniedEob = new ExplanationOfBenefitEntity();
            deniedEob.setClaim(claim);
            deniedEob.setPatient(claim.getPatient());
            deniedEob.setStatus("active");
            deniedEob.setOutcome("error");
            deniedEob.setType(claim.getType());
            deniedEob.setTotalSubmitted(claim.getTotalAmount());
            deniedEob.setTotalBenefit(BigDecimal.ZERO);
            deniedEob.setPatientResponsibility(claim.getTotalAmount());
            deniedEob.setDisposition("Denied: no active coverage found");
            return eobRepository.save(deniedEob);
        }

        // Step 2: Calculate adjudication amounts
        BigDecimal submitted = claim.getTotalAmount();
        BigDecimal afterCopay = submitted.subtract(COPAY).max(BigDecimal.ZERO);
        BigDecimal deductibleApplied = afterCopay.min(DEDUCTIBLE);
        BigDecimal afterDeductible = afterCopay.subtract(deductibleApplied);
        BigDecimal coinsurance = afterDeductible.multiply(COINSURANCE_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal patientTotal = COPAY.add(deductibleApplied).add(coinsurance);
        BigDecimal insurerPays = submitted.subtract(patientTotal).max(BigDecimal.ZERO);

        // Step 3: Generate EOB
        ExplanationOfBenefitEntity eob = new ExplanationOfBenefitEntity();
        eob.setClaim(claim);
        eob.setPatient(claim.getPatient());
        eob.setStatus("active");
        eob.setOutcome("complete");
        eob.setType(claim.getType());
        eob.setTotalSubmitted(submitted);
        eob.setTotalBenefit(insurerPays);
        eob.setPatientResponsibility(patientTotal);
        eob.setDeductibleApplied(deductibleApplied);
        eob.setCoinsuranceAmount(coinsurance);
        eob.setCopayAmount(COPAY);
        eob.setDisposition("Claim adjudicated successfully");

        ExplanationOfBenefitEntity savedEob = eobRepository.save(eob);

        // Update claim status
        claim.setStatus("active"); // remains active; EOB tracks outcome
        claimRepository.save(claim);

        log.info("Adjudicated claim {}: submitted=${}, insurer=${}, patient=${}",
                claimId, submitted, insurerPays, patientTotal);

        // Step 4: Publish events
        eventPublisher.publishClaimEvent(new ClaimEvent(
                claimId.toString(),
                claim.getPatient().getId().toString(),
                ClaimEvent.EventType.CLAIM_ADJUDICATED,
                String.format("Adjudicated: insurer pays $%s, patient owes $%s",
                        insurerPays, patientTotal)));

        eventPublisher.publishClaimEvent(new ClaimEvent(
                claimId.toString(),
                claim.getPatient().getId().toString(),
                ClaimEvent.EventType.EOB_GENERATED,
                "EOB " + savedEob.getId() + " generated"));

        return savedEob;
    }

    public Optional<ExplanationOfBenefitEntity> findEobById(UUID id) {
        return eobRepository.findById(id);
    }

    public Optional<ExplanationOfBenefitEntity> findEobByClaimId(UUID claimId) {
        return eobRepository.findByClaimId(claimId);
    }

    public List<ExplanationOfBenefitEntity> findEobsByPatient(UUID patientId) {
        return eobRepository.findByPatientId(patientId);
    }
}
