package com.example.lifeline.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import com.example.lifeline.core.MeshForegroundService
import com.example.lifeline.core.NearbyMeshEngine
import com.example.lifeline.databinding.ActivityMainBinding
import com.example.lifeline.receivers.MorseFlashlight
import com.example.lifeline.receivers.ShakeDetector
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
 * - SOS Flashlight toggle (Morse code beacon)
 * - Shake-to-SOS automatic trigger
 * - Role toggle (Victim / Rescuer)
 * - Medical profile embedded in SOS messages
 *
 * Binds to MeshForegroundService for 24/7 mesh networking
 * instead of directly using the deprecated NearbyManager.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Service binding
    private var meshService: MeshForegroundService? = null
    private var meshEngine: NearbyMeshEngine? = null
    private var isBound = false

    // Hardware features
    private lateinit var morseFlashlight: MorseFlashlight
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var locationManager: LocationManager
    private lateinit var audioHelper: AudioHelper
    private lateinit var messagesAdapter: MessagesAdapter

    private var currentStatus = "TRAPPED"
    private var userName = "Unknown"
    private var userPhone = ""
    private var deviceId = ""

    // Medical profile (loaded from SharedPreferences)
    private var bloodType = ""
    private var allergies = ""
    private var medicalConditions = ""
    private var emergencyContact = ""

    // ── Service Connection ──────────────────────────────────────────────

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshForegroundService.MeshBinder
            meshService = binder.service
            meshEngine = binder.service.meshEngine
            isBound = true

            // Configure the mesh engine
            meshEngine?.apply {
                setLocalUserName(userName)
                setLocalDeviceId(deviceId)

                setOnMessageReceived { message ->
                    runOnUiThread {
                        viewModel.insertMessage(message)
                        Toast.makeText(
                            this@MainActivity,
                            "SOS from ${message.senderName}: ${message.status}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                setOnConnectionCountChanged { count ->
                    viewModel.updateConnectionCount(count)
                    // Also let the service know so it updates the notification
                    meshService?.onPeerCountChanged(count)
                }

                setOnAudioReceived { senderId, audioBytes ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Voice message received", Toast.LENGTH_SHORT).show()
                        audioHelper.playAudioFromBytes(audioBytes, senderId)
                    }
                }

                setOnAckReceived { originalMessageId, rescuerName ->
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "✅ $rescuerName acknowledged your SOS!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            viewModel.setBroadcasting(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            meshEngine = null
            isBound = false
            viewModel.setBroadcasting(false)
        }
    }

    // ── Permission Handling ─────────────────────────────────────────────

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.addAll(listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ))
            } else {
                perms.addAll(listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                ))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            perms.add(Manifest.permission.CAMERA)
            return perms.toTypedArray()
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
            // Still try to initialize what we can
            initializeServices()
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

        // Load medical profile
        bloodType = prefs.getString(UserIdentityActivity.KEY_BLOOD_TYPE, "") ?: ""
        allergies = prefs.getString(UserIdentityActivity.KEY_ALLERGIES, "") ?: ""
        medicalConditions = prefs.getString(UserIdentityActivity.KEY_MEDICAL_CONDITIONS, "") ?: ""
        emergencyContact = prefs.getString(UserIdentityActivity.KEY_EMERGENCY_CONTACT, "") ?: ""

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
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        if (::shakeDetector.isInitialized) shakeDetector.stop()
        if (::morseFlashlight.isInitialized) morseFlashlight.stopSOS()
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
            meshEngine?.restart()
        }

        // Voice record button
        binding.btnVoice.setOnClickListener {
            toggleVoiceRecording()
        }

        // SOS Flashlight toggle
        binding.btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }

        // Map button
        binding.btnMap.setOnClickListener {
            startActivity(Intent(this, OfflineMapActivity::class.java))
        }

        // Role toggle chip
        binding.chipRole.setOnClickListener {
            val currentRole = viewModel.userRole.value ?: "VICTIM"
            val newRole = if (currentRole == "VICTIM") "RESCUER" else "VICTIM"
            viewModel.setUserRole(newRole)
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

        viewModel.userRole.observe(this) { role ->
            binding.chipRole.text = role
            if (role == "RESCUER") {
                binding.chipRole.setChipBackgroundColorResource(R.color.safe_green)
            } else {
                binding.chipRole.setChipBackgroundColorResource(R.color.warning_amber)
            }
        }

        viewModel.isFlashlightActive.observe(this) { active ->
            if (active) {
                binding.btnFlashlight.text = "🔦✓"
                binding.btnFlashlight.setStrokeColorResource(R.color.warning_amber)
            } else {
                binding.btnFlashlight.text = "🔦"
                binding.btnFlashlight.setStrokeColorResource(R.color.border_color)
            }
        }
    }

    // ── Services ────────────────────────────────────────────────────────

    private fun initializeServices() {
        // Start and bind to MeshForegroundService
        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

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

        // Initialize Morse Flashlight
        morseFlashlight = MorseFlashlight(this)

        // Initialize Shake Detector
        shakeDetector = ShakeDetector(this)
        shakeDetector.start {
            // Shake-to-SOS triggered!
            runOnUiThread {
                Toast.makeText(this, "📳 SHAKE SOS TRIGGERED!", Toast.LENGTH_LONG).show()
                currentStatus = "TRAPPED"
                updateStatusUI()
                sendSosMessage()
            }
        }
        binding.tvShakeStatus.text = "📳 Shake-to-SOS: Active"
        binding.tvShakeStatus.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
    }

    // ── SOS Actions ─────────────────────────────────────────────────────

    private fun sendSosMessage() {
        val customMessage = binding.etMessage.text.toString().trim()
        val content = if (customMessage.isNotEmpty()) customMessage else "SOS - $currentStatus"

        var lat = viewModel.currentLocation.value?.first ?: 0.0
        var lng = viewModel.currentLocation.value?.second ?: 0.0

        if (lat == 0.0 && lng == 0.0) {
            if (::locationManager.isInitialized) {
                val fallback = locationManager.getLastKnownLocationSync()
                if (fallback != null) {
                    lat = fallback.latitude
                    lng = fallback.longitude
                    viewModel.updateLocation(lat, lng)
                } else {
                    Toast.makeText(this, "GPS signal weak. Sending with unknown location.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val role = viewModel.userRole.value ?: "VICTIM"

        val message = viewModel.createSosMessage(
            senderId = deviceId,
            senderName = userName,
            senderPhone = userPhone,
            status = currentStatus,
            content = content,
            latitude = lat,
            longitude = lng,
            bloodType = bloodType,
            allergies = allergies,
            medicalConditions = medicalConditions,
            emergencyContact = emergencyContact,
            senderRole = role
        )

        // Save locally
        viewModel.insertMessage(message)

        // Broadcast to nearby peers via the mesh engine
        meshEngine?.sendMessage(message)

        // Clear input
        binding.etMessage.text?.clear()

        // Haptic feedback
        binding.btnSos.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        Toast.makeText(this, "Message sent: $content", Toast.LENGTH_LONG).show()
    }

    private fun toggleVoiceRecording() {
        if (!::audioHelper.isInitialized) return

        if (audioHelper.isRecording) {
            // Stop recording and send
            val filePath = audioHelper.stopRecording()
            binding.btnVoice.text = "🎤"
            binding.tvRecordingStatus.visibility = View.GONE

            if (filePath != null) {
                val audioFile = File(filePath)
                if (audioFile.exists()) {
                    meshEngine?.sendAudio(audioFile.readBytes(), deviceId)
                    Toast.makeText(this, "Voice message sent!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Start recording
            val filename = "sos_${System.currentTimeMillis()}"
            audioHelper.startRecording(filename)
            binding.btnVoice.text = "⏹"
            binding.tvRecordingStatus.visibility = View.VISIBLE
            binding.tvRecordingStatus.text = "🔴 Recording..."
        }
    }

    private fun toggleFlashlight() {
        if (!::morseFlashlight.isInitialized) return

        if (morseFlashlight.isFlashing) {
            morseFlashlight.stopSOS()
            viewModel.setFlashlightActive(false)
            Toast.makeText(this, "SOS Flashlight OFF", Toast.LENGTH_SHORT).show()
        } else {
            morseFlashlight.startSOS()
            viewModel.setFlashlightActive(true)
            Toast.makeText(this, "SOS Flashlight ON (··· ─── ···)", Toast.LENGTH_SHORT).show()
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
