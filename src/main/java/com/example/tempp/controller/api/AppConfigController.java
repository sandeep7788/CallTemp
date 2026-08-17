package com.example.tempp.controller.api;

import com.example.tempp.model.AppConfig;
import com.example.tempp.service.AppConfigService;
import com.example.tempp.service.TwilioNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing application configuration.
 *
 * <p>All Twilio, Razorpay, and wallet settings are stored in the Firestore
 * {@code app_config} collection and exposed here for runtime updates without
 * redeployment.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/config                       — list all config entries</li>
 *   <li>GET  /api/config/{key}                 — get one config entry</li>
 *   <li>GET  /api/config/{key}/value           — get raw value</li>
 *   <li>POST /api/config                       — generic upsert</li>
 *   <li>PATCH /api/config/{key}/toggle         — toggle enabled flag</li>
 *   <li>DELETE /api/config/{key}               — delete entry</li>
 *   <li>POST /api/config/initialize-defaults   — seed all defaults</li>
 *   <li>GET  /api/config/twilio                — get all Twilio config</li>
 *   <li>PUT  /api/config/twilio                — update Twilio config</li>
 *   <li>GET  /api/config/razorpay              — get all Razorpay config</li>
 *   <li>PUT  /api/config/razorpay              — update Razorpay config</li>
 *   <li>GET  /api/config/wallet                — get wallet config</li>
 *   <li>PUT  /api/config/wallet                — update wallet config</li>
 *   <li>GET  /api/config/dashboard-message     — dashboard message</li>
 *   <li>POST /api/config/dashboard-message     — update dashboard message</li>
 *   <li>GET  /api/config/maintenance-mode      — maintenance status</li>
 *   <li>POST /api/config/maintenance-mode      — toggle maintenance mode</li>
 *   <li>GET  /api/config/minimum-call-charge   — get min call charge</li>
 *   <li>PUT  /api/config/minimum-call-charge   — set min call charge</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class AppConfigController {

    private static final String MASKED = "***masked***";

    private final AppConfigService appConfigService;
    private final TwilioNumberService twilioNumberService;

    // ─── Generic CRUD ─────────────────────────────────────────────────────────

    /**
     * List all configuration entries
     */
    @GetMapping
    public ResponseEntity<List<AppConfig>> getAllConfigs() {
        log.info("Fetching all configurations");
        return ResponseEntity.ok(appConfigService.getAllConfigs());
    }

    /**
     * Get single config entry by key
     */
    @GetMapping("/{key}")
    public ResponseEntity<AppConfig> getConfig(@PathVariable String key) {
        log.info("Fetching configuration: {}", key);
        return appConfigService.getConfig(key).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get raw config value by key
     */
    @GetMapping("/{key}/value")
    public ResponseEntity<Map<String, String>> getConfigValue(@PathVariable String key) {
        log.info("Fetching configuration value: {}", key);
        String value = appConfigService.getConfigValue(key);
        if (value != null) {
            Map<String, String> result = new HashMap<>();
            result.put("config_key", key);
            result.put("config_value", value);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Generic upsert a config entry
     */
    @PostMapping
    public ResponseEntity<?> setConfig(@RequestBody Map<String, Object> configData) {
        log.info("Setting configuration: key={}", configData.get("config_key"));

        String key = getString(configData, "config_key", "configKey", "key");
        String value = getString(configData, "config_value", "configValue", "value");
        String type = getString(configData, "config_type", "configType", "type");
        String description = getString(configData, "description");
        Boolean enabled = getBoolean(configData, "is_enabled", "isEnabled", "enabled");

        if (key == null || key.isBlank() || value == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("config_key and config_value are required"));
        }
        if (type == null || type.isBlank())
            type = "STRING";

        AppConfig saved = description != null ? appConfigService.setConfig(key, value, type, description) : appConfigService.setConfig(key, value, type);

        if (enabled != null) {
            saved = appConfigService.setEnabled(key, enabled);
        }
        return ResponseEntity.ok(saved);
    }

    /**
     * Toggle the enabled flag of a config entry
     */
    @PatchMapping("/{key}/toggle")
    public ResponseEntity<AppConfig> toggleEnabled(@PathVariable String key) {
        log.info("Toggling enabled status for: {}", key);
        try {
            return ResponseEntity.ok(appConfigService.toggleEnabled(key));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a config entry
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, String>> deleteConfig(@PathVariable String key) {
        log.info("Deleting configuration: {}", key);
        try {
            appConfigService.deleteConfig(key);
            return ResponseEntity.ok(Map.of("message", "Configuration deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Seed all default config entries (idempotent — skips existing keys)
     */
    @PostMapping("/initialize-defaults")
    public ResponseEntity<Map<String, String>> initializeDefaults() {
        log.info("Initializing default configurations");
        appConfigService.initializeDefaults();
        return ResponseEntity.ok(Map.of("message", "Default configurations initialized successfully"));
    }

    // ─── Twilio config ────────────────────────────────────────────────────────

    /**
     * GET /api/config/twilio
     * Returns all Twilio config values. Sensitive fields are masked.
     */
    @GetMapping("/twilio")
    public ResponseEntity<Map<String, Object>> getTwilioConfig() {
        log.info("Fetching Twilio configuration");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account_sid", appConfigService.getTwilioAccountSid());
        result.put("auth_token", mask(appConfigService.getTwilioAuthToken()));
        result.put("caller_id", appConfigService.getTwilioCallerId());
        result.put("app_sid", appConfigService.getTwilioAppSid());
        result.put("api_key", appConfigService.getTwilioApiKey());
        result.put("api_secret", mask(appConfigService.getTwilioApiSecret()));
        return ResponseEntity.ok(result);
    }

    /**
     * PUT /api/config/twilio
     * Updates one or more Twilio config values. Only provided fields are updated.
     *
     * <p>Request body (all fields optional):
     * <pre>
     * {
     *   "account_sid": "ACxxxxxxx",
     *   "auth_token":  "xxxxxxx",
     *   "caller_id":   "+1XXXXXXXXXX",
     *   "app_sid":     "APxxxxxxx",
     *   "api_key":     "SKxxxxxxx",
     *   "api_secret":  "xxxxxxx"
     * }
     * </pre>
     */
    @PutMapping("/twilio")
    public ResponseEntity<Map<String, Object>> updateTwilioConfig(@RequestBody Map<String, Object> body) {
        log.info("Updating Twilio configuration");
        int updated = 0;

        String accountSid = getString(body, "account_sid", "accountSid");
        if (accountSid != null) {
            appConfigService.setTwilioAccountSid(accountSid);
            updated++;
        }

        String authToken = getString(body, "auth_token", "authToken");
        if (authToken != null) {
            appConfigService.setTwilioAuthToken(authToken);
            updated++;
        }

        String callerId = getString(body, "caller_id", "callerId", "trial_number", "trialNumber");
        if (callerId != null) {
            appConfigService.setTwilioCallerId(callerId);
            // Also upsert into the twilio_numbers pool so it is available for allocation
            try {
                twilioNumberService.upsertNumber(callerId, "Primary Number", "Set via Twilio config API");
                log.info("Caller ID '{}' also upserted into twilio_numbers pool", callerId);
            } catch (Exception e) {
                log.warn("Could not upsert caller_id into twilio_numbers pool: {}", e.getMessage());
            }
            updated++;
        }

        String appSid = getString(body, "app_sid", "appSid", "twiml_app_sid");
        if (appSid != null) {
            appConfigService.setTwilioAppSid(appSid);
            updated++;
        }

        String apiKey = getString(body, "api_key", "apiKey");
        if (apiKey != null) {
            appConfigService.setTwilioApiKey(apiKey);
            updated++;
        }

        String apiSecret = getString(body, "api_secret", "apiSecret");
        if (apiSecret != null) {
            appConfigService.setTwilioApiSecret(apiSecret);
            updated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Twilio configuration updated");
        result.put("fields_updated", updated);
        result.put("account_sid", appConfigService.getTwilioAccountSid());
        result.put("auth_token", mask(appConfigService.getTwilioAuthToken()));
        result.put("caller_id", appConfigService.getTwilioCallerId());
        result.put("app_sid", appConfigService.getTwilioAppSid());
        result.put("api_key", appConfigService.getTwilioApiKey());
        result.put("api_secret", mask(appConfigService.getTwilioApiSecret()));
        return ResponseEntity.ok(result);
    }

    // ─── Razorpay config ──────────────────────────────────────────────────────

    /**
     * GET /api/config/razorpay
     * Returns all Razorpay config values. key_secret is masked.
     */
    @GetMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> getRazorpayConfig() {
        log.info("Fetching Razorpay configuration");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key_id", appConfigService.getRazorpayKeyId());
        result.put("key_secret", mask(appConfigService.getRazorpayKeySecret()));
        result.put("currency", appConfigService.getRazorpayCurrency());
        result.put("min_amount", appConfigService.getRazorpayMinAmount());
        result.put("max_amount", appConfigService.getRazorpayMaxAmount());
        return ResponseEntity.ok(result);
    }

    /**
     * PUT /api/config/razorpay
     * Updates one or more Razorpay config values.
     *
     * <p>Request body (all fields optional):
     * <pre>
     * {
     *   "key_id":     "rzp_live_xxx",
     *   "key_secret": "xxxxxxx",
     *   "currency":   "INR",
     *   "min_amount": 5.0,
     *   "max_amount": 10000.0
     * }
     * </pre>
     */
    @PutMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> updateRazorpayConfig(@RequestBody Map<String, Object> body) {
        log.info("Updating Razorpay configuration");
        int updated = 0;

        String keyId = getString(body, "key_id", "keyId");
        if (keyId != null) {
            appConfigService.setRazorpayKeyId(keyId);
            updated++;
        }

        String keySecret = getString(body, "key_secret", "keySecret");
        if (keySecret != null) {
            appConfigService.setRazorpayKeySecret(keySecret);
            updated++;
        }

        String currency = getString(body, "currency");
        if (currency != null) {
            appConfigService.setRazorpayCurrency(currency);
            updated++;
        }

        Double minAmount = getDouble(body, "min_amount", "minAmount");
        if (minAmount != null) {
            appConfigService.setRazorpayMinAmount(minAmount);
            updated++;
        }

        Double maxAmount = getDouble(body, "max_amount", "maxAmount");
        if (maxAmount != null) {
            appConfigService.setRazorpayMaxAmount(maxAmount);
            updated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Razorpay configuration updated");
        result.put("fields_updated", updated);
        result.put("key_id", appConfigService.getRazorpayKeyId());
        result.put("key_secret", mask(appConfigService.getRazorpayKeySecret()));
        result.put("currency", appConfigService.getRazorpayCurrency());
        result.put("min_amount", appConfigService.getRazorpayMinAmount());
        result.put("max_amount", appConfigService.getRazorpayMaxAmount());
        return ResponseEntity.ok(result);
    }

    // ─── Wallet config ────────────────────────────────────────────────────────

    /**
     * GET /api/config/wallet
     * Returns all wallet-related config values.
     */
    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWalletConfig() {
        log.info("Fetching wallet configuration");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registration_reward", appConfigService.getWalletRegistrationReward());
        result.put("minimum_balance", appConfigService.getWalletMinimumBalance());
        result.put("call_rate_per_minute", appConfigService.getCallRatePerMinute());
        result.put("minimum_call_charge", appConfigService.getMinimumCallCharge());
        result.put("max_concurrent_calls", appConfigService.getMaxConcurrentCalls());
        result.put("outbound_twiml_ttl_minutes", appConfigService.getOutboundCallTwimlTtlMinutes());
        return ResponseEntity.ok(result);
    }

    /**
     * PUT /api/config/wallet
     * Updates wallet/billing config values.
     *
     * <p>Request body (all fields optional):
     * <pre>
     * {
     *   "registration_reward":        3.0,
     *   "minimum_balance":            1.0,
     *   "call_rate_per_minute":       10.0,
     *   "minimum_call_charge":        0.01,
     *   "max_concurrent_calls":       10,
     *   "outbound_twiml_ttl_minutes": 15
     * }
     * </pre>
     */
    @PutMapping("/wallet")
    public ResponseEntity<Map<String, Object>> updateWalletConfig(@RequestBody Map<String, Object> body) {
        log.info("Updating wallet configuration");
        int updated = 0;

        Double registrationReward = getDouble(body, "registration_reward", "registrationReward");
        if (registrationReward != null) {
            appConfigService.setWalletRegistrationReward(registrationReward);
            updated++;
        }

        Double minimumBalance = getDouble(body, "minimum_balance", "minimumBalance");
        if (minimumBalance != null) {
            appConfigService.setWalletMinimumBalance(minimumBalance);
            updated++;
        }

        Double callRate = getDouble(body, "call_rate_per_minute", "callRatePerMinute");
        if (callRate != null) {
            appConfigService.setConfig(AppConfigService.CALL_RATE_PER_MINUTE, String.valueOf(callRate), "DECIMAL", "Call rate per minute used for billing");
            updated++;
        }

        Double minCallCharge = getDouble(body, "minimum_call_charge", "minimumCallCharge");
        if (minCallCharge != null && minCallCharge >= 0) {
            appConfigService.upsertMinimumCallCharge(minCallCharge);
            updated++;
        }

        Integer maxConcurrent = getInteger(body, "max_concurrent_calls", "maxConcurrentCalls");
        if (maxConcurrent != null) {
            appConfigService.setConfig(AppConfigService.MAX_CONCURRENT_CALLS, String.valueOf(maxConcurrent), "INTEGER", "Maximum number of concurrent calls");
            updated++;
        }

        Integer ttlMinutes = getInteger(body, "outbound_twiml_ttl_minutes", "outboundTwimlTtlMinutes");
        if (ttlMinutes != null) {
            appConfigService.setOutboundCallTwimlTtlMinutes(ttlMinutes);
            updated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Wallet configuration updated");
        result.put("fields_updated", updated);
        result.put("registration_reward", appConfigService.getWalletRegistrationReward());
        result.put("minimum_balance", appConfigService.getWalletMinimumBalance());
        result.put("call_rate_per_minute", appConfigService.getCallRatePerMinute());
        result.put("minimum_call_charge", appConfigService.getMinimumCallCharge());
        result.put("max_concurrent_calls", appConfigService.getMaxConcurrentCalls());
        result.put("outbound_twiml_ttl_minutes", appConfigService.getOutboundCallTwimlTtlMinutes());
        return ResponseEntity.ok(result);
    }

    // ─── Dashboard message ────────────────────────────────────────────────────

    @GetMapping("/dashboard-message")
    public ResponseEntity<Map<String, Object>> getDashboardMessage() {
        return ResponseEntity.ok(appConfigService.getDashboardMessage());
    }

    @PostMapping("/dashboard-message")
    public ResponseEntity<?> updateDashboardMessage(@RequestBody Map<String, Object> data) {
        String message = getString(data, "dashboard_message", "message", "config_value", "configValue");
        Boolean enabled = getBoolean(data, "dashboard_message_enabled", "enabled", "is_enabled", "isEnabled");
        if (message == null || enabled == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("message/dashboard_message and enabled/dashboard_message_enabled are required"));
        }
        appConfigService.updateDashboardMessage(message, enabled);
        return ResponseEntity.ok(Map.of("message", "Dashboard message updated successfully"));
    }

    // ─── Maintenance mode ─────────────────────────────────────────────────────

    @GetMapping("/maintenance-mode")
    public ResponseEntity<Map<String, Object>> getMaintenanceMode() {
        return ResponseEntity.ok(appConfigService.getMaintenanceStatus());
    }

    @PostMapping("/maintenance-mode")
    public ResponseEntity<?> updateMaintenanceMode(@RequestBody Map<String, Object> data) {
        Boolean enabled = getBoolean(data, "maintenance_mode", "enabled", "is_enabled", "isEnabled");
        String message = getString(data, "maintenance_message", "message", "config_value", "configValue");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("maintenance_mode/enabled is required"));
        }
        appConfigService.updateMaintenanceMode(enabled, message);
        return ResponseEntity.ok(Map.of("message", "Maintenance mode updated successfully"));
    }

    // ─── Feature flags ────────────────────────────────────────────────────────

    /**
     * GET /api/config/feature-flags
     * Returns all feature-flag values so the client can re-read them live
     * (e.g. before opening a login dialog) without a full page reload.
     */
    @GetMapping("/feature-flags")
    public ResponseEntity<Map<String, Object>> getFeatureFlags() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enableGoogleSignIn", appConfigService.isGoogleSignInEnabled());
        return ResponseEntity.ok(result);
    }

    // ─── Minimum call charge ──────────────────────────────────────────────────

    @GetMapping("/minimum-call-charge")
    public ResponseEntity<Map<String, Object>> getMinimumCallCharge() {
        double value = appConfigService.getMinimumCallCharge();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", AppConfigService.MINIMUM_CALL_CHARGE);
        result.put("value", value);
        result.put("description", "Minimum charge per call in rupees");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/minimum-call-charge")
    public ResponseEntity<?> setMinimumCallCharge(@RequestBody Map<String, Object> body) {
        Object rawValue = body.get("value");
        if (rawValue == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("'value' is required"));
        }
        double amount;
        try {
            amount = Double.parseDouble(rawValue.toString());
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(createErrorResponse("'value' must be a valid decimal number"));
        }
        if (amount < 0) {
            return ResponseEntity.badRequest().body(createErrorResponse("'value' must be >= 0"));
        }
        AppConfig saved = appConfigService.upsertMinimumCallCharge(amount);
        return ResponseEntity.ok(Map.of("key", saved.getConfigKey(), "value", amount, "message", "minimum_call_charge updated successfully"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getString(Map<String, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null)
                return String.valueOf(value);
        }
        return null;
    }

    private Boolean getBoolean(Map<String, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Boolean b)
                return b;
            if (value != null) {
                String text = String.valueOf(value).trim();
                if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text))
                    return true;
                if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text))
                    return false;
            }
        }
        return null;
    }

    private Double getDouble(Map<String, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number n)
                return n.doubleValue();
            if (value != null) {
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number n)
                return n.intValue();
            if (value != null) {
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return response;
    }

    /**
     * Masks a sensitive string — shows nothing if blank, otherwise shows {@code ***masked***}.
     */
    private String mask(String value) {
        if (value == null || value.isBlank() || value.startsWith("replace_with"))
            return "(not configured)";
        return MASKED;
    }
}
