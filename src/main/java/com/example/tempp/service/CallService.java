package com.example.tempp.service;

import com.example.tempp.constants.AppMessages;
import com.example.tempp.model.*;
import com.example.tempp.repository.CallHistoryRepository;
import com.example.tempp.repository.UserAccountRepository;
import com.example.tempp.repository.WalletTransactionRepository;
import com.google.cloud.firestore.Firestore;
import com.twilio.http.HttpMethod;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Client;
import com.twilio.twiml.voice.Dial;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.voice.Say;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service for creating TwiML voice responses and billing call charges.
 * Wallet deductions are atomic via Firestore transactions.
 *
 * <p>Auto-disconnect is implemented by setting a {@code timeLimit} on the TwiML
 * {@code <Dial>} verb. The limit is the smaller of:
 * <ul>
 *   <li>The configured {@code max_call_duration_seconds}</li>
 *   <li>The number of complete billing intervals the user's wallet can cover</li>
 * </ul>
 */
@Slf4j
@Service
public class CallService {

    private static final List<Number.Event> NUMBER_STATUS_EVENTS = List.of(
            Number.Event.INITIATED,
            Number.Event.RINGING,
            Number.Event.ANSWERED,
            Number.Event.COMPLETED
    );

    private final UserService userService;
    private final UserAccountRepository userAccountRepository;
    private final CallHistoryRepository callHistoryRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TwilioNumberService twilioNumberService;
    private final AppConfigService appConfigService;
    private final Firestore firestore;


    public CallService(UserService userService, UserAccountRepository userAccountRepository, CallHistoryRepository callHistoryRepository, WalletTransactionRepository walletTransactionRepository, TwilioNumberService twilioNumberService, AppConfigService appConfigService, Firestore firestore) {
        this.userService = userService;
        this.userAccountRepository = userAccountRepository;
        this.callHistoryRepository = callHistoryRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.twilioNumberService = twilioNumberService;
        this.appConfigService = appConfigService;
        this.firestore = firestore;
    }

    // ─── Voice TwiML ─────────────────────────────────────────────────────────

    public String createVoiceResponse(String to, String callSid, String callerIdentity) {
        return createVoiceResponse(to, callSid, callerIdentity, null);
    }

    public String createVoiceResponse(String to, String callSid, String callerIdentity, String statusCallbackUrl) {
        return createVoiceResponse(to, callSid, callerIdentity, statusCallbackUrl, null);
    }

    public String createVoiceResponse(String to, String callSid, String callerIdentity, String statusCallbackUrl, String dialActionUrl) {
        String destination = to == null ? null : to.trim();
        log.info("Creating voice response: to={}, callSid={}, identity={}, hasStatusCallback={}, hasDialAction={}", destination, callSid, callerIdentity, hasText(statusCallbackUrl), hasText(dialActionUrl));

        if (appConfigService.isMaintenanceModeEnabled()) {
            return sayXml(appConfigService.getMaintenanceMessage());
        }

        UserAccount caller;
        try {
            caller = userService.getUserByTwilioIdentity(callerIdentity);
        } catch (IllegalStateException ex) {
            log.warn("Call blocked – caller identity unresolvable: {}", ex.getMessage());
            return sayXml("Please login again before making a call.");
        }

        if (caller.isBlocked()) {
            log.error("Blocked user call attempt – userId={}", caller.getId());
            return sayXml("Your account has been blocked. Please contact support for assistance.");
        }

        if (!userService.userCanCall(caller.getId())) {
            double minBalance = userService.getMinimumWalletBalance();
            String rupees = formatRupees(minBalance);
            return sayXml("Your wallet balance is too low. Please add at least " + rupees + " to make a call.");
        }

        // Calculate auto-disconnect time limit based on current wallet balance and billing config
        int timeLimit = calculateCallTimeLimit(caller.getWalletBalance());
        if (timeLimit <= 0) {
            log.warn("Call rejected – computed timeLimit=0 for userId={}, balance=₹{}", caller.getId(), caller.getWalletBalance());
            double minBalance = userService.getMinimumWalletBalance();
            String rupees = formatRupees(minBalance);
            return sayXml("Your wallet balance is too low. Please add at least " + rupees + " to make a call.");
        }
        log.info("Auto-disconnect timeLimit={}s – userId={}, balance=₹{}", timeLimit, caller.getId(), caller.getWalletBalance());

        int maxConcurrentCalls = appConfigService.getMaxConcurrentCalls();
        int activeCalls = twilioNumberService.getInUseNumbers().size();
        if (maxConcurrentCalls > 0 && activeCalls >= maxConcurrentCalls) {
            return sayXml("The maximum number of concurrent calls is currently reached. Please try again later.");
        }

        if (destination != null && !destination.isBlank()) {
            if (callSid == null || callSid.isBlank()) {
                log.error("Cannot place call – Twilio did not provide a CallSid");
                return sayXml("Call could not be connected. Please try again.");
            }
            try {
                String allocatedNumber = resolveCallerIdForCall(callSid, maxConcurrentCalls);
                log.info("Using caller ID {} for CallSid={}, destination={}", allocatedNumber, callSid, destination);
                String receiver = normalizeReceiver(destination);
                if (isPhoneNumber(destination) && !isE164PhoneNumber(receiver)) {
                    log.warn("Call rejected – invalid PSTN destination after normalization. callSid={}, originalDestination={}, normalizedReceiver={}", callSid, destination, receiver);
                    return sayXml("The phone number is invalid. Please enter the number in international format and try again.");
                }
                log.info("Dialing receiver. callSid={}, originalDestination={}, normalizedReceiver={}, pstn={}", callSid, destination, receiver, isE164PhoneNumber(receiver));
                Dial.Builder dialBuilder = new Dial.Builder()
                        .callerId(allocatedNumber)
                        .timeLimit(timeLimit)
                        .answerOnBridge(true);
                if (hasText(dialActionUrl)) {
                    dialBuilder.action(dialActionUrl.trim()).method(HttpMethod.POST);
                }
                Dial.Builder withReceiver = addChildReceiver(dialBuilder, receiver, statusCallbackUrl);
                return new VoiceResponse.Builder().dial(withReceiver.build()).build().toXml();
            } catch (IllegalStateException e) {
                log.error("Failed to allocate Twilio number for CallSid={}: {}", callSid, e.getMessage());
                return sayXml("No Twilio numbers are currently available. Please try again later.");
            }
        }

        return new VoiceResponse.Builder().say(new Say.Builder(AppMessages.DEFAULT_CALL_MESSAGE).build()).build().toXml();
    }

