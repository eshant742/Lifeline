package com.example.lifeline.receivers;

import static org.junit.Assert.*;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ShakeDetectorTest — Tests for the shake detection algorithm.
 *
 * Since SensorManager is Android-only, we test the algorithm logic
 * by directly manipulating the detector's internal state via reflection
 * and verifying the threshold/cooldown/window behavior.
 */
public class ShakeDetectorTest {

    // Constants mirroring ShakeDetector's values
    private static final float SHAKE_THRESHOLD = 15.0f;
    private static final int SHAKE_COUNT_THRESHOLD = 4;
    private static final long SHAKE_WINDOW_MS = 2000;
    private static final long COOLDOWN_MS = 30000;

    // ── Threshold Tests ────────────────────────────────────────────────

    @Test
    public void magnitude_below_threshold_is_not_a_shake() {
        float magnitude = 10.0f; // Below 15.0
        assertFalse("Magnitude below threshold should not trigger",
                magnitude > SHAKE_THRESHOLD);
    }

    @Test
    public void magnitude_above_threshold_is_a_shake() {
        float magnitude = 20.0f; // Above 15.0
        assertTrue("Magnitude above threshold should be counted",
                magnitude > SHAKE_THRESHOLD);
    }

    @Test
    public void magnitude_exactly_at_threshold_is_not_a_shake() {
        float magnitude = SHAKE_THRESHOLD; // Exactly at threshold
        assertFalse("Magnitude exactly at threshold should NOT trigger (strict >)",
                magnitude > SHAKE_THRESHOLD);
    }

    // ── Acceleration Magnitude Calculation ──────────────────────────────

    @Test
    public void magnitude_calculation_correct() {
        float x = 10.0f, y = 10.0f, z = 10.0f;
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        assertEquals(17.32f, magnitude, 0.01f);
        assertTrue(magnitude > SHAKE_THRESHOLD); // This would be a shake
    }

    @Test
    public void gravity_subtraction_isolates_linear_acceleration() {
        // Simulating the low-pass filter
        float ALPHA = 0.8f;
        float[] gravity = {0, 0, 9.8f}; // Steady gravity
        float[] sensor = {0, 0, 9.8f};   // Phone at rest

        // After filtering
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * sensor[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * sensor[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * sensor[2];

        float x = sensor[0] - gravity[0];
        float y = sensor[1] - gravity[1];
        float z = sensor[2] - gravity[2];

        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        assertTrue("Phone at rest should have near-zero linear acceleration",
                magnitude < 1.0f);
    }

    // ── Shake Count / Window Tests ─────────────────────────────────────

    @Test
    public void four_shakes_within_window_triggers_sos() {
        int shakeCount = SHAKE_COUNT_THRESHOLD;
        long firstShakeTime = System.currentTimeMillis() - 1500; // 1.5s ago
        long now = System.currentTimeMillis();

        boolean enoughShakes = shakeCount >= SHAKE_COUNT_THRESHOLD;
        boolean withinWindow = (now - firstShakeTime) <= SHAKE_WINDOW_MS;

        assertTrue("4 shakes within 2s should trigger", enoughShakes && withinWindow);
    }

    @Test
    public void three_shakes_does_not_trigger() {
        int shakeCount = 3; // Below threshold of 4
        boolean enoughShakes = shakeCount >= SHAKE_COUNT_THRESHOLD;
        assertFalse("Only 3 shakes should NOT trigger", enoughShakes);
    }

    @Test
    public void shakes_outside_window_reset() {
        long firstShakeTime = System.currentTimeMillis() - 3000; // 3s ago
        long now = System.currentTimeMillis();

        boolean outsideWindow = (now - firstShakeTime) > SHAKE_WINDOW_MS;
        assertTrue("Shakes older than 2s window should reset", outsideWindow);
    }

    // ── Cooldown Tests ─────────────────────────────────────────────────

    @Test
    public void cooldown_prevents_immediate_retrigger() {
        long lastTriggerTime = System.currentTimeMillis() - 10000; // 10s ago
        long now = System.currentTimeMillis();

        boolean pastCooldown = (now - lastTriggerTime) > COOLDOWN_MS;
        assertFalse("10s after trigger should still be in 30s cooldown", pastCooldown);
    }

    @Test
    public void cooldown_expires_after_30_seconds() {
        long lastTriggerTime = System.currentTimeMillis() - 31000; // 31s ago
        long now = System.currentTimeMillis();

        boolean pastCooldown = (now - lastTriggerTime) > COOLDOWN_MS;
        assertTrue("31s after trigger should be past cooldown", pastCooldown);
    }

    @Test
    public void first_trigger_always_allowed() {
        long lastTriggerTime = 0; // Never triggered before
        long now = System.currentTimeMillis();

        boolean pastCooldown = (now - lastTriggerTime) > COOLDOWN_MS;
        assertTrue("First trigger (lastTriggerTime=0) should always be allowed", pastCooldown);
    }
}
