package com.example.tempp.model;

/**
 * Request body for Google Sign-In authentication.
 * The frontend sends a Firebase ID token obtained after Google Sign-In.
 */
public class GoogleLoginRequest {

    /**
     * Firebase ID token returned by Firebase client SDK after Google Sign-In.
     */
    private String idToken;

    /**
     * Optional device/client identifier for fraud detection.
     */
    private String deviceIdentifier;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }
}

