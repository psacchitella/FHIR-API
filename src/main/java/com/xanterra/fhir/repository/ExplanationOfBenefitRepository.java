package com.xanterra.fhir.repository;

import com.xanterra.fhir.model.entity.ExplanationOfBenefitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExplanationOfBenefitRepository extends JpaRepository<ExplanationOfBenefitEntity, UUID> {
    Optional<ExplanationOfBenefitEntity> findByClaimId(UUID claimId);
    List<ExplanationOfBenefitEntity> findByPatientId(UUID patientId);
}
