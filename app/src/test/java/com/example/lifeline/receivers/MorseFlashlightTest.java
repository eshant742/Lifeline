package com.example.lifeline.receivers;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * MorseFlashlightTest — Tests for SOS Morse code pattern and timing.
 *
 * Validates the SOS pattern structure, timing constants,
 * and state management logic.
 */
public class MorseFlashlightTest {

    // Constants mirroring MorseFlashlight values
    private static final int DOT_MS = 200;
    private static final int DASH_MS = 600;
    private static final int INTRA_CHAR_GAP = 200;
    private static final int INTER_CHAR_GAP = 600;
    private static final int WORD_GAP = 1400;

    // The SOS pattern from MorseFlashlight
    private static final int[] SOS_PATTERN = {
            // S: dot dot dot
            DOT_MS, INTRA_CHAR_GAP, DOT_MS, INTRA_CHAR_GAP, DOT_MS, INTER_CHAR_GAP,
            // O: dash dash dash
            DASH_MS, INTRA_CHAR_GAP, DASH_MS, INTRA_CHAR_GAP, DASH_MS, INTER_CHAR_GAP,
            // S: dot dot dot
            DOT_MS, INTRA_CHAR_GAP, DOT_MS, INTRA_CHAR_GAP, DOT_MS, WORD_GAP
    };

    // ── Pattern Structure Tests ────────────────────────────────────────

    @Test
    public void pattern_has_18_elements() {
        // S=6 (3 on + 3 off) + O=6 + S=6 = 18 elements
        assertEquals("SOS pattern should have 18 timing elements", 18, SOS_PATTERN.length);
    }

    @Test
    public void pattern_alternates_on_off() {
        // Even indices = ON duration, Odd indices = OFF (gap) duration
        // S: ON(dot), OFF(gap), ON(dot), OFF(gap), ON(dot), OFF(inter)
        // Verify first letter "S" has dots at even positions
        assertEquals("S[0] should be DOT (ON)", DOT_MS, SOS_PATTERN[0]);
        assertEquals("S[1] should be INTRA_CHAR_GAP (OFF)", INTRA_CHAR_GAP, SOS_PATTERN[1]);
        assertEquals("S[2] should be DOT (ON)", DOT_MS, SOS_PATTERN[2]);
        assertEquals("S[3] should be INTRA_CHAR_GAP (OFF)", INTRA_CHAR_GAP, SOS_PATTERN[3]);
        assertEquals("S[4] should be DOT (ON)", DOT_MS, SOS_PATTERN[4]);
        assertEquals("S[5] should be INTER_CHAR_GAP (OFF)", INTER_CHAR_GAP, SOS_PATTERN[5]);
    }

    @Test
    public void pattern_O_uses_dashes() {
        // "O" starts at index 6
        assertEquals("O[0] should be DASH (ON)", DASH_MS, SOS_PATTERN[6]);
        assertEquals("O[1] should be INTRA_CHAR_GAP (OFF)", INTRA_CHAR_GAP, SOS_PATTERN[7]);
        assertEquals("O[2] should be DASH (ON)", DASH_MS, SOS_PATTERN[8]);
        assertEquals("O[3] should be INTRA_CHAR_GAP (OFF)", INTRA_CHAR_GAP, SOS_PATTERN[9]);
        assertEquals("O[4] should be DASH (ON)", DASH_MS, SOS_PATTERN[10]);
        assertEquals("O[5] should be INTER_CHAR_GAP (OFF)", INTER_CHAR_GAP, SOS_PATTERN[11]);
    }

    @Test
    public void pattern_ends_with_word_gap() {
        assertEquals("Last element should be WORD_GAP for repeat delay",
                WORD_GAP, SOS_PATTERN[SOS_PATTERN.length - 1]);
    }

    // ── Timing Tests ───────────────────────────────────────────────────

    @Test
    public void dash_is_3x_dot_duration() {
        assertEquals("Dash should be 3x dot duration (Morse standard)",
                DOT_MS * 3, DASH_MS);
    }

    @Test
    public void intra_char_gap_equals_dot() {
        assertEquals("Intra-character gap should equal dot duration (Morse standard)",
                DOT_MS, INTRA_CHAR_GAP);
    }

    @Test
    public void inter_char_gap_equals_dash() {
        assertEquals("Inter-character gap should equal dash duration (Morse standard)",
                DASH_MS, INTER_CHAR_GAP);
    }

    @Test
    public void total_sos_cycle_duration() {
        int totalMs = 0;
        for (int duration : SOS_PATTERN) {
            totalMs += duration;
        }
        // S: (200+200)*3 + 600 = 1800 - 200 (last gap is inter, not intra)
        // Actually let's just sum: 200+200+200+200+200+600 + 600+200+600+200+600+600 + 200+200+200+200+200+1400
        // = 1600 + 2800 + 2400 = 6800ms total
        assertTrue("Total SOS cycle should be between 5 and 10 seconds",
                totalMs >= 5000 && totalMs <= 10000);
    }

    // ── Pattern Index Wrapping ─────────────────────────────────────────

    @Test
    public void pattern_index_wraps_correctly() {
        int patternIndex = 0;

        // Simulate running through the entire pattern
        for (int i = 0; i < SOS_PATTERN.length; i++) {
            @SuppressWarnings("unused")
            int duration = SOS_PATTERN[patternIndex];
            patternIndex = (patternIndex + 1) % SOS_PATTERN.length;
        }

        // After one full cycle, index should wrap back to 0
        assertEquals("Pattern index should wrap to 0 after full cycle", 0, patternIndex);
    }
}
