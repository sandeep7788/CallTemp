package com.example.tempp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global in-memory rate limiting service.
 * Tracks request attempts per IP address and enforces rate limits.
 * Supports both global API rate limiting and specific endpoint rate limiting (e.g., login).
 */
@Slf4j
@Service
public class RateLimitingService {

    /**
     * Maximum global API requests per window (default: 100 requests per minute).
     */
    private static final int MAX_GLOBAL_ATTEMPTS = 150;

    /**
     * Global time window in milliseconds (default: 1 minute = 60,000 ms).
     */
    private static final long GLOBAL_TIME_WINDOW_MS = 60 * 1000;

    /**
     * Maximum login/signup attempts per window (default: 5 attempts).
     */
    private static final int MAX_LOGIN_ATTEMPTS = 7;

    /**
     * Login time window in milliseconds (default: 15 minutes = 900,000 ms).
     */
    private static final long LOGIN_TIME_WINDOW_MS = 15 * 60 * 1000;

    /**
     * Rate limit entry: tracks attempt count and window start time.
     */
    private static class RateLimitEntry {
        AtomicInteger attemptCount = new AtomicInteger(0);
        long windowStart;

        RateLimitEntry() {
            this.windowStart = System.currentTimeMillis();
        }
    }

    /**
     * In-memory map to track global rate limit entries per IP address.
     */
    private final ConcurrentHashMap<String, RateLimitEntry> globalRateLimitMap = new ConcurrentHashMap<>();

    /**
     * In-memory map to track login-specific rate limit entries per IP address.
     */
    private final ConcurrentHashMap<String, RateLimitEntry> loginRateLimitMap = new ConcurrentHashMap<>();

    // ─── Global Rate Limiting ────────────────────────────────────────────────

    /**
     * Check if the given IP address exceeds the global rate limit.
     * Applies to all API requests.
     *
     * @param ipAddress client IP address
     * @return true if rate limited, false if under limit
     */
    public boolean isGlobalRateLimited(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "unknown";
        }

        long currentTime = System.currentTimeMillis();

        // Get or create entry for this IP
        RateLimitEntry entry = globalRateLimitMap.computeIfAbsent(ipAddress, k -> new RateLimitEntry());

        // Check if we're still in the same time window
        if (currentTime - entry.windowStart > GLOBAL_TIME_WINDOW_MS) {
            // Reset the window
            entry.attemptCount.set(0);
            entry.windowStart = currentTime;
        }

        // Increment attempt count
        int attempts = entry.attemptCount.incrementAndGet();

        // Log if approaching or exceeding limit
        if (attempts > MAX_GLOBAL_ATTEMPTS) {
            log.warn("Global rate limit exceeded - IP: {}, attempts: {}/{}", ipAddress, attempts, MAX_GLOBAL_ATTEMPTS);
            return true;
        } else if (attempts > (MAX_GLOBAL_ATTEMPTS * 0.8)) {
            log.debug("Approaching global rate limit - IP: {}, attempts: {}/{}", ipAddress, attempts, MAX_GLOBAL_ATTEMPTS);
        }

        return false;
    }

    /**
     * Get global attempt count for an IP (for debugging/monitoring).
     *
     * @param ipAddress client IP address
     * @return current attempt count, or 0 if not found
     */
    public int getGlobalAttemptCount(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "unknown";
        }
        RateLimitEntry entry = globalRateLimitMap.get(ipAddress);
        if (entry != null && System.currentTimeMillis() - entry.windowStart <= GLOBAL_TIME_WINDOW_MS) {
            return entry.attemptCount.get();
        }
        return 0;
    }

    /**
     * Get global remaining attempts before rate limit for an IP.
     *
     * @param ipAddress client IP address
     * @return remaining attempts, or MAX_GLOBAL_ATTEMPTS if not found
     */
    public int getGlobalRemainingAttempts(String ipAddress) {
        int attempts = getGlobalAttemptCount(ipAddress);
        return Math.max(0, MAX_GLOBAL_ATTEMPTS - attempts);
    }

    // ─── Login-Specific Rate Limiting ───────────────────────────────────────

    /**
     * Check if the given IP address is rate limited for login attempts.
     * Returns true if the limit is exceeded, false otherwise.
     *
     * @param ipAddress client IP address
     * @return true if rate limited, false if under limit
     */
    public boolean isLoginRateLimited(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "unknown";
        }

        long currentTime = System.currentTimeMillis();

        // Get or create entry for this IP
        RateLimitEntry entry = loginRateLimitMap.computeIfAbsent(ipAddress, k -> new RateLimitEntry());

        // Check if we're still in the same time window
        if (currentTime - entry.windowStart > LOGIN_TIME_WINDOW_MS) {
            // Reset the window
            entry.attemptCount.set(0);
            entry.windowStart = currentTime;
        }

        // Increment attempt count
        int attempts = entry.attemptCount.incrementAndGet();

        // Log attempt
        if (attempts > 1) {
            log.warn("Login rate limit check - IP: {}, attempts: {}/{}", ipAddress, attempts, MAX_LOGIN_ATTEMPTS);
        }

        return attempts > MAX_LOGIN_ATTEMPTS;
    }

    /**
     * Reset rate limit counter for login attempts (e.g., after successful login).
     *
     * @param ipAddress client IP address
     */
    public void resetLoginRateLimit(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "unknown";
        }
        loginRateLimitMap.remove(ipAddress);
        log.debug("Login rate limit reset for IP: {}", ipAddress);
    }

    /**
     * Get login attempt count for an IP (for debugging/monitoring).
     *
     * @param ipAddress client IP address
     * @return current attempt count, or 0 if not found
     */
    public int getLoginAttemptCount(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = "unknown";
        }
        RateLimitEntry entry = loginRateLimitMap.get(ipAddress);
        if (entry != null && System.currentTimeMillis() - entry.windowStart <= LOGIN_TIME_WINDOW_MS) {
            return entry.attemptCount.get();
        }
        return 0;
    }

    /**
     * Get remaining login attempts before rate limit for an IP.
     *
     * @param ipAddress client IP address
     * @return remaining attempts, or MAX_LOGIN_ATTEMPTS if not found
     */
    public int getLoginRemainingAttempts(String ipAddress) {
        int attempts = getLoginAttemptCount(ipAddress);
        return Math.max(0, MAX_LOGIN_ATTEMPTS - attempts);
    }

    // ─── Cleanup ────────────────────────────────────────────────────────────

    /**
     * Clear all rate limit data (useful for testing or admin operations).
     */
    public void clearAllRateLimits() {
        globalRateLimitMap.clear();
        loginRateLimitMap.clear();
        log.info("All rate limits cleared");
    }

    /**
     * Get size of global rate limit map (for monitoring).
     */
    public int getGlobalMapSize() {
        return globalRateLimitMap.size();
    }

    /**
     * Get size of login rate limit map (for monitoring).
     */
    public int getLoginMapSize() {
        return loginRateLimitMap.size();
    }
}

