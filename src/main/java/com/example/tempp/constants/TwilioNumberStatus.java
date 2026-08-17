package com.example.tempp.constants;

/**
 * Status constants for Twilio phone numbers in the managed number pool.
 *
 * <p><b>Status Lifecycle:</b>
 * <ul>
 *   <li><b>AVAILABLE:</b> Number is ready for allocation. Default state for new numbers.
 *       Set when a number is first added, deactivated, or released from a call.</li>
 *   <li><b>IN_USE:</b> Number is currently allocated to an active call.
 *       Set during {@link com.example.tempp.repository.TwilioNumberRepository#allocateFirstAvailable(String, int)}.
 *       Must always be accompanied by {@code inUse = true} and a valid {@code currentCallSid}.</li>
 *   <li><b>DISABLED:</b> Number is inactive and cannot be used.
 *       Set when a number is deactivated via {@link com.example.tempp.service.TwilioNumberService#toggleActive(String)}.
 *       When disabled, {@code inUse} must be false (enforced by delete constraints).</li>
 *   <li><b>MAINTENANCE:</b> Number is temporarily unavailable for maintenance.
 *       Reserved for future use by operational/admin tools.
 *       When in maintenance, {@code inUse} should be false.</li>
 * </ul>
 *
 * <p><b>Consistency Rules:</b>
 * <ul>
 *   <li>When status is IN_USE: {@code inUse} must be true, {@code currentCallSid} must be non-null</li>
 *   <li>When status is AVAILABLE: {@code inUse} must be false, {@code currentCallSid} must be null/deleted</li>
 *   <li>When status is DISABLED: {@code inUse} must be false, {@code currentCallSid} must be null/deleted</li>
 *   <li>When status is MAINTENANCE: {@code inUse} must be false, {@code currentCallSid} must be null/deleted</li>
 * </ul>
 *
 * <p><b>Error Recovery:</b>
 * If an error or unexpected termination occurs during a call:
 * <ul>
 *   <li>The number must be released immediately with {@code inUse = false}</li>
 *   <li>Status should be reset to AVAILABLE (if active) or DISABLED (if deactivated)</li>
 *   <li>currentCallSid must be cleared</li>
 * </ul>
 * See {@link com.example.tempp.repository.TwilioNumberRepository#releaseByCallSid(String)}.
 */
public final class TwilioNumberStatus {
    private TwilioNumberStatus() {
        // Utility class - prevent instantiation
    }

    /**
     * Number is ready for allocation.
     * Default status for newly added numbers or released numbers.
     */
    public static final String AVAILABLE = "available";

    /**
     * Number is currently allocated to an active call.
     * Must always be accompanied by {@code inUse = true}.
     */
    public static final String IN_USE = "in_use";

    /**
     * Number is deactivated and cannot be used.
     * Must always be accompanied by {@code inUse = false}.
     */
    public static final String DISABLED = "disabled";

    /**
     * Number is in maintenance and temporarily unavailable.
     * Reserved for future use. Must always be accompanied by {@code inUse = false}.
     */
    public static final String MAINTENANCE = "maintenance";

    /**
     * All valid status values.
     */
    private static final String[] VALID_STATUSES = {AVAILABLE, IN_USE, DISABLED, MAINTENANCE};

    /**
     * Validates that a status string is one of the allowed values.
     *
     * @param status the status to validate
     * @return true if status is valid, false otherwise
     */
    public static boolean isValid(String status) {
        if (status == null)
            return false;
        for (String valid : VALID_STATUSES) {
            if (valid.equals(status))
                return true;
        }
        return false;
    }

    /**
     * Asserts that a status is valid, throwing an exception if not.
     *
     * @param status the status to validate
     * @throws IllegalArgumentException if status is invalid
     */
    public static void validateOrThrow(String status) {
        if (!isValid(status)) {
            throw new IllegalArgumentException("Invalid Twilio number status: '" + status + "'. Valid values: "
                    + java.util.Arrays.toString(VALID_STATUSES));
        }
    }
}

