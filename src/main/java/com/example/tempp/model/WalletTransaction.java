package com.example.tempp.model;

import java.time.Instant;

/**
 * Unified wallet ledger entry.
 * Every credit/debit to a user's wallet is recorded here.
 * Stored as a sub-collection under {@code users/{userId}/wallet_transactions/{id}}.
 *
 * <p>Types: {@code CREDIT} | {@code DEBIT}
 * <p>Reason examples:
 * <ul>
 *   <li>Registration welcome reward</li>
 *   <li>Razorpay payment verified – order: {orderId}</li>
 *   <li>Call charge – {durationSeconds}s – {callSid}</li>
 * </ul>
 */
public class WalletTransaction {

    private String id;            // Firestore document ID (UUID)
    private String userId;
    private String type;          // CREDIT | DEBIT
    private double amount;
    private double balanceBefore;
    private double balanceAfter;
    private String reason;
    private String referenceId;   // optional: callSid, razorpayOrderId, etc.
    private Instant createdAt;

    public WalletTransaction() {
    }

    public WalletTransaction(String userId, String type, double amount, double balanceBefore, double balanceAfter, String reason, String referenceId) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(double balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(double balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
