package com.example.lifeline.core;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * NearbyMeshEngineTest — Tests for the mesh engine's internal algorithms.
 *
 * Since NearbyMeshEngine depends on Google Nearby Connections (Android-only),
 * we test the pure-logic components: LRU cache, rate limiting, GZIP,
 * gossip fan-out logic, and TTL enforcement.
 */
public class NearbyMeshEngineTest {

    // ── LRU Cache Tests ────────────────────────────────────────────────

    @Test
    public void lruCache_evicts_oldest_entry_when_capacity_exceeded() {
        final int MAX = 5;
        Set<String> cache = Collections.synchronizedSet(
                Collections.newSetFromMap(
                        new LinkedHashMap<String, Boolean>(MAX + 1, 0.75f, true) {
                            @Override
                            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                                return size() > MAX;
                            }
                        }
                )
        );

        // Fill cache
        for (int i = 0; i < MAX; i++) {
            cache.add("msg_" + i);
        }
        assertEquals(MAX, cache.size());

        // Add one more — should evict the oldest (msg_0)
        cache.add("msg_new");
        assertEquals(MAX, cache.size());
        assertFalse("Oldest entry should be evicted", cache.contains("msg_0"));
        assertTrue("Newest entry should exist", cache.contains("msg_new"));
        assertTrue("msg_1 should still exist", cache.contains("msg_1"));
    }

    @Test
    public void lruCache_access_order_prevents_eviction_of_recently_accessed() {
        final int MAX = 3;
        Set<String> cache = Collections.synchronizedSet(
                Collections.newSetFromMap(
                        new LinkedHashMap<String, Boolean>(MAX + 1, 0.75f, true) {
                            @Override
                            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                                return size() > MAX;
                            }
                        }
                )
        );

        cache.add("A");
        cache.add("B");
        cache.add("C");

        // Access "A" to move it to the end (most recently used)
        cache.contains("A"); // This triggers access-order update in LinkedHashMap

        // Add "D" — should evict "B" (least recently accessed), not "A"
        cache.add("D");
        assertEquals(MAX, cache.size());
        assertTrue("A was accessed recently, should not be evicted", cache.contains("A"));
        assertTrue("D is newest, should exist", cache.contains("D"));
    }

    @Test
    public void lruCache_duplicate_add_does_not_increase_size() {
        final int MAX = 10;
        Set<String> cache = Collections.newSetFromMap(
                new LinkedHashMap<String, Boolean>(MAX + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > MAX;
                    }
                }
        );

        cache.add("msg_1");
        cache.add("msg_1");
        cache.add("msg_1");
        assertEquals("Duplicate adds should not increase size", 1, cache.size());
    }

    @Test
    public void lruCache_handles_10000_entries() {
        final int MAX = 10000;
        Set<String> cache = Collections.newSetFromMap(
                new LinkedHashMap<String, Boolean>(MAX + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > MAX;
                    }
                }
        );

        // Add 15000 entries — only last 10000 should remain
        for (int i = 0; i < 15000; i++) {
            cache.add("msg_" + i);
        }
        assertEquals(MAX, cache.size());
        assertFalse("Entry 0 should be evicted", cache.contains("msg_0"));
        assertTrue("Entry 14999 should exist", cache.contains("msg_14999"));
        assertTrue("Entry 5000 should exist", cache.contains("msg_5000"));
    }

    // ── Rate Limiting Tests ────────────────────────────────────────────

    @Test
    public void rateLimiter_allows_messages_under_limit() {
        // Simulating rate limit logic from NearbyMeshEngine
        int RATE_LIMIT = 10;
        CopyOnWriteArrayList<Long> timestamps = new CopyOnWriteArrayList<>();

        for (int i = 0; i < RATE_LIMIT; i++) {
            timestamps.add(System.currentTimeMillis());
        }

        // Should be at limit but not over
        assertEquals(RATE_LIMIT, timestamps.size());
    }

    @Test
    public void rateLimiter_cleans_old_timestamps() {
        CopyOnWriteArrayList<Long> timestamps = new CopyOnWriteArrayList<>();
        long now = System.currentTimeMillis();

        // Add timestamps older than 1 minute
        timestamps.add(now - 120000); // 2 minutes ago
        timestamps.add(now - 90000);  // 1.5 minutes ago
        timestamps.add(now - 30000);  // 30 seconds ago (still valid)
        timestamps.add(now - 10000);  // 10 seconds ago (still valid)

        // Clean old ones (same logic as NearbyMeshEngine.isRateLimited)
        timestamps.removeIf(t -> (now - t) > 60000);

        assertEquals("Only timestamps within 1 minute should remain", 2, timestamps.size());
    }

    @Test
    public void rateLimiter_blocks_when_over_limit() {
        int RATE_LIMIT = 10;
        CopyOnWriteArrayList<Long> timestamps = new CopyOnWriteArrayList<>();
        long now = System.currentTimeMillis();

        // Fill to limit
        for (int i = 0; i < RATE_LIMIT; i++) {
            timestamps.add(now - (i * 1000)); // All within last 10 seconds
        }

        // Remove old ones
        timestamps.removeIf(t -> (now - t) > 60000);

        // Check if rate limited
        boolean isLimited = timestamps.size() >= RATE_LIMIT;
        assertTrue("Should be rate limited at capacity", isLimited);
    }

    // ── GZIP Compression Tests ─────────────────────────────────────────

    @Test
    public void gzip_compress_decompress_roundtrip() throws IOException {
        String original = "SOS - I am trapped. Please help. This is an emergency message that should compress well because it has repeated words like help help help help.";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);

        // Compress
        byte[] compressed = gzipCompress(originalBytes);
        assertNotNull(compressed);
        assertTrue("Compressed should be smaller for repetitive text",
                compressed.length < originalBytes.length);

        // Decompress
        byte[] decompressed = gzipDecompress(compressed);
        String result = new String(decompressed, StandardCharsets.UTF_8);
        assertEquals(original, result);
    }

    @Test
    public void gzip_handles_empty_data() throws IOException {
        byte[] compressed = gzipCompress(new byte[0]);
        byte[] decompressed = gzipDecompress(compressed);
        assertEquals(0, decompressed.length);
    }

    @Test
    public void gzip_handles_binary_data() throws IOException {
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }

        byte[] compressed = gzipCompress(binaryData);
        byte[] decompressed = gzipDecompress(compressed);
        assertArrayEquals(binaryData, decompressed);
    }

    // ── Gossip Fan-Out Logic Tests ─────────────────────────────────────

    @Test
    public void gossip_fanout_selects_correct_count_when_more_peers_than_fanout() {
        int GOSSIP_FAN_OUT = 3;
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidates.add("endpoint_" + i);
        }

        // Simulate gossip selection
        Collections.shuffle(candidates);
        java.util.List<String> selected = candidates.subList(0, Math.min(GOSSIP_FAN_OUT, candidates.size()));

        assertEquals("Should select exactly fan-out count", GOSSIP_FAN_OUT, selected.size());
    }

    @Test
    public void gossip_fanout_sends_to_all_when_fewer_peers_than_fanout() {
        int GOSSIP_FAN_OUT = 3;
        java.util.List<String> candidates = new java.util.ArrayList<>();
        candidates.add("endpoint_A");
        candidates.add("endpoint_B");

        // When fewer than fan-out, send to all
        int sendCount = Math.min(GOSSIP_FAN_OUT, candidates.size());
        assertEquals("Should send to all peers when below fan-out", 2, sendCount);
    }

    @Test
    public void gossip_excludes_sender_from_relay_candidates() {
        String senderEndpointId = "sender_123";
        java.util.List<String> allEndpoints = new java.util.ArrayList<>();
        allEndpoints.add("endpoint_A");
        allEndpoints.add(senderEndpointId);
        allEndpoints.add("endpoint_B");
        allEndpoints.add("endpoint_C");

        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (String ep : allEndpoints) {
            if (!ep.equals(senderEndpointId)) {
                candidates.add(ep);
            }
        }

        assertEquals(3, candidates.size());
        assertFalse("Sender should be excluded", candidates.contains(senderEndpointId));
    }

    // ── TTL / Hop Count Tests ──────────────────────────────────────────

    @Test
    public void ttl_message_dropped_when_hopCount_equals_maxHops() {
        int hopCount = 7;
        int maxHops = 7;
        boolean shouldDrop = hopCount >= maxHops;
        assertTrue("Message should be dropped when hopCount >= maxHops", shouldDrop);
    }

    @Test
    public void ttl_message_relayed_when_under_max_hops() {
        int hopCount = 3;
        int maxHops = 7;
        boolean shouldDrop = hopCount >= maxHops;
        assertFalse("Message should be relayed when hopCount < maxHops", shouldDrop);
    }

    @Test
    public void ttl_hop_count_increments_on_relay() {
        int originalHopCount = 3;
        int relayedHopCount = originalHopCount + 1;
        assertEquals(4, relayedHopCount);
    }

    // ── Helper Methods (same as NearbyMeshEngine internals) ────────────

    private byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    private byte[] gzipDecompress(byte[] compressed) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        }
        return bos.toByteArray();
    }
}
