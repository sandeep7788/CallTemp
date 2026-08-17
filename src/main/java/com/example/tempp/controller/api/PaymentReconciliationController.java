package com.example.tempp.controller.api;

import com.example.tempp.model.PaymentRecord;
import com.example.tempp.model.PaymentVerifyResponse;
import com.example.tempp.model.UserAccount;
import com.example.tempp.model.WalletTransaction;
import com.example.tempp.repository.PaymentRecordRepository;
import com.example.tempp.repository.UserAccountRepository;
import com.example.tempp.repository.WalletTransactionRepository;
import com.example.tempp.service.UserService;
import com.example.tempp.service.payment.CashfreePaymentService;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment Reconciliation Controller
 * Handles stuck payments where verification succeeded but wallet credit failed
 */
@Slf4j
@RestController
@RequestMapping("/admin/payment/reconcile")
@RequiredArgsConstructor
public class PaymentReconciliationController {

    private final PaymentRecordRepository paymentRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CashfreePaymentService cashfreePaymentService;
    private final Firestore firestore;

    /**
     * Helper method to get order ID from payment record based on gateway
     */
    private String getOrderId(PaymentRecord record) {
        if ("cashfree".equals(record.getGateway())) {
            return record.getCashfreeOrderId();
        }
        return record.getRazorpayOrderId();
    }

    private void requireReconcileKey(String providedKey) {
        String expectedKey = System.getenv("PAYMENT_RECONCILE_KEY");
        if (expectedKey == null || expectedKey.isBlank()) {
            throw new IllegalStateException("Payment reconciliation is not configured. Set PAYMENT_RECONCILE_KEY first.");
        }
        if (providedKey == null || !providedKey.equals(expectedKey)) {
            throw new IllegalArgumentException("Invalid reconciliation key");
        }
    }

