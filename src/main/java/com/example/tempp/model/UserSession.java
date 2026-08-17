package com.example.tempp.model;

public class UserSession {
    private final String id;       // String ID now (Firebase document ID)
    private final String name;
    private final String email;
    private final String number;
    private final String location;
    private final String identity;
    private final double walletBalance;
    private final boolean active;
    private final String token;
    private final boolean needsProfile;

    public UserSession(UserAccount user, String token) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.number = user.getNumber();
        this.location = user.getLocation();
        this.identity = user.getTemp();
        this.walletBalance = user.getWalletBalance();
        this.active = user.isActive();
        this.token = token;
        this.needsProfile = (user.getNumber() == null || user.getNumber().isBlank());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getNumber() {
        return number;
    }

    public String getLocation() {
        return location;
    }

    public String getIdentity() {
        return identity;
    }

    public double getWalletBalance() {
        return walletBalance;
    }

    public boolean isActive() {
        return active;
    }

    public String getToken() {
        return token;
    }

    public boolean isNeedsProfile() {
        return needsProfile;
    }
}
