package com.example.lifeline.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String, // UUID
    val senderId: String,       // Device ID
    val senderName: String,     // User's Real Name
    val senderPhone: String,    // User's Phone Number
    val status: String,         // "SAFE" or "TRAPPED"
    val content: String,        // "SOS" or custom message
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isSynced: Boolean = false
)
