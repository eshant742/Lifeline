package com.example.lifeline.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MessageEntity — The core data packet that travels through the mesh network.
 *
 * Every SOS signal, status update, ACK, resource post, and danger zone report
 * is represented as a MessageEntity. This entity is:
 * - Stored locally in Room (SQLite)
 * - Serialized to JSON and broadcast over Nearby Connections
 * - Optionally synced to a cloud backend when internet is available
 *
 * Key networking fields:
 * - hopCount: incremented at each relay, message is dropped when >= maxHops
 * - messageType: distinguishes SOS, ACK, HEARTBEAT, RESOURCE, DANGER_ZONE
 * - priority: 1 (low/safe) to 5 (critical/life-threatening) for triage
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,         // UUID — unique per message
    val senderId: String,               // Device's ANDROID_ID
    val senderName: String,             // User's display name
    val senderPhone: String,            // User's phone number
    val status: String,                 // "SAFE", "TRAPPED", "INJURED", "EVACUATED"
    val content: String,                // "SOS" or custom message text
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isSynced: Boolean = false,

    // -- Mesh Networking Fields --
    val hopCount: Int = 0,              // Incremented at each relay hop
    val maxHops: Int = 7,               // TTL — message dies after this many hops
    val messageType: String = "SOS",    // SOS, ACK, HEARTBEAT, RESOURCE, DANGER_ZONE, STATUS_UPDATE
    val priority: Int = 3,              // 1=Low(safe), 2=Info, 3=Medium, 4=High, 5=Critical

    // -- Medical Profile (Embedded in SOS) --
    val bloodType: String = "",         // e.g., "O+", "AB-"
    val allergies: String = "",         // e.g., "Penicillin, Peanuts"
    val medicalConditions: String = "", // e.g., "Diabetes, Asthma"
    val emergencyContact: String = "",  // e.g., "Mom: +91-9876543210"

    // -- Rescue Coordination --
    val rescueStatus: String = "",      // "", "RECEIVED", "EN_ROUTE", "REACHED", "FIRST_AID", "EVACUATED"
    val rescuerId: String = "",         // Device ID of the rescuer who claimed this victim
    val rescuerName: String = "",       // Name of the rescuer
    val ackForMessageId: String = "",   // If messageType=ACK, which original message this acknowledges

    // -- Role & Metadata --
    val senderRole: String = "VICTIM",  // "VICTIM" or "RESCUER"
    val isRelayed: Boolean = false      // Whether this message was relayed (not original)
)
