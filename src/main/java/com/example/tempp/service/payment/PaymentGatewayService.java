package com.example.tempp.service.payment;

import com.example.tempp.model.PaymentOrderResponse;
import com.example.tempp.model.PaymentVerifyResponse;

/**
 * Payment Gateway Service Interface
 * Abstracts payment operations to support multiple payment providers (Razorpay, Cashfree, etc.)
 */
public interface PaymentGatewayService {
    
    /**
     * Get the gateway identifier (e.g., "razorpay", "cashfree")
     */
    String getGatewayName();
    
    /**
     * Check if this gateway is enabled
     */
    boolean isEnabled();
    
    /**
     * Create a payment order
     * 
     * @param amountInRupees Amount in INR
     * @param userId User ID
     * @param receiptId Receipt/Reference ID
     * @return PaymentOrderResponse containing order ID and other details
     */
    PaymentOrderResponse createPaymentOrder(double amountInRupees, String userId, String receiptId) throws Exception;
    
    /**
     * Create a payment order with customer details
     * 
     * @param amountInRupees Amount in INR
     * @param userId User ID
     * @param receiptId Receipt/Reference ID
     * @param customerPhone Customer phone number
     * @param customerName Customer name (optional)
     * @param customerEmail Customer email (optional)
     * @return PaymentOrderResponse containing order ID and other details
     */
    default PaymentOrderResponse createPaymentOrder(double amountInRupees, String userId, String receiptId, 
                                                     String customerPhone, String customerName, String customerEmail) throws Exception {
        return createPaymentOrder(amountInRupees, userId, receiptId);
    }
    
    /**
     * Verify payment after completion
     * 
     * @param orderId Order ID from gateway
     * @param paymentId Payment ID from gateway
     * @param signature Signature for verification
     * @return PaymentVerifyResponse with verification result
     */
    PaymentVerifyResponse verifyPayment(String orderId, String paymentId, String signature) throws Exception;
    
    /**
     * Handle webhook/callback from payment gateway
     * 
     * @param payload Webhook payload
     * @param signature Webhook signature for verification
     * @return true if webhook is valid and processed
     */
    boolean handleWebhook(String payload, String signature) throws Exception;
}

