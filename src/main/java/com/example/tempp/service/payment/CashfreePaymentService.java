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
        String secretKey = appConfigService.getCashfreeSecretKey();
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

        String appId = appConfigService.getCashfreeAppId();
        String secretKey = appConfigService.getCashfreeSecretKey();
        String apiVersion = appConfigService.getCashfreeApiVersion();
        String currency = appConfigService.getCashfreeCurrency();
        boolean sandbox = appConfigService.isCashfreeSandbox();
        String baseUrl = sandbox ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg";
        String url = baseUrl + "/orders";

        // Create order request
        Map<String, Object> orderRequest = new LinkedHashMap<>();
        orderRequest.put("order_id", receiptId == null || receiptId.isBlank() ? "wallet_" + System.currentTimeMillis() : receiptId);
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
                // (keyId, orderId, currency, amount, amountPaise, name, description, prefillName, prefillContact)
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

        log.info("Verifying Cashfree payment – orderId={}, paymentId={}", orderId, paymentId);

        String appId = appConfigService.getCashfreeAppId();
        String secretKey = appConfigService.getCashfreeSecretKey();
        String apiVersion = appConfigService.getCashfreeApiVersion();
        String baseUrl = appConfigService.isCashfreeSandbox() ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg";
        String url = baseUrl + "/orders/" + orderId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", apiVersion);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            log.debug("Calling Cashfree API: GET {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                // Extract order details
                String orderStatus = (String) body.get("order_status");
                Object orderAmountObj = body.get("order_amount");
                Object cfOrderIdObj = body.get("cf_order_id");
                Object orderIdObj = body.get("order_id");
                Object paymentObj = body.get("payments");
                Object settlementStatusObj = body.get("order_expiry_time");
                
                log.info("Cashfree order status: orderId={}, status={}, cfOrderId={}, amount={}", 
                         orderIdObj, orderStatus, cfOrderIdObj, orderAmountObj);
                
                // Additional logging for debugging
                if (paymentObj != null) {
                    log.debug("Cashfree payment details: {}", paymentObj);
                }
                
                // Determine if payment is successful
                // Cashfree order_status can be: ACTIVE, PAID, EXPIRED, etc.
                boolean isSuccess = "PAID".equalsIgnoreCase(orderStatus);
                
                // Additional check: if order has payment array, check payment status
                if (paymentObj instanceof Map) {
                    Map<String, Object> paymentDetails = (Map<String, Object>) paymentObj;
                    String paymentStatus = (String) paymentDetails.get("payment_status");
                    log.info("Cashfree payment status: {}", paymentStatus);
                    isSuccess = isSuccess || "SUCCESS".equalsIgnoreCase(paymentStatus);
                }
                
                String message;
                if (isSuccess) {
                    message = "Payment verified successfully via Cashfree (Order Status: " + orderStatus + ")";
                    log.info("✅ Cashfree payment verified: orderId={}, status={}, cfOrderId={}", orderId, orderStatus, cfOrderIdObj);
                } else {
                    message = "Payment verification failed: Order status is " + orderStatus;
                    log.warn("❌ Cashfree payment not successful: orderId={}, status={}", orderId, orderStatus);
                }
                
                // Create response matching existing constructor (verified, wallet, message)
                // Note: Wallet update should be handled separately in the service layer
                PaymentVerifyResponse verifyResponse = new PaymentVerifyResponse(
                    isSuccess,
                    null,  // Wallet response will be set by the calling service
                    message
                );
                
                return verifyResponse;
            } else {
                log.error("Cashfree API returned non-OK status: {} for orderId={}", response.getStatusCode(), orderId);
                throw new Exception("Failed to verify Cashfree payment: HTTP " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Handle 4xx errors (order not found, etc.)
            log.error("Cashfree API client error: status={}, body={}, orderId={}", e.getStatusCode(), e.getResponseBodyAsString(), orderId);
            
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new Exception("Order not found in Cashfree. Order ID: " + orderId);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new Exception("Cashfree authentication failed. Please check API credentials.");
            } else {
                throw new Exception("Cashfree API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            }
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // Handle 5xx errors (Cashfree server issues)
            log.error("Cashfree API server error: status={}, body={}, orderId={}", e.getStatusCode(), e.getResponseBodyAsString(), orderId);
            throw new Exception("Cashfree server error. Please try again in a few moments.");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Handle network/timeout errors
            log.error("Cashfree API network error: {}, orderId={}", e.getMessage(), orderId);
            throw new Exception("Network error connecting to Cashfree. Please check your internet connection and try again.");
        } catch (Exception e) {
            log.error("Unexpected error verifying Cashfree payment: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new Exception("Failed to verify Cashfree payment: " + e.getMessage());
        }
    }

    @Override
    public boolean handleWebhook(String payload, String signature) throws Exception {
        if (!isEnabled()) {
            log.warn("Cashfree webhook received but gateway is not enabled");
            return false;
        }

        // Validate phone number format for Cashfree
        // Cashfree requires phone with country code (e.g., +919876543210)
        // 
        // @param phone Phone number to validate
        // @return true if valid
        if (!isValidPhoneNumber(payload)) {
            log.error("Invalid phone number in webhook payload: {}", payload);
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

    private boolean verifyWebhookSignature(String payload, String receivedSignature) {
        try {
            // Cashfree webhook verification
            // Compute HMAC SHA256 of payload using secret key
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String signedPayload = timestamp + "." + payload;
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(appConfigService.getCashfreeSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hmacBytes = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hmacBytes);
            
            return computedSignature.equals(receivedSignature);
        } catch (Exception e) {
            log.error("Error verifying Cashfree webhook signature: {}", e.getMessage());
            return false;
        }
    }
}
