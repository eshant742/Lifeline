package com.example.lifeline.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MessageDaoTest — Tests all 15+ DAO queries against an in-memory Room database.
 *
 * Uses Robolectric to run on JVM without a physical device.
 * Uses InstantTaskExecutorRule for synchronous LiveData/Flow execution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MessageDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Insert & Query ─────────────────────────────────────────────────

    @Test
    fun `insertMessage and getMessageById`() = runTest {
        val msg = createMessage("msg-1", status = "TRAPPED", priority = 5)
        dao.insertMessage(msg)

        val retrieved = dao.getMessageById("msg-1")
        assertNotNull(retrieved)
        assertEquals("msg-1", retrieved!!.id)
        assertEquals("TRAPPED", retrieved.status)
        assertEquals(5, retrieved.priority)
    }

    @Test
    fun `insertMessage with conflict replaces existing`() = runTest {
        val original = createMessage("msg-1", content = "Original")
        dao.insertMessage(original)

        val updated = original.copy(content = "Updated")
        dao.insertMessage(updated)

        val retrieved = dao.getMessageById("msg-1")
        assertEquals("Updated", retrieved!!.content)
    }

    @Test
    fun `getMessageById returns null for nonexistent id`() = runTest {
        val result = dao.getMessageById("nonexistent")
        assertNull(result)
    }

    // ── getAllMessages (ordered by priority DESC, timestamp DESC) ──────

    @Test
    fun `getAllMessages returns messages ordered by priority then timestamp`() = runTest {
        dao.insertMessage(createMessage("low", priority = 1, timestamp = 3000))
        dao.insertMessage(createMessage("high", priority = 5, timestamp = 1000))
        dao.insertMessage(createMessage("med", priority = 3, timestamp = 2000))

        val messages = dao.getAllMessages().first()
        assertEquals(3, messages.size)
        assertEquals("high", messages[0].id) // P5 first
        assertEquals("med", messages[1].id)  // P3 second
        assertEquals("low", messages[2].id)  // P1 last
    }

    // ── getMessagesByType ──────────────────────────────────────────────

    @Test
    fun `getMessagesByType filters correctly`() = runTest {
        dao.insertMessage(createMessage("sos-1", messageType = "SOS"))
        dao.insertMessage(createMessage("ack-1", messageType = "ACK"))
        dao.insertMessage(createMessage("hb-1", messageType = "HEARTBEAT"))
        dao.insertMessage(createMessage("sos-2", messageType = "SOS"))

        val sosMessages = dao.getMessagesByType("SOS").first()
        assertEquals(2, sosMessages.size)
        assertTrue(sosMessages.all { it.messageType == "SOS" })

        val ackMessages = dao.getMessagesByType("ACK").first()
        assertEquals(1, ackMessages.size)
    }

    // ── getActiveDistressSignals ───────────────────────────────────────

    @Test
    fun `getActiveDistressSignals returns only TRAPPED and INJURED`() = runTest {
        dao.insertMessage(createMessage("trapped", status = "TRAPPED", priority = 5))
        dao.insertMessage(createMessage("injured", status = "INJURED", priority = 4))
        dao.insertMessage(createMessage("safe", status = "SAFE", priority = 1))
        dao.insertMessage(createMessage("evacuated", status = "EVACUATED", priority = 1))

        val distress = dao.getActiveDistressSignals().first()
        assertEquals(2, distress.size)
        assertTrue(distress.any { it.id == "trapped" })
        assertTrue(distress.any { it.id == "injured" })
        assertFalse(distress.any { it.id == "safe" })
    }

    // ── getDangerZones ─────────────────────────────────────────────────

    @Test
    fun `getDangerZones returns only DANGER_ZONE type`() = runTest {
        dao.insertMessage(createMessage("dz-1", messageType = "DANGER_ZONE"))
        dao.insertMessage(createMessage("sos-1", messageType = "SOS"))
        dao.insertMessage(createMessage("dz-2", messageType = "DANGER_ZONE"))

        val zones = dao.getDangerZones().first()
        assertEquals(2, zones.size)
        assertTrue(zones.all { it.messageType == "DANGER_ZONE" })
    }

    // ── getResources ───────────────────────────────────────────────────

    @Test
    fun `getResources returns only RESOURCE type`() = runTest {
        dao.insertMessage(createMessage("res-1", messageType = "RESOURCE"))
        dao.insertMessage(createMessage("sos-1", messageType = "SOS"))

        val resources = dao.getResources().first()
        assertEquals(1, resources.size)
        assertEquals("res-1", resources[0].id)
    }

    // ── Count Queries ──────────────────────────────────────────────────

    @Test
    fun `getActiveDistressCount returns correct count`() = runTest {
        dao.insertMessage(createMessage("t1", status = "TRAPPED"))
        dao.insertMessage(createMessage("t2", status = "INJURED"))
        dao.insertMessage(createMessage("s1", status = "SAFE"))

        val count = dao.getActiveDistressCount().first()
        assertEquals(2, count)
    }

    @Test
    fun `getSafeCount returns correct count`() = runTest {
        dao.insertMessage(createMessage("s1", status = "SAFE"))
        dao.insertMessage(createMessage("s2", status = "SAFE"))
        dao.insertMessage(createMessage("t1", status = "TRAPPED"))

        val count = dao.getSafeCount().first()
        assertEquals(2, count)
    }

    @Test
    fun `getTotalMessageCount returns all messages`() = runTest {
        dao.insertMessage(createMessage("m1"))
        dao.insertMessage(createMessage("m2"))
        dao.insertMessage(createMessage("m3"))

        val count = dao.getTotalMessageCount().first()
        assertEquals(3, count)
    }

    // ── Rescue Status Update ───────────────────────────────────────────

    @Test
    fun `updateRescueStatus modifies correct fields`() = runTest {
        dao.insertMessage(createMessage("sos-victim"))
        dao.updateRescueStatus("sos-victim", "EN_ROUTE", "rescuer-1", "Dr. Smith")

        val updated = dao.getMessageById("sos-victim")!!
        assertEquals("EN_ROUTE", updated.rescueStatus)
        assertEquals("rescuer-1", updated.rescuerId)
        assertEquals("Dr. Smith", updated.rescuerName)
    }

    // ── Sync Operations ────────────────────────────────────────────────

    @Test
    fun `markAsSynced updates isSynced flag`() = runTest {
        dao.insertMessage(createMessage("unsync", isSynced = false))
        dao.markAsSynced("unsync")

        val msg = dao.getMessageById("unsync")!!
        assertTrue(msg.isSynced)
    }

    @Test
    fun `getUnsyncedMessages returns only unsynced`() = runTest {
        dao.insertMessage(createMessage("synced-1", isSynced = true))
        dao.insertMessage(createMessage("unsynced-1", isSynced = false))
        dao.insertMessage(createMessage("unsynced-2", isSynced = false))

        val unsynced = dao.getUnsyncedMessages()
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { !it.isSynced })
    }

    @Test
    fun `getUnsyncedMessagesSince filters by timestamp`() = runTest {
        dao.insertMessage(createMessage("old", isSynced = false, timestamp = 1000))
        dao.insertMessage(createMessage("new", isSynced = false, timestamp = 5000))

        val recent = dao.getUnsyncedMessagesSince(3000)
        assertEquals(1, recent.size)
        assertEquals("new", recent[0].id)
    }

    // ── Heartbeat Operations ───────────────────────────────────────────

    @Test
    fun `getLastHeartbeat returns most recent heartbeat from sender`() = runTest {
        dao.insertMessage(createMessage("hb-old", senderId = "device-A",
            messageType = "HEARTBEAT", timestamp = 1000))
        dao.insertMessage(createMessage("hb-new", senderId = "device-A",
            messageType = "HEARTBEAT", timestamp = 5000))
        dao.insertMessage(createMessage("hb-other", senderId = "device-B",
            messageType = "HEARTBEAT", timestamp = 9000))

        val latest = dao.getLastHeartbeat("device-A")
        assertNotNull(latest)
        assertEquals("hb-new", latest!!.id)
    }

    @Test
    fun `pruneOldHeartbeats removes only old heartbeats`() = runTest {
        dao.insertMessage(createMessage("hb-old", messageType = "HEARTBEAT", timestamp = 1000))
        dao.insertMessage(createMessage("hb-new", messageType = "HEARTBEAT", timestamp = 9000))
        dao.insertMessage(createMessage("sos-old", messageType = "SOS", timestamp = 1000))

        dao.pruneOldHeartbeats(5000)

        // Old heartbeat removed, new heartbeat kept, SOS kept
        assertNull(dao.getMessageById("hb-old"))
        assertNotNull(dao.getMessageById("hb-new"))
        assertNotNull(dao.getMessageById("sos-old")) // SOS not affected
    }

    // ── Recent Messages ────────────────────────────────────────────────

    @Test
    fun `getRecentMessages respects limit`() = runTest {
        for (i in 1..10) {
            dao.insertMessage(createMessage("msg-$i", timestamp = i.toLong() * 1000))
        }

        val recent = dao.getRecentMessages(3)
        assertEquals(3, recent.size)
        // Should be ordered by timestamp DESC
        assertEquals("msg-10", recent[0].id)
        assertEquals("msg-9", recent[1].id)
        assertEquals("msg-8", recent[2].id)
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private fun createMessage(
        id: String,
        senderId: String = "device-test",
        status: String = "TRAPPED",
        content: String = "Test message",
        messageType: String = "SOS",
        priority: Int = 3,
        timestamp: Long = System.currentTimeMillis(),
        isSynced: Boolean = false
    ): MessageEntity {
        return MessageEntity(
            id = id,
            senderId = senderId,
            senderName = "Test User",
            senderPhone = "+91-000",
            status = status,
            content = content,
            latitude = 28.6139,
            longitude = 77.2090,
            timestamp = timestamp,
            isSynced = isSynced,
            messageType = messageType,
            priority = priority
        )
    }
}
