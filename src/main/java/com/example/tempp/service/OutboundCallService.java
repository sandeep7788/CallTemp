package com.example.tempp.service;

import com.example.tempp.model.OutboundCall;
import com.example.tempp.model.OutboundCallRequest;
import com.example.tempp.model.OutboundCallResponse;
import com.example.tempp.model.UserAccount;
import com.example.tempp.repository.OutboundCallRepository;
import com.twilio.Twilio;
import com.twilio.http.HttpMethod;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Say;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for initiating and managing outbound Twilio calls.
 * Dynamic TwiML is served from a unique per-call endpoint.
 */
@Slf4j
@Service
public class OutboundCallService {

    private static final String STATUS_CREATED = "created";
    private static final String STATUS_INITIATED = "initiated";
    private static final String STATUS_TWIML_REQUESTED = "twiml_requested";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_FAILED = "failed";
    private static final String DEFAULT_MESSAGE = "Your call is connected. Thank you.";
    private static final List<String> CALLBACK_EVENTS = List.of("initiated", "ringing", "answered", "completed");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OutboundCallRepository outboundCallRepository;
    private final UserService userService;
    private final TwilioNumberService twilioNumberService;
    private final AppConfigService appConfigService;


    public OutboundCallService(OutboundCallRepository outboundCallRepository, UserService userService, TwilioNumberService twilioNumberService, AppConfigService appConfigService) {
        this.outboundCallRepository = outboundCallRepository;
        this.userService = userService;
        this.twilioNumberService = twilioNumberService;
        this.appConfigService = appConfigService;
    }

    // ─── Start outbound call ─────────────────────────────────────────────────

    public OutboundCallResponse startOutboundCall(String userId, OutboundCallRequest request, String publicBaseUrl) {
        validateRequest(request);
        String baseUrl = normalizeBaseUrl(publicBaseUrl);

        // 1. Pre-flight checks + create pending call record
        OutboundCall pendingCall = prepareOutboundCall(userId, request);
        String twimlUrl = buildTwiMlUrl(baseUrl, pendingCall.getPublicId());
        String statusCallbackUrl = buildStatusCallbackUrl(baseUrl, pendingCall.getPublicId());
        log.debug("Outbound call URLs – twiml={}, statusCallback={}", twimlUrl, statusCallbackUrl);
        try {
            Twilio.init(appConfigService.getTwilioAccountSid(), appConfigService.getTwilioAuthToken());
            com.twilio.rest.api.v2010.account.Call twilioCall = com.twilio.rest.api.v2010.account.Call.creator(new PhoneNumber(pendingCall.getDestination()), new PhoneNumber(pendingCall.getFromNumber()), URI.create(twimlUrl)).setMethod(HttpMethod.POST).setStatusCallback(URI.create(statusCallbackUrl)).setStatusCallbackMethod(HttpMethod.POST).setStatusCallbackEvent(CALLBACK_EVENTS).create();

            String callSid = twilioCall.getSid();

            // 2. Update call record with Twilio SID
            OutboundCall initiated = Objects.requireNonNull(outboundCallRepository.runTransaction(pendingCall.getPublicId(), (call, tx, ref) -> {
                call.setCallSid(callSid);
                call.setStatus(STATUS_INITIATED);
                return call;
            }));
            twilioNumberService.bindAllocatedNumberToCallSid(pendingCall.getPublicId(), callSid);

            log.info("Started outbound call. callId={}, callSid={}, to={}", initiated.getPublicId(), callSid, initiated.getDestination());
            return new OutboundCallResponse(initiated, twimlUrl);

        } catch (Exception ex) {
            markFailedAndRelease(pendingCall.getPublicId(), ex.getMessage());
            log.error("Unable to start outbound call. callId={}, to={}: {}", pendingCall.getPublicId(), pendingCall.getDestination(), ex.getMessage(), ex);
            throw new IllegalStateException("Unable to start outbound call. Please try again later.", ex);
        }
    }

    // ─── Dynamic TwiML endpoint ───────────────────────────────────────────────

