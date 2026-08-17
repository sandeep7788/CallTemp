package com.example.tempp.model;

import com.example.tempp.constants.TwilioNumberStatus;
import java.time.Instant;

/**
 * Represents a Twilio phone number in the number pool.
 * Stored in the Firestore {@code twilio_numbers} collection.
 *
 * <p><b>Status Consistency:</b>
 * The {@code status} field works in conjunction with the {@code inUse} flag:
 * <ul>
 *   <li>When status is IN_USE: inUse must be true, currentCallSid must be non-null</li>
 *   <li>When status is AVAILABLE: inUse must be false, currentCallSid must be null</li>
 *   <li>When status is DISABLED: inUse must be false, currentCallSid must be null</li>
 *   <li>When status is MAINTENANCE: inUse must be false, currentCallSid must be null</li>
 * </ul>
 */
public class TwilioNumber {

    private String id;              // Firestore document ID (UUID)
    private String phoneNumber;
    private String friendlyName;
    private boolean isActive = true;
    private boolean inUse = false;
    private String status = TwilioNumberStatus.AVAILABLE; // See TwilioNumberStatus for valid values
    private String countryCode;
    private String description;
    private String currentCallSid;
    private Instant lastUsedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public TwilioNumber() {
    }

    public TwilioNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public String getStatus() {
        return status;
    }

    /**
     * Sets the status to one of the valid TwilioNumberStatus values.
     *
     * @param status a valid status constant from TwilioNumberStatus
     * @throws IllegalArgumentException if status is not a valid value
     */
    public void setStatus(String status) {
        TwilioNumberStatus.validateOrThrow(status);
        this.status = status;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCurrentCallSid() {
        return currentCallSid;
    }

    public void setCurrentCallSid(String currentCallSid) {
        this.currentCallSid = currentCallSid;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
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
