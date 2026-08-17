package com.example.tempp.model;

public class PaymentVerifyResponse {
    private final boolean verified;
    private final WalletResponse wallet;
    private final String message;

    public PaymentVerifyResponse(boolean verified, WalletResponse wallet, String message) {
        this.verified = verified;
        this.wallet = wallet;
        this.message = message;
    }

    public boolean isVerified() {
        return verified;
    }

    public WalletResponse getWallet() {
        return wallet;
    }

    public String getMessage() {
        return message;
    }
}

