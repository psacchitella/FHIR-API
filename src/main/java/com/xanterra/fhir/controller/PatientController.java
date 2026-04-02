package com.xanterra.fhir.controller;

import ca.uhn.fhir.parser.IParser;
import com.xanterra.fhir.model.entity.PatientEntity;
import com.xanterra.fhir.model.fhir.FhirMapper;
import com.xanterra.fhir.service.PatientService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fhir/Patient")
public class PatientController {

    private static final String FHIR_JSON = "application/fhir+json";

    private final PatientService patientService;
    private final FhirMapper fhirMapper;
    private final IParser fhirParser;

    public PatientController(PatientService patientService, FhirMapper fhirMapper, IParser fhirParser) {
        this.patientService = patientService;
        this.fhirMapper = fhirMapper;
        this.fhirParser = fhirParser;
    }

    @GetMapping(value = "/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> read(@PathVariable UUID id) {
        return patientService.findById(id)
                .map(fhirMapper::toFhirPatient)
                .map(fhirParser::encodeResourceToString)
                .map(json -> ResponseEntity.ok().contentType(MediaType.valueOf(FHIR_JSON)).body(json))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = FHIR_JSON)
    public ResponseEntity<String> search(@RequestParam(required = false) String identifier) {
        List<PatientEntity> patients;
        if (identifier != null) {
            patients = patientService.findByMrn(identifier).map(List::of).orElse(List.of());
        } else {
            patients = patientService.findAll();
        }

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(patients.size());
        for (PatientEntity entity : patients) {
            Patient fhir = fhirMapper.toFhirPatient(entity);
            bundle.addEntry()
                    .setFullUrl("Patient/" + entity.getId())
                    .setResource(fhir);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(fhirParser.encodeResourceToString(bundle));
    }

    @PostMapping(consumes = FHIR_JSON, produces = FHIR_JSON)
    public ResponseEntity<String> create(@RequestBody String body) {
        Patient fhirPatient = fhirParser.parseResource(Patient.class, body);

        PatientEntity entity = new PatientEntity();
        fhirMapper.updatePatientEntity(entity, fhirPatient);
        PatientEntity saved = patientService.save(entity);

        Patient result = fhirMapper.toFhirPatient(saved);
        return ResponseEntity.status(201)
                .contentType(MediaType.valueOf(FHIR_JSON))
                .header("Location", "/fhir/Patient/" + saved.getId())
                .body(fhirParser.encodeResourceToString(result));
    }

    @PutMapping(value = "/{id}", consumes = FHIR_JSON, produces = FHIR_JSON)
    public ResponseEntity<String> update(@PathVariable UUID id, @RequestBody String body) {
        return patientService.findById(id)
                .map(entity -> {
                    Patient fhirPatient = fhirParser.parseResource(Patient.class, body);
                    fhirMapper.updatePatientEntity(entity, fhirPatient);
                    PatientEntity saved = patientService.save(entity);
                    Patient result = fhirMapper.toFhirPatient(saved);
                    return ResponseEntity.ok()
                            .contentType(MediaType.valueOf(FHIR_JSON))
                            .body(fhirParser.encodeResourceToString(result));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
