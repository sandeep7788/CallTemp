package com.example.tempp.service.payment;

import com.example.tempp.service.AppConfigService;
import com.example.tempp.model.PaymentOrderResponse;
import com.example.tempp.model.PaymentVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Payment Gateway Manager
 * Routes payment operations to the appropriate gateway (Razorpay, Cashfree, etc.)
 * Supports multiple payment providers with automatic fallback
 */
@Slf4j
@Service
public class PaymentGatewayManager {

    private final List<PaymentGatewayService> paymentGateways;
    private final AppConfigService appConfigService;

    public PaymentGatewayManager(List<PaymentGatewayService> paymentGateways, AppConfigService appConfigService) {
        this.paymentGateways = paymentGateways;
        this.appConfigService = appConfigService;
        log.info("PaymentGatewayManager initialized with {} gateways", paymentGateways.size());
    }

    /**
     * Get the default payment gateway
     */
    public PaymentGatewayService getDefaultGateway() {
        String defaultGateway = appConfigService.getPaymentDefaultGateway();
        return getGatewayByName(defaultGateway)
                .orElseGet(() -> {
                    log.warn("Default gateway '{}' not found, using first available gateway", defaultGateway);
                    return paymentGateways.stream()
                            .filter(PaymentGatewayService::isEnabled)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No payment gateway is enabled"));
                });
    }

    /**
     * Get a specific payment gateway by name
     */
    public Optional<PaymentGatewayService> getGatewayByName(String gatewayName) {
        return paymentGateways.stream()
                .filter(gateway -> gateway.getGatewayName().equalsIgnoreCase(gatewayName))
                .filter(PaymentGatewayService::isEnabled)
                .findFirst();
    }

    /**
     * Get all enabled payment gateways
     */
    public List<PaymentGatewayService> getEnabledGateways() {
        return paymentGateways.stream()
                .filter(PaymentGatewayService::isEnabled)
                .toList();
    }

    /**
     * Create a payment order using the specified or default gateway
     */
    public PaymentOrderResponse createPaymentOrder(String gatewayName, double amountInRupees, String userId, String receiptId) throws Exception {
        PaymentGatewayService gateway = gatewayName != null
                ? getGatewayByName(gatewayName).orElseThrow(() -> new IllegalArgumentException("Gateway not found or not enabled: " + gatewayName))
                : getDefaultGateway();

        log.info("Creating payment order via {}: amount={}, userId={}", gateway.getGatewayName(), amountInRupees, userId);
        return gateway.createPaymentOrder(amountInRupees, userId, receiptId);
    }

    /**
     * Verify payment using the specified gateway
     */
    public PaymentVerifyResponse verifyPayment(String gatewayName, String orderId, String paymentId, String signature) throws Exception {
        PaymentGatewayService gateway = getGatewayByName(gatewayName)
                .orElseThrow(() -> new IllegalArgumentException("Gateway not found: " + gatewayName));

        log.info("Verifying payment via {}: orderId={}, paymentId={}", gateway.getGatewayName(), orderId, paymentId);
        return gateway.verifyPayment(orderId, paymentId, signature);
    }

    /**
     * Handle webhook from the specified gateway
     */
    public boolean handleWebhook(String gatewayName, String payload, String signature) throws Exception {
        PaymentGatewayService gateway = getGatewayByName(gatewayName)
                .orElseThrow(() -> new IllegalArgumentException("Gateway not found: " + gatewayName));

        log.info("Handling webhook for gateway: {}", gateway.getGatewayName());
        return gateway.handleWebhook(payload, signature);
    }
}

