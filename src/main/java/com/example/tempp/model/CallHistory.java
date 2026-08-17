package com.example.tempp.model;

import java.time.Instant;

/**
 * Records a completed call and wallet deduction.
 * Stored as a sub-collection under {@code users/{userId}/call_history/{id}}.
 */
public class CallHistory {

    private String id;                    // Firestore document ID (UUID)
    private String userId;
    private String destination;
    private String callSid;
    private String twilioNumberUsed;      // Twilio caller ID used for this call
    private long durationSeconds;         // total: ringing → hangup
    private long connectedDurationSeconds; // billing: answered → hangup
    private long billedMinutes;
    private double ratePerMinute;
    private double amountCharged;
    private double walletBefore;
    private double walletAfter;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private String disconnectReason;
    private Instant createdAt;

    public CallHistory() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getTwilioNumberUsed() {
        return twilioNumberUsed;
    }

    public void setTwilioNumberUsed(String twilioNumberUsed) {
        this.twilioNumberUsed = twilioNumberUsed;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public long getConnectedDurationSeconds() {
        return connectedDurationSeconds;
    }

    public void setConnectedDurationSeconds(long connectedDurationSeconds) {
        this.connectedDurationSeconds = connectedDurationSeconds;
    }

    public long getBilledMinutes() {
        return billedMinutes;
    }

    public void setBilledMinutes(long billedMinutes) {
        this.billedMinutes = billedMinutes;
    }

    public double getRatePerMinute() {
        return ratePerMinute;
    }

    public void setRatePerMinute(double ratePerMinute) {
        this.ratePerMinute = ratePerMinute;
    }

    public double getAmountCharged() {
        return amountCharged;
    }

    public void setAmountCharged(double amountCharged) {
        this.amountCharged = amountCharged;
    }

    public double getWalletBefore() {
        return walletBefore;
    }

    public void setWalletBefore(double walletBefore) {
        this.walletBefore = walletBefore;
    }

    public double getWalletAfter() {
        return walletAfter;
    }

    public void setWalletAfter(double walletAfter) {
        this.walletAfter = walletAfter;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    public void setDisconnectReason(String disconnectReason) {
        this.disconnectReason = disconnectReason;
    }
}
