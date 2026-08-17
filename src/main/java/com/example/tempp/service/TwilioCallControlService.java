package com.example.tempp.service;

import com.example.tempp.constants.TwilioNumberStatus;
import com.example.tempp.model.TwilioNumber;
import com.example.tempp.repository.TwilioNumberRepository;
import com.twilio.Twilio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for forcefully controlling active Twilio calls.
 * Twilio credentials are loaded at call-time from Firestore via AppConfigService.
 */
@Slf4j
@Service
public class TwilioCallControlService {

    private final TwilioNumberRepository twilioNumberRepository;
    /**
     * @Lazy breaks the circular dependency: TwilioCallControlService ↔ AppConfigService
     */
    private final AppConfigService appConfigService;

    public TwilioCallControlService(TwilioNumberRepository twilioNumberRepository, @Lazy AppConfigService appConfigService) {
        this.twilioNumberRepository = twilioNumberRepository;
        this.appConfigService = appConfigService;
    }

    /**
     * Forcefully disconnects a single active Twilio call.
     * Used for auto-disconnect when wallet balance is exhausted or max duration is reached.
     */
    public void disconnectCall(String callSid) {
        if (callSid == null || callSid.isBlank()) {
            log.warn("Auto-disconnect requested with blank callSid – skipped");
            return;
        }
        try {
            Twilio.init(appConfigService.getTwilioAccountSid(), appConfigService.getTwilioAuthToken());
            com.twilio.rest.api.v2010.account.Call.updater(callSid)
                    .setStatus(com.twilio.rest.api.v2010.account.Call.UpdateStatus.COMPLETED)
                    .update();
            log.info("Auto-disconnect: Twilio call {} terminated", callSid);
        } catch (Exception ex) {
            log.warn("Auto-disconnect: unable to terminate Twilio call {}: {}", callSid, ex.getMessage());
        }
    }

    public int disconnectAllOngoingCalls() {
        List<TwilioNumber> inUseNumbers = twilioNumberRepository.findByInUseTrue();
        if (inUseNumbers == null)
            inUseNumbers = Collections.emptyList();

        Set<String> callSids = new LinkedHashSet<>();
        for (TwilioNumber number : inUseNumbers) {
            if (number.getCurrentCallSid() != null && !number.getCurrentCallSid().isBlank()) {
                callSids.add(number.getCurrentCallSid().trim());
            }
        }

        Twilio.init(appConfigService.getTwilioAccountSid(), appConfigService.getTwilioAuthToken());
        addTwilioActiveCalls(callSids, com.twilio.rest.api.v2010.account.Call.Status.QUEUED);
        addTwilioActiveCalls(callSids, com.twilio.rest.api.v2010.account.Call.Status.RINGING);
        addTwilioActiveCalls(callSids, com.twilio.rest.api.v2010.account.Call.Status.IN_PROGRESS);

        if (callSids.isEmpty()) {
            releaseLocalNumbers(inUseNumbers);
            log.info("Maintenance mode disconnect: no active Twilio call SIDs found.");
            return 0;
        }

        for (String callSid : callSids) {
            try {
                com.twilio.rest.api.v2010.account.Call.updater(callSid).setStatus(com.twilio.rest.api.v2010.account.Call.UpdateStatus.COMPLETED).update();
                log.info("Force disconnected Twilio call: {}", callSid);
            } catch (Exception ex) {
                log.warn("Unable to force disconnect Twilio call {}: {}", callSid, ex.getMessage());
            }
        }

        releaseLocalNumbers(inUseNumbers);
        return callSids.size();
    }

    private void addTwilioActiveCalls(Set<String> callSids, com.twilio.rest.api.v2010.account.Call.Status status) {
        try {
            for (com.twilio.rest.api.v2010.account.Call call : com.twilio.rest.api.v2010.account.Call.reader().setStatus(status).read()) {
                if (call.getSid() != null && !call.getSid().isBlank()) {
                    callSids.add(call.getSid());
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to read Twilio calls with status {}: {}", status, ex.getMessage());
        }
    }

    private void releaseLocalNumbers(List<TwilioNumber> numbers) {
        for (TwilioNumber number : numbers) {
            try {
                if (number.getCurrentCallSid() != null) {
                    // Atomic release via repository transaction
                    twilioNumberRepository.releaseByCallSid(number.getCurrentCallSid());
                } else if (number.getId() != null) {
                    // Fallback: direct update if no callSid available
                    // Ensures status and inUse are consistent
                    number.setInUse(false);
                    number.setStatus(number.isActive() ? TwilioNumberStatus.AVAILABLE : TwilioNumberStatus.DISABLED);
                    number.setCurrentCallSid(null);
                    twilioNumberRepository.save(number);
                }
            } catch (Exception ex) {
                log.warn("Could not release number {}: {}", number.getPhoneNumber(), ex.getMessage());
            }
        }
    }
}
