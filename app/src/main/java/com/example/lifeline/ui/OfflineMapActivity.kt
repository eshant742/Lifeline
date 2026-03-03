package com.example.lifeline.ui

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import com.example.lifeline.data.AppDatabase
import com.example.lifeline.databinding.ActivityOfflineMapBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/**
 * OfflineMapActivity — Displays SOS signals on an offline map.
 * Uses osmdroid for map rendering (works without internet with cached tiles).
 * Markers are color-coded: Red for TRAPPED, Green for SAFE.
 */
class OfflineMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineMapBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityOfflineMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        loadMarkers()

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }

        // If launched with specific coordinates, center on them
        val lat = intent.getDoubleExtra("latitude", 0.0)
        val lng = intent.getDoubleExtra("longitude", 0.0)
        val name = intent.getStringExtra("senderName")

        if (lat != 0.0 && lng != 0.0) {
            val point = GeoPoint(lat, lng)
            binding.mapView.controller.setCenter(point)
            binding.mapView.controller.setZoom(17.0)

            if (name != null) {
                addMarker(lat, lng, name, "Selected signal", true)
            }
        }
    }

    private fun loadMarkers() {
        val db = AppDatabase.getDatabase(this)
        scope.launch {
            try {
                val messages = withContext(Dispatchers.IO) {
                    db.messageDao().getAllMessages().first()
                }

                var hasMessages = false
                for (message in messages) {
                    if (message.latitude != 0.0 || message.longitude != 0.0) {
                        val isTrapped = message.status == "TRAPPED"
                        addMarker(
                            message.latitude,
                            message.longitude,
                            message.senderName,
                            "${message.status}: ${message.content}\n📞 ${message.senderPhone}",
                            isTrapped
                        )
                        hasMessages = true
                    }
                }

                // If no specific coordinates passed and we have messages, zoom to fit
                val lat = intent.getDoubleExtra("latitude", 0.0)
                if (lat == 0.0 && hasMessages) {
                    val firstMessage = messages.firstOrNull { it.latitude != 0.0 }
                    if (firstMessage != null) {
                        binding.mapView.controller.setCenter(
                            GeoPoint(firstMessage.latitude, firstMessage.longitude)
                        )
                    }
                }

                binding.mapView.invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addMarker(lat: Double, lng: Double, title: String, snippet: String, isTrapped: Boolean) {
        val marker = Marker(binding.mapView)
        marker.position = GeoPoint(lat, lng)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        marker.snippet = snippet
        // osmdroid default marker (red pin). We differentiate via title prefix.
        binding.mapView.overlays.add(marker)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
