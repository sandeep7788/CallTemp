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

    private static final double RATE_PER_MINUTE    = 6.0;
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
        " 1,  1.00",   // 0.10 raw → minimum applied
        " 2,  1.00",   // 0.20 raw → minimum applied
        " 3,  1.00",   // 0.30 raw → minimum applied
        " 5,  1.00",   // 0.50 raw → minimum applied
        " 6,  1.00",   // 0.60 raw → minimum applied
        "15,  1.50",   // 1.50 raw → above minimum
        "30,  3.00",   // 3.00 raw → above minimum
        "60,  6.00",   // 6.00 raw → above minimum
        "90,  9.00",   // 9.00 raw → above minimum
    })
    @DisplayName("Final charge with minimum ₹1.00 applied")
    void finalCharge_withMinimum(long seconds, double expectedCharge) {
        assertEquals(expectedCharge, calculateCharge(seconds), 0.001,
                () -> seconds + "s should be charged ₹" + expectedCharge);
    }

    // ─── Parameterized: raw calculated charge (before minimum) ───────────────

    @ParameterizedTest(name = "{0}s → raw ₹{1}")
    @CsvSource({
        " 1,  0.10",
        " 2,  0.20",
        " 3,  0.30",
        " 5,  0.50",
        " 6,  0.60",
        "15,  1.50",
        "30,  3.00",
        "60,  6.00",
        "90,  9.00",
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
    @DisplayName("Exactly 10s hits minimum boundary precisely")
    void tenSeconds_isExactlyMinimum() {
        // Breakeven point moved from 6s (at ₹10/min) to 10s (at ₹6/min):
        // (10/60)*6 = 1.00 exactly.
        double raw = rawCalculatedCharge(10);
        assertEquals(1.00, raw, 0.001, "10s raw should be exactly ₹1.00");
        assertEquals(1.00, calculateCharge(10), 0.001, "10s final should be exactly ₹1.00");
    }

    @Test
    @DisplayName("Minimum charge does NOT apply when duration exceeds breakeven")
    void aboveBreakeven_minimumNotApplied() {
        // 11 seconds = (11/60)*6 = 1.10 → round2 = 1.10 > 1.00
        double charge = calculateCharge(11);
        double raw    = rawCalculatedCharge(11);
        assertEquals(raw, charge, 0.001, "Above breakeven: final charge should equal calculated charge");
    }

    @Test
    @DisplayName("round2 precision: repeating decimals are rounded HALF_UP, not truncated")
    void round2_halfUpRounding() {
        // At ₹6/min every integer-second charge is an exact multiple of 0.1, so this
        // test exercises UserService.round2() directly with a repeating decimal instead
        // of going through the billing formula: 1/60 = 0.016666... → HALF_UP → 0.02.
        double rounded = UserService.round2(1.0 / 60.0);
        assertEquals(0.02, rounded, 0.001, "1/60 should round HALF_UP to 0.02, not truncate to 0.01");
    }

    @Test
    @DisplayName("Wallet deduction matches displayed charge exactly")
    void walletDeductionMatchesCharge() {
        // Verifies deduction == displayed amount (no extra rounding on subtraction)
        double walletBefore = 3.00;
        double charge = calculateCharge(15);          // ₹1.50
        double walletAfter = UserService.round2(Math.max(0.0, walletBefore - charge));
        assertEquals(1.50, charge, 0.001);
        assertEquals(1.50, walletAfter, 0.001,
                "Wallet after a 15s call from a ₹3.00 balance should be ₹1.50");
    }

    @Test
    @DisplayName("Wallet floors at 0 if insufficient funds")
    void walletFloor_doesNotGoNegative() {
        double walletBefore = 0.50;
        double charge = calculateCharge(15);          // ₹1.50
        double walletAfter = UserService.round2(Math.max(0.0, walletBefore - charge));
        assertEquals(0.0, walletAfter, 0.001);
    }
}
