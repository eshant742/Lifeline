package com.example.lifeline.core;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

/**
 * CryptoManagerTest — Comprehensive tests for AES-256-GCM encryption engine.
 *
 * Tests cover:
 * - Encrypt/decrypt roundtrip for strings and bytes
 * - Wrong key rejection (GCM authentication tag verification)
 * - Null and empty input handling
 * - Key derivation determinism
 * - SHA-256 hashing correctness
 * - Random key generation uniqueness
 */
public class CryptoManagerTest {

    private CryptoManager cryptoManager;
    private SecretKey testKey;

    @Before
    public void setUp() {
        cryptoManager = new CryptoManager();
        testKey = cryptoManager.deriveKeyFromPassphrase("TEST_PASSPHRASE_2024");
    }

    // ── String Encryption ──────────────────────────────────────────────

    @Test
    public void encrypt_decrypt_roundtrip_succeeds() {
        String plaintext = "SOS - I am trapped on the 3rd floor. Building collapsed.";
        String encrypted = cryptoManager.encrypt(plaintext, testKey);

        assertNotNull("Encrypted result should not be null", encrypted);
        assertNotEquals("Encrypted should differ from plaintext", plaintext, encrypted);

        String decrypted = cryptoManager.decrypt(encrypted, testKey);
        assertEquals("Decrypted text should match original", plaintext, decrypted);
    }

    @Test
    public void encrypt_produces_different_ciphertexts_for_same_input() {
        // Each encryption uses a unique random IV, so two encryptions of the same
        // plaintext must produce different ciphertexts (semantic security).
        String plaintext = "Help me!";
        String encrypted1 = cryptoManager.encrypt(plaintext, testKey);
        String encrypted2 = cryptoManager.encrypt(plaintext, testKey);

        assertNotNull(encrypted1);
        assertNotNull(encrypted2);
        assertNotEquals("Two encryptions of the same text must differ (unique IV)", encrypted1, encrypted2);

        // Both must decrypt to the same plaintext
        assertEquals(plaintext, cryptoManager.decrypt(encrypted1, testKey));
        assertEquals(plaintext, cryptoManager.decrypt(encrypted2, testKey));
    }

    @Test
    public void decrypt_with_wrong_key_returns_null() {
        String plaintext = "Secret message";
        String encrypted = cryptoManager.encrypt(plaintext, testKey);
        assertNotNull(encrypted);

        // Create a different key
        SecretKey wrongKey = cryptoManager.deriveKeyFromPassphrase("WRONG_PASSPHRASE");
        String decrypted = cryptoManager.decrypt(encrypted, wrongKey);

        assertNull("Decryption with wrong key should return null (GCM tag mismatch)", decrypted);
    }

    @Test
    public void encrypt_empty_string_roundtrip() {
        String encrypted = cryptoManager.encrypt("", testKey);
        assertNotNull(encrypted);

        String decrypted = cryptoManager.decrypt(encrypted, testKey);
        assertEquals("", decrypted);
    }

    @Test
    public void encrypt_unicode_content_roundtrip() {
        String unicode = "🆘 मदद करो! 紧急求救! Help!";
        String encrypted = cryptoManager.encrypt(unicode, testKey);
        assertNotNull(encrypted);

        String decrypted = cryptoManager.decrypt(encrypted, testKey);
        assertEquals(unicode, decrypted);
    }

    @Test
    public void decrypt_corrupted_data_returns_null() {
        String decrypted = cryptoManager.decrypt("not_valid_base64!!!", testKey);
        assertNull("Corrupted base64 input should return null", decrypted);
    }

    // ── Byte Encryption ────────────────────────────────────────────────

    @Test
    public void encryptBytes_decryptBytes_roundtrip() {
        byte[] original = "Audio SOS data payload".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cryptoManager.encryptBytes(original, testKey);

        assertNotNull(encrypted);
        assertTrue("Encrypted bytes should be longer (IV + tag)", encrypted.length > original.length);

        byte[] decrypted = cryptoManager.decryptBytes(encrypted, testKey);
        assertNotNull(decrypted);
        assertArrayEquals("Decrypted bytes should match original", original, decrypted);
    }

    @Test
    public void decryptBytes_with_wrong_key_returns_null() {
        byte[] original = new byte[]{1, 2, 3, 4, 5};
        byte[] encrypted = cryptoManager.encryptBytes(original, testKey);

        SecretKey wrongKey = cryptoManager.deriveKeyFromPassphrase("DIFFERENT_KEY");
        byte[] decrypted = cryptoManager.decryptBytes(encrypted, wrongKey);

        assertNull("Byte decryption with wrong key should return null", decrypted);
    }

    // ── Key Derivation ─────────────────────────────────────────────────

    @Test
    public void deriveKey_is_deterministic() {
        SecretKey key1 = cryptoManager.deriveKeyFromPassphrase("LIFELINE_MESH_2024");
        SecretKey key2 = cryptoManager.deriveKeyFromPassphrase("LIFELINE_MESH_2024");

        assertArrayEquals(
                "Same passphrase must produce same key bytes",
                key1.getEncoded(), key2.getEncoded()
        );
    }

    @Test
    public void deriveKey_different_passphrases_produce_different_keys() {
        SecretKey key1 = cryptoManager.deriveKeyFromPassphrase("passphrase_A");
        SecretKey key2 = cryptoManager.deriveKeyFromPassphrase("passphrase_B");

        assertFalse(
                "Different passphrases must produce different keys",
                java.util.Arrays.equals(key1.getEncoded(), key2.getEncoded())
        );
    }

    @Test
    public void deriveKey_produces_256_bit_key() {
        SecretKey key = cryptoManager.deriveKeyFromPassphrase("test");
        assertEquals("Key should be 256 bits (32 bytes)", 32, key.getEncoded().length);
    }

    // ── SHA-256 ────────────────────────────────────────────────────────

    @Test
    public void computeSHA256_produces_64_char_hex() {
        String hash = cryptoManager.computeSHA256("Hello, World!");
        assertNotNull(hash);
        assertEquals("SHA-256 hex string should be 64 chars", 64, hash.length());
        assertTrue("Should be valid hex", hash.matches("[0-9a-f]{64}"));
    }

    @Test
    public void computeSHA256_is_deterministic() {
        String hash1 = cryptoManager.computeSHA256("test data");
        String hash2 = cryptoManager.computeSHA256("test data");
        assertEquals("Same input must produce same hash", hash1, hash2);
    }

    @Test
    public void computeSHA256_different_inputs_produce_different_hashes() {
        String hash1 = cryptoManager.computeSHA256("input A");
        String hash2 = cryptoManager.computeSHA256("input B");
        assertNotEquals("Different inputs must produce different hashes", hash1, hash2);
    }

    // ── Random Key Generation ──────────────────────────────────────────

    @Test
    public void generateRandomKey_produces_unique_keys() {
        SecretKey key1 = cryptoManager.generateRandomKey();
        SecretKey key2 = cryptoManager.generateRandomKey();

        assertNotNull(key1);
        assertNotNull(key2);
        assertFalse(
                "Random keys must be different",
                java.util.Arrays.equals(key1.getEncoded(), key2.getEncoded())
        );
    }

    @Test
    public void generateRandomKey_is_256_bits() {
        SecretKey key = cryptoManager.generateRandomKey();
        assertEquals("Random key should be 256 bits", 32, key.getEncoded().length);
    }
}
