package com.example.tempp.service;

import com.example.tempp.model.*;
import com.example.tempp.repository.PaymentRecordRepository;
import com.example.tempp.repository.UserAccountRepository;
import com.example.tempp.repository.WalletTransactionRepository;
import com.example.tempp.service.payment.CashfreePaymentService;
import com.google.cloud.firestore.Firestore;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles Razorpay payment creation and verification.
 * On successful verification the user's wallet is credited atomically in Firestore.
 */
@Slf4j
@Service
public class PaymentService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";

    private final UserAccountRepository userAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final AppConfigService appConfigService;
    private final CashfreePaymentService cashfreePaymentService;
    private final Firestore firestore;


    public PaymentService(UserAccountRepository userAccountRepository, WalletTransactionRepository walletTransactionRepository, PaymentRecordRepository paymentRecordRepository, AppConfigService appConfigService, CashfreePaymentService cashfreePaymentService, Firestore firestore) {
        this.userAccountRepository = userAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.appConfigService = appConfigService;
        this.cashfreePaymentService = cashfreePaymentService;
        this.firestore = firestore;
    }

    // ─── Create Order ─────────────────────────────────────────────────────────

    public PaymentOrderResponse createOrder(String userId, PaymentOrderRequest request) {
        String gateway = resolveGateway(request == null ? null : request.getGateway());
        double amount = normalizeAndValidateAmount(request == null ? -1 : request.getAmount());
        UserAccount user = getUser(userId);

        int amountPaise = toPaise(amount);
        String receipt = "wallet_" + userId.substring(0, Math.min(8, userId.length())) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);

        if ("cashfree".equals(gateway)) {
            return createCashfreeOrder(userId, user, amount, amountPaise, receipt);
        }

        validateRazorpayConfig();

        try {
            // Use AppConfig values when present; fall back to environment variables for local/test runs.
            String keyId = appConfigService.getRazorpayKeyId();
            String keySecret = appConfigService.getRazorpayKeySecret();
            if (keyId == null || keyId.isBlank() || keyId.contains("replace_with")) {

                if (envKeyId != null && !envKeyId.isBlank()) keyId = envKeyId.trim();
            }
            if (keySecret == null || keySecret.isBlank() || keySecret.contains("replace_with")) {
                if (envKeySecret != null && !envKeySecret.isBlank()) keySecret = envKeySecret.trim();
            }
            String currency = appConfigService.getRazorpayCurrency();
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);
            orderRequest.put("payment_capture", 1);

            Order order = client.orders.create(orderRequest);
            String orderId = order.get("id");

            PaymentRecord record = new PaymentRecord();
            record.setGateway("razorpay");
            record.setUserId(userId);
            record.setRazorpayOrderId(orderId);
            record.setAmount(amount);
            record.setAmountPaise(amountPaise);
            record.setCurrency(currency);
            record.setReceipt(receipt);
            record.setStatus(STATUS_CREATED);
            paymentRecordRepository.save(record);

            return new PaymentOrderResponse(keyId, orderId, currency, amount, amountPaise, "MakeCall Wallet", "Wallet recharge ₹" + String.format("%.2f", amount), user.getName(), user.getNumber());
        } catch (Exception ex) {
            log.error("Unable to create Razorpay order", ex);
            throw new IllegalStateException("Unable to start payment. Please try again later.");
        }
    }

    // ─── Verify Payment ──────────────────────────────────────────────────────

    public PaymentVerifyResponse verifyPayment(String userId, PaymentVerifyRequest request) {
        String gateway = resolveVerifyGateway(request);
        if ("cashfree".equals(gateway)) {
            return verifyCashfreePayment(userId, request);
        }

        validateRazorpayVerifyRequest(request);

        PaymentRecord record = paymentRecordRepository.findByOrderIdAndUserId(request.getRazorpayOrderId().trim(), userId).orElseThrow(() -> new IllegalArgumentException("Payment order was not found for this user"));

        UserAccount currentUser = getUser(userId);

        // Idempotent: already paid
        if (STATUS_PAID.equals(record.getStatus())) {
            WalletResponse wallet = new WalletResponse(currentUser.getWalletBalance(), appConfigService.getWalletMinimumBalance(), appConfigService.getCallRatePerMinute());
            return new PaymentVerifyResponse(true, wallet, "Payment was already verified");
        }

        // Prevent duplicate payment IDs
        if (paymentRecordRepository.existsByPaymentId(request.getRazorpayPaymentId().trim())) {
            throw new IllegalArgumentException("This payment has already been used");
        }

        // Signature verification
        boolean valid = verifySignature(request.getRazorpayOrderId().trim(), request.getRazorpayPaymentId().trim(), request.getRazorpaySignature().trim());

        if (!valid) {
            record.setStatus(STATUS_FAILED);
            record.setFailureReason("Invalid Razorpay payment signature");
            paymentRecordRepository.save(record);
            throw new IllegalArgumentException("Payment verification failed. Wallet was not credited.");
        }

        final double creditAmount = record.getAmount();
        final String orderId = record.getRazorpayOrderId();

        // Atomic credit via Firestore transaction
        UserAccount saved = userAccountRepository.runTransaction(userId, (user, tx, ref) -> {
            double before = user.getWalletBalance();
            double after = UserService.round2(before + creditAmount);
            user.setWalletBalance(after);

            WalletTransaction walletTx = new WalletTransaction(userId, "CREDIT", creditAmount, before, after, "Razorpay payment verified – order: " + orderId, orderId);
            walletTransactionRepository.saveInTransaction(tx, walletTx, firestore);
            return user;
        });

        // Update payment record
        record.setRazorpayPaymentId(request.getRazorpayPaymentId().trim());
        record.setRazorpaySignature(request.getRazorpaySignature().trim());
        record.setStatus(STATUS_PAID);
        record.setPaidAt(Instant.now());
        paymentRecordRepository.save(record);

        log.info("Wallet recharged – userId={}, orderId={}, amount=₹{}, newBalance=₹{}", userId, orderId, creditAmount, saved.getWalletBalance());

        WalletResponse wallet = new WalletResponse(saved.getWalletBalance(), appConfigService.getWalletMinimumBalance(), appConfigService.getCallRatePerMinute());
        return new PaymentVerifyResponse(true, wallet, "Payment verified and wallet credited successfully");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private PaymentOrderResponse createCashfreeOrder(String userId, UserAccount user, double amount, int amountPaise, String receipt) {
        if (!cashfreePaymentService.isEnabled()) {
            throw new IllegalStateException("Cashfree payment gateway is not enabled or configured.");
        }
        
        // Validate that user has a phone number (required by Cashfree)
        if (user.getNumber() == null || user.getNumber().isBlank()) {
            throw new IllegalArgumentException("Phone number is required for Cashfree payments. Please update your profile with a valid phone number.");
        }
        
        try {
            // Pass customer details to Cashfree (phone is required by Cashfree API)
            PaymentOrderResponse response = cashfreePaymentService.createPaymentOrder(
                amount, 
                userId, 
                receipt,
                user.getNumber(),  // Customer phone - required
                user.getName(),    // Customer name - optional
                user.getEmail()    // Customer email - optional
            );
            PaymentRecord record = new PaymentRecord();
            record.setGateway("cashfree");
            record.setUserId(userId);
            record.setCashfreeOrderId(response.getOrderId());
            record.setAmount(amount);
            record.setAmountPaise(amountPaise);
            record.setCurrency(response.getCurrency());
            record.setReceipt(receipt);
            record.setStatus(STATUS_CREATED);
            paymentRecordRepository.save(record);
            return new PaymentOrderResponse(response.getKeyId(), response.getOrderId(), response.getCurrency(), response.getAmount(), response.getAmountPaise(), "MakeCall Wallet", "Wallet recharge ₹" + String.format("%.2f", amount), user.getName(), user.getNumber(), "cashfree", response.getPaymentSessionId(), response.getGatewayMode());
        } catch (IllegalArgumentException ex) {
            // Re-throw validation exceptions as-is
            throw ex;
        } catch (Exception ex) {
            log.error("Unable to create Cashfree order", ex);
            throw new IllegalStateException("Unable to start Cashfree payment. Please try again later.");
        }
    }

    private PaymentVerifyResponse verifyCashfreePayment(String userId, PaymentVerifyRequest request) {
        validateCashfreeVerifyRequest(request);
        String orderId = request.getCashfreeOrderId().trim();
        
        log.info("Starting Cashfree payment verification – userId={}, orderId={}", userId, orderId);
        
        PaymentRecord record = paymentRecordRepository.findByOrderIdAndUserId(orderId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Payment order was not found for this user"));
        UserAccount currentUser = getUser(userId);

        // Idempotency: Return success if already processed
        if (STATUS_PAID.equals(record.getStatus())) {
            log.info("Payment already verified and credited – userId={}, orderId={}, amount=₹{}", userId, orderId, record.getAmount());
            WalletResponse wallet = new WalletResponse(currentUser.getWalletBalance(), appConfigService.getWalletMinimumBalance(), appConfigService.getCallRatePerMinute());
            return new PaymentVerifyResponse(true, wallet, "Payment was already verified and wallet credited");
        }

        String paymentId = request.getCashfreePaymentId() == null ? "" : request.getCashfreePaymentId().trim();
        
        // Prevent duplicate payment ID usage
        if (!paymentId.isBlank() && paymentRecordRepository.existsByPaymentId(paymentId)) {
            log.warn("Duplicate payment ID detected – userId={}, orderId={}, paymentId={}", userId, orderId, paymentId);
            throw new IllegalArgumentException("This payment has already been used");
        }

        int maxRetries = 3;
        int retryCount = 0;
        while (true) {
            try {
                log.info("Verifying with Cashfree gateway (attempt {}/{}) – orderId={}", retryCount + 1, maxRetries, orderId);
                PaymentVerifyResponse gatewayResponse = cashfreePaymentService.verifyPayment(orderId, paymentId, null);
                
                if (!gatewayResponse.isVerified()) {
                    log.warn("Cashfree payment verification failed – userId={}, orderId={}, reason={}", userId, orderId, gatewayResponse.getMessage());
                    record.setStatus(STATUS_FAILED);
                    record.setFailureReason(gatewayResponse.getMessage());
                    paymentRecordRepository.save(record);
                    throw new IllegalArgumentException("Payment verification failed: " + gatewayResponse.getMessage());
                }
                
                log.info("Cashfree gateway verification successful – orderId={}, paymentId={}", orderId, paymentId);
                break; // Success, exit retry loop
                
            } catch (IllegalArgumentException ex) {
                throw ex; // Don't retry validation errors
            } catch (Exception ex) {
                retryCount++;
                log.error("Cashfree verification attempt {}/{} failed – orderId={}, error={}", retryCount, maxRetries, orderId, ex.getMessage());
                
                if (retryCount < maxRetries) {
                    try {
                        // Exponential backoff: 1s, 2s, 4s
                        long waitMs = (long) (Math.pow(2, retryCount - 1) * 1000);
                        log.info("Retrying after {}ms...", waitMs);
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Payment verification interrupted");
                    }
                } else {
                    log.error("All Cashfree verification attempts failed – orderId={}", orderId, ex);
                    throw new IllegalStateException("Unable to verify Cashfree payment after " + maxRetries + " attempts. Please contact support with order ID: " + orderId);
                }
            }
        }

        // Step 2: Credit wallet, create ledger entry, and mark payment PAID in ONE transaction.
        // This makes retries/concurrent verify calls idempotent and prevents duplicate wallet credits.
        final double creditAmount = record.getAmount();
        final String storedPaymentId = paymentId.isBlank() ? orderId : paymentId;
        UserAccount saved = null;
        AtomicBoolean alreadyCredited = new AtomicBoolean(false);
        retryCount = 0;

        while (saved == null) {
            try {
                log.info("Crediting wallet atomically (attempt {}/{}) – userId={}, orderId={}, amount=₹{}", retryCount + 1, maxRetries, userId, orderId, creditAmount);

                saved = userAccountRepository.runTransaction(userId, (user, tx, ref) -> {
                    var paymentRef = firestore.collection("payment_records").document(orderId);
                    var paymentSnap = tx.get(paymentRef).get();
                    if (!paymentSnap.exists()) {
                        throw new IllegalStateException("Payment order was not found during wallet credit: " + orderId);
                    }
                    String txUserId = paymentSnap.getString("userId");
                    if (!userId.equals(txUserId)) {
                        throw new IllegalStateException("Payment order does not belong to this user: " + orderId);
                    }
                    String txStatus = paymentSnap.getString("status");
                    if (STATUS_PAID.equals(txStatus)) {
                        alreadyCredited.set(true);
                        log.info("Payment became PAID before transaction credit – userId={}, orderId={}", userId, orderId);
                        return user;
                    }

                    double before = user.getWalletBalance();
                    double after = UserService.round2(before + creditAmount);
                    user.setWalletBalance(after);

                    WalletTransaction walletTx = new WalletTransaction(
                        userId, 
                        "CREDIT", 
                        creditAmount, 
                        before, 
                        after, 
                        "Cashfree payment verified – order: " + orderId, 
                        orderId
                    );
                    walletTransactionRepository.saveInTransaction(tx, walletTx, firestore);

                    tx.update(paymentRef,
                            "cashfreePaymentId", storedPaymentId,
                            "status", STATUS_PAID,
                            "failureReason", null,
                            "paidAt", Date.from(Instant.now()),
                            "updatedAt", Date.from(Instant.now()));

                    log.info("Wallet and payment record updated in one transaction – userId={}, before=₹{}, after=₹{}, credit=₹{}", userId, before, after, creditAmount);
                    return user;
                });

                log.info("Wallet credit transaction completed – userId={}, newBalance=₹{}", userId, saved.getWalletBalance());

            } catch (Exception ex) {
                retryCount++;
                log.error("Wallet credit attempt {}/{} failed – orderId={}, error={}", retryCount, maxRetries, orderId, ex.getMessage(), ex);
                
                if (retryCount < maxRetries) {
                    try {
                        long waitMs = (long) (Math.pow(2, retryCount - 1) * 1000);
                        log.info("Retrying wallet credit after {}ms...", waitMs);
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Wallet credit interrupted");
                    }
                } else {
                    // CRITICAL: Payment verified but wallet credit failed
                    log.error("❌ CRITICAL: Payment verified with Cashfree but wallet credit failed after {} attempts – orderId={}, userId={}, amount=₹{}. MANUAL INTERVENTION REQUIRED!", maxRetries, orderId, userId, creditAmount, ex);
                    
                    // Mark as special status for manual review
                    record.setStatus("VERIFIED_NOT_CREDITED");
                    record.setFailureReason("Payment verified but wallet credit failed. Support will resolve this within 24 hours. Order ID: " + orderId);
                    paymentRecordRepository.save(record);
                    
                    throw new IllegalStateException(
                        "Payment verified successfully but wallet credit encountered an error. " +
                        "Don't worry - your payment is safe and will be credited within 24 hours. " +
                        "Please save this order ID for reference: " + orderId
                    );
                }
            }
        }

        log.info("✅ Cashfree payment fully processed – userId={}, orderId={}, amount=₹{}, newBalance=₹{}, alreadyCredited={}", userId, orderId, creditAmount, saved.getWalletBalance(), alreadyCredited.get());
        
        WalletResponse wallet = new WalletResponse(saved.getWalletBalance(), appConfigService.getWalletMinimumBalance(), appConfigService.getCallRatePerMinute());
        return new PaymentVerifyResponse(true, wallet, "Payment verified and wallet credited successfully");
    }

    private String resolveGateway(String requestedGateway) {
        String gateway = requestedGateway == null || requestedGateway.isBlank() ? appConfigService.getPaymentDefaultGateway() : requestedGateway.trim().toLowerCase();
        if (!"razorpay".equals(gateway) && !"cashfree".equals(gateway)) {
            throw new IllegalArgumentException("Unsupported payment gateway: " + requestedGateway);
        }
        return gateway;
    }

    private String resolveVerifyGateway(PaymentVerifyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment verification request is required");
        }
        if (request.getGateway() != null && !request.getGateway().isBlank()) {
            return resolveGateway(request.getGateway());
        }
        if (request.getCashfreeOrderId() != null && !request.getCashfreeOrderId().isBlank()) {
            return "cashfree";
        }
        return "razorpay";
    }

    private UserAccount getUser(String userId) {
        if (userId == null || userId.isBlank())
            throw new IllegalStateException("Please login first");
        return userAccountRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Logged-in user was not found"));
    }

    private double normalizeAndValidateAmount(double amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount is required");
        double normalized = Math.round(amount * 100.0) / 100.0;
        double minAmount = appConfigService.getRazorpayMinAmount();
        double maxAmount = appConfigService.getRazorpayMaxAmount();
        if (normalized < minAmount)
            throw new IllegalArgumentException("Minimum recharge amount is ₹" + String.format("%.2f", minAmount));
        if (normalized > maxAmount)
            throw new IllegalArgumentException("Maximum recharge amount is ₹" + String.format("%.2f", maxAmount));
        return normalized;
    }

    private int toPaise(double amount) {
        return (int) Math.round(amount * 100);
    }
    String envKeyId = "rzp_live_TNIadgeX1uIrkR";
    String envKeySecret = "Q2NszLWvfNVEoydAYpLOu27z";
//    String envKeyId = "rzp_test_TNINNKVvsbSbqd";
//    String envKeySecret = "JI2aNSE5UvqgZtfKpazqasgv";

    private void validateRazorpayConfig() {
        // Prefer values from AppConfig (Firestore). If they are placeholders, allow
        // environment variable fallbacks (useful for local testing with Razorpay test keys).
        String keyId = appConfigService.getRazorpayKeyId();
        String keySecret = appConfigService.getRazorpayKeySecret();

        if (keyId == null || keyId.isBlank() || keyId.contains("replace_with")) {

            if (envKeyId != null && !envKeyId.isBlank()) {
                keyId = envKeyId.trim();
            }
        }

        if (keySecret == null || keySecret.isBlank() || keySecret.contains("replace_with")) {

            if (envKeySecret != null && !envKeySecret.isBlank()) {
                keySecret = envKeySecret.trim();
            }
        }

        if (keyId == null || keyId.isBlank() || keyId.contains("replace_with"))
            throw new IllegalStateException("Razorpay key_id is not configured.");
        if (keySecret == null || keySecret.isBlank() || keySecret.contains("replace_with"))
            throw new IllegalStateException("Razorpay key_secret is not configured.");
    }

    private void validateRazorpayVerifyRequest(PaymentVerifyRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Payment verification request is required");
        if (request.getRazorpayOrderId() == null || request.getRazorpayOrderId().isBlank())
            throw new IllegalArgumentException("Razorpay order id is required");
        if (request.getRazorpayPaymentId() == null || request.getRazorpayPaymentId().isBlank())
            throw new IllegalArgumentException("Razorpay payment id is required");
        if (request.getRazorpaySignature() == null || request.getRazorpaySignature().isBlank())
            throw new IllegalArgumentException("Razorpay signature is required");
    }

    private void validateCashfreeVerifyRequest(PaymentVerifyRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Payment verification request is required");
        if (request.getCashfreeOrderId() == null || request.getCashfreeOrderId().isBlank())
            throw new IllegalArgumentException("Cashfree order id is required");
    }

    private boolean verifySignature(String orderId, String paymentId, String providedSignature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac hmac = Mac.getInstance("HmacSHA256");
            // Use AppConfig secret, but fall back to environment variable for local/test runs.
            String keySecret = appConfigService.getRazorpayKeySecret();
            if (keySecret == null || keySecret.isBlank() || keySecret.contains("replace_with")) {
                String envKeySecret = System.getenv("RAZORPAY_KEY_SECRET");
                if (envKeySecret != null && !envKeySecret.isBlank()) {
                    keySecret = envKeySecret.trim();
                }
            }
            hmac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), providedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Unable to verify Razorpay signature", ex);
            return false;
        }
    }
}
