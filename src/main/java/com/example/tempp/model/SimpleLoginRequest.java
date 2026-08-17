package com.example.tempp.model;

/**
 * Request payload for the simple (non-Google) login flow.
 * Used when {@code enableGoogleSignIn} feature flag is {@code false}.
 */
public class SimpleLoginRequest {

    /** Required – the user's mobile number. */
    private String userNumber;

    /** Optional – geo-coordinates or location string; defaults to "x" when blank. */
    private String location;

    /** Optional – device identifier (IP address or browser fingerprint). */
    private String deviceIdentifier;

    public SimpleLoginRequest() {}

    public String getUserNumber() {
        return userNumber;
    }

    public void setUserNumber(String userNumber) {
        this.userNumber = userNumber;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }
}

