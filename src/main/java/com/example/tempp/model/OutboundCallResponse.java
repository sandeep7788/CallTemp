package com.example.tempp.model;

import java.time.Instant;

public class OutboundCallResponse {
    private final String callId;
    private final String callSid;
    private final String destination;
    private final String fromNumber;
    private final String status;
    private final String twimlUrl;
    private final Instant expiresAt;

    public OutboundCallResponse(OutboundCall call, String twimlUrl) {
        this.callId = call.getPublicId();
        this.callSid = call.getCallSid();
        this.destination = call.getDestination();
        this.fromNumber = call.getFromNumber();
        this.status = call.getStatus();
        this.twimlUrl = twimlUrl;
        this.expiresAt = call.getExpiresAt();
    }

    public String getCallId() {
        return callId;
    }

    public String getCallSid() {
        return callSid;
    }

    public String getDestination() {
        return destination;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getTwimlUrl() {
        return twimlUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
