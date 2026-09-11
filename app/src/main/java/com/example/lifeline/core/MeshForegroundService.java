package com.example.lifeline.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.lifeline.R;
import com.example.lifeline.data.MessageEntity;
import com.example.lifeline.ui.MainActivity;

/**
 * MeshForegroundService — Keeps the mesh network alive 24/7.
 * <p>
 * Why Java is superior here:
 * - Android ForegroundService API is Java-first, deeply documented in Java
 * - WakeLock management and BroadcastReceiver patterns are Java-native
 * - Battery monitoring with BatteryManager is cleaner in Java
 * <p>
 * Features:
 * - Persistent notification showing mesh status
 * - WakeLock to prevent CPU sleep during mesh operations
 * - Battery-aware discovery intervals (aggressive/conservative/survival)
 * - Auto-restarts mesh on crash/disconnect
 * - Binds to Activities so UI can communicate with the mesh engine
 */
public class MeshForegroundService extends Service {

    private static final String TAG = "MeshForegroundService";
    private static final String CHANNEL_ID = "lifeline_mesh_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Battery thresholds for adaptive discovery
    private static final int BATTERY_AGGRESSIVE = 50;   // > 50%: full power
    private static final int BATTERY_CONSERVATIVE = 20;  // 20-50%: reduced power
    // < 20%: survival mode

    private NearbyMeshEngine meshEngine;
    private PowerManager.WakeLock wakeLock;
    private final IBinder binder = new MeshBinder();

    private int currentBatteryLevel = 100;
    private int connectedPeers = 0;
    private int messagesReceived = 0;

    // ── Binder for Activity communication ──────────────────────────────

    public class MeshBinder extends Binder {
        public MeshForegroundService getService() {
            return MeshForegroundService.this;
        }
    }

    public NearbyMeshEngine getMeshEngine() {
        return meshEngine;
    }

    /**
     * Called by Activity when connection count changes, so the service
     * can update its notification even after the Activity overrides the callback.
     */
    public void onPeerCountChanged(int count) {
        connectedPeers = count;
        updateNotification();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        createNotificationChannel();
        acquireWakeLock();
        registerBatteryReceiver();

        // Initialize mesh engine
        meshEngine = new NearbyMeshEngine(this);

        // Track connection count for notification
        meshEngine.setOnConnectionCountChanged(count -> {
            connectedPeers = count;
            updateNotification();
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started (intent=" + (intent != null ? "present" : "null (restart)") + ")");

        // Start as foreground immediately
        startForeground(NOTIFICATION_ID, buildNotification());

        // Start the mesh engine (safe to call multiple times — guarded by AtomicBoolean)
        meshEngine.start();

        // START_STICKY ensures Android restarts the service if it's killed.
        // When restarted after kill, intent will be null — that's fine,
        // we just re-start the mesh engine above.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        if (meshEngine != null) {
            meshEngine.shutdown();
        }
        releaseWakeLock();
        unregisterBatteryReceiver();
    }

    // ── Notification ───────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lifeline Mesh Network",
                    NotificationManager.IMPORTANCE_LOW  // Low = no sound, shows in status bar
            );
            channel.setDescription("Keeps the emergency mesh network active in the background");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String batteryMode;
        if (currentBatteryLevel > BATTERY_AGGRESSIVE) {
            batteryMode = "Full Power";
        } else if (currentBatteryLevel > BATTERY_CONSERVATIVE) {
            batteryMode = "Power Saving";
        } else {
            batteryMode = "Survival Mode";
        }

        String contentText = connectedPeers + " peers connected | " + batteryMode
                + " | Battery: " + currentBatteryLevel + "%";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lifeline Mesh Active")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    // ── WakeLock ───────────────────────────────────────────────────────

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Lifeline::MeshWakeLock");
            wakeLock.acquire(24 * 60 * 60 * 1000L); // 24 hours max
            Log.d(TAG, "WakeLock acquired");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }
    }

    // ── Battery Monitoring ─────────────────────────────────────────────

    private BroadcastReceiver batteryReceiver;

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    currentBatteryLevel = (int) ((level / (float) scale) * 100);
                    updateNotification();
                    Log.d(TAG, "Battery: " + currentBatteryLevel + "%");
                }
            }
        };
        // API 34+ requires explicit export flag for broadcast receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }

    private void unregisterBatteryReceiver() {
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver not registered
            }
        }
    }
}