package com.example.tempp.controller.api;

import com.example.tempp.exception.BlockedUserException;
import com.example.tempp.model.*;
import com.example.tempp.service.PaymentService;
import com.example.tempp.service.RateLimitingService;
import com.example.tempp.service.UserService;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for user-related operations.
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    /**
     * Session attribute key that stores the current user's Firestore document ID (String).
     */
    public static final String SESSION_USER_ID = "PHONEBOOTH_USER_ID";

    private final UserService userService;
    private final PaymentService paymentService;
    private final RateLimitingService rateLimitingService;

    @PostMapping("/login")
    public ResponseEntity<UserSession> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpSession session) {
        try {
            String clientIp = getClientIpAddress(httpRequest);
            if (rateLimitingService.isLoginRateLimited(clientIp)) {
                log.warn("Login rate limit exceeded for IP: {}", clientIp);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again later.");
            }

            String deviceIdentifier = resolveDeviceIdentifier(request, httpRequest);
            request.setDeviceIdentifier(deviceIdentifier);

            UserSession userSession = userService.login(request);
            session.setAttribute(SESSION_USER_ID, userSession.getId());
            rateLimitingService.resetLoginRateLimit(clientIp);
            return ResponseEntity.ok(userSession);
        } catch (BlockedUserException ex) {
            log.warn("Blocked user login attempt: number={}, message={}", request.getNumber(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest, HttpSession session) {
        try {
            String clientIp = getClientIpAddress(httpRequest);
            if (rateLimitingService.isLoginRateLimited(clientIp)) {
                log.warn("Login rate limit exceeded for IP: {}", clientIp);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again later.");
            }

            if (request.getDeviceIdentifier() == null || request.getDeviceIdentifier().isBlank()) {
                request.setDeviceIdentifier(getClientIpAddress(httpRequest));
            }
            UserSession userSession = userService.googleLogin(request);
            session.setAttribute(SESSION_USER_ID, userSession.getId());
            rateLimitingService.resetLoginRateLimit(clientIp);
            return ResponseEntity.ok(userSession);
        } catch (BlockedUserException ex) {
            log.warn("Blocked user Google login: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (FirebaseAuthException ex) {
            log.warn("Firebase token verification failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Google sign-in token. Please sign in again.", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * Simple login endpoint – used when the {@code enableGoogleSignIn} feature flag is {@code false}.
     * Creates or retrieves a user account using only a mobile number; no Google authentication.
     */
    @PostMapping("/simple-login")
    public ResponseEntity<?> simpleLogin(@RequestBody com.example.tempp.model.SimpleLoginRequest request, HttpServletRequest httpRequest, HttpSession session) {
        try {
            String clientIp = getClientIpAddress(httpRequest);
            if (rateLimitingService.isLoginRateLimited(clientIp)) {
                log.warn("Login rate limit exceeded for IP: {}", clientIp);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again later.");
            }

            if (request.getDeviceIdentifier() == null || request.getDeviceIdentifier().isBlank()) {
                request.setDeviceIdentifier(getClientIpAddress(httpRequest));
            }
            UserSession userSession = userService.simpleLogin(request);
            session.setAttribute(SESSION_USER_ID, userSession.getId());
            rateLimitingService.resetLoginRateLimit(clientIp);
            return ResponseEntity.ok(userSession);
        } catch (BlockedUserException ex) {
            log.warn("Blocked user simple-login: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileRequest request, HttpSession session) {
        try {
            UserSession userSession = userService.updateProfile(requiredUserId(session), request);
            return ResponseEntity.ok(userSession);
        } catch (BlockedUserException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserSession> currentUser(HttpSession session) {
        return ResponseEntity.ok(userService.getCurrentSession(requiredUserId(session)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/token")
    public ResponseEntity<User> getUserToken(HttpSession session) {
        try {
            User user = userService.createToken(requiredUserId(session));
            return ResponseEntity.ok(user);
        } catch (BlockedUserException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> wallet(HttpSession session) {
        return ResponseEntity.ok(userService.getWallet(requiredUserId(session)));
    }

    @PostMapping("/wallet/top-up")
    public ResponseEntity<Map<String, String>> topUpWallet() {
        return ResponseEntity.status(HttpStatus.GONE).body(createErrorResponse("Demo wallet top-up is disabled. Please use Razorpay payment gateway."));
    }

    @PostMapping("/wallet/payment/order")
    public ResponseEntity<?> createPaymentOrder(@RequestBody PaymentOrderRequest request, HttpSession session) {
        try {
            return ResponseEntity.ok(paymentService.createOrder(requiredUserId(session), request));
        } catch (IllegalArgumentException ex) {
            log.warn("Payment order creation failed with bad request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage()));
        } catch (IllegalStateException ex) {
            log.warn("Payment order creation failed - service unavailable: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(createErrorResponse(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error creating payment order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("An unexpected error occurred. Please try again later."));
        }
    }

    @PostMapping("/wallet/payment/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentVerifyRequest request, HttpSession session) {
        try {
            return ResponseEntity.ok(paymentService.verifyPayment(requiredUserId(session), request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(createErrorResponse(ex.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<CallHistoryResponse>> history(HttpSession session) {
        return ResponseEntity.ok(userService.getCurrentUserHistory(requiredUserId(session)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns the String userId stored in the session, or throws 401.
     */
    private String requiredUserId(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId instanceof String id && !id.isBlank()) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first");
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        response.put("message", message);
        return response;
    }

    private String resolveDeviceIdentifier(LoginRequest request, HttpServletRequest httpRequest) {
        if (request.getDeviceIdentifier() != null && !request.getDeviceIdentifier().isBlank()) {
            return request.getDeviceIdentifier().trim();
        }
        String clientIp = getClientIpAddress(httpRequest);
        return clientIp != null ? clientIp : "unknown";
    }

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
