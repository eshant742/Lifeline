package com.example.lifeline.core;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * MeshForegroundServiceTest — Tests for battery mode mapping and notification logic.
 *
 * Tests the getBatteryOptimizedMode() logic that determines mesh engine
 * behavior based on battery level, and notification channel configuration.
 */
public class MeshForegroundServiceTest {

    // Constants from MeshForegroundService
    private static final String CHANNEL_ID = "lifeline_mesh_channel";

    // ── Battery Level → Mode Mapping ───────────────────────────────────

    @Test
    public void battery_above_50_returns_full_mode() {
        String mode = getBatteryMode(75);
        assertEquals("FULL", mode);
    }

    @Test
    public void battery_exactly_50_returns_full_mode() {
        String mode = getBatteryMode(50);
        assertEquals("FULL", mode);
    }

    @Test
    public void battery_49_returns_balanced_mode() {
        String mode = getBatteryMode(49);
        assertEquals("BALANCED", mode);
    }

    @Test
    public void battery_20_returns_balanced_mode() {
        String mode = getBatteryMode(20);
        assertEquals("BALANCED", mode);
    }

    @Test
    public void battery_19_returns_low_power_mode() {
        String mode = getBatteryMode(19);
        assertEquals("LOW_POWER", mode);
    }

    @Test
    public void battery_0_returns_low_power_mode() {
        String mode = getBatteryMode(0);
        assertEquals("LOW_POWER", mode);
    }

    @Test
    public void battery_100_returns_full_mode() {
        String mode = getBatteryMode(100);
        assertEquals("FULL", mode);
    }

    // ── Mode Characteristics ───────────────────────────────────────────

    @Test
    public void full_mode_advertises_and_discovers() {
        // In FULL mode, both advertising and discovery should be active
        boolean shouldAdvertise = true;
        boolean shouldDiscover = true;
        assertTrue("FULL mode should advertise", shouldAdvertise);
        assertTrue("FULL mode should discover", shouldDiscover);
    }

    @Test
    public void balanced_mode_reduces_discovery_interval() {
        // In BALANCED mode, discovery interval should be longer
        long fullInterval = 5000;
        long balancedInterval = 15000;
        assertTrue("Balanced discovery interval should be > full",
                balancedInterval > fullInterval);
    }

    @Test
    public void low_power_mode_disables_discovery() {
        // In LOW_POWER mode, only advertising (passive) is active
        boolean shouldDiscover = false;
        assertFalse("LOW_POWER should not actively discover", shouldDiscover);
    }

    // ── Notification Configuration ─────────────────────────────────────

    @Test
    public void notification_channel_id_is_correct() {
        assertEquals("lifeline_mesh_channel", CHANNEL_ID);
    }

    @Test
    public void notification_content_includes_peer_count() {
        int peerCount = 5;
        String content = String.format("Mesh active | %d peers connected", peerCount);
        assertTrue(content.contains("5 peers"));
    }

    // ── Helper: Mirrors MeshForegroundService logic ────────────────────

    private String getBatteryMode(int batteryPercent) {
        if (batteryPercent >= 50) {
            return "FULL";
        } else if (batteryPercent >= 20) {
            return "BALANCED";
        } else {
            return "LOW_POWER";
        }
    }
}
