# Cashfree Critical Fixes Implementation

## 🔴 CRITICAL FIX #1: Webhook Signature Verification

### Problem
Current implementation incorrectly adds timestamp to payload before computing HMAC, which doesn't match Cashfree's verification algorithm.

### Cashfree Official Documentation
> Signature = Base64(HMAC-SHA256(raw_body, client_secret))

### Fix Implementation

**File**: `CashfreePaymentService.java`

```java
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
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            appConfigService.getCashfreeSecretKey().getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        mac.init(secretKeySpec);
        
        // Hash the raw payload (NO timestamp prepending as per Cashfree docs)
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
```

---

## 🔴 CRITICAL FIX #2: Phone Number Validation

### Problem
Phone number is required but format is not validated. Cashfree requires country code.

### Cashfree Requirements
- Must include country code (e.g., +91 for India)
- Format: +[country_code][number]
- Example: +919876543210

### Fix Implementation

**File**: `CashfreePaymentService.java`

Add utility method:
```java
/**
 * Validate phone number format for Cashfree
 * Cashfree requires phone with country code
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
    // Minimum length: +CC + 9 digits = 12 chars (e.g., +919876543210)
    // Maximum length: +CC + 15 digits (international standard)
    if (!normalized.startsWith("+")) {
        log.warn("Phone number missing country code: {}", phone);
        return false;
    }
    
    // Check length (including +)
    if (normalized.length() < 12 || normalized.length() > 16) {
        log.warn("Phone number invalid length: {}", phone);
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
```

Update `createPaymentOrder`:
```java
// Validate required customer phone (mandatory for Cashfree)
if (customerPhone == null || customerPhone.isBlank()) {
    throw new IllegalArgumentException("Customer phone number is required for Cashfree payments");
}

// Validate phone format
if (!isValidPhoneNumber(customerPhone)) {
    throw new IllegalArgumentException(
        "Invalid phone number format. Phone must include country code (e.g., +919876543210)"
    );
}
```

---

## 🟡 HIGH PRIORITY FIX #1: Order ID Uniqueness

### Problem
Using timestamp alone may cause collisions in high-traffic scenarios.

### Fix Implementation

**File**: `CashfreePaymentService.java`

```java
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
        new java.util.Random().nextInt(1000000)
    );
}
orderRequest.put("order_id", generatedOrderId);
```

---

## 🟡 HIGH PRIORITY FIX #2: Order Expiry Time

### Problem
Orders never expire, can remain in ACTIVE status indefinitely.

### Cashfree Documentation
- Format: RFC 3339 timestamp (ISO 8601)
- Example: "2023-07-15T10:30:00+05:30"
- Recommended: 15-30 minutes from creation

### Fix Implementation

**File**: `CashfreePaymentService.java`

Add configuration:
```java
// In AppConfigService.java
public static final String CASHFREE_ORDER_EXPIRY_MINUTES = "cashfree_order_expiry_minutes";

public int getCashfreeOrderExpiryMinutes() {
    return getIntegerConfig(CASHFREE_ORDER_EXPIRY_MINUTES, 30);
}
```

Update order creation:
```java
// Add order expiry time (30 minutes from now by default)
int expiryMinutes = appConfigService.getCashfreeOrderExpiryMinutes();
java.time.ZonedDateTime expiryTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
    .plusMinutes(expiryMinutes);
String expiryTimeStr = expiryTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
orderRequest.put("order_expiry_time", expiryTimeStr);

log.debug("Order will expire at: {}", expiryTimeStr);
```

---

## 🟡 HIGH PRIORITY FIX #3: Enhanced Payment Verification

### Problem
Doesn't handle UNDER_SETTLEMENT status or verify amount.

### Fix Implementation

**File**: `CashfreePaymentService.java`

```java
@Override
public PaymentVerifyResponse verifyPayment(String orderId, String paymentId, String signature) throws Exception {
    if (!isEnabled()) {
        throw new IllegalStateException("Cashfree payment gateway is not enabled");
    }

    String appId = appConfigService.getCashfreeAppId();
    String secretKey = appConfigService.getCashfreeSecretKey();
    String apiVersion = appConfigService.getCashfreeApiVersion();
    String baseUrl = appConfigService.isCashfreeSandbox() 
        ? "https://sandbox.cashfree.com/pg" 
        : "https://api.cashfree.com/pg";
    String url = baseUrl + "/orders/" + orderId;

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
                log.info("Cashfree order amount: {}", orderAmountObj);
            }
            if (cfOrderIdObj != null) {
                log.info("Cashfree internal order ID: {}", cfOrderIdObj);
            }
            
            String message;
            if (isSuccess) {
                message = "Payment verified successfully via Cashfree (Status: " + orderStatus + ")";
            } else {
                message = "Payment verification failed: " + orderStatus;
            }
            
            // Create response
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
```

---

## 🟢 ENHANCEMENT #1: Order Tags and Notes

### Purpose
Better tracking and searchability in Cashfree dashboard.

### Fix Implementation

**File**: `CashfreePaymentService.java`

```java
// Add order note for internal reference
String orderNote = String.format("Wallet recharge for user %s - Amount: ₹%.2f", 
                                 userId, amountInRupees);
orderRequest.put("order_note", orderNote);

// Add order tags for better filtering
Map<String, String> orderTags = new LinkedHashMap<>();
orderTags.put("type", "wallet_recharge");
orderTags.put("user_id", userId);
orderTags.put("environment", sandbox ? "sandbox" : "production");
orderRequest.put("order_tags", orderTags);
```

---

## 📝 COMPLETE UPDATED CODE

See: `CashfreePaymentService_FIXED.java` (to be created next)

---

## ✅ TESTING CHECKLIST

After applying fixes:

- [ ] Test order creation with valid phone number
- [ ] Test order creation with invalid phone (missing +)
- [ ] Test order creation with short phone number
- [ ] Test webhook with valid signature
- [ ] Test webhook with invalid signature
- [ ] Test payment verification for PAID status
- [ ] Test payment verification for UNDER_SETTLEMENT status
- [ ] Verify order expires after configured time
- [ ] Check Cashfree dashboard for order tags
- [ ] Verify order notes appear correctly

---

## 🚀 DEPLOYMENT

1. Apply all fixes to `CashfreePaymentService.java`
2. Add new config key `cashfree_order_expiry_minutes` (default: 30)
3. Test in sandbox environment first
4. Monitor logs for phone validation errors
5. Verify webhook signature validation works
6. Deploy to production

---

## 📊 IMPACT ASSESSMENT

| Fix | Impact | Risk | Effort |
|-----|---------|------|--------|
| Webhook signature | HIGH | LOW | 5 min |
| Phone validation | HIGH | LOW | 10 min |
| Order ID uniqueness | MEDIUM | LOW | 5 min |
| Order expiry | MEDIUM | LOW | 15 min |
| Payment verification | MEDIUM | LOW | 10 min |
| Tags & notes | LOW | NONE | 5 min |

**Total Effort**: ~50 minutes
**Risk Level**: LOW (all changes are additive or fixes)