    /**
     * Generates TwiML for a specific outbound call identified by {@code publicId}.
     * Called by Twilio when the call is answered.
     */
    public String generateTwiMl(String publicId, String twilioCallSid) {
        if (publicId == null || publicId.isBlank()) {
            log.warn("Dynamic TwiML requested without a call identifier");
            return messageTwiMl("This call request is invalid.");
        }

        OutboundCall result = outboundCallRepository.runTransaction(publicId.trim(), (call, tx, ref) -> {
            if (call.getExpiresAt() != null && call.getExpiresAt().isBefore(Instant.now())) {
                call.setStatus(STATUS_EXPIRED);
                releaseTrackedNumber(call);
                log.warn("Dynamic TwiML after expiration. callId={}", publicId);
                return call; // will be detected below
            }

            if (twilioCallSid != null && !twilioCallSid.isBlank() && call.getCallSid() != null && !call.getCallSid().isBlank() && !twilioCallSid.trim().equals(call.getCallSid())) {
                log.warn("TwiML CallSid mismatch. callId={}, expected={}, got={}", publicId, call.getCallSid(), twilioCallSid);
                return null; // return null → treat as invalid
            }

            call.setTwimlRequestedAt(Instant.now());
            if (!isTerminalStatus(call.getStatus())) {
                call.setStatus(STATUS_TWIML_REQUESTED);
            }
            log.info("Generated TwiML. callId={}, callSid={}, to={}", publicId, twilioCallSid, call.getDestination());
            return call;
        });

        if (result == null) {
            return messageTwiMl("This call request is invalid.");
        }
        if (STATUS_EXPIRED.equals(result.getStatus())) {
            return messageTwiMl("This call request has expired.");
        }
        return messageTwiMl(emptyToDefault(result.getMessage(), DEFAULT_MESSAGE));
    }

    // ─── TwiML URL lookup (for testing / verification) ────────────────────────

