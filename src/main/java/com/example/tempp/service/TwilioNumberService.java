package com.example.tempp.service;

import com.example.tempp.constants.TwilioNumberStatus;
import com.example.tempp.model.TwilioNumber;
import com.example.tempp.repository.TwilioNumberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing Twilio phone numbers.
 * Number allocation uses Firestore transactions for atomicity.
 */
@Slf4j
@Service
public class TwilioNumberService {

    private final TwilioNumberRepository twilioNumberRepository;

    public TwilioNumberService(TwilioNumberRepository twilioNumberRepository) {
        this.twilioNumberRepository = twilioNumberRepository;
    }

    public boolean hasAvailableNumbers() {
        return twilioNumberRepository.findByIsActiveTrue().stream().anyMatch(n -> !n.isInUse());
    }

    public long getAvailableNumberCount() {
        return twilioNumberRepository.findByIsActiveTrue().stream().filter(n -> !n.isInUse()).count();
    }

    public String allocateNumber(String callSid) {
        return allocateNumber(callSid, 0);
    }

    public String allocateNumber(String callSid, int maxConcurrentCalls) {
        log.info("Allocating Twilio number for callSid={}", callSid);

        // Idempotent: if already allocated return existing
        if (callSid != null && !callSid.isBlank()) {
            Optional<TwilioNumber> existing = twilioNumberRepository.findByCurrentCallSid(callSid.trim());
            if (existing.isPresent()) {
                log.info("Call {} already has allocated number: {}", callSid, existing.get().getPhoneNumber());
                return existing.get().getPhoneNumber();
            }
        }

        Optional<TwilioNumber> allocated = twilioNumberRepository.allocateFirstAvailable(callSid, maxConcurrentCalls);
        if (allocated.isPresent()) {
            log.info("Allocated number: {} for call: {}", allocated.get().getPhoneNumber(), callSid);
            return allocated.get().getPhoneNumber();
        }
        log.error("No available Twilio numbers for call: {}. Add numbers via POST /api/twilio-numbers", callSid);
        throw new IllegalStateException("No Twilio numbers are currently available. Please try again later.");
    }


    public void releaseNumber(String callSid) {
        if (callSid == null || callSid.isBlank()) {
            log.warn("Cannot release number: callSid is null or blank");
            return;
        }
        twilioNumberRepository.releaseByCallSid(callSid.trim());
        log.info("Released number for callSid: {}", callSid);
    }

    /**
     * Returns the phone number currently allocated to the given callSid, or empty.
     * Used by billing to record which Twilio number was used for a call.
     */
    public Optional<String> findPhoneNumberByCallSid(String callSid) {
        if (callSid == null || callSid.isBlank()) return Optional.empty();
        try {
            return twilioNumberRepository.findByCurrentCallSid(callSid.trim())
                    .map(TwilioNumber::getPhoneNumber);
        } catch (Exception ex) {
            log.debug("Could not look up Twilio number for callSid {}: {}", callSid, ex.getMessage());
            return Optional.empty();
        }
    }

    public void releaseNumberByPhoneNumber(String phoneNumber) {
        twilioNumberRepository.findByPhoneNumber(phoneNumber).ifPresent(n -> {
            n.setInUse(false);
            n.setStatus(TwilioNumberStatus.AVAILABLE);
            n.setCurrentCallSid(null);
            twilioNumberRepository.save(n);
            log.info("Released number by phone: {}", phoneNumber);
        });
    }

    public void bindAllocatedNumberToCallSid(String allocationKey, String callSid) {
        if (allocationKey == null || allocationKey.isBlank() || callSid == null || callSid.isBlank()) {
            log.warn("Cannot bind allocated number. allocationKey={}, callSid={}", allocationKey, callSid);
            return;
        }
        twilioNumberRepository.bindCallSid(allocationKey.trim(), callSid.trim());
        log.info("Bound allocated number from key {} to callSid {}", allocationKey, callSid);
    }

    public TwilioNumber addNumber(TwilioNumber twilioNumber) {
        if (twilioNumber.getPhoneNumber() == null || twilioNumber.getPhoneNumber().isBlank())
            throw new IllegalArgumentException("Phone number is required");
        if (twilioNumberRepository.findByPhoneNumber(twilioNumber.getPhoneNumber()).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists in the pool: " + twilioNumber.getPhoneNumber());
        }
        log.info("Adding Twilio number to pool: {}", twilioNumber.getPhoneNumber());
        return twilioNumberRepository.save(twilioNumber);
    }

    /**
     * Adds a number if it doesn't already exist; updates friendly name / description if it does.
     * Used by the auto-migration and the config API to keep the pool in sync.
     */
    public TwilioNumber upsertNumber(String phoneNumber, String friendlyName, String description) {
        if (phoneNumber == null || phoneNumber.isBlank())
            throw new IllegalArgumentException("Phone number is required");
        return twilioNumberRepository.findByPhoneNumber(phoneNumber).map(existing -> {
            if (friendlyName != null) existing.setFriendlyName(friendlyName);
            if (description != null) existing.setDescription(description);
            log.info("Updated existing Twilio number in pool: {}", phoneNumber);
            return twilioNumberRepository.save(existing);
        }).orElseGet(() -> {
            TwilioNumber n = new TwilioNumber(phoneNumber);
            n.setFriendlyName(friendlyName);
            n.setDescription(description);
            n.setActive(true);
            n.setInUse(false);
            n.setStatus(TwilioNumberStatus.AVAILABLE);
            log.info("Added new Twilio number to pool: {}", phoneNumber);
            return twilioNumberRepository.save(n);
        });
    }

    public TwilioNumber updateNumber(String id, TwilioNumber updates) {
        TwilioNumber existing = twilioNumberRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Twilio number not found with ID: " + id));
        if (updates.getFriendlyName() != null)
            existing.setFriendlyName(updates.getFriendlyName());
        if (updates.getDescription() != null)
            existing.setDescription(updates.getDescription());
        if (updates.getCountryCode() != null)
            existing.setCountryCode(updates.getCountryCode());
        existing.setActive(updates.isActive());
        if (!existing.isInUse() && updates.getStatus() != null)
            existing.setStatus(updates.getStatus());
        return twilioNumberRepository.save(existing);
    }

    public List<TwilioNumber> getAllNumbers() {
        return twilioNumberRepository.findAll();
    }

    public List<TwilioNumber> getActiveNumbers() {
        return twilioNumberRepository.findByIsActiveTrue();
    }

    public List<TwilioNumber> getInUseNumbers() {
        return twilioNumberRepository.findByInUseTrue();
    }

    public Optional<TwilioNumber> getNumberById(String id) {
        return twilioNumberRepository.findById(id);
    }

    public void deleteNumber(String id) {
        TwilioNumber number = twilioNumberRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Twilio number not found with ID: " + id));
        if (number.isInUse())
            throw new IllegalStateException("Cannot delete a number that is currently in use");
        twilioNumberRepository.delete(number);
    }

    public TwilioNumber toggleActive(String id) {
        TwilioNumber number = twilioNumberRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Twilio number not found with ID: " + id));
        if (number.isInUse())
            throw new IllegalStateException("Cannot deactivate a number that is currently in use");
        number.setActive(!number.isActive());
        number.setStatus(number.isActive() ? TwilioNumberStatus.AVAILABLE : TwilioNumberStatus.DISABLED);
        return twilioNumberRepository.save(number);
    }
}
