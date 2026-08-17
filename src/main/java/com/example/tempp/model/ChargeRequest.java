package com.example.tempp.model;

import java.time.Instant;

public class ChargeRequest {
    private String destination;
    private String callSid;
    private long durationSeconds;               // total: ringing → hangup
    private long connectedDurationSeconds = -1; // billing: answered → hangup; -1 = not provided
    private String status;
    private String fromNumber;                  // Twilio caller ID (optional)
    private Instant startTime;
    private Instant endTime;
    private String disconnectReason;

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getCallSid() { return callSid; }
    public void setCallSid(String callSid) { this.callSid = callSid; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public long getConnectedDurationSeconds() { return connectedDurationSeconds; }
    public void setConnectedDurationSeconds(long v) { this.connectedDurationSeconds = v; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getDisconnectReason() { return disconnectReason; }
    public void setDisconnectReason(String disconnectReason) { this.disconnectReason = disconnectReason; }
}