    /**
     * Returns the TwiML URL and call metadata for a given {@code publicId}.
     * Used by the debug endpoint to verify the URL before initiating a live call.
     *
     * @param publicId the unique call identifier
     * @param baseUrl  the public base URL of this application
     * @return a map containing the TwiML URL, status, destination, and expiry
     * @throws IllegalArgumentException         if publicId is blank
     * @throws java.util.NoSuchElementException if no call record found for the given publicId
     * @throws IllegalStateException            if the call has already expired
     */
    public Map<String, Object> getTwiMlUrlInfo(String publicId, String baseUrl) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("publicId is required");
        }
        String trimmedId = publicId.trim();
        OutboundCall call = outboundCallRepository.findByPublicId(trimmedId).orElseThrow(() -> new java.util.NoSuchElementException("No outbound call record found for publicId: " + trimmedId));

        String twimlUrl = buildTwiMlUrl(normalizeBaseUrl(baseUrl), trimmedId);
        boolean expired = call.getExpiresAt() != null && call.getExpiresAt().isBefore(Instant.now());

        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("publicId", call.getPublicId());
        info.put("twimlUrl", twimlUrl);
        info.put("status", call.getStatus());
        info.put("destination", call.getDestination());
        info.put("fromNumber", call.getFromNumber());
        info.put("callSid", call.getCallSid());
        info.put("expiresAt", call.getExpiresAt() != null ? call.getExpiresAt().toString() : null);
        info.put("expired", expired);
        info.put("message", call.getMessage());
        log.info("TwiML URL lookup – publicId={}, twimlUrl={}, status={}, expired={}", trimmedId, twimlUrl, call.getStatus(), expired);
        return info;
    }

    // ─── Status callback ──────────────────────────────────────────────────────

    public void handleStatusCallback(String publicId, Map<String, String> parameters) {
        if (publicId == null || publicId.isBlank()) {
            log.warn("Outbound status callback missing call identifier");
            return;
        }

        outboundCallRepository.runTransaction(publicId.trim(), (call, tx, ref) -> {
            String callSid = parameters.get("CallSid");
            String callStatus = emptyToDefault(parameters.get("CallStatus"), "callback_received");

            if (callSid != null && !callSid.isBlank() && (call.getCallSid() == null || call.getCallSid().isBlank())) {
                call.setCallSid(callSid.trim());
                twilioNumberService.bindAllocatedNumberToCallSid(call.getPublicId(), callSid.trim());
            }
            call.setStatus(callStatus);

            if (isTerminalStatus(callStatus)) {
                releaseTrackedNumber(call);
            }
            log.info("Status callback. callId={}, callSid={}, status={}", publicId, callSid, callStatus);
            return call;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private OutboundCall prepareOutboundCall(String userId, OutboundCallRequest request) {
        if (appConfigService.isMaintenanceModeEnabled()) {
            throw new IllegalStateException(appConfigService.getMaintenanceMessage());
        }

        UserAccount user = userService.getUserById(userId);
        if (user.isBlocked()) {
            throw new IllegalStateException("Your account has been blocked. Please contact support for assistance.");
        }
        if (user.getWalletBalance() < userService.getMinimumWalletBalance()) {
            double min = userService.getMinimumWalletBalance();
            throw new IllegalStateException(String.format("Your wallet balance is too low. Please add at least \u20b9%.2f to make a call.", min));
        }

        int maxConcurrentCalls = appConfigService.getMaxConcurrentCalls();
        String publicId = generateUniquePublicId();
        String fromNumber = twilioNumberService.allocateNumber(publicId, maxConcurrentCalls);

        OutboundCall call = new OutboundCall();
        call.setPublicId(publicId);
        call.setUserId(userId);
        call.setDestination(request.getDestination().trim());
        call.setFromNumber(fromNumber);
        call.setMessage(sanitizeMessage(request.getMessage()));
        call.setStatus(STATUS_CREATED);
        long ttlMinutes = Math.max(1, appConfigService.getOutboundCallTwimlTtlMinutes());
        call.setExpiresAt(Instant.now().plusSeconds(ttlMinutes * 60));
        return outboundCallRepository.save(call);
    }

    private void markFailedAndRelease(String publicId, String reason) {
        try {
            outboundCallRepository.runTransaction(publicId, (call, tx, ref) -> {
                call.setStatus(STATUS_FAILED);
                releaseTrackedNumber(call);
                log.warn("Marked outbound call failed. callId={}, reason={}", publicId, reason);
                return call;
            });
        } catch (Exception ex) {
            log.warn("Could not mark call failed: {}", ex.getMessage());
        }
    }

    private void releaseTrackedNumber(OutboundCall call) {
        String key = (call.getCallSid() != null && !call.getCallSid().isBlank()) ? call.getCallSid() : call.getPublicId();
        twilioNumberService.releaseNumber(key);
    }

    private String buildTwiMlUrl(String baseUrl, String publicId) {
        return baseUrl + "/call/outbound/twiml/" + publicId;
    }

    private String buildStatusCallbackUrl(String baseUrl, String publicId) {
        return baseUrl + "/call/outbound/status/" + publicId;
    }

    private String messageTwiMl(String message) {
        return new VoiceResponse.Builder().say(new Say.Builder(message).build()).build().toXml();
    }

    private void validateRequest(OutboundCallRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Outbound call request is required");
        if (request.getDestination() == null || request.getDestination().isBlank())
            throw new IllegalArgumentException("Destination phone number is required");
        String destination = request.getDestination().trim();
        if (!destination.matches("^\\+[1-9]\\d{7,14}$"))
            throw new IllegalArgumentException("Destination must be an E.164 phone number, e.g. +14155552671");
        if (request.getMessage() != null && request.getMessage().length() > 500)
            throw new IllegalArgumentException("Message must be 500 characters or less");
    }

    private String normalizeBaseUrl(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank())
            throw new IllegalStateException("Public base URL is required to create Twilio webhook URLs");
        String trimmed = publicBaseUrl.trim();
        while (trimmed.endsWith("/"))
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private String generateUniquePublicId() {
        for (int i = 0; i < 5; i++) {
            byte[] bytes = new byte[24];
            SECURE_RANDOM.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            if (outboundCallRepository.findByPublicId(token).isEmpty())
                return token;
        }
        throw new IllegalStateException("Unable to generate a unique outbound call identifier");
    }

    private String sanitizeMessage(String value) {
        return (value == null || value.isBlank()) ? DEFAULT_MESSAGE : value.trim();
    }

    private String emptyToDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private boolean isTerminalStatus(String status) {
        return "completed".equals(status) || "failed".equals(status) || "busy".equals(status) || "no-answer".equals(status) || "canceled".equals(status) || STATUS_EXPIRED.equals(status);
    }
}
