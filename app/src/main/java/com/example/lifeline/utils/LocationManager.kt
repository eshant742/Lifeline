package com.example.lifeline.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

/**
 * LocationManager — GPS location provider using FusedLocationProviderClient.
 * Provides battery-efficient location tracking for SOS messages.
 */
class LocationManager(private val context: Context) {

    companion object {
        private const val TAG = "LocationManager"
        private const val UPDATE_INTERVAL_MS = 30_000L    // 30 seconds
        private const val FASTEST_INTERVAL_MS = 10_000L   // 10 seconds
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    var onLocationUpdated: ((latitude: Double, longitude: Double) -> Unit)? = null

    /**
     * Get the last known location (quick, no GPS wait).
     * Returns null if permissions are missing or location unavailable.
     */
    fun getLastKnownLocation(callback: (latitude: Double, longitude: Double) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Missing location permission")
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        callback(location.latitude, location.longitude)
                    } else {
                        Log.w(TAG, "Last known location is null")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get last location: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    /**
     * Start continuous location updates.
     */
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Missing location permission for updates")
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onLocationUpdated?.invoke(location.latitude, location.longitude)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting location updates: ${e.message}")
        }
    }

    /**
     * Stop location updates. Call in onPause/onDestroy.
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            Log.d(TAG, "Location updates stopped")
        }
        locationCallback = null
    }

    /**
     * Get the last known location synchronously using Android's native LocationManager.
     * This acts as a fallback when FusedLocationProvider hasn't returned a location yet.
     */
    fun getLastKnownLocationSync(): Location? {
        if (!hasLocationPermission()) return null
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return try {
            val gpsLoc = locManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            val netLoc = locManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
                else -> gpsLoc ?: netLoc
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            null
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
