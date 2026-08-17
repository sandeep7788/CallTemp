package com.example.tempp.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class PaymentVerifyRequest {
    private String gateway;
    @JsonAlias("razorpay_order_id")
    private String razorpayOrderId;
    @JsonAlias("razorpay_payment_id")
    private String razorpayPaymentId;
    @JsonAlias("razorpay_signature")
    private String razorpaySignature;
    @JsonAlias({"cashfree_order_id", "order_id"})
    private String cashfreeOrderId;
    @JsonAlias({"cashfree_payment_id", "cf_payment_id"})
    private String cashfreePaymentId;

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
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
}


