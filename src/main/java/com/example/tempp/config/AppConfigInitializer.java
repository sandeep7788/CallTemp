package com.example.tempp.config;

import com.example.tempp.constants.TwilioNumberStatus;
import com.example.tempp.model.TwilioNumber;
import com.example.tempp.service.AppConfigService;
import com.example.tempp.service.TwilioNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes default application configurations in Firestore on startup.
 * Also migrates the legacy single {@code twilio_caller_id} config key into the
 * {@code twilio_numbers} pool so existing deployments work without manual intervention.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppConfigInitializer implements CommandLineRunner {

    private final AppConfigService appConfigService;
    private final TwilioNumberService twilioNumberService;

    @Override
    public void run(String... args) {
        log.info("Initializing default application configurations...");
        try {
            appConfigService.initializeDefaults();
            log.info("Application configurations initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize application configurations", e);
        }

        // Migrate legacy single twilio_caller_id → twilio_numbers pool (runs only once)
        try {
            migrateLegacyCallerIdToPool();
        } catch (Exception e) {
            log.warn("Legacy twilio_caller_id migration skipped: {}", e.getMessage());
        }
    }

    /**
     * If the {@code twilio_numbers} pool is empty <em>and</em> the legacy
     * {@code twilio_caller_id} config key holds a real phone number, automatically
     * creates the first pool entry so calls work immediately without any manual steps.
     */
    private void migrateLegacyCallerIdToPool() {
        // Only migrate when the pool has no numbers yet
        if (!twilioNumberService.getAllNumbers().isEmpty()) {
            log.debug("twilio_numbers pool already populated – skipping legacy migration");
            return;
        }

        String legacyCallerId = appConfigService.getTwilioCallerId();
        if (legacyCallerId == null || legacyCallerId.isBlank() || legacyCallerId.contains("replace_with")) {
            log.info("twilio_numbers pool is empty and twilio_caller_id is not configured – add numbers via POST /api/twilio-numbers");
            return;
        }

        log.info("twilio_numbers pool is empty – migrating legacy twilio_caller_id '{}' into the pool...", legacyCallerId);
        TwilioNumber number = new TwilioNumber(legacyCallerId);
        number.setFriendlyName("Primary Number (auto-migrated from config)");
        number.setDescription("Migrated from legacy twilio_caller_id app config key on startup");
        number.setActive(true);
        number.setInUse(false);
        number.setStatus(TwilioNumberStatus.AVAILABLE);
        twilioNumberService.addNumber(number);
        log.info("Legacy twilio_caller_id '{}' successfully added to the twilio_numbers pool", legacyCallerId);
    }
}
