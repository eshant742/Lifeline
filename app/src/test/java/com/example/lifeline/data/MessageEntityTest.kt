package com.example.lifeline.data

import org.junit.Assert.*
import org.junit.Test

/**
 * MessageEntityTest — Tests for the MessageEntity data class.
 *
 * Validates:
 * - Default values for all optional fields
 * - Data class equality and copy semantics
 * - All field combinations and edge cases
 * - Priority values range
 * - Status/messageType string constants
 */
class MessageEntityTest {

    // ── Default Values ─────────────────────────────────────────────────

    @Test
    fun `default values are correctly set`() {
        val msg = MessageEntity(
            id = "test-id",
            senderId = "sender-1",
            senderName = "Alice",
            senderPhone = "+91-123",
            status = "TRAPPED",
            content = "Help!",
            latitude = 28.6139,
            longitude = 77.2090,
            timestamp = 1000L
        )

        // Check all defaults
        assertEquals(false, msg.isSynced)
        assertEquals(0, msg.hopCount)
        assertEquals(7, msg.maxHops)
        assertEquals("SOS", msg.messageType)
        assertEquals(3, msg.priority)
        assertEquals("", msg.bloodType)
        assertEquals("", msg.allergies)
        assertEquals("", msg.medicalConditions)
        assertEquals("", msg.emergencyContact)
        assertEquals("", msg.rescueStatus)
        assertEquals("", msg.rescuerId)
        assertEquals("", msg.rescuerName)
        assertEquals("", msg.ackForMessageId)
        assertEquals("VICTIM", msg.senderRole)
        assertEquals(false, msg.isRelayed)
    }

    @Test
    fun `full constructor with all fields`() {
        val msg = MessageEntity(
            id = "uuid-123",
            senderId = "device-A",
            senderName = "Bob",
            senderPhone = "+1-555-0123",
            status = "INJURED",
            content = "Broken leg, need help",
            latitude = 40.7128,
            longitude = -74.0060,
            timestamp = System.currentTimeMillis(),
            isSynced = true,
            hopCount = 3,
            maxHops = 5,
            messageType = "SOS",
            priority = 5,
            bloodType = "O+",
            allergies = "Penicillin",
            medicalConditions = "Diabetes",
            emergencyContact = "Mom: +1-555-9999",
            rescueStatus = "EN_ROUTE",
            rescuerId = "rescuer-1",
            rescuerName = "Rescuer John",
            ackForMessageId = "",
            senderRole = "VICTIM",
            isRelayed = true
        )

        assertEquals("uuid-123", msg.id)
        assertEquals("INJURED", msg.status)
        assertEquals(5, msg.priority)
        assertEquals("O+", msg.bloodType)
        assertEquals("Penicillin", msg.allergies)
        assertEquals("EN_ROUTE", msg.rescueStatus)
        assertEquals(true, msg.isRelayed)
        assertEquals(3, msg.hopCount)
    }

    // ── Data Class Equality ────────────────────────────────────────────

    @Test
    fun `two entities with same fields are equal`() {
        val msg1 = createTestMessage("id-1")
        val msg2 = createTestMessage("id-1")
        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())
    }

    @Test
    fun `two entities with different ids are not equal`() {
        val msg1 = createTestMessage("id-1")
        val msg2 = createTestMessage("id-2")
        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `two entities with same id but different content are not equal`() {
        val msg1 = createTestMessage("id-1").copy(content = "Help A")
        val msg2 = createTestMessage("id-1").copy(content = "Help B")
        assertNotEquals(msg1, msg2)
    }

    // ── Copy Semantics ─────────────────────────────────────────────────

    @Test
    fun `copy creates independent instance`() {
        val original = createTestMessage("original-id")
        val copy = original.copy(priority = 5, status = "INJURED")

        assertEquals(5, copy.priority)
        assertEquals("INJURED", copy.status)
        // Original unchanged
        assertEquals(3, original.priority)
        assertEquals("TRAPPED", original.status)
        // Same ID
        assertEquals(original.id, copy.id)
    }

    @Test
    fun `copy with rescue status update`() {
        val original = createTestMessage("sos-1")
        val updated = original.copy(
            rescueStatus = "REACHED",
            rescuerId = "rescuer-42",
            rescuerName = "Dr. Smith"
        )

        assertEquals("REACHED", updated.rescueStatus)
        assertEquals("rescuer-42", updated.rescuerId)
        assertEquals("Dr. Smith", updated.rescuerName)
        // Original unchanged
        assertEquals("", original.rescueStatus)
    }

    @Test
    fun `copy increments hop count for relay`() {
        val original = createTestMessage("relay-test").copy(hopCount = 2)
        val relayed = original.copy(hopCount = original.hopCount + 1, isRelayed = true)

        assertEquals(3, relayed.hopCount)
        assertTrue(relayed.isRelayed)
        assertEquals(2, original.hopCount)
    }

    // ── Message Types ──────────────────────────────────────────────────

    @Test
    fun `all message types are valid strings`() {
        val validTypes = listOf("SOS", "ACK", "HEARTBEAT", "RESOURCE", "DANGER_ZONE", "STATUS_UPDATE")
        for (type in validTypes) {
            val msg = createTestMessage("type-test").copy(messageType = type)
            assertEquals(type, msg.messageType)
        }
    }

    @Test
    fun `all status values are valid`() {
        val validStatuses = listOf("SAFE", "TRAPPED", "INJURED", "EVACUATED")
        for (status in validStatuses) {
            val msg = createTestMessage("status-test").copy(status = status)
            assertEquals(status, msg.status)
        }
    }

    // ── Priority Range ─────────────────────────────────────────────────

    @Test
    fun `priority values span 1 to 5`() {
        for (p in 1..5) {
            val msg = createTestMessage("priority-$p").copy(priority = p)
            assertEquals(p, msg.priority)
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private fun createTestMessage(id: String): MessageEntity {
        return MessageEntity(
            id = id,
            senderId = "device-test",
            senderName = "Test User",
            senderPhone = "+91-000",
            status = "TRAPPED",
            content = "SOS - Test",
            latitude = 28.6139,
            longitude = 77.2090,
            timestamp = 1000000L
        )
    }
}
