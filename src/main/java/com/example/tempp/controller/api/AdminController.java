package com.example.tempp.controller.api;

import com.example.tempp.model.UserAccount;
import com.example.tempp.model.WalletTransaction;
import com.example.tempp.service.AdminService;
import com.example.tempp.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.tempp.controller.api.UserController.SESSION_USER_ID;

/**
 * REST API for the admin panel (user, wallet and transaction management, plus
 * a dashboard summary). Every endpoint here is gated by {@code AdminAuthInterceptor}
 * (requires a signed-in, non-blocked user with {@code isAdmin=true}).
 */
@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserAccount> me(HttpSession session) {
        return ResponseEntity.ok(userService.getUserById(requiredUserId(session)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<UserAccount>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return ResponseEntity.ok(adminService.listUsers(search, resolveBlockedFilter(status), limit));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserDetail(@PathVariable String id) {
        try {
            return ResponseEntity.ok(adminService.getUserDetail(id));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<?> blockUser(@PathVariable String id, HttpSession session) {
        try {
            return ResponseEntity.ok(adminService.blockUser(id, requiredUserId(session)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse(ex.getMessage()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/users/{id}/unblock")
    public ResponseEntity<?> unblockUser(@PathVariable String id, HttpSession session) {
        try {
            return ResponseEntity.ok(adminService.unblockUser(id, requiredUserId(session)));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    // ─── Wallets ─────────────────────────────────────────────────────────────

    @GetMapping("/wallets")
    public ResponseEntity<List<UserAccount>> listWallets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return ResponseEntity.ok(adminService.listWallets(search, limit));
    }

    // ─── Transactions ────────────────────────────────────────────────────────

    @GetMapping("/transactions")
    public ResponseEntity<?> listTransactions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        try {
            Instant fromInstant = parseInstant(from, false);
            Instant toInstant = parseInstant(to, true);
            return ResponseEntity.ok(adminService.listTransactions(userId, type, fromInstant, toInstant, limit));
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse("Invalid date format. Use YYYY-MM-DD or an ISO-8601 timestamp."));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Boolean resolveBlockedFilter(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        if ("blocked".equalsIgnoreCase(status)) {
            return true;
        }
        if ("active".equalsIgnoreCase(status)) {
            return false;
        }
        return null;
    }

    private Instant parseInstant(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            LocalDate date = LocalDate.parse(trimmed);
            return endOfDay
                    ? date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
                    : date.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

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
}
