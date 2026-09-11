package com.example.lifeline.receivers;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MorseFlashlight — SOS Morse code flashlight beacon.
 * <p>
 * Blinks the camera flashlight in the international Morse code SOS pattern:
 *   S = ... (3 short)
 *   O = --- (3 long)
 *   S = ... (3 short)
 * <p>
 * Why Java is superior here:
 * - Camera2 API is documented and designed in Java
 * - Handler/HandlerThread pattern for precise timing is Java-native
 * - AtomicBoolean for thread-safe state management
 * <p>
 * At night, a blinking flashlight is visible from hundreds of meters.
 * This works even when all wireless radios fail — a zero-tech fallback.
 */
public class MorseFlashlight {

    private static final String TAG = "MorseFlashlight";

    // Morse code timing (milliseconds)
    private static final int DOT_MS = 200;          // Short flash
    private static final int DASH_MS = 600;          // Long flash
    private static final int INTRA_CHAR_GAP = 200;   // Gap between dots/dashes within a letter
    private static final int INTER_CHAR_GAP = 600;    // Gap between letters (S and O)
    private static final int WORD_GAP = 1400;         // Gap before repeating SOS

    // SOS pattern: S=..., O=---, S=...
    // true = flash ON, false = flash OFF (gap)
    private static final int[] SOS_PATTERN = {
            // S: dot dot dot
            DOT_MS, INTRA_CHAR_GAP, DOT_MS, INTRA_CHAR_GAP, DOT_MS, INTER_CHAR_GAP,
            // O: dash dash dash
            DASH_MS, INTRA_CHAR_GAP, DASH_MS, INTRA_CHAR_GAP, DASH_MS, INTER_CHAR_GAP,
            // S: dot dot dot
            DOT_MS, INTRA_CHAR_GAP, DOT_MS, INTRA_CHAR_GAP, DOT_MS, WORD_GAP
    };

    private final Context context;
    private final CameraManager cameraManager;
    private String cameraId;
    private HandlerThread handlerThread;
    private Handler handler;
    private final AtomicBoolean isFlashing = new AtomicBoolean(false);
    private int patternIndex = 0;
    private boolean flashOn = false;

    public MorseFlashlight(Context context) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Get the first camera with a flash
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    cameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to access camera: " + e.getMessage(), e);
        }
    }

    /**
     * Start blinking SOS in Morse code. Loops indefinitely until stopped.
     */
    public void startSOS() {
        if (cameraId == null) {
            Log.e(TAG, "No camera with flash available");
            return;
        }
        if (isFlashing.compareAndSet(false, true)) {
            patternIndex = 0;
            flashOn = false;
            handlerThread = new HandlerThread("MorseFlashThread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
            handler.post(this::runPattern);
            Log.d(TAG, "SOS flashlight started");
        }
    }

    /**
     * Stop the SOS flashlight beacon.
     */
    public void stopSOS() {
        if (isFlashing.compareAndSet(true, false)) {
            setFlash(false);
            if (handlerThread != null) {
                handlerThread.quitSafely();
                handlerThread = null;
            }
            handler = null;
            Log.d(TAG, "SOS flashlight stopped");
        }
    }

    public boolean isFlashing() {
        return isFlashing.get();
    }

    private void runPattern() {
        if (!isFlashing.get()) return;

        // Alternate: flash ON for duration, then OFF for gap
        flashOn = !flashOn;
        setFlash(flashOn);

        int duration = SOS_PATTERN[patternIndex];
        patternIndex = (patternIndex + 1) % SOS_PATTERN.length;

        if (handler != null) {
            handler.postDelayed(this::runPattern, duration);
        }
    }

    private void setFlash(boolean on) {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to set torch: " + e.getMessage(), e);
        }
    }
}