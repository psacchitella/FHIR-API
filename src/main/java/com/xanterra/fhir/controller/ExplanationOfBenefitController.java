package com.xanterra.fhir.controller;

import ca.uhn.fhir.parser.IParser;
import com.xanterra.fhir.model.entity.ExplanationOfBenefitEntity;
import com.xanterra.fhir.model.fhir.FhirMapper;
import com.xanterra.fhir.service.ClaimAdjudicationService;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fhir/ExplanationOfBenefit")
public class ExplanationOfBenefitController {

    private static final String FHIR_JSON = "application/fhir+json";

    private final ClaimAdjudicationService adjudicationService;
    private final FhirMapper fhirMapper;
    private final IParser fhirParser;

    public ExplanationOfBenefitController(ClaimAdjudicationService adjudicationService,
                                           FhirMapper fhirMapper,
                                           IParser fhirParser) {
        this.adjudicationService = adjudicationService;
        this.fhirMapper = fhirMapper;
        this.fhirParser = fhirParser;
    }

    @GetMapping(value = "/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> read(@PathVariable UUID id) {
        return adjudicationService.findEobById(id)
                .map(fhirMapper::toFhirEob)
                .map(fhirParser::encodeResourceToString)
                .map(json -> ResponseEntity.ok().contentType(MediaType.valueOf(FHIR_JSON)).body(json))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = FHIR_JSON)
    public ResponseEntity<String> search(
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String claim) {

        List<ExplanationOfBenefitEntity> eobs;
        if (claim != null) {
            eobs = adjudicationService.findEobByClaimId(UUID.fromString(claim))
                    .map(List::of).orElse(List.of());
        } else if (patient != null) {
            eobs = adjudicationService.findEobsByPatient(UUID.fromString(patient));
        } else {
            eobs = List.of();
        }

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(eobs.size());
        for (ExplanationOfBenefitEntity entity : eobs) {
            bundle.addEntry()
                    .setFullUrl("ExplanationOfBenefit/" + entity.getId())
                    .setResource(fhirMapper.toFhirEob(entity));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(fhirParser.encodeResourceToString(bundle));
    }
}
