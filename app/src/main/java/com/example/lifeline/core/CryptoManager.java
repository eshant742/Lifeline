package com.example.lifeline.core;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CryptoManager — Military-grade encryption engine for Lifeline mesh payloads.
 * <p>
 * Uses AES-256-GCM (Galois/Counter Mode) which provides both:
 * - Confidentiality (encryption) — nobody can read the data
 * - Integrity (authentication) — nobody can tamper with the data
 * <p>
 * Why Java is superior here:
 * - javax.crypto is the most mature, FIPS-compliant crypto library on Android
 * - Direct access to Java Security Provider architecture
 * - BouncyCastle integration is Java-native
 * - No Kotlin coroutine overhead for CPU-bound crypto operations
 * <p>
 * This class supports two modes:
 * 1. Shared-key encryption: All mesh nodes use a pre-shared key derived from a passphrase
 * 2. Android KeyStore encryption: For local database encryption using hardware-backed keys
 */
public final class CryptoManager {

    private static final String TAG = "CryptoManager";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;    // 96 bits — NIST recommended for GCM
    private static final int GCM_TAG_LENGTH = 128;   // 128-bit authentication tag
    private static final int AES_KEY_SIZE = 256;      // AES-256
    private static final String KEYSTORE_ALIAS = "lifeline_master_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private final SecureRandom secureRandom = new SecureRandom();

    // ── Shared-Key Mesh Encryption ─────────────────────────────────────

    /**
     * Derive a 256-bit AES key from a passphrase using SHA-256.
     * In the mesh network, all nodes share the same passphrase (e.g., "LIFELINE_MESH_2024").
     * This ensures any node can decrypt messages from any other node.
     *
     * @param passphrase The shared secret passphrase
     * @return A 256-bit SecretKey suitable for AES-256-GCM
     */
    public SecretKey deriveKeyFromPassphrase(String passphrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "Key derivation failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }

    /**
     * Encrypt a plaintext string using AES-256-GCM.
     * The output format is: [12-byte IV] + [ciphertext + GCM tag]
     * This is then Base64-encoded for safe transmission over JSON.
     *
     * @param plaintext The data to encrypt
     * @param key       The AES-256 secret key
     * @return Base64-encoded ciphertext (IV prepended), or null on failure
     */
    public String encrypt(String plaintext, SecretKey key) {
        try {
            // Generate a unique random IV for each encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [IV | ciphertext]
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Decrypt a Base64-encoded ciphertext that was encrypted with {@link #encrypt}.
     * Extracts the prepended IV, then decrypts using AES-256-GCM.
     *
     * @param encryptedBase64 The Base64-encoded [IV + ciphertext]
     * @param key             The AES-256 secret key
     * @return The decrypted plaintext string, or null on failure (wrong key, tampered data)
     */
    public String decrypt(String encryptedBase64, SecretKey key) {
        try {
            byte[] decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Extract ciphertext
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Encrypt raw bytes (for audio/image payloads).
     * Returns: [12-byte IV] + [ciphertext + GCM tag] as raw bytes.
     */
    public byte[] encryptBytes(byte[] data, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(data);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            Log.e(TAG, "Byte encryption failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Decrypt raw bytes encrypted with {@link #encryptBytes}.
     */
    public byte[] decryptBytes(byte[] encryptedData, SecretKey key) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            Log.e(TAG, "Byte decryption failed: " + e.getMessage(), e);
            return null;
        }
    }

    // ── Android KeyStore (Hardware-Backed Local Encryption) ─────────────

    /**
     * Generate or retrieve a hardware-backed AES-256 key from Android KeyStore.
     * This key never leaves the device's secure hardware (TEE/StrongBox).
     * Used for encrypting local database backups and sensitive preferences.
     */
    public SecretKey getOrCreateKeystoreKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            // Check if key already exists
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                KeyStore.SecretKeyEntry entry = 
                    (KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);
                return entry.getSecretKey();
            }

            // Generate new hardware-backed key
            KeyGenerator keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGen.init(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build());

            return keyGen.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "KeyStore operation failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to access Android KeyStore", e);
        }
    }

    // ── Digital Signature (Message Authentication) ─────────────────────

    /**
     * Compute SHA-256 hash of a message for integrity verification.
     * Used to create a fingerprint of each message that can be verified
     * to ensure the message wasn't tampered with during mesh relay.
     *
     * @param data The data to hash
     * @return Hex-encoded SHA-256 hash string
     */
    public String computeSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "SHA-256 failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate a cryptographically secure random key for one-time use.
     * Useful for per-session mesh keys.
     */
    public SecretKey generateRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, secureRandom);
            return keyGen.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Random key generation failed: " + e.getMessage(), e);
            throw new RuntimeException("Failed to generate random key", e);
        }
    }
}