    public void releaseNumber(String callSid) {
        twilioNumberService.releaseNumber(callSid);
    }

    public WalletResponse canCall(String userId) {
        if (appConfigService.isMaintenanceModeEnabled()) {
            throw new IllegalStateException(appConfigService.getMaintenanceMessage());
        }
        return userService.getWallet(userId);
    }

    // ─── Call charging (atomic) ───────────────────────────────────────────────

    /**
     * Charges the user for a completed call.
     *
     * <p><b>Billing formula:</b>
     * <pre>
     *   completedIntervals = ceil(durationSeconds / billingIntervalSeconds)
     *   billedSeconds      = completedIntervals × billingIntervalSeconds
     *   calculatedCharge   = round2((billedSeconds / 60.0) × ratePerMinute)
     *   finalCharge        = round2(max(calculatedCharge, minimumCallCharge))
     * </pre>
     *
     * <p>When {@code billingIntervalSeconds = 1} (default), this is equivalent to
     * per-second billing and preserves backward compatibility.
     *
     * <p>{@code minimumCallCharge} is read from Firebase App Config key
     * {@code minimum_call_charge} at call time (default ₹0.01).
     *
     * <p>The deduction is wrapped in a Firestore transaction to prevent duplicates.
     */
    public WalletResponse chargeUser(String userId, ChargeRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Charge request is required");

        long totalDurationSeconds    = Math.max(0, request.getDurationSeconds());
        long connectedDurationSeconds = request.getConnectedDurationSeconds(); // -1 = not provided by client
        double ratePerMinute          = userService.getCallRatePerMinute();
        double minimumCallCharge      = appConfigService.getMinimumCallCharge();
        int billingIntervalSeconds    = appConfigService.getBillingIntervalSeconds();
        String callSid = request.getCallSid();

        // Determine billing seconds:
        //   connectedDurationSeconds >= 0  → client explicitly sent connected time; use it
        //   connectedDurationSeconds == 0  → call was not answered; skip billing
        //   connectedDurationSeconds == -1 → old client; fall back to totalDuration
        long billingInputSeconds;
        if (connectedDurationSeconds < 0) {
            billingInputSeconds = totalDurationSeconds;          // backward-compatible fallback
        } else if (connectedDurationSeconds == 0) {
            log.info("Call not answered – no charge. userId={}, callSid={}", userId, callSid);
            return userService.getWallet(userId);                // unanswered: no deduction
        } else {
            billingInputSeconds = connectedDurationSeconds;      // answered: bill actual connect time
        }

        // Step 1: round up to complete billing intervals
        long completedIntervals = billingInputSeconds > 0
                ? (long) Math.ceil((double) billingInputSeconds / billingIntervalSeconds)
                : 0;
        long billedSeconds = completedIntervals * billingIntervalSeconds;

        // Step 2: proportional charge for billed seconds, rounded to 2dp
        double calculatedCharge = UserService.round2((billedSeconds / 60.0) * ratePerMinute);

        // Step 3: apply minimum call charge
        double finalCharge = UserService.round2(Math.max(calculatedCharge, minimumCallCharge));

        log.debug("Billing – total={}s, connected={}s, billed={}s (interval={}s), rate=₹{}/min → calculated=₹{}, final=₹{}", totalDurationSeconds, billingInputSeconds, billedSeconds, billingIntervalSeconds, ratePerMinute, calculatedCharge, finalCharge);

        // Idempotency: skip if this callSid was already charged
        if (callSid != null && !callSid.isBlank()) {
            if (walletTransactionRepository.existsByReferenceId(userId, callSid)) {
                log.info("Call {} already charged for user {}. Skipping duplicate.", callSid, userId);
                return userService.getWallet(userId);
            }
        }

        // Look up Twilio number used (may be null if released before this call)
        final String twilioNumberUsed = (callSid != null && !callSid.isBlank())
                ? twilioNumberService.findPhoneNumberByCallSid(callSid).orElse(request.getFromNumber())
                : request.getFromNumber();

        final long finalTotalDuration      = totalDurationSeconds;
        final long finalConnectedDuration  = Math.max(0, billingInputSeconds);
        final long finalBilledSeconds      = billedSeconds;
        final double finalAmount           = finalCharge;
        final double finalRate             = ratePerMinute;
        final double finalMinCharge        = minimumCallCharge;

        UserAccount saved = userAccountRepository.runTransaction(userId, (user, tx, ref) -> {
            double before = user.getWalletBalance();
            double after  = UserService.round2(Math.max(0.0, before - finalAmount));
            user.setWalletBalance(after);

            // Wallet audit entry
            String reason = "Call charge – " + finalBilledSeconds + "s billed ("
                    + finalConnectedDuration + "s connected, " + finalTotalDuration + "s total)"
                    + (finalAmount > calculatedCharge ? " (minimum ₹" + finalMinCharge + " applied)" : "")
                    + " – " + callSid;
            WalletTransaction walletTx = new WalletTransaction(userId, "DEBIT", finalAmount, before, after, reason, callSid);
            walletTransactionRepository.saveInTransaction(tx, walletTx, firestore);

            // Call history
            CallHistory history = new CallHistory();
            history.setUserId(userId);
            history.setDestination(emptyToUnknown(request.getDestination()));
            history.setCallSid(callSid);
            history.setTwilioNumberUsed(twilioNumberUsed);
            history.setDurationSeconds(finalTotalDuration);
            history.setConnectedDurationSeconds(finalConnectedDuration);
            history.setBilledMinutes((long) Math.ceil(finalBilledSeconds / 60.0));
            history.setRatePerMinute(finalRate);
            history.setAmountCharged(finalAmount);
            history.setWalletBefore(before);
            history.setWalletAfter(after);
            history.setStatus(emptyToDefault(request.getStatus(), "completed"));
            history.setStartTime(request.getStartTime());
            history.setEndTime(request.getEndTime() != null ? request.getEndTime() : Instant.now());
            history.setDisconnectReason(emptyToDefault(request.getDisconnectReason(), emptyToDefault(request.getStatus(), "completed")));
            history.setCreatedAt(Instant.now());
            callHistoryRepository.saveInTransaction(tx, history, firestore);

            return user;
        });

        log.info("Charged userId={} – {}s connected, {}s billed (interval={}s) → ₹{} (min ₹{}), balance ₹{}, via {}", userId, finalConnectedDuration, billedSeconds, billingIntervalSeconds, finalCharge, minimumCallCharge, saved.getWalletBalance(), twilioNumberUsed != null ? twilioNumberUsed : "unknown");
        return new WalletResponse(saved.getWalletBalance(), userService.getMinimumWalletBalance(), ratePerMinute);
    }

