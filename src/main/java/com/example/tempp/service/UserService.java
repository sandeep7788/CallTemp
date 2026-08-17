package com.example.tempp.service;

import com.example.tempp.exception.BlockedUserException;
import com.example.tempp.model.*;
import com.example.tempp.repository.CallHistoryRepository;
import com.example.tempp.repository.UserAccountRepository;
import com.example.tempp.repository.WalletTransactionRepository;import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.twilio.jwt.accesstoken.AccessToken;
import com.twilio.jwt.accesstoken.VoiceGrant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Twilio user tokens, identities and wallet balance.
 */
@Slf4j
@Service
public class UserService {

    /**
     * ₹1 minimum wallet balance required before placing a call.
     */
    public static final double MINIMUM_WALLET_BALANCE = 1.0;
    /**
     * ₹10 per minute call rate.
     */
    public static final double CALL_RATE_PER_MINUTE = 10.0;
    /**
     * ₹3 welcome credit for every new registration.
     */
    public static final double REGISTRATION_REWARD = 3.0;

    private final UserAccountRepository userAccountRepository;
    private final CallHistoryRepository callHistoryRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AppConfigService appConfigService;
    private final Firestore firestore;
    private final FirebaseApp firebaseApp;


    public UserService(UserAccountRepository userAccountRepository, CallHistoryRepository callHistoryRepository, WalletTransactionRepository walletTransactionRepository, AppConfigService appConfigService, Firestore firestore, FirebaseApp firebaseApp) {
        this.userAccountRepository = userAccountRepository;
        this.callHistoryRepository = callHistoryRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.appConfigService = appConfigService;
        this.firestore = firestore;
        this.firebaseApp = firebaseApp;
    }

    // ─── Login / Registration ────────────────────────────────────────────────

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ─── Google Sign-In ──────────────────────────────────────────────────────

    public UserSession login(LoginRequest request) {
        validateLoginRequest(request);

        String normalizedNumber = request.getNumber().trim();
        String deviceIdentifier = request.getDeviceIdentifier();

        // Load existing user or build a new one
        UserAccount user = userAccountRepository.findByNumber(normalizedNumber).orElseGet(UserAccount::new);

        boolean isNewUser = (user.getId() == null || user.getId().isBlank());

        if (!isNewUser && user.isBlocked()) {
            log.error("Blocked user login attempt - Number: {}", normalizedNumber);
            throw new BlockedUserException("Your account has been blocked. Please contact support for assistance.");
        }

        // Device identifier validation (log-only)
        if (!isNewUser && user.getDeviceIdentifier() != null && !user.getDeviceIdentifier().isBlank()) {
            if (deviceIdentifier == null || !deviceIdentifier.equals(user.getDeviceIdentifier())) {
                log.warn("Device mismatch for user - Number: {}", normalizedNumber);
            }
        }

        user.setName(request.getName().trim());
        user.setNumber(normalizedNumber);
        user.setLocation(emptyToNull(request.getLocation()));
        user.setActive(true);
        user.setTermsAccepted(true);
        if (deviceIdentifier != null && !deviceIdentifier.isBlank()) {
            user.setDeviceIdentifier(deviceIdentifier);
        }
        if (user.getTemp() == null || user.getTemp().isBlank()) {
            user.setTemp(generateIdentity());
        }

        // If new user and reward not yet credited → set initial balance to registration reward
        if (isNewUser && !user.isWalletRewardCredited()) {
            double reward = appConfigService.getWalletRegistrationReward();
            user.setWalletBalance(reward);
            user.setWalletRewardCredited(true);
        }

        UserAccount saved = userAccountRepository.save(user);

        // Persist registration reward wallet transaction for new users
        if (isNewUser) {
            recordRegistrationReward(saved.getId());
        }

        log.info("User login successful - Number: {}, isNew={}", normalizedNumber, isNewUser);
        return new UserSession(saved, tryCreateAccessToken(saved.getTemp()));
    }

    /**
     * Authenticates or registers a user via Firebase Google Sign-In.
     * Verifies the Firebase ID token and creates/retrieves the user account.
     * Returns a session with {@code needsProfile=true} when the user's phone number is missing.
     */
    public UserSession googleLogin(GoogleLoginRequest request) throws FirebaseAuthException {
        if (request == null || request.getIdToken() == null || request.getIdToken().isBlank()) {
            throw new IllegalArgumentException("Firebase ID token is required");
        }

        FirebaseToken firebaseToken = FirebaseAuth.getInstance(firebaseApp).verifyIdToken(request.getIdToken());

        String googleUid = firebaseToken.getUid();
        String email = firebaseToken.getEmail();
        String displayName = firebaseToken.getName();
        String deviceIdentifier = request.getDeviceIdentifier();

        Optional<UserAccount> existing = userAccountRepository.findByGoogleUid(googleUid);

        UserAccount user;
        boolean isNew;

        if (existing.isPresent()) {
            user = existing.get();
            isNew = false;

            if (user.isBlocked()) {
                log.error("Blocked user Google login - UID: {}", googleUid);
                throw new BlockedUserException("Your account has been blocked. Please contact support for assistance.");
            }

            // Keep name/email up to date from Google profile
            boolean dirty = false;
            if (displayName != null && !displayName.isBlank() && !displayName.equals(user.getName())) {
                user.setName(displayName);
                dirty = true;
            }
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                dirty = true;
            }
            if (dirty) {
                user = userAccountRepository.save(user);
            }
        } else {
            // New user – create with welcome reward
            user = new UserAccount();
            user.setGoogleUid(googleUid);
            user.setEmail(email);
            user.setName(displayName != null && !displayName.isBlank() ? displayName : email);
            user.setActive(true);
            user.setTemp(generateIdentity());
            double reward = appConfigService.getWalletRegistrationReward();
            user.setWalletBalance(reward);
            user.setWalletRewardCredited(true);
            if (deviceIdentifier != null && !deviceIdentifier.isBlank()) {
                user.setDeviceIdentifier(deviceIdentifier);
            }
            user = userAccountRepository.save(user);
            recordRegistrationReward(user.getId());
            isNew = true;
        }

