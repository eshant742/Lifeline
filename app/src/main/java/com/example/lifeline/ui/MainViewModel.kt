package com.example.lifeline.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.lifeline.data.MessageEntity
import com.example.lifeline.data.MessageRepository
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MainViewModel — Manages app state and business logic for MainActivity.
 * Updated with medical profile support, role toggle, priority classification,
 * and rescue coordination.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MessageRepository(application)

    val allMessages: LiveData<List<MessageEntity>> = repository.allMessages.asLiveData()

    private val _connectionCount = MutableLiveData(0)
    val connectionCount: LiveData<Int> = _connectionCount

    private val _currentLocation = MutableLiveData<Pair<Double, Double>>()
    val currentLocation: LiveData<Pair<Double, Double>> = _currentLocation

    private val _isBroadcasting = MutableLiveData(false)
    val isBroadcasting: LiveData<Boolean> = _isBroadcasting

    private val _userRole = MutableLiveData("VICTIM")
    val userRole: LiveData<String> = _userRole

    private val _isFlashlightActive = MutableLiveData(false)
    val isFlashlightActive: LiveData<Boolean> = _isFlashlightActive

    fun updateConnectionCount(count: Int) {
        _connectionCount.postValue(count)
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        _currentLocation.postValue(Pair(latitude, longitude))
    }

    fun setBroadcasting(broadcasting: Boolean) {
        _isBroadcasting.postValue(broadcasting)
    }

    fun setUserRole(role: String) {
        _userRole.postValue(role)
    }

    fun setFlashlightActive(active: Boolean) {
        _isFlashlightActive.postValue(active)
    }

    /**
     * Create an SOS message with full medical profile and mesh networking fields.
     */
    fun createSosMessage(
        senderId: String,
        senderName: String,
        senderPhone: String,
        status: String,
        content: String,
        latitude: Double,
        longitude: Double,
        messageType: String = "SOS",
        priority: Int = 3,
        bloodType: String = "",
        allergies: String = "",
        medicalConditions: String = "",
        emergencyContact: String = "",
        senderRole: String = "VICTIM"
    ): MessageEntity {
        // Auto-classify priority based on status and keywords
        val autoPriority = classifyPriority(status, content, priority)

        return MessageEntity(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderName = senderName,
            senderPhone = senderPhone,
            status = status,
            content = content,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis(),
            messageType = messageType,
            priority = autoPriority,
            bloodType = bloodType,
            allergies = allergies,
            medicalConditions = medicalConditions,
            emergencyContact = emergencyContact,
            senderRole = senderRole
        )
    }

    /**
     * Smart Priority Classification (NLP keyword-based).
     * Scans message content for severity indicators and adjusts priority.
     */
    private fun classifyPriority(status: String, content: String, basePriority: Int): Int {
        val lowerContent = content.lowercase()

        // Critical keywords → Priority 5
        val criticalKeywords = listOf(
            "bleeding", "blood", "unconscious", "not breathing", "heart attack",
            "fire", "collapse", "child", "baby", "pregnant", "dying", "crushed",
            "can't breathe", "broken bone", "fracture", "severe", "critical",
            "drowning", "electrocution", "gas leak"
        )
        if (criticalKeywords.any { lowerContent.contains(it) }) return 5

        // High keywords → Priority 4
        val highKeywords = listOf(
            "trapped", "injured", "hurt", "pain", "stuck", "help",
            "water rising", "smoke", "no food", "no water", "medication"
        )
        if (highKeywords.any { lowerContent.contains(it) }) return 4

        // Status-based priority
        return when (status) {
            "TRAPPED" -> maxOf(basePriority, 4)
            "INJURED" -> maxOf(basePriority, 4)
            "SAFE" -> 1
            else -> basePriority
        }
    }

    /**
     * Save a message to the local database.
     */
    fun insertMessage(message: MessageEntity) {
        viewModelScope.launch {
            repository.insertMessage(message)
        }
    }
}
