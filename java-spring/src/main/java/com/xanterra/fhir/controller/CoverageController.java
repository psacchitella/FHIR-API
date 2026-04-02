package com.xanterra.fhir.controller;

import ca.uhn.fhir.parser.IParser;
import com.xanterra.fhir.model.entity.CoverageEntity;
import com.xanterra.fhir.model.entity.PatientEntity;
import com.xanterra.fhir.model.fhir.FhirMapper;
import com.xanterra.fhir.service.CoverageService;
import com.xanterra.fhir.service.PatientService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fhir/Coverage")
public class CoverageController {

    private static final String FHIR_JSON = "application/fhir+json";

    private final CoverageService coverageService;
    private final PatientService patientService;
    private final FhirMapper fhirMapper;
    private final IParser fhirParser;

    public CoverageController(CoverageService coverageService,
                               PatientService patientService,
                               FhirMapper fhirMapper,
                               IParser fhirParser) {
        this.coverageService = coverageService;
        this.patientService = patientService;
        this.fhirMapper = fhirMapper;
        this.fhirParser = fhirParser;
    }

    @GetMapping(value = "/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> read(@PathVariable UUID id) {
        return coverageService.findById(id)
                .map(fhirMapper::toFhirCoverage)
                .map(fhirParser::encodeResourceToString)
                .map(json -> ResponseEntity.ok().contentType(MediaType.valueOf(FHIR_JSON)).body(json))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = FHIR_JSON)
    public ResponseEntity<String> search(@RequestParam(required = false) String beneficiary) {
        List<CoverageEntity> coverages;
        if (beneficiary != null) {
            coverages = coverageService.findByPatientId(UUID.fromString(beneficiary));
        } else {
            coverages = List.of();
        }

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(coverages.size());
        for (CoverageEntity entity : coverages) {
            bundle.addEntry()
                    .setFullUrl("Coverage/" + entity.getId())
                    .setResource(fhirMapper.toFhirCoverage(entity));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(fhirParser.encodeResourceToString(bundle));
    }

    @PostMapping(consumes = FHIR_JSON, produces = FHIR_JSON)
    public ResponseEntity<String> create(@RequestBody String body) {
        Coverage fhirCoverage = fhirParser.parseResource(Coverage.class, body);

        String patientRef = fhirCoverage.getBeneficiary().getReference();
        UUID patientId = UUID.fromString(patientRef.replace("Patient/", ""));
        PatientEntity patient = patientService.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        CoverageEntity entity = new CoverageEntity();
        entity.setPatient(patient);
        entity.setSubscriberId(fhirCoverage.getSubscriberId());
        entity.setStatus(fhirCoverage.getStatus().toCode());
        entity.setPayorName(fhirCoverage.getPayorFirstRep().getDisplay());
        entity.setPayorId(fhirCoverage.getPayorFirstRep().getReference()
                .replace("Organization/", ""));
        entity.setPlanName(fhirCoverage.getPayorFirstRep().getDisplay());
        entity.setRelationship(fhirCoverage.getRelationship().getCodingFirstRep().getCode());

        if (fhirCoverage.getPeriod() != null) {
            if (fhirCoverage.getPeriod().getStart() != null) {
                entity.setPeriodStart(fhirCoverage.getPeriod().getStart().toInstant()
                        .atZone(ZoneOffset.UTC).toLocalDate());
            }
            if (fhirCoverage.getPeriod().getEnd() != null) {
                entity.setPeriodEnd(fhirCoverage.getPeriod().getEnd().toInstant()
                        .atZone(ZoneOffset.UTC).toLocalDate());
            }
        }

        CoverageEntity saved = coverageService.save(entity);

        return ResponseEntity.status(201)
                .contentType(MediaType.valueOf(FHIR_JSON))
                .header("Location", "/fhir/Coverage/" + saved.getId())
                .body(fhirParser.encodeResourceToString(fhirMapper.toFhirCoverage(saved)));
    }
}
