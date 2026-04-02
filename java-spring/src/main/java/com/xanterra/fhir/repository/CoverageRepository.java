package com.xanterra.fhir.repository;

import com.xanterra.fhir.model.entity.CoverageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoverageRepository extends JpaRepository<CoverageEntity, UUID> {
    List<CoverageEntity> findByPatientId(UUID patientId);
    List<CoverageEntity> findByPatientIdAndStatus(UUID patientId, String status);
}
