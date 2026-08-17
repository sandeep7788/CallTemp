package com.example.tempp.controller.api;

import com.example.tempp.model.TwilioNumber;
import com.example.tempp.service.TwilioNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing Twilio phone numbers.
 * IDs are now String (Firebase document IDs).
 */
@Slf4j
@RestController
@RequestMapping("/api/twilio-numbers")
@RequiredArgsConstructor
public class TwilioNumberController {

    private final TwilioNumberService twilioNumberService;

    @GetMapping
    public ResponseEntity<List<TwilioNumber>> getAllNumbers() {
        return ResponseEntity.ok(twilioNumberService.getAllNumbers());
    }

    @GetMapping("/active")
    public ResponseEntity<List<TwilioNumber>> getActiveNumbers() {
        return ResponseEntity.ok(twilioNumberService.getActiveNumbers());
    }

    @GetMapping("/in-use")
    public ResponseEntity<List<TwilioNumber>> getInUseNumbers() {
        return ResponseEntity.ok(twilioNumberService.getInUseNumbers());
    }

    /**
     * ID is now a String (Firebase document ID).
     */
    @GetMapping("/{id}")
    public ResponseEntity<TwilioNumber> getNumberById(@PathVariable String id) {
        return twilioNumberService.getNumberById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> addNumber(@RequestBody TwilioNumber twilioNumber) {
        try {
            TwilioNumber saved = twilioNumberService.addNumber(twilioNumber);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNumber(@PathVariable String id, @RequestBody TwilioNumber twilioNumber) {
        try {
            return ResponseEntity.ok(twilioNumberService.updateNumber(id, twilioNumber));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable String id) {
        try {
            return ResponseEntity.ok(twilioNumberService.toggleActive(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/release")
    public ResponseEntity<?> releaseNumber(@RequestParam String callSid) {
        twilioNumberService.releaseNumber(callSid);
        return ResponseEntity.ok(createSuccessResponse("Number released successfully"));
    }

    @PostMapping("/release-by-phone")
    public ResponseEntity<?> releaseNumberByPhone(@RequestParam String phoneNumber) {
        twilioNumberService.releaseNumberByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(createSuccessResponse("Number released successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNumber(@PathVariable String id) {
        try {
            twilioNumberService.deleteNumber(id);
            return ResponseEntity.ok(createSuccessResponse("Number deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<TwilioNumber> all = twilioNumberService.getAllNumbers();
        List<TwilioNumber> active = twilioNumberService.getActiveNumbers();
        List<TwilioNumber> inUse = twilioNumberService.getInUseNumbers();
        long available = active.stream().filter(n -> !n.isInUse()).count();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNumbers", all.size());
        stats.put("activeNumbers", active.size());
        stats.put("inUseNumbers", inUse.size());
        stats.put("availableNumbers", available);
        stats.put("inactiveNumbers", all.size() - active.size());
        stats.put("hasAvailableNumbers", available > 0);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability() {
        boolean hasAvailable = twilioNumberService.hasAvailableNumbers();
        long count = twilioNumberService.getAvailableNumberCount();
        Map<String, Object> result = new HashMap<>();
        result.put("available", hasAvailable);
        result.put("count", count);
        result.put("message", hasAvailable ? count + " number(s) available" : "No Twilio numbers are currently available. Please try again later.");
        return ResponseEntity.ok(result);
    }

    private Map<String, String> createErrorResponse(String message) {
        return Map.of("error", message);
    }

    private Map<String, String> createSuccessResponse(String message) {
        return Map.of("message", message);
    }
}
