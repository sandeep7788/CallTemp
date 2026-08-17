package com.example.tempp.interceptor;

import com.example.tempp.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Global rate limiting interceptor for all API requests.
 * Applies rate limit of 100 requests per minute per IP address.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    /**
     * Pre-handle: Check global rate limit before processing request.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIpAddress(request);

        // Check if globally rate limited
        if (rateLimitingService.isGlobalRateLimited(clientIp)) {
            log.warn("Global rate limit exceeded for IP: {}, remaining attempts: {}",
                clientIp, rateLimitingService.getGlobalRemainingAttempts(clientIp));

            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
            } catch (Exception e) {
                log.error("Error writing rate limit response", e);
            }
            return false;
        }

        return true;
    }

    /**
     * Extract client IP address from request, checking X-Forwarded-For headers first.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", "HTTP_CLIENT_IP", "REMOTE_ADDR"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.contains(",") ? ip.split(",")[0].trim() : ip;
            }
        }
        return request.getRemoteAddr();
    }
}


