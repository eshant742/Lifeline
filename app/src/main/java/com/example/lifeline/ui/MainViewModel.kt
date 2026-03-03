package com.example.lifeline.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.lifeline.data.MessageEntity
import com.example.lifeline.data.MessageRepository
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MainViewModel — Manages app state and business logic for MainActivity.
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

    fun updateConnectionCount(count: Int) {
        _connectionCount.postValue(count)
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        _currentLocation.postValue(Pair(latitude, longitude))
    }

    fun setBroadcasting(broadcasting: Boolean) {
        _isBroadcasting.postValue(broadcasting)
    }

    /**
     * Create and return an SOS message entity.
     */
    fun createSosMessage(
        senderId: String,
        senderName: String,
        senderPhone: String,
        status: String,
        content: String
    ): MessageEntity {
        val location = _currentLocation.value
        return MessageEntity(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderName = senderName,
            senderPhone = senderPhone,
            status = status,
            content = content,
            latitude = location?.first ?: 0.0,
            longitude = location?.second ?: 0.0,
            timestamp = System.currentTimeMillis()
        )
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
