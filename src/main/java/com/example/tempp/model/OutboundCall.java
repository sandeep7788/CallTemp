package com.example.tempp.model;

import java.time.Instant;

/**
 * Plain domain object representing a pending/active outbound call.
 * Persisted in the Firestore {@code outbound_calls} collection,
 * keyed by {@code publicId}.
 */
public class OutboundCall {

    private String publicId;       // Firestore document ID (secure random hex)
    private String userId;         // References users/{userId}
    private String destination;
    private String fromNumber;
    private String callSid;
    private String status;
    private String message;
    private Instant twimlRequestedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public OutboundCall() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTwimlRequestedAt() {
        return twimlRequestedAt;
    }

    public void setTwimlRequestedAt(Instant twimlRequestedAt) {
        this.twimlRequestedAt = twimlRequestedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
