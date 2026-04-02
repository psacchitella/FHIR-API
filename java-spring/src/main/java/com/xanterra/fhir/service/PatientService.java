package com.xanterra.fhir.service;

import com.xanterra.fhir.model.entity.PatientEntity;
import com.xanterra.fhir.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public PatientEntity save(PatientEntity entity) {
        return patientRepository.save(entity);
    }

    public Optional<PatientEntity> findById(UUID id) {
        return patientRepository.findById(id);
    }

    public Optional<PatientEntity> findByMrn(String mrn) {
        return patientRepository.findByMrn(mrn);
    }

    public List<PatientEntity> findAll() {
        return patientRepository.findAll();
    }

    public void deleteById(UUID id) {
        patientRepository.deleteById(id);
    }
}
