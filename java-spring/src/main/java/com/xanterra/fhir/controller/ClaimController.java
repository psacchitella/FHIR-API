package com.xanterra.fhir.controller;

import ca.uhn.fhir.parser.IParser;
import com.xanterra.fhir.model.entity.*;
import com.xanterra.fhir.model.fhir.FhirMapper;
import com.xanterra.fhir.service.ClaimAdjudicationService;
import com.xanterra.fhir.service.CoverageService;
import com.xanterra.fhir.service.PatientService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fhir/Claim")
public class ClaimController {

    private static final String FHIR_JSON = "application/fhir+json";

    private final ClaimAdjudicationService adjudicationService;
    private final PatientService patientService;
    private final CoverageService coverageService;
    private final FhirMapper fhirMapper;
    private final IParser fhirParser;

    public ClaimController(ClaimAdjudicationService adjudicationService,
                           PatientService patientService,
                           CoverageService coverageService,
                           FhirMapper fhirMapper,
                           IParser fhirParser) {
        this.adjudicationService = adjudicationService;
        this.patientService = patientService;
        this.coverageService = coverageService;
        this.fhirMapper = fhirMapper;
        this.fhirParser = fhirParser;
    }

    @GetMapping(value = "/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> read(@PathVariable UUID id) {
        return adjudicationService.findClaimById(id)
                .map(fhirMapper::toFhirClaim)
                .map(fhirParser::encodeResourceToString)
                .map(json -> ResponseEntity.ok().contentType(MediaType.valueOf(FHIR_JSON)).body(json))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = FHIR_JSON)
    public ResponseEntity<String> search(@RequestParam(required = false) String patient) {
        List<ClaimEntity> claims;
        if (patient != null) {
            claims = adjudicationService.findClaimsByPatient(UUID.fromString(patient));
        } else {
            claims = List.of(); // require patient filter for safety
        }

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(claims.size());
        for (ClaimEntity entity : claims) {
            bundle.addEntry()
                    .setFullUrl("Claim/" + entity.getId())
                    .setResource(fhirMapper.toFhirClaim(entity));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(fhirParser.encodeResourceToString(bundle));
    }

    /**
     * Submit a new claim. Accepts a FHIR Claim resource, persists it,
     * and publishes a CLAIM_SUBMITTED event to Kafka.
     */
    @PostMapping(consumes = FHIR_JSON, produces = FHIR_JSON)
    public ResponseEntity<String> create(@RequestBody String body) {
        Claim fhirClaim = fhirParser.parseResource(Claim.class, body);

        // Resolve patient reference
        String patientRef = fhirClaim.getPatient().getReference(); // "Patient/{id}"
        UUID patientId = UUID.fromString(patientRef.replace("Patient/", ""));
        PatientEntity patient = patientService.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        // Resolve coverage reference
        String coverageRef = fhirClaim.getInsuranceFirstRep().getCoverage().getReference();
        UUID coverageId = UUID.fromString(coverageRef.replace("Coverage/", ""));
        CoverageEntity coverage = coverageService.findById(coverageId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage not found: " + coverageId));

        // Build entity
        ClaimEntity entity = new ClaimEntity();
        entity.setPatient(patient);
        entity.setCoverage(coverage);
        entity.setType(fhirClaim.getType().getCodingFirstRep().getCode());
        entity.setUse(fhirClaim.getUse().toCode());
        entity.setBillablePeriodStart(fhirClaim.getBillablePeriod().getStart().toInstant()
                .atZone(ZoneOffset.UTC).toLocalDate());
        entity.setBillablePeriodEnd(fhirClaim.getBillablePeriod().getEnd().toInstant()
                .atZone(ZoneOffset.UTC).toLocalDate());
        entity.setTotalAmount(fhirClaim.getTotal().getValue());

        if (fhirClaim.getProvider() != null) {
            entity.setProviderReference(fhirClaim.getProvider().getReference());
        }

        // Map line items
        int seq = 1;
        for (Claim.ItemComponent fhirItem : fhirClaim.getItem()) {
            ClaimItemEntity item = new ClaimItemEntity();
            item.setSequence(seq++);
            item.setProductOrServiceCode(fhirItem.getProductOrService().getCodingFirstRep().getCode());
            item.setProductOrServiceDisplay(fhirItem.getProductOrService().getCodingFirstRep().getDisplay());
            item.setQuantity(fhirItem.getQuantity().getValue().intValue());
            item.setUnitPrice(fhirItem.getUnitPrice().getValue());
            item.setNetAmount(fhirItem.getNet().getValue());
            entity.addItem(item);
        }

        ClaimEntity saved = adjudicationService.submitClaim(entity);

        return ResponseEntity.status(201)
                .contentType(MediaType.valueOf(FHIR_JSON))
                .header("Location", "/fhir/Claim/" + saved.getId())
                .body(fhirParser.encodeResourceToString(fhirMapper.toFhirClaim(saved)));
    }

    /**
     * Trigger adjudication via $adjudicate operation.
     * POST /fhir/Claim/{id}/$adjudicate
     *
     * This is a FHIR custom operation that processes the claim and
     * returns the generated ExplanationOfBenefit.
     */
    @PostMapping(value = "/{id}/$adjudicate", produces = FHIR_JSON)
    public ResponseEntity<String> adjudicate(@PathVariable UUID id) {
        ExplanationOfBenefitEntity eob = adjudicationService.adjudicate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(fhirParser.encodeResourceToString(fhirMapper.toFhirEob(eob)));
    }
}
