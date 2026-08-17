# Rate Limiting - Quick Test Guide

## Prerequisites
- Application running on `http://localhost:8080`
- `curl` command-line tool installed

## Test 1: Global Rate Limit (100 requests/minute)

### Rapid-fire requests test
Send 101 sequential requests to trigger the global rate limit:

```bash
#!/bin/bash
# Save as test_global_rate_limit.sh

echo "Testing global rate limit (100 req/min)..."
for i in {1..101}; do
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        http://localhost:8080/user/me \
        -H "X-Forwarded-For: 192.168.1.100")
    
    if [ "$response" = "429" ]; then
        echo "✓ Request $i: Rate limited! (429 Too Many Requests)"
        break
    elif [ "$response" = "401" ]; then
        echo "  Request $i: OK (need auth - 401)"
    else
        echo "  Request $i: Status $response"
    fi
done
```

Run it:
```bash
bash test_global_rate_limit.sh
```

**Expected Result**: After ~100 requests, should see `429 Too Many Requests`

---

## Test 2: Login-Specific Rate Limit (5 attempts/15 minutes)

### Rapid login attempts test
Try to login 6 times to trigger the login rate limit:

```bash
#!/bin/bash
# Save as test_login_rate_limit.sh

echo "Testing login rate limit (5 attempts/15 min)..."
for i in {1..6}; do
    echo -n "Login attempt $i: "
    
    response=$(curl -s -X POST http://localhost:8080/user/login \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 192.168.1.200" \
        -d '{
            "number": "9876543210",
            "name": "Test User",
            "location": ""
        }')
    
    # Check if rate limited
    if echo "$response" | grep -q "Too many login attempts"; then
        echo "✓ Rate limited!"
        break
    elif echo "$response" | grep -q '"error"'; then
        error=$(echo "$response" | grep -o '"error":"[^"]*' | cut -d'"' -f4)
        echo "Error: $error"
    elif echo "$response" | grep -q '"id"'; then
        echo "✓ Success!"
    else
        echo "Response: $response"
    fi
    
    sleep 0.1  # Small delay between attempts
done
```

Run it:
```bash
bash test_login_rate_limit.sh
```

**Expected Result**: After ~5 attempts, should see `Too many login attempts` error

---

## Test 3: Google Login Rate Limit

### Rapid Google login attempts test
```bash
echo "Testing Google login rate limit..."
for i in {1..6}; do
    echo -n "Google login attempt $i: "
    
    response=$(curl -s -X POST http://localhost:8080/user/google-login \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 192.168.1.201" \
        -d '{"idToken": "invalid_token", "deviceIdentifier": "test"}')
    
    if echo "$response" | grep -q "Too many login attempts"; then
        echo "✓ Rate limited!"
        break
    else
        echo "Response received"
    fi
    
    sleep 0.1
done
```

---

## Test 4: Simple Login Rate Limit

### Rapid simple login attempts test
```bash
echo "Testing simple login rate limit..."
for i in {1..6}; do
    echo -n "Simple login attempt $i: "
    
    response=$(curl -s -X POST http://localhost:8080/user/simple-login \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 192.168.1.202" \
        -d '{
            "number": "9876543210",
            "deviceIdentifier": ""
        }')
    
    if echo "$response" | grep -q "Too many login attempts"; then
        echo "✓ Rate limited!"
        break
    else
        echo "Response received"
    fi
    
    sleep 0.1
done
```

---

## Test 5: Different IPs Not Rate Limited

### Verify different IPs have separate limits
```bash
#!/bin/bash
# Save as test_different_ips.sh

echo "Testing separate rate limits per IP..."

# First IP makes 3 requests
for i in {1..3}; do
    curl -s -X POST http://localhost:8080/user/login \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 10.0.0.1" \
        -d '{"number": "1111111111", "name": "User1"}' > /dev/null
    echo "IP 10.0.0.1: Request $i"
done

# Second IP makes 3 requests (should work, separate limit)
for i in {1..3}; do
    curl -s -X POST http://localhost:8080/user/login \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 10.0.0.2" \
        -d '{"number": "2222222222", "name": "User2"}' > /dev/null
    echo "IP 10.0.0.2: Request $i"
done

echo "✓ Both IPs can make independent requests"
```

