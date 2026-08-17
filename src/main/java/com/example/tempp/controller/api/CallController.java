package com.example.tempp.controller.api;

import com.example.tempp.model.ChargeRequest;
import com.example.tempp.model.OutboundCallRequest;
import com.example.tempp.model.OutboundCallResponse;
import com.example.tempp.model.WalletResponse;
import com.example.tempp.service.CallService;
import com.example.tempp.service.AppConfigService;
import com.example.tempp.service.OutboundCallService;
import com.example.tempp.service.UserService;
import com.twilio.security.RequestValidator;
import com.twilio.twiml.VoiceResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

import static com.example.tempp.controller.api.UserController.SESSION_USER_ID;

/**
 * REST Controller for call-related operations and TwiML responses.
 */
@Slf4j
@RestController
@RequestMapping("/call")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final UserService userService;
    private final OutboundCallService outboundCallService;
    private final AppConfigService appConfigService;

    @Value("${app.public-base-url:}")
    private String configuredPublicBaseUrl;
    @Value("${app.twilio.validate-signature:false}")
    private boolean validateTwilioSignature;

    @GetMapping("/can-call")
    public ResponseEntity<WalletResponse> canCall(HttpSession session) {
        try {
            return ResponseEntity.ok(callService.canCall(requiredUserId(session)));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }
    }

    @PostMapping("/charge")
    public ResponseEntity<WalletResponse> charge(@RequestBody ChargeRequest request, HttpSession session) {
        return ResponseEntity.ok(callService.chargeUser(requiredUserId(session), request));
    }

    @PostMapping("/outbound")
    public ResponseEntity<OutboundCallResponse> startOutboundCall(@RequestBody OutboundCallRequest request, HttpServletRequest httpRequest, HttpSession session) {
        try {
            String publicBaseUrl = resolvePublicBaseUrl(httpRequest);
            return ResponseEntity.ok(outboundCallService.startOutboundCall(requiredUserId(session), request, publicBaseUrl));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }
    }

    @RequestMapping(value = "/outbound/twiml/{callId}", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> outboundTwiMl(@PathVariable String callId, @RequestParam Map<String, String> parameters, HttpServletRequest request) {
        validateTwilioWebhook(request, parameters);
        String xml = outboundCallService.generateTwiMl(callId, parameters.get("CallSid"));
        return xmlResponse(xml);
    }

    /**
     * GET /call/outbound/twiml-url/{publicId}
     *
     * <p>Returns the TwiML URL and call metadata for a given outbound call.
     * Intended for testing and debugging – lets you verify that:
     * <ul>
     *   <li>The publicId exists in Firestore.</li>
     *   <li>The generated TwiML URL is correct.</li>
     *   <li>The call record has not expired.</li>
     * </ul>
     *
     * <p>You can then open the {@code twimlUrl} directly in a browser to confirm it
     * returns valid TwiML XML.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "publicId":    "a3f9...",
     *   "twimlUrl":    "https://phonebooth.com/call/outbound/twiml/a3f9...",
     *   "status":      "initiated",
     *   "destination": "+919876543210",
     *   "fromNumber":  "+12025551234",
     *   "callSid":     "CA...",
     *   "expiresAt":   "2026-06-28T19:00:00Z",
     *   "expired":     false,
     *   "message":     "Your call is connected. Thank you."
     * }
     * </pre>
     */
    @GetMapping("/outbound/twiml-url/{publicId}")
    public ResponseEntity<Map<String, Object>> getTwiMlUrl(@PathVariable String publicId, HttpServletRequest httpRequest, HttpSession session) {
        log.info("TwiML URL lookup request – publicId={}", publicId);
        try {
            String baseUrl = resolvePublicBaseUrl(httpRequest);
            Map<String, Object> info = outboundCallService.getTwiMlUrlInfo(publicId, baseUrl);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException ex) {
            log.warn("TwiML URL lookup – bad request: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (java.util.NoSuchElementException ex) {
            log.warn("TwiML URL lookup – not found: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @RequestMapping(value = "/outbound/twiml", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> outboundTwiMlMissingIdentifier(@RequestParam Map<String, String> parameters, HttpServletRequest request) {
        validateTwilioWebhook(request, parameters);
        return xmlResponse(outboundCallService.generateTwiMl(null, parameters.get("CallSid")));
    }

    @PostMapping("/outbound/status/{callId}")
    public ResponseEntity<Void> outboundStatusCallback(@PathVariable String callId, @RequestParam Map<String, String> parameters, HttpServletRequest request) {
        validateTwilioWebhook(request, parameters);
        outboundCallService.handleStatusCallback(callId, parameters);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/voice", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleClientVoice(@RequestParam Map<String, String> parameters, HttpServletRequest request) {
        validateTwilioWebhook(request, parameters);
        String destination = resolveDestination(parameters);
        String callSid = parameters.get("CallSid");
        String callerIdentity = resolveCallerIdentity(parameters);
        String publicBaseUrl = resolvePublicBaseUrl(request);
        String statusCallbackUrl = publicBaseUrl + "/call/status-callback";
        String dialActionUrl = publicBaseUrl + "/call/dial-complete";
        log.info("Resolved voice request – destination={}, callSid={}, callerIdentity={}, statusCallbackUrl={}, dialActionUrl={}", destination, callSid, callerIdentity, statusCallbackUrl, dialActionUrl);
        String xml = callService.createVoiceResponse(destination, callSid, callerIdentity, statusCallbackUrl, dialActionUrl);
        log.debug("Generated voice TwiML for callSid={}: {}", callSid, xml);
        return xmlResponse(xml);
    }

    @RequestMapping(value = "/status-callback", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> handleCallStatusCallback(@RequestParam Map<String, String> parameters, HttpServletRequest request) {
        validateTwilioWebhook(request, parameters);
        String callSid    = parameters.get("CallSid");
        String callStatus = firstNonBlank(parameters.get("CallStatus"), parameters.get("DialCallStatus"), parameters.get("DialBridged"));
        String from       = parameters.get("From");
        String to         = parameters.get("To");
        String duration   = parameters.get("CallDuration");
        String dialCallSid = parameters.get("DialCallSid");

        log.info("Twilio status callback – CallSid={}, DialCallSid={}, Status={}, From={}, To={}, Duration={}s",
                callSid, dialCallSid, callStatus, from, to, duration);

        if (callSid == null || callSid.isBlank()) {
            log.warn("Status callback received without CallSid");
            return ResponseEntity.ok().build();
        }

        // Release the allocated Twilio number on any terminal call state
        if ("completed".equals(callStatus) || "failed".equals(callStatus)
                || "busy".equals(callStatus) || "no-answer".equals(callStatus)
                || "canceled".equals(callStatus)) {
            callService.releaseNumber(callSid);
            log.info("Number released for CallSid={} (status={})", callSid, callStatus);
        } else if ("in-progress".equals(callStatus)) {
            log.info("Call answered – CallSid={}, From={}, To={}", callSid, from, to);
        } else if ("ringing".equals(callStatus)) {
            log.info("Call ringing – CallSid={}, To={}", callSid, to);
        } else if ("initiated".equals(callStatus)) {
            log.info("Call initiated – CallSid={}", callSid);
        }

        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/dial-complete", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleDialComplete(@RequestParam Map<String, String> parameters, HttpServletRequest request) {
        validateTwilioWebhook(request, parameters);
        String callSid = parameters.get("CallSid");
        String dialCallStatus = parameters.get("DialCallStatus");
        log.info("Dial completed – CallSid={}, DialCallSid={}, DialCallStatus={}, DialCallDuration={}s",
                callSid, parameters.get("DialCallSid"), dialCallStatus, parameters.get("DialCallDuration"));
        if (callSid != null && !callSid.isBlank()) {
            callService.releaseNumber(callSid);
        }
        return xmlResponse(new VoiceResponse.Builder().build().toXml());
    }

    @RequestMapping(value = "/direct-client", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleDirectClientVoice(@RequestParam Map<String, String> parameters, HttpServletRequest request, HttpSession session) {
        validateTwilioWebhook(request, parameters);
        String targetIdentity = resolveTargetIdentity(parameters, session);
        String xml = callService.getRedirectToClientVoiceResponse(targetIdentity);
        log.debug("Generated direct-client TwiML for targetIdentity={}: {}", targetIdentity, xml);
        return xmlResponse(xml);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String requiredUserId(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId instanceof String id && !id.isBlank())
            return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first");
    }

    private String resolveDestination(Map<String, String> parameters) {
        return firstNonBlank(parameters.get("DialedNumber"), parameters.get("dialedNumber"), parameters.get("WebDialerTo"), parameters.get("To"), parameters.get("to"), parameters.get("PhoneNumber"), parameters.get("phoneNumber"), parameters.get("Called"), parameters.get("CalledTo"));
    }

    private String resolveCallerIdentity(Map<String, String> parameters) {
        return normalizeClientIdentity(firstNonBlank(parameters.get("From"), parameters.get("Caller"), parameters.get("ClientIdentity"), parameters.get("clientIdentity"), parameters.get("Identity"), parameters.get("identity")));
    }

    private String resolveTargetIdentity(Map<String, String> parameters, HttpSession session) {
        String requestIdentity = normalizeClientIdentity(firstNonBlank(parameters.get("client"), parameters.get("Client"), parameters.get("targetIdentity"), parameters.get("TargetIdentity"), parameters.get("identity"), parameters.get("Identity")));
        if (requestIdentity != null)
            return requestIdentity;

        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId instanceof String id && !id.isBlank()) {
            return userService.getUserById(id).getTemp();
        }
        return normalizeClientIdentity(appConfigService.getTwilioIncomingClientIdentity());
    }

    private void validateTwilioWebhook(HttpServletRequest request, Map<String, String> parameters) {
        if (!validateTwilioSignature)
            return;
        String signature = request.getHeader("X-Twilio-Signature");
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing Twilio signature");
        }
        String requestUrl = resolveExternalRequestUrl(request);
        RequestValidator validator = new RequestValidator(appConfigService.getTwilioAuthToken());
        Map<String, String> validationParameters = "GET".equalsIgnoreCase(request.getMethod()) ? Collections.emptyMap() : parameters;
        if (!validator.validate(requestUrl, validationParameters, signature)) {
            log.warn("Twilio webhook signature validation failed. method={}, url={}", request.getMethod(), requestUrl);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Twilio signature");
        }
    }

    private ResponseEntity<String> xmlResponse(String xml) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    private String resolvePublicBaseUrl(HttpServletRequest request) {
        if (configuredPublicBaseUrl != null && !configuredPublicBaseUrl.isBlank()) {
            return trimTrailingSlash(configuredPublicBaseUrl);
        }
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null)
            scheme = request.getScheme();
        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (host == null)
            host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
        }
        return trimTrailingSlash(scheme + "://" + host + request.getContextPath());
    }

    private String resolveExternalRequestUrl(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath))
            requestPath = requestPath.substring(contextPath.length());
        String url = resolvePublicBaseUrl(request) + requestPath;
        if (request.getQueryString() != null && !request.getQueryString().isBlank())
            url += "?" + request.getQueryString();
        return url;
    }

    private String firstHeaderValue(String value) {
        if (value == null || value.isBlank())
            return null;
        return value.split(",")[0].trim();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/"))
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private String normalizeClientIdentity(String value) {
        if (value == null || value.isBlank())
            return null;
        String trimmed = value.trim();
        return trimmed.startsWith("client:") ? trimmed.substring("client:".length()).trim() : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value.trim();
        }
        return null;
    }
}
