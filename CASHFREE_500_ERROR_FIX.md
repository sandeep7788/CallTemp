# Cashfree Payment API 500 Error - Complete Fix

## Problem
The `/user/wallet/payment/order` API endpoint was returning a 500 Internal Server Error when attempting to create a Cashfree payment order.

## Root Causes Identified

### 1. **Missing Customer Phone Number**
   - Cashfree API requires `customer_details.customer_phone` as a **mandatory field**
   - Previous implementation only sent `customer_id`, causing a 400 Bad Request from Cashfree
   - This error was being logged but may have been causing cascading failures

### 2. **Missing Phone Number Validation**
   - Users who signed up via Google login may not have a phone number in their profile
   - Attempting to create a Cashfree order for such users would fail
   - No validation was present to catch this before calling the Cashfree API

### 3. **Inadequate Exception Handling**
   - Generic exceptions were not properly caught and logged
   - Some exceptions might have been wrapped incorrectly, hiding the real issue

## Complete Solution

### Phase 1: Customer Details Integration

**File: `PaymentGatewayService.java`**
- Added new method signature to accept customer details:
  ```java
  PaymentOrderResponse createPaymentOrder(
      double amountInRupees, 
      String userId, 
      String receiptId,
      String customerPhone,   // Required by Cashfree
      String customerName,    // Optional
      String customerEmail    // Optional
  )
  ```
- Maintained backward compatibility with default method implementation

**File: `CashfreePaymentService.java`**
- Implemented the new method to accept and validate customer details
- Added validation: Phone number is mandatory for Cashfree
- Updated order request to include:
  - `customer_phone` (always sent when provided)
  - `customer_name` (sent if available)
  - `customer_email` (sent if available)
- Added debug logging for troubleshooting

**File: `PaymentService.java`**
- Updated `createCashfreeOrder()` to pass user details:
  - `user.getNumber()` → customer phone
  - `user.getName()` → customer name
  - `user.getEmail()` → customer email

### Phase 2: Validation & Error Handling

**File: `PaymentService.java`**
- Added validation before creating Cashfree order:
  ```java
  if (user.getNumber() == null || user.getNumber().isBlank()) {
      throw new IllegalArgumentException(
          "Phone number is required for Cashfree payments. " +
          "Please update your profile with a valid phone number."
      );
  }
  ```
- Improved exception handling:
  - `IllegalArgumentException` is re-thrown as-is (validation errors)
  - Other exceptions are caught and wrapped in `IllegalStateException`

**File: `CashfreePaymentService.java`**
- Added validation at service level:
  ```java
  if (customerPhone == null || customerPhone.isBlank()) {
      throw new IllegalArgumentException(
          "Customer phone number is required for Cashfree payments"
      );
  }
  ```

**File: `UserController.java`**
- Enhanced exception handling with comprehensive logging:
  - `IllegalArgumentException` → 400 Bad Request
  - `IllegalStateException` → 503 Service Unavailable
  - `Exception` (catch-all) → 500 Internal Server Error with logging

## Error Response Mapping

| Exception Type | HTTP Status | User Message |
|---------------|-------------|--------------|
| IllegalArgumentException | 400 Bad Request | Specific validation error (e.g., "Phone number is required") |
| IllegalStateException | 503 Service Unavailable | "Unable to start Cashfree payment. Please try again later." |
| Generic Exception | 500 Internal Server Error | "An unexpected error occurred. Please try again later." |

## Testing Instructions

### 1. Test with Valid User (Has Phone Number)
```bash
POST /user/wallet/payment/order
Content-Type: application/json

{
  "amount": 100,
  "gateway": "cashfree"
}
```

**Expected Result:**
- Status: 200 OK
- Response contains order details with payment session ID

### 2. Test with User Missing Phone Number
**Expected Result:**
- Status: 400 Bad Request
- Response:
  ```json
  {
    "error": "Phone number is required for Cashfree payments. Please update your profile with a valid phone number.",
    "message": "Phone number is required for Cashfree payments. Please update your profile with a valid phone number."
  }
  ```

### 3. Test with Invalid Amount
```bash
POST /user/wallet/payment/order
Content-Type: application/json

{
  "amount": -10,
  "gateway": "cashfree"
}
```

**Expected Result:**
- Status: 400 Bad Request
- Response: Validation error for amount

### 4. Test with Cashfree Disabled
**Expected Result:**
- Status: 503 Service Unavailable
- Response: "Cashfree payment gateway is not enabled or configured."

## Debugging Guide

### Check Application Logs

Look for these log patterns:

1. **Phone Validation Failed:**
   ```
   WARN c.e.tempp.controller.api.UserController - Payment order creation failed with bad request: Phone number is required for Cashfree payments...
   ```

2. **Cashfree API Call Details:**
   ```
   DEBUG c.e.t.s.p.CashfreePaymentService - Creating Cashfree order: amount=100.0, phone=+919876543210, name=John Doe, email=john@example.com
   ```

3. **Successful Order Creation:**
   ```
   INFO c.e.t.s.p.CashfreePaymentService - Cashfree order created: orderId=wallet_abc123, amount=100.0, customerPhone=+919876543210
   ```

4. **Unexpected Errors:**
   ```
   ERROR c.e.tempp.controller.api.UserController - Unexpected error creating payment order
   ```

### Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| 400 Bad Request from Cashfree | Missing or invalid customer_phone | Ensure user has phone number in profile |
| 500 Internal Server Error | Uncaught exception | Check application logs for stack trace |
| 503 Service Unavailable | Cashfree disabled or misconfigured | Verify Cashfree credentials in AppConfig |
| NullPointerException | User data missing | Ensure user is logged in and session is valid |

## Database Impact
- ✅ No database schema changes required
- ✅ Existing payment records remain unaffected
- ✅ No data migration needed

## Deployment Checklist
- [x] Code changes applied
- [ ] Verify Cashfree credentials are configured
- [ ] Test with a user who has a phone number
- [ ] Test with a user without a phone number
- [ ] Monitor application logs after deployment
- [ ] Verify error responses are user-friendly
- [ ] Test both sandbox and production environments

## Files Modified
1. `src/main/java/com/example/tempp/service/payment/PaymentGatewayService.java`
2. `src/main/java/com/example/tempp/service/payment/CashfreePaymentService.java`
3. `src/main/java/com/example/tempp/service/PaymentService.java`
4. `src/main/java/com/example/tempp/controller/api/UserController.java`

## Rollback Plan
If issues occur after deployment:
1. The changes are backward compatible with Razorpay
2. Can disable Cashfree gateway via configuration
3. No database rollback required
4. Revert commits if necessary

## Next Steps
1. Deploy the changes to staging/development environment
2. Test all scenarios listed above
3. Monitor logs for any unexpected errors
4. If all tests pass, deploy to production
5. Monitor production logs for the first few transactions

## Support
If you continue to see 500 errors:
1. Check application logs for the full stack trace
2. Verify user has a valid phone number
3. Confirm Cashfree API credentials are correct
4. Test Cashfree API directly using curl/Postman
5. Contact Cashfree support if API continues to reject requests