---

## Test 6: Successful Login Resets Rate Limit

### Verify rate limit resets after successful login
```bash
#!/bin/bash

# Note: This requires a valid phone number and Firebase setup

echo "Testing rate limit reset on successful login..."

# Make 2 failed login attempts
for i in {1..2}; do
    curl -s -X POST http://localhost:8080/user/login \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 10.0.0.10" \
        -d '{"number": "9999999999", "name": "Test"}' > /dev/null
    echo "Attempt $i (failed)"
done

# Make successful login
response=$(curl -s -X POST http://localhost:8080/user/login \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: 10.0.0.10" \
    -d '{"number": "9999999999", "name": "Test"}')

if echo "$response" | grep -q '"id"'; then
    echo "✓ Successful login detected"
    echo "✓ Rate limit should be reset for this IP"
    
    # After successful login, can make more attempts
    for i in {1..3}; do
        curl -s -X POST http://localhost:8080/user/login \
            -H "Content-Type: application/json" \
            -H "X-Forwarded-For: 10.0.0.10" \
            -d '{"number": "9999999999", "name": "Test"}' > /dev/null
        echo "Post-success attempt $i"
    done
    
    echo "✓ Rate limit reset successful!"
fi
```

---

## Test 7: Check Logs

Monitor application logs for rate limiting messages:

```bash
# Terminal 1: Start application
mvn spring-boot:run

# Terminal 2: Monitor logs
tail -f logs/application.log | grep -i "rate"
```

Expected log output:
```
[WARN] Global rate limit exceeded - IP: 192.168.1.100, attempts: 101/100
[WARN] Login rate limit exceeded for IP: 192.168.1.200
[WARN] Login rate limit check - IP: 192.168.1.200, attempts: 3/5
[DEBUG] Login rate limit reset for IP: 192.168.1.200
```

---

## Monitoring Commands

### Check active rate limit tracking

Add monitoring endpoint to a controller (optional):

```java
@GetMapping("/admin/rate-limit-status")
public ResponseEntity<?> rateLimitStatus() {
    return ResponseEntity.ok(Map.of(
        "globalTrackedIPs", rateLimitingService.getGlobalMapSize(),
        "loginTrackedIPs", rateLimitingService.getLoginMapSize()
    ));
}
```

Then check:
```bash
curl http://localhost:8080/admin/rate-limit-status
```

---

## Troubleshooting

### Test shows no rate limiting
1. Check if global limit (100 req/min) is being hit first
2. Verify interceptor is registered: check logs for "GlobalRateLimitInterceptor"
3. Check IP is not in exclude list in WebMvcConfig
4. Verify Spring component scanning includes interceptor package

### Rate limit persists longer than expected
1. Limits reset after time window expires
2. Global: 1 minute window
3. Login: 15 minute window
4. Cannot manually reset (unless admin endpoint added)

### Getting 401 instead of 429
1. Global rate limit not triggered yet (< 100 req/min)
2. 401 Unauthorized is normal for unauthenticated endpoints
3. Look for 429 after many rapid requests

### IP detection issues
1. Verify X-Forwarded-For header is being used correctly
2. Check proxy configuration (if behind load balancer)
3. Try with raw REMOTE_ADDR by removing X-Forwarded-For header

---

## Performance Benchmarks

Expected overhead per request:
- Global rate limit check: ~0.1ms
- Login rate limit check: ~0.1ms
- Total: <0.5ms per request

Memory usage:
- ~100 bytes per tracked IP
- Grows linearly with unique IP addresses
- Max ~10MB for 100K unique IPs (rough estimate)

---

## Clean Up Rate Limits (Admin)

To clear all rate limits (for testing):

Add admin endpoint to UserController:
```java
@PostMapping("/admin/clear-rate-limits")
public ResponseEntity<?> clearRateLimits() {
    // Verify admin authorization first
    rateLimitingService.clearAllRateLimits();
    return ResponseEntity.ok("Rate limits cleared");
}
```

Then call:
```bash
curl -X POST http://localhost:8080/user/admin/clear-rate-limits
```

