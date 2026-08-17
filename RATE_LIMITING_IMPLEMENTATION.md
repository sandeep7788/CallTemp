# Global Rate Limiting Implementation

## Overview
A complete rate limiting solution has been implemented with two tiers:
1. **Global Rate Limiting** - Applied to all API requests (100 requests/minute per IP)
2. **Login-Specific Rate Limiting** - Applied to authentication endpoints (5 attempts/15 minutes per IP)

## Components

### 1. RateLimitingService
**Location**: `src/main/java/com/example/tempp/service/RateLimitingService.java`

Core service managing both global and login-specific rate limits using in-memory maps with atomic counters.

**Global Rate Limiting Methods**:
- `isGlobalRateLimited(String ipAddress)` - Check if IP exceeded global limit (100 req/min)
- `getGlobalAttemptCount(String ipAddress)` - Get current attempt count
- `getGlobalRemainingAttempts(String ipAddress)` - Get remaining requests
- `getGlobalMapSize()` - Monitor active tracking entries

**Login-Specific Rate Limiting Methods**:
- `isLoginRateLimited(String ipAddress)` - Check if IP exceeded login limit (5 attempts/15 min)
- `getLoginAttemptCount(String ipAddress)` - Get current login attempt count
- `getLoginRemainingAttempts(String ipAddress)` - Get remaining login attempts
- `resetLoginRateLimit(String ipAddress)` - Reset login counter on successful login

**Configuration**:
```
MAX_GLOBAL_ATTEMPTS = 100 requests
GLOBAL_TIME_WINDOW_MS = 60,000 ms (1 minute)
MAX_LOGIN_ATTEMPTS = 5 attempts
LOGIN_TIME_WINDOW_MS = 900,000 ms (15 minutes)
```

### 2. GlobalRateLimitInterceptor
**Location**: `src/main/java/com/example/tempp/interceptor/GlobalRateLimitInterceptor.java`

Spring HandlerInterceptor that intercepts all API requests and enforces global rate limits.

**Behavior**:
- Runs before any controller method
- Checks if client IP has exceeded global rate limit
- Returns 429 (Too Many Requests) if limit exceeded
- Supports IP extraction from X-Forwarded-For and other proxy headers

### 3. WebMvcConfig
**Location**: `src/main/java/com/example/tempp/config/WebMvcConfig.java`

Spring WebMvcConfigurer that registers the global rate limit interceptor.

**Path Patterns**:
- Applies to: `/api/**`, `/user/**`, `/call/**`, `/payment/**`
- Excludes: `/index`, `/`, `/static/**`, `/favicon.ico`, `/health`, `/public/**`

### 4. UserController Enhancements
**Location**: `src/main/java/com/example/tempp/controller/api/UserController.java`

Three login endpoints now enforce login-specific rate limiting:

1. **POST /user/login**
   - Checks login rate limit
   - Returns 429 if exceeded
   - Resets counter on successful login

2. **POST /user/google-login**
   - Checks login rate limit
   - Returns 429 if exceeded
   - Resets counter on successful login

3. **POST /user/simple-login**
   - Checks login rate limit
   - Returns 429 if exceeded
   - Resets counter on successful login

## Flow Diagram

```
Client Request
     ↓
GlobalRateLimitInterceptor.preHandle()
     ↓
isGlobalRateLimited(IP)?
     ├─ YES → Return 429 Too Many Requests
     │
     └─ NO → Continue to Controller
           ↓
        /user/login, /user/google-login, /user/simple-login
           ↓
        isLoginRateLimited(IP)?
           ├─ YES → Return 429 Too Many Requests
           │
           └─ NO → Process login
                 ↓
              Success → resetLoginRateLimit(IP)
                 ↓
              Return 200 UserSession
```

## IP Detection

The system extracts client IP from:
1. `X-Forwarded-For` header (proxy)
2. `Proxy-Client-IP` header
3. `WL-Proxy-Client-IP` header
4. `HTTP_X_FORWARDED_FOR` header
5. `HTTP_CLIENT_IP` header
6. `REMOTE_ADDR` header (fallback)

This ensures correct rate limiting even behind load balancers and proxies.

## HTTP Status Codes

- **200 OK** - Request successful (under rate limit)
- **429 Too Many Requests** - Rate limit exceeded (global or login-specific)
- **400 Bad Request** - Invalid request
- **401 Unauthorized** - Authentication failed
- **403 Forbidden** - Blocked user
- **500 Internal Server Error** - Server error

## Response Format

