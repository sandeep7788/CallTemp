package com.example.tempp.service;

import com.example.tempp.model.AppConfig;
import com.example.tempp.repository.AppConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing application configuration settings stored in Firestore.
 * All Twilio, Razorpay, and wallet settings are stored here instead of
 * application.properties so they can be updated without redeployment.
 */
@Slf4j
@Service
public class AppConfigService {

    // ── Existing operational keys ─────────────────────────────────────────────
    public static final String DASHBOARD_MESSAGE = "dashboard_message";
    public static final String DASHBOARD_MESSAGE_ENABLED = "dashboard_message_enabled";
    public static final String MAINTENANCE_MODE = "maintenance_mode";
    public static final String MAINTENANCE_MESSAGE = "maintenance_message";
    public static final String DEFAULT_MAINTENANCE_MESSAGE = "The application is currently under maintenance. Please try again later.";
    public static final String MAX_CONCURRENT_CALLS = "max_concurrent_calls";
    public static final String CALL_RATE_PER_MINUTE = "call_rate_per_minute";
    /**
     * Minimum charge applied per call (rupees).
     * Ensures short calls (e.g. 2 seconds) are charged at least this amount.
     * Default: ₹0.01. Configurable via Firebase App Config.
     */
    public static final String MINIMUM_CALL_CHARGE = "minimum_call_charge";
    /**
     * Safe fallback used when the Firestore config entry is absent or invalid.
     */
    public static final double DEFAULT_MINIMUM_CALL_CHARGE = 0.01;

    // ── Twilio credentials & settings ─────────────────────────────────────────
    public static final String TWILIO_ACCOUNT_SID = "twilio_account_sid";
    public static final String TWILIO_AUTH_TOKEN = "twilio_auth_token";
    public static final String TWILIO_CALLER_ID = "twilio_caller_id";
    public static final String TWILIO_APP_SID = "twilio_app_sid";
    public static final String TWILIO_API_KEY = "twilio_api_key";
    public static final String TWILIO_API_SECRET = "twilio_api_secret";
    public static final String TWILIO_INCOMING_CLIENT_IDENTITY = "twilio_incoming_client_identity";

    // ── Payment gateways ──────────────────────────────────────────────────────
    public static final String PAYMENT_DEFAULT_GATEWAY = "payment_default_gateway";

    // ── Razorpay payment gateway ──────────────────────────────────────────────
    public static final String RAZORPAY_KEY_ID = "razorpay_key_id";
    public static final String RAZORPAY_KEY_SECRET = "razorpay_key_secret";
    public static final String RAZORPAY_CURRENCY = "razorpay_currency";
    public static final String RAZORPAY_MIN_AMOUNT = "razorpay_min_amount";
    public static final String RAZORPAY_MAX_AMOUNT = "razorpay_max_amount";

    // ── Cashfree payment gateway ──────────────────────────────────────────────
    public static final String CASHFREE_APP_ID = "cashfree_app_id";
    public static final String CASHFREE_SECRET_KEY = "cashfree_secret_key";
    public static final String CASHFREE_API_VERSION = "cashfree_api_version";
    public static final String CASHFREE_ENABLED = "cashfree_enabled";
    public static final String CASHFREE_SANDBOX = "cashfree_sandbox";
    public static final String CASHFREE_CURRENCY = "cashfree_currency";

    // ── Wallet settings ───────────────────────────────────────────────────────
    public static final String WALLET_REGISTRATION_REWARD = "wallet_registration_reward";
    public static final String WALLET_MINIMUM_BALANCE = "wallet_minimum_balance";

    // ── Feature flags ─────────────────────────────────────────────────────────
    /**
     * When {@code true} (default), the Google Sign-In flow is mandatory.
     * When {@code false}, a simple phone-number login is used instead and
     * no Google authentication UI or API calls are triggered.
     */
    public static final String ENABLE_GOOGLE_SIGN_IN = "enable_google_sign_in";

