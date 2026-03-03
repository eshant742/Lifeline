package com.example.lifeline.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.example.lifeline.R
import com.example.lifeline.databinding.ActivityMainBinding
import com.example.lifeline.network.NearbyManager
import com.example.lifeline.utils.AudioHelper
import com.example.lifeline.utils.LocationManager
import com.example.lifeline.workers.SyncWorker
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MainActivity — The main SOS dashboard.
 *
 * Features:
 * - Large SOS button to broadcast distress signal
 * - Status toggle (SAFE / TRAPPED)
 * - Custom message input
 * - Voice recording for audio SOS
 * - Live message list from all nearby peers
 * - Connection count display
 * - GPS coordinates display
 * - Map button to view all signals on offline map
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var nearbyManager: NearbyManager
    private lateinit var locationManager: LocationManager
    private lateinit var audioHelper: AudioHelper
    private lateinit var messagesAdapter: MessagesAdapter

    private var currentStatus = "TRAPPED"
    private var userName = "Unknown"
    private var userPhone = ""
    private var deviceId = ""

    // ── Permission Handling ─────────────────────────────────────────────

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.RECORD_AUDIO
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeServices()
        } else {
            Toast.makeText(
                this,
                "Permissions are required for SOS functionality",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load user identity
        val prefs = getSharedPreferences(UserIdentityActivity.PREFS_NAME, MODE_PRIVATE)
        userName = prefs.getString(UserIdentityActivity.KEY_USER_NAME, "Unknown") ?: "Unknown"
        userPhone = prefs.getString(UserIdentityActivity.KEY_USER_PHONE, "") ?: ""
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        setupUI()
        setupRecyclerView()
        observeViewModel()
        scheduleSyncWorker()

        // Check permissions and start services
        if (hasAllPermissions()) {
            initializeServices()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::nearbyManager.isInitialized) nearbyManager.stop()
        if (::locationManager.isInitialized) locationManager.stopLocationUpdates()
        if (::audioHelper.isInitialized) audioHelper.release()
    }

    // ── UI Setup ────────────────────────────────────────────────────────

    private fun setupUI() {
        // User info display
        binding.tvUserName.text = userName

        // Status toggle
        binding.btnTrapped.setOnClickListener {
            currentStatus = "TRAPPED"
            updateStatusUI()
        }
        binding.btnSafe.setOnClickListener {
            currentStatus = "SAFE"
            updateStatusUI()
        }
        updateStatusUI()

        // Send Button
        binding.btnSos.setOnClickListener {
            sendSosMessage()
        }

        // Refresh Peers Button
        binding.btnRefreshPeers.setOnClickListener {
            Toast.makeText(this, "Refreshing peer discovery...", Toast.LENGTH_SHORT).show()
            if (::nearbyManager.isInitialized) {
                nearbyManager.restart()
            }
        }

        // Voice record button
        binding.btnVoice.setOnClickListener {
            toggleVoiceRecording()
        }

        // Map button
        binding.btnMap.setOnClickListener {
            startActivity(Intent(this, OfflineMapActivity::class.java))
        }
    }

    private fun updateStatusUI() {
        if (currentStatus == "TRAPPED") {
            binding.btnTrapped.setBackgroundColor(ContextCompat.getColor(this, R.color.emergency_red))
            binding.btnSafe.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_surface))
            binding.btnSos.setBackgroundColor(ContextCompat.getColor(this, R.color.emergency_red))
        } else {
            binding.btnTrapped.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_surface))
            binding.btnSafe.setBackgroundColor(ContextCompat.getColor(this, R.color.safe_green))
            binding.btnSos.setBackgroundColor(ContextCompat.getColor(this, R.color.safe_green))
        }
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter { message ->
            // Click on message -> show on map
            val intent = Intent(this, OfflineMapActivity::class.java).apply {
                putExtra("latitude", message.latitude)
                putExtra("longitude", message.longitude)
                putExtra("senderName", message.senderName)
            }
            startActivity(intent)
        }
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messagesAdapter
        }
    }

    // ── Observe ViewModel ───────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.allMessages.observe(this) { messages ->
            messagesAdapter.submitList(messages)
            binding.tvMessageCount.text = "${messages.size} signals received"
            // Auto-scroll to top (newest messages first)
            if (messages.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(0)
            }
        }

        viewModel.connectionCount.observe(this) { count ->
            binding.tvConnectionCount.text = "$count peers connected"
            binding.ivConnectionStatus.setColorFilter(
                if (count > 0) ContextCompat.getColor(this, R.color.safe_green)
                else ContextCompat.getColor(this, R.color.text_secondary)
            )
        }

        viewModel.currentLocation.observe(this) { (lat, lng) ->
            binding.tvLocation.text = String.format("📍 %.4f, %.4f", lat, lng)
        }
    }

    // ── Services ────────────────────────────────────────────────────────

    private fun initializeServices() {
        // Initialize Nearby Manager
        nearbyManager = NearbyManager(this)
        nearbyManager.localUserName = userName

        nearbyManager.onMessageReceived = { message ->
            runOnUiThread {
                viewModel.insertMessage(message)
                Toast.makeText(
                    this,
                    "SOS from ${message.senderName}: ${message.status}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        nearbyManager.onConnectionCountChanged = { count ->
            viewModel.updateConnectionCount(count)
        }

        nearbyManager.onAudioReceived = { senderId, audioBytes ->
            runOnUiThread {
                Toast.makeText(this, "Voice message from nearby device", Toast.LENGTH_SHORT).show()
                audioHelper.playAudioFromBytes(audioBytes, senderId)
            }
        }

        nearbyManager.start()
        viewModel.setBroadcasting(true)

        // Initialize Location Manager
        locationManager = LocationManager(this)
        locationManager.onLocationUpdated = { lat, lng ->
            viewModel.updateLocation(lat, lng)
        }
        locationManager.startLocationUpdates()

        // Get initial location
        locationManager.getLastKnownLocation { lat, lng ->
            viewModel.updateLocation(lat, lng)
        }

        // Initialize Audio Helper
        audioHelper = AudioHelper(this)
    }

    // ── SOS Actions ─────────────────────────────────────────────────────

    private fun sendSosMessage() {
        val customMessage = binding.etMessage.text.toString().trim()
        val content = if (customMessage.isNotEmpty()) customMessage else "SOS - $currentStatus"

        val message = viewModel.createSosMessage(
            senderId = deviceId,
            senderName = userName,
            senderPhone = userPhone,
            status = currentStatus,
            content = content
        )

        // Save locally
        viewModel.insertMessage(message)

        // Broadcast to nearby peers
        nearbyManager.sendMessage(message)

        // Clear input
        binding.etMessage.text?.clear()

        // Haptic feedback
        binding.btnSos.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        Toast.makeText(this, "Message sent: $content", Toast.LENGTH_LONG).show()
    }

    private fun toggleVoiceRecording() {
        if (audioHelper.isRecording) {
            // Stop recording and send
            val filePath = audioHelper.stopRecording()
            binding.btnVoice.text = "🎤 Voice"
            binding.tvRecordingStatus.visibility = View.GONE

            if (filePath != null) {
                val audioFile = File(filePath)
                if (audioFile.exists()) {
                    nearbyManager.sendAudio(audioFile.readBytes(), deviceId)
                    Toast.makeText(this, "Voice message sent!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Start recording
            val filename = "sos_${System.currentTimeMillis()}"
            audioHelper.startRecording(filename)
            binding.btnVoice.text = "⏹ Stop"
            binding.tvRecordingStatus.visibility = View.VISIBLE
            binding.tvRecordingStatus.text = "🔴 Recording..."
        }
    }

    // ── Background Sync ─────────────────────────────────────────────────

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "lifeline_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    // ── Permissions ─────────────────────────────────────────────────────

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
