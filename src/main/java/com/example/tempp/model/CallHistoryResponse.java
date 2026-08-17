package com.example.tempp.model;

import java.time.Instant;

public class CallHistoryResponse {
    private final String id;
    private final String callSid;
    private final String twilioNumberUsed;
    private final String destination;
    private final long durationSeconds;
    private final long connectedDurationSeconds;
    private final long billedMinutes;
    private final double amountCharged;
    private final double walletAfter;
    private final String status;
    private final Instant startTime;
    private final Instant endTime;
    private final String disconnectReason;
    private final Instant createdAt;

    public CallHistoryResponse(CallHistory history) {
        this.id = history.getId();
        this.callSid = history.getCallSid();
        this.twilioNumberUsed = history.getTwilioNumberUsed();
        this.destination = history.getDestination();
        this.durationSeconds = history.getDurationSeconds();
        this.connectedDurationSeconds = history.getConnectedDurationSeconds();
        this.billedMinutes = history.getBilledMinutes();
        this.amountCharged = history.getAmountCharged();
        this.walletAfter = history.getWalletAfter();
        this.status = history.getStatus();
        this.startTime = history.getStartTime();
        this.endTime = history.getEndTime();
        this.disconnectReason = history.getDisconnectReason();
        this.createdAt = history.getCreatedAt();
    }

    public String getId() { return id; }
    public String getCallSid() { return callSid; }
    public String getTwilioNumberUsed() { return twilioNumberUsed; }
    public String getDestination() { return destination; }
    public long getDurationSeconds() { return durationSeconds; }
    public long getConnectedDurationSeconds() { return connectedDurationSeconds; }
    public long getBilledMinutes() { return billedMinutes; }
    public double getAmountCharged() { return amountCharged; }
    public double getWalletAfter() { return walletAfter; }
    public String getStatus() { return status; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getDisconnectReason() { return disconnectReason; }
    public Instant getCreatedAt() { return createdAt; }
}