    // ── Outbound call settings ────────────────────────────────────────────────
    public static final String OUTBOUND_CALL_TWIML_TTL_MINUTES = "outbound_call_twiml_ttl_minutes";

    // ── Call billing & auto-disconnect ────────────────────────────────────────
    /**
     * Billing granularity in seconds. Default: 1 (per-second billing).
     * Set to 60 for per-minute rounding. Charges are rounded up to the nearest
     * complete interval.
     */
    public static final String BILLING_INTERVAL_SECONDS  = "billing_interval_seconds";
    /**
     * Hard cap on call duration. Twilio disconnects the call after this many
     * seconds regardless of wallet balance. Default: 3600 (1 hour).
     */
    public static final String MAX_CALL_DURATION_SECONDS = "max_call_duration_seconds";
    /**
     * Wallet balance threshold that triggers auto-disconnect. The call is
     * disconnected when the remaining balance falls to or below this value.
     * Default: 0.0 (disconnect when balance reaches zero).
     */
    public static final String AUTO_DISCONNECT_THRESHOLD = "auto_disconnect_threshold";

    private final AppConfigRepository appConfigRepository;
    /**
     * @Lazy breaks the circular dependency: AppConfigService ↔ TwilioCallControlService
     */
    private final TwilioCallControlService twilioCallControlService;

    public AppConfigService(AppConfigRepository appConfigRepository, @Lazy TwilioCallControlService twilioCallControlService) {
        this.appConfigRepository = appConfigRepository;
        this.twilioCallControlService = twilioCallControlService;
    }

    // ─── Read helpers ────────────────────────────────────────────────────────

    public String getConfigValue(String key) {
        return appConfigRepository.findByConfigKey(normalizeKey(key)).filter(AppConfig::isEnabled).map(AppConfig::getConfigValue).orElse(null);
    }

    public String getConfigValue(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }

    public boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = getConfigValue(key);
        if (value == null)
            return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public int getIntegerConfig(String key, int defaultValue) {
        String value = getConfigValue(key);
        if (value == null || value.isBlank())
            return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public double getDecimalConfig(String key, double defaultValue) {
        String value = getConfigValue(key);
        if (value == null || value.isBlank())
            return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    // ── Twilio getters ────────────────────────────────────────────────────────

    public String getTwilioAccountSid() {
        return getConfigValue(TWILIO_ACCOUNT_SID, "");
    }

    public void setTwilioAccountSid(String value) {
        setConfig(TWILIO_ACCOUNT_SID, value, "STRING", "Twilio Account SID");
    }

    public String getTwilioAuthToken() {
        return getConfigValue(TWILIO_AUTH_TOKEN, "");
    }

    public void setTwilioAuthToken(String value) {
        setConfig(TWILIO_AUTH_TOKEN, value, "STRING", "Twilio Auth Token");
    }

    public String getTwilioCallerId() {
        return getConfigValue(TWILIO_CALLER_ID, "");
    }

    public void setTwilioCallerId(String value) {
        setConfig(TWILIO_CALLER_ID, value, "STRING", "Twilio default caller ID / trial number");
    }

    public String getTwilioAppSid() {
        return getConfigValue(TWILIO_APP_SID, "");
    }

    public void setTwilioAppSid(String value) {
        setConfig(TWILIO_APP_SID, value, "STRING", "Twilio TwiML App SID");
    }

    public String getTwilioApiKey() {
        return getConfigValue(TWILIO_API_KEY, "");
    }

    public void setTwilioApiKey(String value) {
        setConfig(TWILIO_API_KEY, value, "STRING", "Twilio API Key (SK...)");
    }

    public String getTwilioApiSecret() {
        return getConfigValue(TWILIO_API_SECRET, "");
    }

    public void setTwilioApiSecret(String value) {
        setConfig(TWILIO_API_SECRET, value, "STRING", "Twilio API Secret");
    }

    public String getTwilioIncomingClientIdentity() {
        return getConfigValue(TWILIO_INCOMING_CLIENT_IDENTITY, "");
    }

    public void setTwilioIncomingClientIdentity(String value) {
        setConfig(TWILIO_INCOMING_CLIENT_IDENTITY, value, "STRING", "Default Twilio Voice SDK client identity for inbound PSTN calls");
    }

    // ── Razorpay getters ──────────────────────────────────────────────────────

    public String getPaymentDefaultGateway() {
        String gateway = getConfigValue(PAYMENT_DEFAULT_GATEWAY, "razorpay");
        return gateway == null || gateway.isBlank() ? "razorpay" : gateway.trim().toLowerCase();
    }

    public String getRazorpayKeyId() {
        return getConfigValue(RAZORPAY_KEY_ID, "rzp_test_replace_with_your_key_id");
    }

    public void setRazorpayKeyId(String value) {
        setConfig(RAZORPAY_KEY_ID, value, "STRING", "Razorpay Key ID (rzp_...)");
    }

    public String getRazorpayKeySecret() {
        return getConfigValue(RAZORPAY_KEY_SECRET, "replace_with_your_key_secret");
    }

    public void setRazorpayKeySecret(String value) {
        setConfig(RAZORPAY_KEY_SECRET, value, "STRING", "Razorpay Key Secret");
    }

    public String getRazorpayCurrency() {
        return getConfigValue(RAZORPAY_CURRENCY, "INR");
    }

    public void setRazorpayCurrency(String value) {
        setConfig(RAZORPAY_CURRENCY, value, "STRING", "Razorpay currency code (e.g. INR)");
    }

    public double getRazorpayMinAmount() {
        return getDecimalConfig(RAZORPAY_MIN_AMOUNT, 5.0);
    }

    public void setRazorpayMinAmount(double value) {
        setConfig(RAZORPAY_MIN_AMOUNT, String.valueOf(value), "DECIMAL", "Minimum Razorpay recharge amount (rupees)");
    }

    public double getRazorpayMaxAmount() {
        return getDecimalConfig(RAZORPAY_MAX_AMOUNT, 10000.0);
    }

    public void setRazorpayMaxAmount(double value) {
        setConfig(RAZORPAY_MAX_AMOUNT, String.valueOf(value), "DECIMAL", "Maximum Razorpay recharge amount (rupees)");
    }

    // ── Cashfree getters ──────────────────────────────────────────────────────

    public String getCashfreeAppId() {
        return getConfigValue(CASHFREE_APP_ID, "replace_with_cashfree_app_id");
    }

    public String getCashfreeSecretKey() {
        return getConfigValue(CASHFREE_SECRET_KEY, "replace_with_cashfree_secret_key");
    }

    public String getCashfreeApiVersion() {
        return getConfigValue(CASHFREE_API_VERSION, "2023-08-01");
    }

    public boolean isCashfreeEnabled() {
        return getBooleanConfig(CASHFREE_ENABLED, false);
    }

    public boolean isCashfreeSandbox() {
        return getBooleanConfig(CASHFREE_SANDBOX, true);
    }

    public String getCashfreeCurrency() {
        return getConfigValue(CASHFREE_CURRENCY, "INR");
    }

    // ── Wallet getters ────────────────────────────────────────────────────────

    public double getWalletRegistrationReward() {
        return getDecimalConfig(WALLET_REGISTRATION_REWARD, UserService.REGISTRATION_REWARD);
    }

    public void setWalletRegistrationReward(double value) {
        setConfig(WALLET_REGISTRATION_REWARD, String.valueOf(value), "DECIMAL", "Welcome credit given to every new user on registration (rupees)");
    }

    public double getWalletMinimumBalance() {
        return getDecimalConfig(WALLET_MINIMUM_BALANCE, UserService.MINIMUM_WALLET_BALANCE);
    }

    public void setWalletMinimumBalance(double value) {
        setConfig(WALLET_MINIMUM_BALANCE, String.valueOf(value), "DECIMAL", "Minimum wallet balance required to place a call (rupees)");
    }

    // ── Outbound call getters ─────────────────────────────────────────────────

    public int getOutboundCallTwimlTtlMinutes() {
        return getIntegerConfig(OUTBOUND_CALL_TWIML_TTL_MINUTES, 15);
    }

    public void setOutboundCallTwimlTtlMinutes(int value) {
        setConfig(OUTBOUND_CALL_TWIML_TTL_MINUTES, String.valueOf(value), "INTEGER", "TTL in minutes for outbound call TwiML URLs");
    }

    // ── Billing & auto-disconnect getters ─────────────────────────────────────

    /**
     * Returns the billing interval in seconds (minimum 1).
     * Calls are billed in multiples of this value (ceiling rounding).
     */
    public int getBillingIntervalSeconds() {
        int value = getIntegerConfig(BILLING_INTERVAL_SECONDS, 1);
        return Math.max(1, value);
    }

    public void setBillingIntervalSeconds(int value) {
        setConfig(BILLING_INTERVAL_SECONDS, String.valueOf(value), "INTEGER",
                "Billing interval in seconds for call charging (1 = per-second billing)");
    }

    /**
     * Returns the maximum allowed call duration in seconds (minimum 1).
     * Twilio will auto-disconnect the call when this limit is reached.
     */
    public int getMaxCallDurationSeconds() {
        int value = getIntegerConfig(MAX_CALL_DURATION_SECONDS, 3600);
        return Math.max(1, value);
    }

    public void setMaxCallDurationSeconds(int value) {
        setConfig(MAX_CALL_DURATION_SECONDS, String.valueOf(value), "INTEGER",
                "Maximum allowed call duration in seconds (default 3600 = 1 hour)");
    }

    /**
     * Returns the wallet balance threshold that triggers auto-disconnect.
     * A value of 0 means disconnect when the balance reaches exactly zero.
     */
    public double getAutoDisconnectThreshold() {
        double value = getDecimalConfig(AUTO_DISCONNECT_THRESHOLD, 0.0);
        return Math.max(0.0, value);
    }

    public void setAutoDisconnectThreshold(double value) {
        setConfig(AUTO_DISCONNECT_THRESHOLD, String.valueOf(value), "DECIMAL",
                "Minimum wallet balance that triggers auto-disconnect during a call (0 = disconnect at zero)");
    }

    // ── Existing operational getters ──────────────────────────────────────────

    public int getMaxConcurrentCalls() {
        return getIntegerConfig(MAX_CONCURRENT_CALLS, 10);
    }

    public double getCallRatePerMinute() {
        return getDecimalConfig(CALL_RATE_PER_MINUTE, UserService.CALL_RATE_PER_MINUTE);
    }

    /**
     * Returns the minimum charge applied per call in rupees.
     * Read from Firebase App Config key {@code minimum_call_charge}.
     * Falls back to {@link #DEFAULT_MINIMUM_CALL_CHARGE} when missing or invalid.
     */
    public double getMinimumCallCharge() {
        double value = getDecimalConfig(MINIMUM_CALL_CHARGE, DEFAULT_MINIMUM_CALL_CHARGE);
        if (value < 0) {
            log.warn("Config '{}' has invalid negative value {}. Using default ₹{}.", MINIMUM_CALL_CHARGE, value, DEFAULT_MINIMUM_CALL_CHARGE);
            return DEFAULT_MINIMUM_CALL_CHARGE;
        }
        return value;
    }

    public boolean isMaintenanceModeEnabled() {
        return getBooleanConfig(MAINTENANCE_MODE, false);
    }

    /**
     * Returns {@code true} (default) when Google Sign-In is enabled.
     * When {@code false}, the simple phone-number login flow is used instead.
     * Controlled by the {@code enable_google_sign_in} DB Config entry.
     */
    public boolean isGoogleSignInEnabled() {
        return getBooleanConfig(ENABLE_GOOGLE_SIGN_IN, true);
    }

    public String getMaintenanceMessage() {
        String msg = getConfigValue(MAINTENANCE_MESSAGE, DEFAULT_MAINTENANCE_MESSAGE);
        return (msg == null || msg.isBlank()) ? DEFAULT_MAINTENANCE_MESSAGE : msg;
    }

    public Map<String, Object> getDashboardMessage() {
        Map<String, Object> result = new HashMap<>();
        String message = getConfigValue(DASHBOARD_MESSAGE, "");
        boolean enabled = getBooleanConfig(DASHBOARD_MESSAGE_ENABLED, false);
        result.put("message", message);
        result.put("enabled", enabled);
        result.put("dashboard_message", message);
        result.put("dashboard_message_enabled", enabled);
        return result;
    }

    public Map<String, Object> getMaintenanceStatus() {
        Map<String, Object> result = new HashMap<>();
        boolean enabled = isMaintenanceModeEnabled();
        String message = getMaintenanceMessage();
        result.put("enabled", enabled);
        result.put("message", message);
        result.put("maintenance_mode", enabled);
        result.put("maintenance_message", message);
        return result;
    }

    // ─── Write helpers ───────────────────────────────────────────────────────

    public AppConfig setConfig(String key, String value, String type) {
        return setConfig(key, value, type, null);
    }

    public AppConfig setConfig(String key, String value, String type, String description) {
        String normalizedKey = normalizeKey(key);
        log.info("Setting config: key={}, value={}, type={}", normalizedKey, maskSensitiveValue(normalizedKey, value), type);

        Optional<AppConfig> existingOpt = appConfigRepository.findByConfigKey(normalizedKey);
        AppConfig config = existingOpt.orElseGet(() -> new AppConfig(normalizedKey, value, type));
        config.setConfigValue(value);
        config.setConfigType(type);
        if (description != null)
            config.setDescription(description);
        config.setEnabled(true);

        AppConfig saved = appConfigRepository.save(config);
        disconnectCallsIfMaintenanceEnabled(normalizedKey, value);
        return saved;
    }

    public void updateDashboardMessage(String message, boolean enabled) {
        setConfig(DASHBOARD_MESSAGE, message == null ? "" : message.trim(), "STRING", "Dashboard announcement message");
        setConfig(DASHBOARD_MESSAGE_ENABLED, String.valueOf(enabled), "BOOLEAN", "Enable/disable dashboard message");
    }

    public void updateMaintenanceMode(boolean enabled, String message) {
        setConfig(MAINTENANCE_MODE, String.valueOf(enabled), "BOOLEAN", "Enable/disable maintenance mode");
        if (message != null) {
            setConfig(MAINTENANCE_MESSAGE, message.trim(), "STRING", "Maintenance mode message");
        } else
            if (getConfigValue(MAINTENANCE_MESSAGE) == null) {
                setConfig(MAINTENANCE_MESSAGE, DEFAULT_MAINTENANCE_MESSAGE, "STRING", "Maintenance mode message");
            }
    }

    public AppConfig toggleEnabled(String key) {
        String normalizedKey = normalizeKey(key);
        AppConfig config = appConfigRepository.findByConfigKey(normalizedKey).orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + normalizedKey));
        config.setEnabled(!config.isEnabled());
        return appConfigRepository.save(config);
    }