When rate limited:
```json
{
  "error": "Rate limit exceeded. Please try again later."
}
```

Or for login endpoints:
```
HTTP/1.1 429 Too Many Requests
{
  "error": "Too many login attempts. Please try again later."
}
```

## Monitoring

Track rate limiting activity via logs:

**Global Rate Limit Logs**:
```
WARN - Global rate limit exceeded - IP: 192.168.1.1, attempts: 101/100
DEBUG - Approaching global rate limit - IP: 192.168.1.1, attempts: 81/100
```

**Login Rate Limit Logs**:
```
WARN - Login rate limit exceeded for IP: 192.168.1.1
WARN - Login rate limit check - IP: 192.168.1.1, attempts: 3/5
```

## Testing

### Test Global Rate Limit (100 req/min)
```bash
for i in {1..101}; do
  curl -s http://localhost:8080/user/me -H "X-Forwarded-For: 192.168.1.1" | grep -o "429\|error" && break || echo "Request $i OK"
done
```

### Test Login Rate Limit (5 attempts/15 min)
```bash
for i in {1..6}; do
  curl -s -X POST http://localhost:8080/user/login \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: 192.168.1.2" \
    -d '{"number":"9999999999","name":"Test"}' | grep -o "429\|Too many" && break
done
```

## Configuration

### Adjusting Limits

To modify rate limits, update constants in `RateLimitingService`:

```java
// Global limits
private static final int MAX_GLOBAL_ATTEMPTS = 100;  // Requests per minute
private static final long GLOBAL_TIME_WINDOW_MS = 60 * 1000;

// Login limits
private static final int MAX_LOGIN_ATTEMPTS = 5;  // Attempts per 15 min
private static final long LOGIN_TIME_WINDOW_MS = 15 * 60 * 1000;
```

### Changing Path Patterns

Edit `WebMvcConfig.addInterceptors()` to change which endpoints are rate limited:

```java
registry.addInterceptor(globalRateLimitInterceptor)
        .addPathPatterns("/api/**", "/user/**", "/call/**", "/payment/**")
        .excludePathPatterns("/static/**", "/public/**");
```

## Implementation Details

### Thread-Safe Design
- Uses `ConcurrentHashMap` for thread-safe concurrent access
- Uses `AtomicInteger` for atomic counter increments
- No synchronized blocks needed

### Memory Management
- Rate limit entries stored per IP address
- Old entries cleaned up when time window expires
- Suitable for small to medium deployments
- For high-traffic deployments, consider Redis-based rate limiting

### Performance Impact
- Minimal overhead per request (hash lookup + atomic increment)
- Interceptor runs before authentication/authorization checks
- Early rejection of rate-limited requests saves processing

## Future Enhancements

1. **Redis-Based Rate Limiting** - For distributed deployments
2. **Whitelist/Blacklist** - Exclude trusted IPs or block suspicious ones
3. **Dynamic Limits** - Adjust limits based on server load
4. **Prometheus Metrics** - Export rate limit stats for monitoring
5. **Admin API** - Reset/adjust rate limits per IP at runtime
6. **Rate Limit Headers** - Add `RateLimit-*` headers to responses
7. **Distributed Tracking** - Share rate limit state across instances

## Security Considerations

1. **Proxy Headers** - Trusts X-Forwarded-For headers (configure reverse proxy carefully)
2. **DDoS Protection** - This rate limiting is application-level; use WAF/firewall for DDoS
3. **Brute Force** - Login rate limit (5 attempts/15 min) prevents password brute force
4. **API Abuse** - Global rate limit (100 req/min) prevents API scraping/abuse

## Deployment Notes

- Rate limiting state is **NOT** shared across multiple instances
- Each instance maintains its own in-memory rate limit counters
- For multi-instance deployments, use Redis-based rate limiting
- Container restarts will reset all rate limit counters

## Files Modified/Created

1. **Created**: `src/main/java/com/example/tempp/service/RateLimitingService.java`
2. **Created**: `src/main/java/com/example/tempp/interceptor/GlobalRateLimitInterceptor.java`
3. **Created**: `src/main/java/com/example/tempp/config/WebMvcConfig.java`
4. **Modified**: `src/main/java/com/example/tempp/controller/api/UserController.java`
   - Added `RateLimitingService` dependency injection
   - Added login rate limiting to `/login`, `/google-login`, `/simple-login` endpoints

## Verification

Compile project:
```bash
mvn clean compile
```

Run tests:
```bash
mvn test
```

Build application:
```bash
mvn clean package
```

