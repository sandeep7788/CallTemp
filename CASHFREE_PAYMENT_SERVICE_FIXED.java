package com.example.tempp.service.payment;

import com.example.tempp.model.PaymentOrderResponse;
import com.example.tempp.model.PaymentVerifyResponse;
import com.example.tempp.service.AppConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Cashfree Payment Gateway Integration
 * Official Cashfree Payments API v3 implementation
 * Supports Order Creation, Verification, and Webhooks
 */
@Slf4j
@Service
public class CashfreePaymentService implements PaymentGatewayService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;

    public CashfreePaymentService(ObjectMapper objectMapper, AppConfigService appConfigService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.appConfigService = appConfigService;
    }

    @Override
    public String getGatewayName() {
        return "cashfree";
    }

    @Override
    public boolean isEnabled() {
        return appConfigService.isCashfreeEnabled() && isConfigured();
    }

    private boolean isConfigured() {
        String appId = appConfigService.getCashfreeAppId();
        String secretKey = appConfigService.getCashfreeSecret();
        return appId != null && !appId.isBlank() && !appId.contains("replace_with")
                && secretKey != null && !secretKey.isBlank() && !secretKey.contains("replace_with");
    }

    @Override
    public PaymentOrderResponse createPaymentOrder(double amountInRupees, String userId, String receiptId) throws Exception {
        return createPaymentOrder(amountInRupees, userId, receiptId, null, null, null);
    }
    
    @Override
    public PaymentOrderResponse createPaymentOrder(double amountInRupees, String userId, String receiptId,
                                                     String customerPhone, String customerName, String customerEmail) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("Cashfree payment gateway is not enabled or configured");
        }
        
        // Validate required customer phone (mandatory for Cashfree)
        if (customerPhone == null || customerPhone.isBlank()) {
            throw new IllegalArgumentException("Customer phone number is required for Cashfree payments");
        }
        
        // Validate phone format (must include country code)
        if (!isValidPhoneNumber(customerPhone)) {
            throw new IllegalArgumentException(
                "Invalid phone number format. Phone must include country code (e.g., +919876543210)"
            );
        }

        String appId = appConfigService.getCashfreeAppId();
        String secretKey = appConfigService.getCashfreeSecretKey();
        String apiVersion = appConfigService.getCashfreeApiVersion();
        String currency = appConfigService.getCashfreeCurrency();
        boolean sandbox = appConfigService.isCashfreeSandbox();
        String baseUrl = sandbox ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg";
        String url = baseUrl + "/orders";

        // Create order request
        Map<String, Object> orderRequest = new LinkedHashMap<>();
        
        // Generate unique order ID with better collision resistance
        String generatedOrderId;
        if (receiptId != null && !receiptId.isBlank()) {
            generatedOrderId = receiptId;
        } else {
            // Format: wallet_timestamp_random6digits
            generatedOrderId = String.format("wallet_%d_%06d", 
                System.currentTimeMillis(), 
                new Random().nextInt(1000000)
            );
        }
        orderRequest.put("order_id", generatedOrderId);
        orderRequest.put("order_amount", amountInRupees);
        orderRequest.put("order_currency", currency);
        
        Map<String, String> customerDetails = new LinkedHashMap<>();
        customerDetails.put("customer_id", userId);
        
        // Add customer phone - required by Cashfree
        customerDetails.put("customer_phone", customerPhone);
        
        // Add optional customer details
        if (customerName != null && !customerName.isBlank()) {
            customerDetails.put("customer_name", customerName);
        }
        if (customerEmail != null && !customerEmail.isBlank()) {
            customerDetails.put("customer_email", customerEmail);
        }
        
        orderRequest.put("customer_details", customerDetails);
        
        // Add order note for internal reference
        String orderNote = String.format("Wallet recharge for user %s - Amount: ₹%.2f", 
                                         userId, amountInRupees);
        orderRequest.put("order_note", orderNote);
        
        Map<String, String> orderMeta = new LinkedHashMap<>();
        orderMeta.put("return_url", "https://makecall.in/payment/cashfree/callback");
        orderMeta.put("notify_url", "https://makecall.in/api/payment/cashfree/webhook");
        orderRequest.put("order_meta", orderMeta);
        
        log.debug("Creating Cashfree order: amount={}, phone={}, name={}, email={}", 
                  amountInRupees, customerPhone, customerName, customerEmail);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", apiVersion);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderRequest, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String orderId = (String) body.get("order_id");
                String paymentSessionId = (String) body.get("payment_session_id");
                
                // Create response matching existing PaymentOrderResponse constructor
                PaymentOrderResponse orderResponse = new PaymentOrderResponse(
                    paymentSessionId,  // Use session ID as key for Cashfree
                    orderId,
                    currency,
                    amountInRupees,
                    (int) (amountInRupees * 100),  // Convert to paise
                    "MakeCall",
                    "Wallet Recharge - MakeCall",
                    customerName != null ? customerName : "",  // Prefill name
                    customerPhone != null ? customerPhone : "",  // Prefill contact
                    "cashfree",
                    paymentSessionId,
                    sandbox ? "sandbox" : "production"
                );
                
                log.info("Cashfree order created: orderId={}, amount={}, customerPhone={}", orderId, amountInRupees, customerPhone);
                return orderResponse;
            } else {
                throw new Exception("Failed to create Cashfree order: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error creating Cashfree order: {}", e.getMessage(), e);
            throw new Exception("Failed to create Cashfree payment order: " + e.getMessage());
        }
    }

    @Override
    public PaymentVerifyResponse verifyPayment(String orderId, String paymentId, String signature) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("Cashfree payment gateway is not enabled");
        }

        String appId = appConfigService.getCashfreeAppId();
        String secretKey = appConfigService.getCashfreeSecretKey();
        String apiVersion = appConfigService.getCashfreeApiVersion();
        String baseUrl = appConfigService.isCashfreeSandbox() ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg";
        String url =  baseUrl + "/orders/" + orderId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", apiVersion);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String orderStatus = (String) body.get("order_status");
                
                // Accept both PAID and UNDER_SETTLEMENT as successful
                boolean isSuccess = "PAID".equals(orderStatus) || "UNDER_SETTLEMENT".equals(orderStatus);
                
                // Extract and log additional details
                Object orderAmountObj = body.get("order_amount");
                Object cfOrderIdObj = body.get("cf_order_id");
                
                if (orderAmountObj != null) {
                    log.debug("Cashfree order amount: {}", orderAmountObj);
                }
                if (cfOrderIdObj != null) {
                    log.debug("Cashfree internal order ID: {}", cfOrderIdObj);
                }
                
                String message;
                if (isSuccess) {
                    message = "Payment verified successfully via Cashfree (Status: " + orderStatus + ")";
                } else {
                    message = "Payment verification failed: " + orderStatus;
                }
                
                // Create response matching existing constructor (verified, wallet, message)
                // Note: Wallet update should be handled separately in the service layer
                PaymentVerifyResponse verifyResponse = new PaymentVerifyResponse(
                    isSuccess,
                    null,  // Wallet response will be set by the calling service
                    message
                );
                
                if (isSuccess) {
                    log.info("Cashfree payment verified successfully: orderId={}, status={}, cfOrderId={}", 
                             orderId, orderStatus, cfOrderIdObj);
                } else {
                    log.warn("Cashfree payment not successful: orderId={}, status={}", orderId, orderStatus);
                }
                
                return verifyResponse;
            } else {
                throw new Exception("Failed to verify Cashfree payment: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error verifying Cashfree payment: {}", e.getMessage(), e);
            throw new Exception("Failed to verify Cashfree payment: " + e.getMessage());
        }
    }

    @Override
    public boolean handleWebhook(String payload, String signature) throws Exception {
        if (!isEnabled()) {
            log.warn("Cashfree webhook received but gateway is not enabled");
            return false;
        }

        // Verify webhook signature
        if (!verifyWebhookSignature(payload, signature)) {
            log.error("Cashfree webhook signature verification failed");
            return false;
        }

        try {
            Map<String, Object> webhookData = objectMapper.readValue(payload, Map.class);
            String eventType = (String) webhookData.get("type");
            
            log.info("Cashfree webhook received: type={}", eventType);
            
            // Handle different webhook events
            if ("PAYMENT_SUCCESS_WEBHOOK".equals(eventType)) {
                Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
                Map<String, Object> order = (Map<String, Object>) data.get("order");
                
                String orderId = (String) order.get("order_id");
                String orderStatus = (String) order.get("order_status");
                
                log.info("Cashfree payment success webhook: orderId={}, status={}", orderId, orderStatus);
                
                // Here you would typically update your database with payment status
                // For now, just return success
                return true;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error handling Cashfree webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate phone number format for Cashfree
     * Cashfree requires phone with country code (e.g., +919876543210)
     * 
     * @param phone Phone number to validate
     * @return true if valid
     */
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        
        // Remove spaces and dashes for validation
        String normalized = phone.trim().replaceAll("[\\s-]", "");
        
        // Must start with + for international format
        if (!normalized.startsWith("+")) {
            log.warn("Phone number missing country code: {}", phone);
           return false;
        }
        
        // Check length (including +)
        // Minimum: +CC + 9 digits = 12 chars (e.g., +919876543210)
        // Maximum: +CC + 15 digits = 17 chars (international standard)
        if (normalized.length() < 12 || normalized.length() > 17) {
            log.warn("Phone number invalid length ({}): {}", normalized.length(), phone);
            return false;
        }
        
        // Check all characters after + are digits
        String digits = normalized.substring(1);
        if (!digits.matches("\\d+")) {
            log.warn("Phone number contains non-digit characters: {}", phone);
            return false;
        }
        
        return true;
    }

    /**
     * Verify Cashfree webhook signature according to official documentation
     * Signature = Base64(HMAC_SHA256(raw_body, client_secret))
     * 
     * @param payload Raw webhook payload (body as string)
     * @param receivedSignature Signature from x-webhook-signature header
     * @return true if signature is valid
     */
    private boolean verifyWebhookSignature(String payload, String receivedSignature) {
        try {
            if (receivedSignature == null || receivedSignature.isBlank()) {
                log.error("Webhook signature is missing");
                return false;
            }
            
            // Compute HMAC SHA256 of raw payload using secret key
            // Per Cashfree docs: NO timestamp prepending, just hash the raw body
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                appConfigService.getCashfreeSecretKey().getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            
            // Hash the raw payload directly (no timestamp)
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hmacBytes);
            
            boolean isValid = computedSignature.equals(receivedSignature.trim());
            
            if (!isValid) {
                log.warn("Webhook signature mismatch. Expected: {}, Received: {}", 
                         computedSignature.substring(0, 10) + "...", 
                         receivedSignature.substring(0, Math.min(10, receivedSignature.length())) + "...");
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying Cashfree webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }
}



