package com.xanterra.fhir.controller;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * FHIR CapabilityStatement endpoint.
 * Every FHIR server must publish /metadata describing its capabilities.
 */
@RestController
@RequestMapping("/fhir")
public class MetadataController {

    private static final String FHIR_JSON = "application/fhir+json";
    private final IParser fhirParser;

    public MetadataController(IParser fhirParser) {
        this.fhirParser = fhirParser;
    }

    @GetMapping(value = "/metadata", produces = FHIR_JSON)
    public ResponseEntity<String> capabilities() {
        CapabilityStatement cs = new CapabilityStatement();
        cs.setStatus(PublicationStatus.ACTIVE);
        cs.setDate(new Date());
        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        cs.addFormat("application/fhir+json");
        cs.getSoftware()
                .setName("FHIR Claim Processing API")
                .setVersion("1.0.0");

        CapabilityStatement.CapabilityStatementRestComponent rest =
                cs.addRest().setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        // Patient
        addResource(rest, "Patient", true, true, true, false);
        // Claim
        addResource(rest, "Claim", true, false, true, false);
        // ExplanationOfBenefit
        addResource(rest, "ExplanationOfBenefit", true, false, false, false);
        // Coverage
        addResource(rest, "Coverage", true, false, true, false);

        // Custom operations
        cs.addRest().addOperation()
                .setName("adjudicate")
                .setDefinition("Claim/{id}/$adjudicate");

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(fhirParser.encodeResourceToString(cs));
    }

    private void addResource(CapabilityStatement.CapabilityStatementRestComponent rest,
                             String type, boolean read, boolean update,
                             boolean create, boolean delete) {
        CapabilityStatement.CapabilityStatementRestResourceComponent resource =
                rest.addResource().setType(type);
        if (read) resource.addInteraction().setCode(
                CapabilityStatement.TypeRestfulInteraction.READ);
        if (create) resource.addInteraction().setCode(
                CapabilityStatement.TypeRestfulInteraction.CREATE);
        if (update) resource.addInteraction().setCode(
                CapabilityStatement.TypeRestfulInteraction.UPDATE);
        if (delete) resource.addInteraction().setCode(
                CapabilityStatement.TypeRestfulInteraction.DELETE);
        resource.addInteraction().setCode(
                CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);
    }
}
