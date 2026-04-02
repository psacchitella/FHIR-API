package com.xanterra.fhir.model.fhir;

import com.xanterra.fhir.model.entity.*;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Bidirectional mapper between JPA entities and FHIR R4 resources.
 * Handles the impedance mismatch between relational storage and FHIR's
 * resource-oriented model.
 */
@Component
public class FhirMapper {

    private static final String SYSTEM_MRN = "http://hospital.example.org/mrn";
    private static final String SYSTEM_CPT = "http://www.ama-assn.org/go/cpt";
    private static final String SYSTEM_CLAIM_TYPE = "http://terminology.hl7.org/CodeSystem/claim-type";
    private static final String SYSTEM_ADJUDICATION = "http://terminology.hl7.org/CodeSystem/adjudication";

    // --- Patient ---

    public Patient toFhirPatient(PatientEntity entity) {
        Patient patient = new Patient();
        patient.setId(entity.getId().toString());
        patient.addIdentifier()
                .setSystem(SYSTEM_MRN)
                .setValue(entity.getMrn());
        patient.addName()
                .setFamily(entity.getFamilyName())
                .addGiven(entity.getGivenName());
        if (entity.getBirthDate() != null) {
            patient.setBirthDate(Date.from(entity.getBirthDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (entity.getGender() != null) {
            patient.setGender(AdministrativeGender.fromCode(entity.getGender()));
        }
        if (entity.getPhone() != null) {
            patient.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(entity.getPhone());
        }
        if (entity.getEmail() != null) {
            patient.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(entity.getEmail());
        }
        if (entity.getAddressLine() != null) {
            patient.addAddress()
                    .addLine(entity.getAddressLine())
                    .setCity(entity.getCity())
                    .setState(entity.getState())
                    .setPostalCode(entity.getPostalCode());
        }
        patient.getMeta().setLastUpdated(Date.from(entity.getUpdatedAt()));
        return patient;
    }

    public void updatePatientEntity(PatientEntity entity, Patient fhir) {
        if (!fhir.getName().isEmpty()) {
            HumanName name = fhir.getNameFirstRep();
            entity.setFamilyName(name.getFamily());
            if (!name.getGiven().isEmpty()) {
                entity.setGivenName(name.getGivenAsSingleString());
            }
        }
        if (!fhir.getIdentifier().isEmpty()) {
            entity.setMrn(fhir.getIdentifierFirstRep().getValue());
        }
        if (fhir.getBirthDate() != null) {
            entity.setBirthDate(fhir.getBirthDate().toInstant()
                    .atZone(ZoneOffset.UTC).toLocalDate());
        }
        if (fhir.getGender() != null) {
            entity.setGender(fhir.getGender().toCode());
        }
    }

    // --- Coverage ---

    public Coverage toFhirCoverage(CoverageEntity entity) {
        Coverage coverage = new Coverage();
        coverage.setId(entity.getId().toString());
        coverage.setStatus(Coverage.CoverageStatus.fromCode(entity.getStatus()));
        coverage.setBeneficiary(new Reference("Patient/" + entity.getPatient().getId()));
        coverage.addPayor(new Reference("Organization/" + entity.getPayorId())
                .setDisplay(entity.getPayorName()));
        coverage.setSubscriberId(entity.getSubscriberId());
        coverage.setRelationship(new CodeableConcept()
                .addCoding(new Coding()
                        .setCode(entity.getRelationship())));
        if (entity.getPeriodStart() != null) {
            Period period = new Period();
            period.setStart(Date.from(entity.getPeriodStart()
                    .atStartOfDay(ZoneOffset.UTC).toInstant()));
            if (entity.getPeriodEnd() != null) {
                period.setEnd(Date.from(entity.getPeriodEnd()
                        .atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            coverage.setPeriod(period);
        }
        return coverage;
    }

    // --- Claim ---

    public Claim toFhirClaim(ClaimEntity entity) {
        Claim claim = new Claim();
        claim.setId(entity.getId().toString());
        claim.setStatus(Claim.ClaimStatus.fromCode(entity.getStatus()));
        claim.setType(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem(SYSTEM_CLAIM_TYPE)
                        .setCode(entity.getType())));
        claim.setUse(Claim.Use.fromCode(entity.getUse()));
        claim.setPatient(new Reference("Patient/" + entity.getPatient().getId()));
        claim.addInsurance()
                .setSequence(1)
                .setFocal(true)
                .setCoverage(new Reference("Coverage/" + entity.getCoverage().getId()));
        if (entity.getProviderReference() != null) {
            claim.setProvider(new Reference(entity.getProviderReference()));
        }

        Period billable = new Period();
        billable.setStart(Date.from(entity.getBillablePeriodStart()
                .atStartOfDay(ZoneOffset.UTC).toInstant()));
        billable.setEnd(Date.from(entity.getBillablePeriodEnd()
                .atStartOfDay(ZoneOffset.UTC).toInstant()));
        claim.setBillablePeriod(billable);

        claim.setTotal(new Money()
                .setValue(entity.getTotalAmount())
                .setCurrency(entity.getCurrency()));

        for (ClaimItemEntity itemEntity : entity.getItems()) {
            Claim.ItemComponent item = claim.addItem();
            item.setSequence(itemEntity.getSequence());
            item.setProductOrService(new CodeableConcept()
                    .addCoding(new Coding()
                            .setSystem(SYSTEM_CPT)
                            .setCode(itemEntity.getProductOrServiceCode())
                            .setDisplay(itemEntity.getProductOrServiceDisplay())));
            item.setQuantity(new Quantity().setValue(itemEntity.getQuantity()));
            item.setUnitPrice(new Money()
                    .setValue(itemEntity.getUnitPrice())
                    .setCurrency(entity.getCurrency()));
            item.setNet(new Money()
                    .setValue(itemEntity.getNetAmount())
                    .setCurrency(entity.getCurrency()));
        }

        claim.getMeta().setLastUpdated(Date.from(entity.getUpdatedAt()));
        return claim;
    }

    // --- ExplanationOfBenefit ---

    public ExplanationOfBenefit toFhirEob(ExplanationOfBenefitEntity entity) {
        ExplanationOfBenefit eob = new ExplanationOfBenefit();
        eob.setId(entity.getId().toString());
        eob.setStatus(ExplanationOfBenefit.ExplanationOfBenefitStatus.fromCode(entity.getStatus()));
        eob.setOutcome(ExplanationOfBenefit.RemittanceOutcome.fromCode(entity.getOutcome()));
        eob.setType(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem(SYSTEM_CLAIM_TYPE)
                        .setCode(entity.getType())));
        eob.setPatient(new Reference("Patient/" + entity.getPatient().getId()));
        eob.setClaim(new Reference("Claim/" + entity.getClaim().getId()));

        if (entity.getDisposition() != null) {
            eob.setDisposition(entity.getDisposition());
        }

        // Total categories
        if (entity.getTotalSubmitted() != null) {
            eob.addTotal()
                    .setCategory(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setSystem(SYSTEM_ADJUDICATION)
                                    .setCode("submitted")))
                    .setAmount(new Money().setValue(entity.getTotalSubmitted()).setCurrency("USD"));
        }
        if (entity.getTotalBenefit() != null) {
            eob.addTotal()
                    .setCategory(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setSystem(SYSTEM_ADJUDICATION)
                                    .setCode("benefit")))
                    .setAmount(new Money().setValue(entity.getTotalBenefit()).setCurrency("USD"));
        }

        // Payment
        if (entity.getPatientResponsibility() != null) {
            ExplanationOfBenefit.PaymentComponent payment = new ExplanationOfBenefit.PaymentComponent();
            payment.setAmount(new Money()
                    .setValue(entity.getTotalBenefit())
                    .setCurrency("USD"));
            eob.setPayment(payment);
        }

        eob.getMeta().setLastUpdated(Date.from(entity.getUpdatedAt()));
        return eob;
    }
}
