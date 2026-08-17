package com.example.tempp.model;

public class PaymentOrderResponse {
    private final String keyId;
    private final String orderId;
    private final String currency;
    private final double amount;
    private final int amountPaise;
    private final String name;
    private final String description;
    private final String prefillName;
    private final String prefillContact;
    private final String gateway;
    private final String paymentSessionId;
    private final String gatewayMode;

    public PaymentOrderResponse(String keyId, String orderId, String currency, double amount, int amountPaise, String name, String description, String prefillName, String prefillContact) {
        this(keyId, orderId, currency, amount, amountPaise, name, description, prefillName, prefillContact, "razorpay", null, null);
    }

    public PaymentOrderResponse(String keyId, String orderId, String currency, double amount, int amountPaise, String name, String description, String prefillName, String prefillContact, String gateway, String paymentSessionId) {
        this(keyId, orderId, currency, amount, amountPaise, name, description, prefillName, prefillContact, gateway, paymentSessionId, null);
    }

    public PaymentOrderResponse(String keyId, String orderId, String currency, double amount, int amountPaise, String name, String description, String prefillName, String prefillContact, String gateway, String paymentSessionId, String gatewayMode) {
        this.keyId = keyId;
        this.orderId = orderId;
        this.currency = currency;
        this.amount = amount;
        this.amountPaise = amountPaise;
        this.name = name;
        this.description = description;
        this.prefillName = prefillName;
        this.prefillContact = prefillContact;
        this.gateway = gateway;
        this.paymentSessionId = paymentSessionId;
        this.gatewayMode = gatewayMode;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCurrency() {
        return currency;
    }

    public double getAmount() {
        return amount;
    }

    public int getAmountPaise() {
        return amountPaise;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPrefillName() {
        return prefillName;
    }

    public String getPrefillContact() {
        return prefillContact;
    }

    public String getGateway() {
        return gateway;
    }

    public String getPaymentSessionId() {
        return paymentSessionId;
    }

    public String getGatewayMode() {
        return gatewayMode;
    }
}
