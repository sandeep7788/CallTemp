package com.example.tempp.model;

public class WalletResponse {
    private final double walletBalance;
    private final double minimumBalance;
    private final double ratePerMinute;
    private final boolean canCall;

    public WalletResponse(double walletBalance, double minimumBalance, double ratePerMinute) {
        this.walletBalance = walletBalance;
        this.minimumBalance = minimumBalance;
        this.ratePerMinute = ratePerMinute;
        this.canCall = walletBalance >= minimumBalance;
    }

    public double getWalletBalance() {
        return walletBalance;
    }

    public double getMinimumBalance() {
        return minimumBalance;
    }

    public double getRatePerMinute() {
        return ratePerMinute;
    }

    public boolean isCanCall() {
        return canCall;
    }
}
