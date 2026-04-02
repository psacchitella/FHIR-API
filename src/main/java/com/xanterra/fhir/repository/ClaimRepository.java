package com.xanterra.fhir.repository;

import com.xanterra.fhir.model.entity.ClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, UUID> {
    List<ClaimEntity> findByPatientId(UUID patientId);
    List<ClaimEntity> findByStatus(String status);
}
