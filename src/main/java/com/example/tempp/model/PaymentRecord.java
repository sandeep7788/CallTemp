package com.example.tempp.model;

import java.time.Instant;

/**
 * Tracks Razorpay payment lifecycle.
 * Stored in the Firestore {@code payment_records} collection, keyed by {@code razorpayOrderId}.
 */
public class PaymentRecord {

    private String id;                  // Firestore document ID = razorpayOrderId
    private String userId;
    private String gateway;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
    private String cashfreeOrderId;
    private String cashfreePaymentId;
    private double amount;
    private int amountPaise;
    private String currency;
    private String receipt;
    private String status;              // CREATED | PAID | FAILED
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant paidAt;

    public PaymentRecord() {
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

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }

    public String getCashfreeOrderId() {
        return cashfreeOrderId;
    }

    public void setCashfreeOrderId(String cashfreeOrderId) {
        this.cashfreeOrderId = cashfreeOrderId;
    }

    public String getCashfreePaymentId() {
        return cashfreePaymentId;
    }

    public void setCashfreePaymentId(String cashfreePaymentId) {
        this.cashfreePaymentId = cashfreePaymentId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(int amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}

