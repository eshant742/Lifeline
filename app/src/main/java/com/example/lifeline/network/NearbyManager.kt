package com.example.lifeline.network

import android.content.Context
import android.util.Log
import com.example.lifeline.data.MessageEntity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson

/**
 * NearbyManager — The original peer-to-peer engine (Kotlin).
 *
 * @deprecated Replaced by {@link com.example.lifeline.core.NearbyMeshEngine} (Java)
 * which provides thread-safe ConcurrentHashMap, bounded LRU cache (fixes OOM),
 * AES-256-GCM encryption, GZIP compression, gossip protocol relay,
 * Store-and-Forward DTN, rate limiting, and audio relay through mesh.
 *
 * This class is kept for reference only. Do NOT use in production.
 */
@Deprecated("Use NearbyMeshEngine via MeshForegroundService instead")
class NearbyManager(private val context: Context) {

    companion object {
        private const val TAG = "NearbyManager"
        private const val SERVICE_ID = "com.example.lifeline"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()

    // Track connected endpoints
    private val connectedEndpoints = mutableMapOf<String, String>() // endpointId -> endpointName

    // Track message IDs we've already seen (prevent infinite relay loops)
    private val seenMessageIds = mutableSetOf<String>()

    // Callbacks set by the UI layer
    var onMessageReceived: ((MessageEntity) -> Unit)? = null
    var onConnectionCountChanged: ((Int) -> Unit)? = null
    var onAudioReceived: ((String, ByteArray) -> Unit)? = null // senderId, audioData

    // The local user's name (set from SharedPreferences)
    var localUserName: String = "Unknown"

    /**
     * Start advertising + discovery simultaneously.
     * Call this when the app launches.
     */
    fun start() {
        startAdvertising()
        startDiscovery()
    }

    /**
     * Stop all Nearby Connections activity.
     */
    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        seenMessageIds.clear()
        onConnectionCountChanged?.invoke(0)
        Log.d(TAG, "Stopped all Nearby Connections")
    }

    /**
     * Manually restart advertising and discovery (drops existing connections).
     */
    fun restart() {
        stop()
        start()
    }

    /**
     * Send an SOS message to all connected peers.
     */
    fun sendMessage(message: MessageEntity) {
        seenMessageIds.add(message.id) // Mark as seen so we don't re-process our own
        val json = gson.toJson(message)
        val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))

        for (endpointId in connectedEndpoints.keys) {
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Sent message to endpoint: $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send to $endpointId: ${e.message}")
                }
        }
    }

    /**
     * Send an audio voice message to all connected peers.
     */
    fun sendAudio(audioData: ByteArray, senderId: String) {
        // Prefix with "AUDIO:" marker + senderId + "|" separator
        val header = "AUDIO:$senderId|".toByteArray(Charsets.UTF_8)
        val combined = header + audioData
        val payload = Payload.fromBytes(combined)

        for (endpointId in connectedEndpoints.keys) {
            connectionsClient.sendPayload(endpointId, payload)
        }
    }

    // ── Advertising ─────────────────────────────────────────────────────

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            localUserName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
            // Retry after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startAdvertising()
            }, 5000)
        }
    }

    // ── Discovery ───────────────────────────────────────────────────────

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
            // Retry after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startDiscovery()
            }, 5000)
        }
    }

    // ── Endpoint Discovery Callback ─────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            // Auto-request connection
            connectionsClient.requestConnection(
                localUserName,
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d(TAG, "Connection requested to $endpointId")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Connection request failed to $endpointId: ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    // ── Connection Lifecycle ────────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: ${info.endpointName}")
            // Auto-accept all connections (emergency scenario — trust everyone)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to: $endpointId")
                    connectedEndpoints[endpointId] = endpointId
                    onConnectionCountChanged?.invoke(connectedEndpoints.size)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected by: $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with: $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            connectedEndpoints.remove(endpointId)
            onConnectionCountChanged?.invoke(connectedEndpoints.size)
        }
    }

    // ── Payload Handling ────────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val data = String(bytes, Charsets.UTF_8)

                // Check if it's an audio payload
                if (data.startsWith("AUDIO:")) {
                    val separatorIndex = data.indexOf('|')
                    if (separatorIndex > 6) {
                        val senderId = data.substring(6, separatorIndex)
                        val audioBytes = bytes.copyOfRange(
                            "AUDIO:$senderId|".toByteArray(Charsets.UTF_8).size,
                            bytes.size
                        )
                        onAudioReceived?.invoke(senderId, audioBytes)
                    }
                    return
                }

                // Regular message payload
                try {
                    val message = gson.fromJson(data, MessageEntity::class.java)

                    // Skip if we've already seen this message
                    if (seenMessageIds.contains(message.id)) {
                        return
                    }
                    seenMessageIds.add(message.id)

                    Log.d(TAG, "Received message from ${message.senderName}: ${message.content}")

                    // Notify the UI
                    onMessageReceived?.invoke(message)

                    // Relay to other connected peers (mesh propagation)
                    relayMessage(payload, endpointId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: ${e.message}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Transfer progress updates (useful for large file payloads)
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload transfer complete from $endpointId")
            }
        }
    }

    /**
     * Relay a message to all connected peers except the sender.
     * This creates a mesh-like network where messages propagate through the crowd.
     */
    private fun relayMessage(payload: Payload, senderEndpointId: String) {
        val bytes = payload.asBytes() ?: return
        for (endpointId in connectedEndpoints.keys) {
            if (endpointId != senderEndpointId) {
                val relayPayload = Payload.fromBytes(bytes)
                connectionsClient.sendPayload(endpointId, relayPayload)
                Log.d(TAG, "Relayed message to $endpointId")
            }
        }
    }

    fun getConnectedCount(): Int = connectedEndpoints.size
}
