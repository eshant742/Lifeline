package com.example.lifeline.receivers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ShakeDetector — Detects violent shaking to trigger SOS.
 * <p>
 * Why Java is superior here:
 * - SensorManager API is Java-first, all Android sensor docs are in Java
 * - SensorEventListener interface is Java-native
 * - Low-pass filter math is cleaner without Kotlin sugar
 * <p>
 * If a person is trapped (phone in pocket, screen cracked, can't see),
 * violently shaking the phone triggers an automatic SOS broadcast.
 * <p>
 * Algorithm:
 * 1. Read accelerometer values (x, y, z)
 * 2. Calculate magnitude: sqrt(x^2 + y^2 + z^2)
 * 3. Subtract gravity (~9.8) to get net acceleration
 * 4. If net acceleration > threshold for N consecutive readings, trigger SOS
 * 5. Cooldown period prevents repeated triggers
 */
public class ShakeDetector implements SensorEventListener {

    private static final String TAG = "ShakeDetector";

    // Detection thresholds
    private static final float SHAKE_THRESHOLD = 15.0f;     // m/s^2 (strong shake)
    private static final int SHAKE_COUNT_THRESHOLD = 4;       // Number of shakes needed
    private static final long SHAKE_WINDOW_MS = 2000;         // Time window for shake count
    private static final long COOLDOWN_MS = 30000;            // 30 second cooldown between triggers

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private OnShakeListener listener;

    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private long firstShakeTime = 0;
    private long lastTriggerTime = 0;
    private final AtomicBoolean isListening = new AtomicBoolean(false);

    // Low-pass filter values
    private final float[] gravity = new float[3];
    private static final float ALPHA = 0.8f;  // Low-pass filter coefficient

    public interface OnShakeListener {
        void onShakeSOS();
    }

    public ShakeDetector(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * Start listening for shakes.
     */
    public void start(OnShakeListener listener) {
        this.listener = listener;
        if (accelerometer != null && isListening.compareAndSet(false, true)) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "Shake detector started");
        }
    }

    /**
     * Stop listening for shakes.
     */
    public void stop() {
        if (isListening.compareAndSet(true, false)) {
            sensorManager.unregisterListener(this);
            Log.d(TAG, "Shake detector stopped");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Apply low-pass filter to isolate gravity
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

        // Remove gravity to get linear acceleration
        float x = event.values[0] - gravity[0];
        float y = event.values[1] - gravity[1];
        float z = event.values[2] - gravity[2];

        // Calculate acceleration magnitude
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        long now = System.currentTimeMillis();

        if (magnitude > SHAKE_THRESHOLD) {
            if (shakeCount == 0) {
                firstShakeTime = now;
            }
            shakeCount++;
            lastShakeTime = now;

            // Check if we have enough shakes within the time window
            if (shakeCount >= SHAKE_COUNT_THRESHOLD
                    && (now - firstShakeTime) <= SHAKE_WINDOW_MS) {

                // Cooldown check
                if ((now - lastTriggerTime) > COOLDOWN_MS) {
                    lastTriggerTime = now;
                    shakeCount = 0;
                    Log.d(TAG, "SHAKE SOS TRIGGERED! Magnitude: " + magnitude);
                    if (listener != null) {
                        listener.onShakeSOS();
                    }
                }
            }
        }

        // Reset shake count if too much time has passed
        if (shakeCount > 0 && (now - firstShakeTime) > SHAKE_WINDOW_MS) {
            shakeCount = 0;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    public boolean isListening() {
        return isListening.get();
    }
}