package com.example.tempp.model;

/**
 * Request body for completing user profile after Google Sign-In.
 * Submitted when a new Google user provides their mobile number and location.
 */
public class ProfileRequest {

    /**
     * User's mobile phone number (required).
     */
    private String phoneNumber;

    /**
     * User's approximate location (optional, from browser geolocation).
     */
    private String location;

    /**
     * Must be true – user has accepted the Terms and Conditions.
     */
    private boolean termsAccepted;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }
}

