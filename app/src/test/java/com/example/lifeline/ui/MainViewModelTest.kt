package com.example.lifeline.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.lifeline.data.MessageEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

/**
 * MainViewModelTest — Tests for the MainViewModel's business logic.
 *
 * Focuses on:
 * - NLP-based priority classification (30 keywords across critical/high)
 * - SOS message creation with all fields
 * - Role toggle
 * - Location updates
 * - Flashlight state
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = MainViewModel(app)
    }

    // ── Priority Classification: Critical Keywords (→ P5) ──────────────

    @Test
    fun `classify_bleeding_as_critical`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "I am bleeding heavily",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority)
    }

    @Test
    fun `classify_unconscious_as_critical`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "Person is unconscious here",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority)
    }

    @Test
    fun `classify_not_breathing_as_critical`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "SAFE", content = "Someone is not breathing",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority)
    }

    @Test
    fun `classify_fire_as_critical`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "There is a fire on the 2nd floor",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority)
    }

    @Test
    fun `classify_child_as_critical`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "A child is trapped under debris",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority)
    }

    @Test
    fun `classify_drowning_as_critical`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "Person is drowning in floodwater",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority)
    }

    // ── Priority Classification: High Keywords (→ P4) ──────────────────

    @Test
    fun `classify_trapped_keyword_as_high`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "SAFE", content = "I am trapped in the elevator",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(4, msg.priority)
    }

    @Test
    fun `classify_injured_keyword_as_high`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "SAFE", content = "I am injured, twisted ankle",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(4, msg.priority)
    }

    @Test
    fun `classify_water_rising_as_high`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "Water rising quickly in basement",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(4, msg.priority)
    }

    // ── Priority Classification: Status-Based ──────────────────────────

    @Test
    fun `TRAPPED_status_defaults_to_priority_4`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "TRAPPED", content = "SOS",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(4, msg.priority) // "help" in high keywords
    }

    @Test
    fun `SAFE_status_defaults_to_priority_1`() {
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "SAFE", content = "I am okay, safe location",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(1, msg.priority)
    }

    @Test
    fun `keyword_priority_overrides_status_priority`() {
        // Even though status is SAFE, critical keyword should win
        val msg = viewModel.createSosMessage(
            senderId = "dev", senderName = "Test", senderPhone = "+91",
            status = "SAFE", content = "Someone has a heart attack",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(5, msg.priority) // "heart attack" → critical
    }

    // ── Message Creation ───────────────────────────────────────────────

    @Test
    fun `createSosMessage_sets_all_fields_correctly`() {
        val msg = viewModel.createSosMessage(
            senderId = "device-ABC",
            senderName = "John Doe",
            senderPhone = "+91-9876543210",
            status = "TRAPPED",
            content = "Help me, building collapsed",
            latitude = 28.6139,
            longitude = 77.2090,
            bloodType = "O+",
            allergies = "Penicillin",
            medicalConditions = "Diabetes",
            emergencyContact = "Mom: +91-111",
            senderRole = "VICTIM"
        )

        assertEquals("device-ABC", msg.senderId)
        assertEquals("John Doe", msg.senderName)
        assertEquals("+91-9876543210", msg.senderPhone)
        assertEquals("TRAPPED", msg.status)
        assertEquals(28.6139, msg.latitude, 0.0001)
        assertEquals(77.2090, msg.longitude, 0.0001)
        assertEquals("O+", msg.bloodType)
        assertEquals("Penicillin", msg.allergies)
        assertEquals("Diabetes", msg.medicalConditions)
        assertEquals("Mom: +91-111", msg.emergencyContact)
        assertEquals("VICTIM", msg.senderRole)
        assertEquals("SOS", msg.messageType)
        assertTrue(msg.id.isNotEmpty()) // UUID generated
        assertTrue(msg.timestamp > 0) // Timestamp set
    }

    @Test
    fun `createSosMessage_generates_unique_ids`() {
        val msg1 = viewModel.createSosMessage(
            senderId = "dev", senderName = "T", senderPhone = "",
            status = "SAFE", content = "A", latitude = 0.0, longitude = 0.0
        )
        val msg2 = viewModel.createSosMessage(
            senderId = "dev", senderName = "T", senderPhone = "",
            status = "SAFE", content = "B", latitude = 0.0, longitude = 0.0
        )
        assertNotEquals("Each message should get a unique UUID", msg1.id, msg2.id)
    }

    // ── ViewModel State ────────────────────────────────────────────────

    @Test
    fun `updateConnectionCount_updates_livedata`() {
        viewModel.updateConnectionCount(5)
        assertEquals(5, viewModel.connectionCount.value)
    }

    @Test
    fun `updateLocation_updates_livedata`() {
        viewModel.updateLocation(28.6139, 77.2090)
        val location = viewModel.currentLocation.value
        assertNotNull(location)
        assertEquals(28.6139, location!!.first, 0.0001)
        assertEquals(77.2090, location.second, 0.0001)
    }

    @Test
    fun `setUserRole_toggles_between_VICTIM_and_RESCUER`() {
        viewModel.setUserRole("RESCUER")
        assertEquals("RESCUER", viewModel.userRole.value)

        viewModel.setUserRole("VICTIM")
        assertEquals("VICTIM", viewModel.userRole.value)
    }

    @Test
    fun `setFlashlightActive_updates_state`() {
        viewModel.setFlashlightActive(true)
        assertEquals(true, viewModel.isFlashlightActive.value)

        viewModel.setFlashlightActive(false)
        assertEquals(false, viewModel.isFlashlightActive.value)
    }

    @Test
    fun `setBroadcasting_updates_state`() {
        viewModel.setBroadcasting(true)
        assertEquals(true, viewModel.isBroadcasting.value)
    }
}
