# Quick Test: Cashfree Payment Order API

## Reproduce the Original Error

### If the user doesn't have a phone number:
**Request:**
```bash
curl -X POST http://localhost:8080/user/wallet/payment/order \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=your-session-id" \
  -d '{"amount": 100, "gateway": "cashfree"}'
```

**Before Fix:**
- Status: 500 Internal Server Error (or 400 from Cashfree)

**After Fix:**
- Status: 400 Bad Request
- Response:
  ```json
  {
    "error": "Phone number is required for Cashfree payments. Please update your profile with a valid phone number.",
    "message": "Phone number is required for Cashfree payments. Please update your profile with a valid phone number."
  }
  ```

## Successful Test Case

### User WITH phone number:
**Request:**
```bash
curl -X POST http://localhost:8080/user/wallet/payment/order \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=your-session-id" \
  -d '{"amount": 100, "gateway": "cashfree"}'
```

**Expected Response:**
- Status: 200 OK
- Response body:
  ```json
  {
    "keyId": "payment_session_id_here",
    "orderId": "wallet_abc123_xyz",
    "currency": "INR",
    "amount": 100.0,
    "amountPaise": 10000,
    "name": "MakeCall Wallet",
    "description": "Wallet recharge ₹100.00",
    "prefillName": "User Name",
    "prefillContact": "+919876543210",
    "gateway": "cashfree",
    "paymentSessionId": "session_id_here",
    "gatewayMode": "sandbox"
  }
  ```

## Check Logs

### What to look for in application.log:

**1. User validation (if no phone):**
```
WARN c.e.tempp.controller.api.UserController - Payment order creation failed with bad request: Phone number is required for Cashfree payments...
```

**2. Cashfree service call (debug level):**
```
DEBUG c.e.t.s.p.CashfreePaymentService - Creating Cashfree order: amount=100.0, phone=+919876543210, name=John Doe, email=john@example.com
```

**3. Success:**
```
INFO c.e.t.s.p.CashfreePaymentService - Cashfree order created: orderId=wallet_xyz, amount=100.0, customerPhone=+919876543210
```

**4. Errors from Cashfree API:**
```
ERROR c.e.t.s.p.CashfreePaymentService - Error creating Cashfree order: <error details>
```

## Database Check

### Verify PaymentRecord was created:
```sql
SELECT * FROM payment_records 
WHERE gateway = 'cashfree' 
ORDER BY created_at DESC 
LIMIT 5;
```

### Check user's phone number:
```sql
SELECT id, name, number, email 
FROM users 
WHERE id = 'user_id_here';
```

## Troubleshooting

### Still getting 500 error?

1. **Check if user is logged in:**
   - 500 error might be from session validation
   - Verify JSESSIONID is valid

2. **Check application startup logs:**
   - Ensure Cashfree service initialized properly
   - Verify configuration is loaded

3. **Enable DEBUG logging:**
   - Add to `application.properties`:
     ```properties
     logging.level.com.example.tempp.service.payment=DEBUG
     ```

4 **Verify Cashfree configuration:**
   ```sql
   SELECT key, value FROM app_config 
   WHERE key LIKE 'cashfree_%';
   ```

### Common Fixes

**If user doesn't have phone:**
1. Update user profile to add phone number
2. Or switch to Razorpay gateway: `{"amount": 100, "gateway": "razorpay"}`

**If Cashfree API rejects request:**
1. Verify phone number format (e.g., +919876543210)
2. Check Cashfree credentials are correct
3. Ensure you're using the right environment (sandbox vs production)

## Next Steps After Testing

✅ Verified user has phone number → Payment order created successfully  
✅ Verified user without phone → Gets clear error message  
✅ Verified logs show detailed debugging info  
✅ Verified error handling returns appropriate HTTP status codes  

Ready for deployment! 🚀

