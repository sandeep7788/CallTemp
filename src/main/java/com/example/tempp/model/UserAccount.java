package com.example.tempp.model;

import java.time.Instant;

/**
 * Plain domain object representing a registered user.
 * Persisted in the Firebase Firestore {@code users} collection.
 */
public class UserAccount {

    private String id;           // Firestore document ID (UUID)
    private String googleUid;    // Firebase Auth UID (for Google Sign-In)
    private String email;        // Google account email
    private String name;
    private String number;       // mobile number (unique, indexed)
    private String location;
    private String temp;         // Twilio client identity (unique)
    private boolean active = true;
    private double walletBalance = 0.0;
    private boolean walletRewardCredited = false; // idempotency guard for ₹3 sign-up reward
    private boolean termsAccepted;
    private String deviceIdentifier;
    private boolean isBlocked = false;
    private boolean isAdmin = false;
    private Instant createdAt;
    private Instant updatedAt;

    public UserAccount() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGoogleUid() {
        return googleUid;
    }

    public void setGoogleUid(String googleUid) {
        this.googleUid = googleUid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getTemp() {
        return temp;
    }

    public void setTemp(String temp) {
        this.temp = temp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(double walletBalance) {
        this.walletBalance = walletBalance;
    }

    public boolean isWalletRewardCredited() {
        return walletRewardCredited;
    }

    public void setWalletRewardCredited(boolean walletRewardCredited) {
        this.walletRewardCredited = walletRewardCredited;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
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
