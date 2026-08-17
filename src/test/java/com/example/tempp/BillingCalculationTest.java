package com.example.tempp;

import com.example.tempp.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the call billing calculation.
 *
 * <p>Formula:
 * <pre>
 *   calculatedCharge = round2((durationSeconds / 60.0) × ratePerMinute)
 *   finalCharge      = round2(max(calculatedCharge, minimumCallCharge))
 * </pre>
 *
 * <p>These tests do NOT require a Spring context – they exercise the pure arithmetic only.
 */
@DisplayName("Call Billing Calculation")
class BillingCalculationTest {

    private static final double RATE_PER_MINUTE    = 10.0;
    private static final double MINIMUM_CALL_CHARGE = 1.0;

    // ─── Core formula ────────────────────────────────────────────────────────

    /**
     * Replicates the production billing logic from {@code CallService.chargeUser()}.
     * Keep in sync with that method.
     */
    private double calculateCharge(long durationSeconds) {
        double calculated = UserService.round2((durationSeconds / 60.0) * RATE_PER_MINUTE);
        return UserService.round2(Math.max(calculated, MINIMUM_CALL_CHARGE));
    }

    private double rawCalculatedCharge(long durationSeconds) {
        return UserService.round2((durationSeconds / 60.0) * RATE_PER_MINUTE);
    }

    // ─── Parameterized: final charge after minimum applied ───────────────────

    @ParameterizedTest(name = "{0}s → ₹{1}")
    @CsvSource({
        " 1,  1.00",   // 0.17 raw → minimum applied
        " 2,  1.00",   // 0.33 raw → minimum applied
        " 3,  1.00",   // 0.50 raw → minimum applied
        " 5,  1.00",   // 0.83 raw → minimum applied
        " 6,  1.00",   // 1.00 raw → exactly meets minimum
        "15,  2.50",   // 2.50 raw → above minimum
        "30,  5.00",   // 5.00 raw → above minimum
        "60, 10.00",   // 10.00 raw → above minimum
        "90, 15.00",   // 15.00 raw → above minimum
    })
    @DisplayName("Final charge with minimum ₹1.00 applied")
    void finalCharge_withMinimum(long seconds, double expectedCharge) {
        assertEquals(expectedCharge, calculateCharge(seconds), 0.001,
                () -> seconds + "s should be charged ₹" + expectedCharge);
    }

    // ─── Parameterized: raw calculated charge (before minimum) ───────────────

    @ParameterizedTest(name = "{0}s → raw ₹{1}")
    @CsvSource({
        " 1,  0.17",
        " 2,  0.33",
        " 3,  0.50",
        " 5,  0.83",
        " 6,  1.00",
        "15,  2.50",
        "30,  5.00",
        "60, 10.00",
        "90, 15.00",
    })
    @DisplayName("Raw calculated charge (before minimum applied)")
    void rawCalculatedCharge(long seconds, double expectedRaw) {
        assertEquals(expectedRaw, rawCalculatedCharge(seconds), 0.001,
                () -> seconds + "s raw charge should be ₹" + expectedRaw);
    }

    // ─── Edge cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Zero-duration call charges minimum")
    void zeroDuration_chargesMinimum() {
        assertEquals(MINIMUM_CALL_CHARGE, calculateCharge(0), 0.001);
    }

    @Test
    @DisplayName("Exactly 6s hits minimum boundary precisely")
    void sixSeconds_isExactlyMinimum() {
        double raw = rawCalculatedCharge(6);
        assertEquals(1.00, raw, 0.001, "6s raw should be exactly ₹1.00");
        assertEquals(1.00, calculateCharge(6), 0.001, "6s final should be exactly ₹1.00");
    }

    @Test
    @DisplayName("Minimum charge does NOT apply when duration exceeds breakeven")
    void aboveBreakeven_minimumNotApplied() {
        // 7 seconds = (7/60)*10 = 1.1666 → round2 = 1.17 > 1.00
        double charge = calculateCharge(7);
        double raw    = rawCalculatedCharge(7);
        assertEquals(raw, charge, 0.001, "Above breakeven: final charge should equal calculated charge");
    }

    @Test
    @DisplayName("round2 precision: 1/3 of a cent is rounded, not truncated")
    void round2_halfUpRounding() {
        // (1/60)*10 = 0.16666... → round2 should give 0.17 (HALF_UP), not 0.16 (floor)
        double raw = rawCalculatedCharge(1);
        assertEquals(0.17, raw, 0.001, "1s raw charge should be ₹0.17 (HALF_UP)");
    }

    @Test
    @DisplayName("Wallet deduction matches displayed charge exactly")
    void walletDeductionMatchesCharge() {
        // Verifies deduction == displayed amount (no extra rounding on subtraction)
        double walletBefore = 3.00;
        double charge = calculateCharge(15);          // ₹2.50
        double walletAfter = UserService.round2(Math.max(0.0, walletBefore - charge));
        assertEquals(2.50, charge, 0.001);
        assertEquals(0.50, walletAfter, 0.001,
                "Wallet after 15s call from get Up To ₹10.00 should be ₹0.50");
    }

    @Test
    @DisplayName("Wallet floors at 0 if insufficient funds")
    void walletFloor_doesNotGoNegative() {
        double walletBefore = 0.50;
        double charge = calculateCharge(15);          // ₹2.50
        double walletAfter = UserService.round2(Math.max(0.0, walletBefore - charge));
        assertEquals(0.0, walletAfter, 0.001);
    }
}