        log.info("Google login successful - UID: {}, email: {}, isNew: {}", googleUid, email, isNew);
        String token = tryCreateAccessToken(user.getTemp());
        return new UserSession(user, token);
    }

    // ─── Token ───────────────────────────────────────────────────────────────

    /**
     * Completes the user profile after Google Sign-In by saving phone number, location and terms acceptance.
     */
    public UserSession updateProfile(String userId, ProfileRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Profile request is required");
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank())
            throw new IllegalArgumentException("Mobile number is required");
        if (!request.isTermsAccepted())
            throw new IllegalArgumentException("Please accept the terms and conditions");

        UserAccount user = getUserById(userId);

        if (user.isBlocked()) {
            throw new BlockedUserException("Your account has been blocked. Please contact support for assistance.");
        }

        user.setNumber(request.getPhoneNumber().trim());
        user.setLocation(resolveLocation(request.getLocation()));  // "x" when unavailable
        user.setTermsAccepted(true);
        UserAccount saved = userAccountRepository.save(user);

        log.info("Profile updated for user: {}", userId);
        return new UserSession(saved, tryCreateAccessToken(saved.getTemp()));
    }

    // ─── Session ─────────────────────────────────────────────────────────────

    public User createToken(String userId) {
        log.info("Creating new access token for user: {}", userId);
        UserAccount user = getUserById(userId);
        if (user.isBlocked()) {
            throw new BlockedUserException("Your account has been blocked. Please contact support for assistance.");
        }
        return createJsonAccessToken(user.getTemp());
    }
    // ─── Wallet ──────────────────────────────────────────────────────────────

    public UserSession getCurrentSession(String userId) {
        return new UserSession(getUserById(userId), null);
    }

    public WalletResponse getWallet(String userId) {
        UserAccount user = getUserById(userId);
        return new WalletResponse(user.getWalletBalance(), getMinimumWalletBalance(), getCallRatePerMinute());
    }

    public WalletResponse topUpWallet(String userId, double amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Top-up amount must be greater than zero");
        UserAccount saved = userAccountRepository.runTransaction(userId, (user, tx, ref) -> {
            user.setWalletBalance(round2(user.getWalletBalance() + amount));
            return user;
        });
        return new WalletResponse(saved.getWalletBalance(), getMinimumWalletBalance(), getCallRatePerMinute());
    }

    public boolean userCanCall(String userId) {
        UserAccount user = getUserById(userId);
        if (user.isBlocked())
            return false;
        return user.getWalletBalance() >= getMinimumWalletBalance();
    }

    public double getMinimumWalletBalance() {
        return appConfigService.getWalletMinimumBalance();
    }

    // ─── User lookups ────────────────────────────────────────────────────────

    public double getCallRatePerMinute() {
        return appConfigService.getCallRatePerMinute();
    }

    public UserAccount getUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Please login first");
        }
        return userAccountRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Logged-in user was not found"));
    }

    // ─── Call history ────────────────────────────────────────────────────────

    public UserAccount getUserByTwilioIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalStateException("Caller identity is required");
        }
        return userAccountRepository.findByTemp(identity.trim()).orElseThrow(() -> new IllegalStateException("Caller user was not found"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public List<CallHistoryResponse> getCurrentUserHistory(String userId) {
        return callHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(CallHistoryResponse::new).toList();
    }

    private String generateIdentity() {
        return "client-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private User createJsonAccessToken(String identity) {
        return new User(identity, createAccessToken(identity));
    }

    /**
     * Attempts to create a Twilio access token. Returns {@code null} (instead of throwing)
     * when Twilio credentials are not yet configured, so login/registration can still succeed
     * and the client receives a session even before Twilio is set up.
     * A warning is logged to make the missing configuration visible.
     */
    private String tryCreateAccessToken(String identity) {
        try {
            return createAccessToken(identity);
        } catch (IllegalStateException ex) {
            log.warn("Twilio access token could not be generated (credentials not configured): {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Twilio access token generation failed unexpectedly: {}", ex.getMessage());
            return null;
        }
    }

    private String createAccessToken(String identity) {
        String accountSid = appConfigService.getTwilioAccountSid();
        String apiKey     = appConfigService.getTwilioApiKey();
        String apiSecret  = appConfigService.getTwilioApiSecret();

        // Validate that Twilio credentials have been configured (not still placeholders)
        if (accountSid == null || accountSid.isBlank() || accountSid.startsWith("replace_with")) {
            throw new IllegalStateException("Twilio Account SID is not configured. Please update 'twilio_account_sid' in app config.");
        }
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("replace_with")) {
            throw new IllegalStateException("Twilio API Key is not configured. Please update 'twilio_api_key' in app config.");
        }
        if (apiSecret == null || apiSecret.isBlank() || apiSecret.startsWith("replace_with")) {
            throw new IllegalStateException("Twilio API Secret is not configured. Please update 'twilio_api_secret' in app config.");
        }
        // HS256 (used internally by the Twilio JWT library) requires a key of at least 256 bits (32 characters)
        if (apiSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "Twilio API Secret is too short for JWT signing (must be at least 32 characters / 256 bits). " +
                "Please update 'twilio_api_secret' in app config with your actual Twilio API Secret.");
        }

        VoiceGrant grant = new VoiceGrant();
        grant.setOutgoingApplicationSid(appConfigService.getTwilioAppSid());
        grant.setIncomingAllow(true);
        AccessToken token = new AccessToken.Builder(accountSid, apiKey, apiSecret).identity(identity).grant(grant).build();
        return token.toJwt();
    }

    private void recordRegistrationReward(String userId) {
        try {
            double reward = appConfigService.getWalletRegistrationReward();
            WalletTransaction rewardTx = new WalletTransaction(userId, "CREDIT", reward, 0.0, reward, "Registration welcome reward", "REGISTRATION_REWARD");
            walletTransactionRepository.save(rewardTx);
            log.info("Registration reward ₹{} credited to new user {}", reward, userId);
        } catch (Exception ex) {
            log.warn("Failed to record registration reward wallet transaction: {}", ex.getMessage());
        }
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Login request is required");
        if (request.getName() == null || request.getName().isBlank())
            throw new IllegalArgumentException("Name is required");
        if (request.getNumber() == null || request.getNumber().isBlank())
            throw new IllegalArgumentException("Phone number is required");
        if (!request.isTermsAccepted())
            throw new IllegalArgumentException("Please accept terms and conditions");
    }

    // ─── Simple (non-Google) login ───────────────────────────────────────────

    /**
     * Authenticates or registers a user using only a mobile number.
     * Used when {@code enableGoogleSignIn} feature flag is {@code false}.
     * Google-related fields (googleUid, email) are left null.
     * Location defaults to {@code "x"} when blank or unavailable.
     *
     * <p>Duplicate-document guard: looks up by {@code number} first.
     * If not found, creates a new document. Google-linked users whose profile
     * was never completed (number == null) will get a separate document;
     * this is an expected edge case when switching the flag on a live deployment.
     */
    public UserSession simpleLogin(SimpleLoginRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Login request is required");
        if (request.getUserNumber() == null || request.getUserNumber().isBlank())
            throw new IllegalArgumentException("User number is required");

        String normalizedNumber = request.getUserNumber().trim();
        String deviceIdentifier = request.getDeviceIdentifier();

        UserAccount user = userAccountRepository.findByNumber(normalizedNumber).orElseGet(UserAccount::new);
        boolean isNewUser = (user.getId() == null || user.getId().isBlank());

        if (!isNewUser && user.isBlocked()) {
            log.error("Blocked user simple-login attempt - Number: {}", normalizedNumber);
            throw new BlockedUserException("Your account has been blocked. Please contact support for assistance.");
        }

        // Populate user fields; Google-specific fields are intentionally left null
        user.setNumber(normalizedNumber);
        user.setName(normalizedNumber);   // use number as display name (no Google profile)
        user.setLocation(resolveLocation(request.getLocation()));
        user.setActive(true);
        user.setTermsAccepted(true);
        if (deviceIdentifier != null && !deviceIdentifier.isBlank()) {
            user.setDeviceIdentifier(deviceIdentifier);
        }
        if (user.getTemp() == null || user.getTemp().isBlank()) {
            user.setTemp(generateIdentity());
        }

        // Credit welcome reward for brand-new accounts
        if (isNewUser && !user.isWalletRewardCredited()) {
            double reward = appConfigService.getWalletRegistrationReward();
            user.setWalletBalance(reward);
            user.setWalletRewardCredited(true);
        }

        UserAccount saved = userAccountRepository.save(user);

        if (isNewUser) {
            recordRegistrationReward(saved.getId());
        }

        log.info("Simple login successful - Number: {}, isNew={}", normalizedNumber, isNewUser);
        return new UserSession(saved, tryCreateAccessToken(saved.getTemp()));
    }

    /**
     * Returns {@code "x"} when the location string is null or blank (the universal default
     * for unavailable location, as per the feature specification).
     */
    private String resolveLocation(String location) {
        return (location == null || location.isBlank()) ? "x" : location.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
