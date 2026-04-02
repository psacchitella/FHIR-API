package com.xanterra.fhir.repository;

import com.xanterra.fhir.model.entity.PatientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<PatientEntity, UUID> {
    Optional<PatientEntity> findByMrn(String mrn);
    boolean existsByMrn(String mrn);
}