    /**
     * Get list of stuck payments that need manual reconciliation
     */
    @GetMapping("/stuck")
    public ResponseEntity<?> getStuckPayments(@RequestHeader(value = "X-Reconcile-Key", required = false) String reconcileKey) {
        try {
            requireReconcileKey(reconcileKey);
            List<PaymentRecord> stuckPayments = paymentRecordRepository.findByStatus("VERIFIED_NOT_CREDITED");
            
            Map<String, Object> response = new HashMap<>();
            response.put("count", stuckPayments.size());
            response.put("payments", stuckPayments);
            response.put("message", "Found " + stuckPayments.size() + " stuck payments requiring reconciliation");
            
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Error fetching stuck payments", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch stuck payments: " + ex.getMessage()));
        }
    }

    /**
     * Manually reconcile a specific payment
     * This will re-verify with Cashfree and credit the wallet
     */
    @PostMapping("/fix/{orderId}")
    public ResponseEntity<?> reconcilePayment(@PathVariable String orderId, @RequestHeader(value = "X-Reconcile-Key", required = false) String reconcileKey) {
        try {
            requireReconcileKey(reconcileKey);
            log.info("🔧 Starting manual reconciliation for order: {}", orderId);
            
            // Find the payment record
            PaymentRecord record = paymentRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment order not found: " + orderId));
            
            String userId = record.getUserId();
            String gateway = record.getGateway();
            
            log.info("Payment record found: userId={}, gateway={}, status={}, amount=₹{}", 
                     userId, gateway, record.getStatus(), record.getAmount());
            
            // Only process Cashfree payments
            if (!"cashfree".equals(gateway)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only Cashfree payments can be reconciled via this endpoint"));
            }
            
            // Check if already paid
            if ("PAID".equals(record.getStatus())) {
                UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
                return ResponseEntity.ok(Map.of(
                    "status", "already_processed",
                    "message", "Payment was already processed successfully",
                    "walletBalance", user.getWalletBalance()
                ));
            }

            if (walletTransactionRepository.existsByReferenceId(userId, orderId)) {
                record.setStatus("PAID");
                record.setPaidAt(Instant.now());
                paymentRecordRepository.save(record);
                UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
                return ResponseEntity.ok(Map.of(
                    "status", "already_credited",
                    "message", "Wallet credit already exists. Payment record was marked as PAID.",
                    "walletBalance", user.getWalletBalance()
                ));
            }
            
            // Re-verify with Cashfree
            log.info("Re-verifying payment with Cashfree gateway...");
            PaymentVerifyResponse gatewayResponse = cashfreePaymentService.verifyPayment(orderId, "", null);
            
            if (!gatewayResponse.isVerified()) {
                log.warn("Cashfree verification failed during reconciliation: {}", gatewayResponse.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "verification_failed",
                    "error", "Payment verification failed: " + gatewayResponse.getMessage()
                ));
            }
            
            log.info("✅ Cashfree verification successful, proceeding to credit wallet");
            
            // Credit wallet atomically
            final double creditAmount = record.getAmount();
            UserAccount saved = userAccountRepository.runTransaction(userId, (user, tx, ref) -> {
                double before = user.getWalletBalance();
                double after = UserService.round2(before + creditAmount);
                user.setWalletBalance(after);
                
                WalletTransaction walletTx = new WalletTransaction(
                    userId,
                    "CREDIT",
                    creditAmount,
                    before,
                    after,
                    "Cashfree payment reconciled – order: " + orderId,
                    orderId
                );
                walletTransactionRepository.saveInTransaction(tx, walletTx, firestore);
                
                log.info("Wallet transaction created: before=₹{}, after=₹{}, credit=₹{}", before, after, creditAmount);
                return user;
            });
            
            // Update payment record
            record.setStatus("PAID");
            record.setPaidAt(Instant.now());
            paymentRecordRepository.save(record);
            
            log.info("✅ Payment reconciliation successful: orderId={}, userId={}, amount=₹{}, newBalance=₹{}", 
                     orderId, userId, creditAmount, saved.getWalletBalance());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Payment reconciled successfully");
            response.put("orderId", orderId);
            response.put("userId", userId);
            response.put("amountCredited", creditAmount);
            response.put("newWalletBalance", saved.getWalletBalance());
            response.put("reconciledAt", Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException ex) {
            log.error("Validation error during reconciliation: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error during payment reconciliation: orderId={}", orderId, ex);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Reconciliation failed: " + ex.getMessage(),
                "orderId", orderId
            ));
        }
    }

    /**
     * Bulk reconciliation - attempts to fix all stuck payments
     */
    @PostMapping("/fix-all")
    public ResponseEntity<?> reconcileAllStuckPayments(@RequestHeader(value = "X-Reconcile-Key", required = false) String reconcileKey) {
        try {
            requireReconcileKey(reconcileKey);
            log.info("🔧 Starting bulk reconciliation of stuck payments");
            
            List<PaymentRecord> stuckPayments = paymentRecordRepository.findByStatus("VERIFIED_NOT_CREDITED");
            
            int successCount = 0;
            int failCount = 0;
            Map<String, String> results = new HashMap<>();
            
            for (PaymentRecord record : stuckPayments) {
                String orderId = getOrderId(record);
                try {
                    log.info("Processing stuck payment: orderId={}", orderId);
                    reconcilePayment(orderId, reconcileKey);
                    successCount++;
                    results.put(orderId, "SUCCESS");
                } catch (Exception ex) {
                    failCount++;
                    results.put(orderId, "FAILED: " + ex.getMessage());
                    log.error("Failed to reconcile order: {}", orderId, ex);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalProcessed", stuckPayments.size());
            response.put("successCount", successCount);
            response.put("failCount", failCount);
            response.put("results", results);
            response.put("message", String.format("Bulk reconciliation complete: %d succeeded, %d failed", successCount, failCount));
            
            log.info("✅ Bulk reconciliation complete: total={}, success={}, fail={}", stuckPayments.size(), successCount, failCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            log.error("Error during bulk reconciliation", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "Bulk reconciliation failed: " + ex.getMessage()));
        }
    }

    /**
     * Check a specific payment status from Cashfree (diagnostic endpoint)
     */
    @GetMapping("/check/{orderId}")
    public ResponseEntity<?> checkPaymentStatus(@PathVariable String orderId, @RequestHeader(value = "X-Reconcile-Key", required = false) String reconcileKey) {
        try {
            requireReconcileKey(reconcileKey);
            log.info("Checking payment status with Cashfree: orderId={}", orderId);
            
            PaymentRecord record = paymentRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment order not found: " + orderId));
            
            if (!"cashfree".equals(record.getGateway())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only Cashfree payments supported"));
            }
            
            PaymentVerifyResponse gatewayResponse = cashfreePaymentService.verifyPayment(orderId, "", null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("userId", record.getUserId());
            response.put("amount", record.getAmount());
            response.put("localStatus", record.getStatus());
            response.put("cashfreeVerified", gatewayResponse.isVerified());
            response.put("cashfreeMessage", gatewayResponse.getMessage());
            response.put("needsReconciliation", gatewayResponse.isVerified() && !"PAID".equals(record.getStatus()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            log.error("Error checking payment status: orderId={}", orderId, ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to check payment status: " + ex.getMessage()));
        }
    }
}