    public AppConfig setEnabled(String key, boolean enabled) {
        String normalizedKey = normalizeKey(key);
        AppConfig config = appConfigRepository.findByConfigKey(normalizedKey).orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + normalizedKey));
        config.setEnabled(enabled);
        return appConfigRepository.save(config);
    }

    public List<AppConfig> getAllConfigs() {
        return appConfigRepository.findAll();
    }

    public Optional<AppConfig> getConfig(String key) {
        return appConfigRepository.findByConfigKey(normalizeKey(key));
    }

    public void deleteConfig(String key) {
        String normalizedKey = normalizeKey(key);
        AppConfig config = appConfigRepository.findByConfigKey(normalizedKey).orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + normalizedKey));
        appConfigRepository.delete(config);
    }

    public void initializeDefaults() {
        log.info("Initializing default configurations");

        // ── Operational settings ──────────────────────────────────────────────
        initIfAbsent(DASHBOARD_MESSAGE, "", "STRING", "Dashboard announcement message");
        initIfAbsent(DASHBOARD_MESSAGE_ENABLED, "false", "BOOLEAN", "Enable/disable dashboard message");
        initIfAbsent(MAINTENANCE_MODE, "false", "BOOLEAN", "Enable/disable maintenance mode");
        initIfAbsent(MAINTENANCE_MESSAGE, DEFAULT_MAINTENANCE_MESSAGE, "STRING", "Maintenance mode message");
        initIfAbsent(MAX_CONCURRENT_CALLS, "10", "INTEGER", "Maximum number of concurrent calls");
        initIfAbsent(CALL_RATE_PER_MINUTE, String.valueOf(UserService.CALL_RATE_PER_MINUTE), "DECIMAL", "Call rate per minute used for billing");
        // Ensure minimum_call_charge always exists (upsert so existing installs get it)
        upsertMinimumCallCharge(DEFAULT_MINIMUM_CALL_CHARGE);

        // ── Twilio credentials ────────────────────────────────────────────────
        initIfAbsent(TWILIO_ACCOUNT_SID, "replace_with_twilio_account_sid", "STRING", "Twilio Account SID (starts with AC)");
        initIfAbsent(TWILIO_AUTH_TOKEN, "replace_with_twilio_auth_token", "STRING", "Twilio Auth Token");
        initIfAbsent(TWILIO_CALLER_ID, "replace_with_twilio_caller_id", "STRING", "[DEPRECATED] Legacy single caller ID – add numbers via the twilio_numbers pool instead (POST /api/twilio-numbers). Setting this will auto-add it to the pool.");
        initIfAbsent(TWILIO_APP_SID, "replace_with_twilio_app_sid", "STRING", "Twilio TwiML App SID (starts with AP)");
        initIfAbsent(TWILIO_API_KEY, "replace_with_twilio_api_key", "STRING", "Twilio API Key (starts with SK)");
        initIfAbsent(TWILIO_API_SECRET, "replace_with_your_twilio_api_secret_here", "STRING", "Twilio API Secret");
        initIfAbsent(TWILIO_INCOMING_CLIENT_IDENTITY, "", "STRING", "Optional default Twilio Voice SDK client identity for inbound PSTN calls (for example client-abc12345)");

        // ── Razorpay payment gateway ──────────────────────────────────────────
        initIfAbsent(PAYMENT_DEFAULT_GATEWAY, "razorpay", "STRING", "Default payment gateway when request does not specify one (razorpay or cashfree)");
        initIfAbsent(RAZORPAY_KEY_ID, "rzp_test_replace_with_your_key_id", "STRING", "Razorpay Key ID (rzp_test_... or rzp_live_...)");
        initIfAbsent(RAZORPAY_KEY_SECRET, "replace_with_your_key_secret", "STRING", "Razorpay Key Secret");
        initIfAbsent(RAZORPAY_CURRENCY, "INR", "STRING", "Razorpay currency code");
        initIfAbsent(RAZORPAY_MIN_AMOUNT, "5.0", "DECIMAL", "Minimum Razorpay recharge amount (rupees)");
        initIfAbsent(RAZORPAY_MAX_AMOUNT, "10000.0", "DECIMAL", "Maximum Razorpay recharge amount (rupees)");

        // ── Cashfree payment gateway ──────────────────────────────────────────
        initIfAbsent(CASHFREE_APP_ID, "replace_with_cashfree_app_id", "STRING", "Cashfree App ID / Client ID");
        initIfAbsent(CASHFREE_SECRET_KEY, "replace_with_cashfree_secret_key", "STRING", "Cashfree Secret Key / Client Secret");
        initIfAbsent(CASHFREE_API_VERSION, "2023-08-01", "STRING", "Cashfree Payments API version");
        initIfAbsent(CASHFREE_ENABLED, "false", "BOOLEAN", "Enable Cashfree wallet recharge gateway");
        initIfAbsent(CASHFREE_SANDBOX, "true", "BOOLEAN", "Use Cashfree sandbox endpoints when true, production endpoints when false");
        initIfAbsent(CASHFREE_CURRENCY, "INR", "STRING", "Cashfree currency code");

        // ── Wallet settings ───────────────────────────────────────────────────
        initIfAbsent(WALLET_REGISTRATION_REWARD, String.valueOf(UserService.REGISTRATION_REWARD), "DECIMAL", "Welcome credit given to every new user on registration (rupees)");
        initIfAbsent(WALLET_MINIMUM_BALANCE, String.valueOf(UserService.MINIMUM_WALLET_BALANCE), "DECIMAL", "Minimum wallet balance required to place a call (rupees)");

        // ── Outbound call TTL ─────────────────────────────────────────────────
        initIfAbsent(OUTBOUND_CALL_TWIML_TTL_MINUTES, "15", "INTEGER", "TTL in minutes for outbound call TwiML URLs");

        // ── Call billing & auto-disconnect ────────────────────────────────────
        initIfAbsent(BILLING_INTERVAL_SECONDS,  "1",    "INTEGER", "Billing interval in seconds for call charging (1 = per-second billing)");
        initIfAbsent(MAX_CALL_DURATION_SECONDS, "3600", "INTEGER", "Maximum allowed call duration in seconds (default 1 hour)");
        initIfAbsent(AUTO_DISCONNECT_THRESHOLD, "0.0",  "DECIMAL", "Minimum wallet balance that triggers auto-disconnect (0 = disconnect at zero balance)");

        // ── Feature flags ─────────────────────────────────────────────────────
        initIfAbsent(ENABLE_GOOGLE_SIGN_IN, "true", "BOOLEAN", "Enable Google Sign-In for user authentication. Set to false to use simple phone-number login instead.");

        log.info("Default configurations initialized.");
    }

    /**
     * Creates or updates the {@code minimum_call_charge} config entry in Firestore.
     * Calling this at startup guarantees the key exists even in older installations
     * that were initialized before this config was introduced.
     *
     * @param value the minimum call charge in rupees (e.g. {@code 0.01})
     */
    public AppConfig upsertMinimumCallCharge(double value) {
        if (value < 0)
            throw new IllegalArgumentException("minimum_call_charge must be >= 0");
        log.info("Upserting config: key={}, value={}", MINIMUM_CALL_CHARGE, value);
        return setConfig(MINIMUM_CALL_CHARGE, String.valueOf(value), "DECIMAL", "Minimum charge per call in rupees (applied when calculated charge is less)");
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void initIfAbsent(String key, String value, String type, String description) {
        if (!appConfigRepository.existsByConfigKey(key)) {
            setConfig(key, value, type, description);
        }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Configuration key is required");
        return key.trim().toLowerCase();
    }

    private String maskSensitiveValue(String normalizedKey, String value) {
        if (value == null) {
            return null;
        }
        if (normalizedKey.contains("auth_token") || normalizedKey.contains("secret")) {
            return "[REDACTED]";
        }
        return value;
    }

    private void disconnectCallsIfMaintenanceEnabled(String key, String value) {
        if (MAINTENANCE_MODE.equals(key) && ("true".equalsIgnoreCase(value) || "1".equals(value))) {
            try {
                int count = twilioCallControlService.disconnectAllOngoingCalls();
                log.info("Maintenance mode enabled. Force disconnect attempted for {} call(s).", count);
            } catch (Exception ex) {
                log.warn("Could not disconnect calls on maintenance enable: {}", ex.getMessage());
            }
        }
    }
}
