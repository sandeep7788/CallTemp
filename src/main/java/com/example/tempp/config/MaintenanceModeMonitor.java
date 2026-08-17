package com.example.tempp.config;

import com.example.tempp.service.AppConfigService;
import com.example.tempp.service.TwilioCallControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically checks maintenance_mode in Firestore and enforces call disconnections.
 */
@Slf4j
@Component
public class MaintenanceModeMonitor {

    private final AppConfigService appConfigService;
    private final TwilioCallControlService twilioCallControlService;
    private final AtomicBoolean lastMaintenanceMode = new AtomicBoolean(false);

    public MaintenanceModeMonitor(AppConfigService appConfigService, TwilioCallControlService twilioCallControlService) {
        this.appConfigService = appConfigService;
        this.twilioCallControlService = twilioCallControlService;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void enforceMaintenanceModeTransitions() {
        boolean enabled = appConfigService.isMaintenanceModeEnabled();
        boolean wasEnabled = lastMaintenanceMode.getAndSet(enabled);
        if (enabled && !wasEnabled) {
            int disconnectedCalls = twilioCallControlService.disconnectAllOngoingCalls();
            log.info("maintenance_mode changed to true. Force disconnect attempted for {} tracked call(s).", disconnectedCalls);
        }
    }
}