    // ─── Redirect TwiML ───────────────────────────────────────────────────────

    public String getRedirectToClientVoiceResponse(String targetIdentity) {
        String identity = targetIdentity == null ? null : targetIdentity.trim();
        log.info("Creating redirect response to client: {}", identity);

        if (appConfigService.isMaintenanceModeEnabled()) {
            return sayXml(appConfigService.getMaintenanceMessage());
        }

        if (identity == null || identity.isBlank()) {
            return sayXml("No destination client is available. Please try again after the user logs in.");
        }

        Say say = new Say.Builder(AppMessages.REDIRECT_WAIT_MESSAGE).build();
        Client client = new Client.Builder(identity).build();
        Dial dial = new Dial.Builder().client(client).build();
        return new VoiceResponse.Builder().say(say).dial(dial).build().toXml();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Calculates the Twilio {@code <Dial>} {@code timeLimit} attribute in seconds.
     *
     * <p>The limit is the minimum of:
     * <ul>
     *   <li>{@code max_call_duration_seconds} from Firestore config</li>
     *   <li>The maximum number of complete billing intervals the wallet can cover,
     *       expressed in seconds</li>
     * </ul>
     *
     * <p>Returns 0 when the wallet cannot afford even one billing interval.
     */
    private int calculateCallTimeLimit(double walletBalance) {
        double ratePerMinute = userService.getCallRatePerMinute();
        int billingIntervalSeconds = appConfigService.getBillingIntervalSeconds();
        int maxCallDurationSeconds = appConfigService.getMaxCallDurationSeconds();
        double autoDisconnectThreshold = appConfigService.getAutoDisconnectThreshold();

        // Free calls: only cap at max configured duration
        if (ratePerMinute <= 0.0) {
            return maxCallDurationSeconds;
        }

        double availableBalance = Math.max(0.0, walletBalance - autoDisconnectThreshold);
        double intervalCost = (billingIntervalSeconds / 60.0) * ratePerMinute;

        if (intervalCost <= 0.0) {
            return maxCallDurationSeconds;
        }

        long affordableIntervals = (long) (availableBalance / intervalCost);
        long maxAffordableSeconds = affordableIntervals * billingIntervalSeconds;

        return (int) Math.min(Math.max(0L, maxAffordableSeconds), maxCallDurationSeconds);
    }

    private Dial.Builder addChildReceiver(Dial.Builder builder, String to, String statusCallbackUrl) {
        if (isE164PhoneNumber(to)) {
            Number.Builder numberBuilder = new Number.Builder(to);
            if (hasText(statusCallbackUrl)) {
                numberBuilder.statusCallback(statusCallbackUrl.trim())
                        .statusCallbackMethod(HttpMethod.POST)
                        .statusCallbackEvents(NUMBER_STATUS_EVENTS);
            }
            return builder.number(numberBuilder.build());
        }
        return builder.client(new Client.Builder(to).build());
    }

    /**
     * Uses the managed number pool when available; otherwise falls back to the
     * configured Twilio caller ID. This keeps learning/single-number projects
     * working after switching from a static TwiML Bin to the dynamic webhook.
     */
    private String resolveCallerIdForCall(String callSid, int maxConcurrentCalls) {
        if (twilioNumberService.hasAvailableNumbers()) {
            return twilioNumberService.allocateNumber(callSid, maxConcurrentCalls);
        }
        String fallbackCallerId = appConfigService.getTwilioCallerId();
        if (fallbackCallerId != null && !fallbackCallerId.isBlank()) {
            log.warn("No available Twilio number-pool record found. Falling back to configured twilio_caller_id={}", fallbackCallerId);
            return fallbackCallerId.trim();
        }
        log.error("No available Twilio numbers and twilio_caller_id is not configured. callSid={}", callSid);
        throw new IllegalStateException("No Twilio numbers are currently available. Please try again later.");
    }

    private boolean isPhoneNumber(String to) {
        return to != null && to.matches("^[\\d+\\-() ]+$");
    }

    private boolean isE164PhoneNumber(String to) {
        return to != null && to.matches("^\\+[1-9]\\d{7,14}$");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Normalises common Indian dialpad input to E.164 for Twilio PSTN dialing.
     * Examples:
     *   9876543210   -> +919876543210
     *   919876543210 -> +919876543210
     *   +919876543210 stays unchanged
     * Non-phone destinations are treated as Twilio Client identities.
     */
    private String normalizeReceiver(String destination) {
        if (!isPhoneNumber(destination)) {
            return destination;
        }
        String compact = destination.trim().replaceAll("[\\s\\-()]", "");
        if (compact.startsWith("+")) {
            return compact;
        }
        String digits = compact.replaceAll("\\D", "");
        if (digits.length() == 10 && digits.matches("^[6-9]\\d{9}$")) {
            return "+91" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("0") && digits.substring(1).matches("^[6-9]\\d{9}$")) {
            return "+91" + digits.substring(1);
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return compact;
    }

    private String sayXml(String message) {
        return new VoiceResponse.Builder().say(new Say.Builder(message).build()).build().toXml();
    }

    private String emptyToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * Formats a rupee amount as spoken text for TwiML, e.g. 1.0 → "one rupee".
     */
    private String formatRupees(double amount) {
        long whole = (long) amount;
        if (whole == 1)
            return "one rupee";
        if (whole == 2)
            return "two rupees";
        if (whole == 5)
            return "five rupees";
        if (whole == 10)
            return "ten rupees";
        return whole + " rupees";
    }
}
