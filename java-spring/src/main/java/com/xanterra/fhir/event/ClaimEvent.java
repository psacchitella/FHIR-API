package com.xanterra.fhir.event;

import java.io.Serializable;
import java.time.Instant;

public class ClaimEvent implements Serializable {

    public enum EventType {
        CLAIM_SUBMITTED,
        CLAIM_ADJUDICATED,
        CLAIM_DENIED,
        CLAIM_CANCELLED,
        EOB_GENERATED
    }

    private String claimId;
    private String patientId;
    private EventType eventType;
    private String detail;
    private Instant timestamp;

    public ClaimEvent() {}

    public ClaimEvent(String claimId, String patientId, EventType eventType, String detail) {
        this.claimId = claimId;
        this.patientId = patientId;
        this.eventType = eventType;
        this.detail = detail;
        this.timestamp = Instant.now();
    }

    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
