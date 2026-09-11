package com.example.lifeline.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppDatabaseMigrationTest — Tests the v1 → v2 database migration.
 *
 * Validates that all 14 ALTER TABLE statements execute correctly
 * and that existing data is preserved during migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `migration_1_2_adds_all_new_columns`() {
        // Create v1 database with a row
        val db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO messages (id, senderId, senderName, senderPhone, status, content, 
                    latitude, longitude, timestamp, isSynced) 
                VALUES ('test-1', 'device-1', 'Alice', '+91-123', 'TRAPPED', 'Help!', 
                    28.6139, 77.2090, 1000000, 0)
            """)
            close()
        }

        // Run migration
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 2, true,
            AppDatabase::class.java.getDeclaredField("MIGRATION_1_2").let { field ->
                field.isAccessible = true
                field.get(AppDatabase.Companion) as androidx.room.migration.Migration
            }
        )

        // Verify the migrated row has all new columns with defaults
        val cursor = migratedDb.query("SELECT * FROM messages WHERE id = 'test-1'")
        assertTrue("Row should exist after migration", cursor.moveToFirst())

        // Check new columns exist with correct defaults
        assertEquals(0, cursor.getInt(cursor.getColumnIndex("hopCount")))
        assertEquals(7, cursor.getInt(cursor.getColumnIndex("maxHops")))
        assertEquals("SOS", cursor.getString(cursor.getColumnIndex("messageType")))
        assertEquals(3, cursor.getInt(cursor.getColumnIndex("priority")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("bloodType")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("allergies")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("medicalConditions")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("emergencyContact")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("rescueStatus")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("rescuerId")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("rescuerName")))
        assertEquals("", cursor.getString(cursor.getColumnIndex("ackForMessageId")))
        assertEquals("VICTIM", cursor.getString(cursor.getColumnIndex("senderRole")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndex("isRelayed"))) // false = 0

        // Original fields preserved
        assertEquals("test-1", cursor.getString(cursor.getColumnIndex("id")))
        assertEquals("Alice", cursor.getString(cursor.getColumnIndex("senderName")))
        assertEquals("TRAPPED", cursor.getString(cursor.getColumnIndex("status")))

        cursor.close()
        migratedDb.close()
    }

    @Test
    fun `migration_1_2_preserves_multiple_rows`() {
        val db = helper.createDatabase(TEST_DB, 1).apply {
            for (i in 1..5) {
                execSQL("""
                    INSERT INTO messages (id, senderId, senderName, senderPhone, status, content, 
                        latitude, longitude, timestamp, isSynced)
                    VALUES ('msg-$i', 'dev-$i', 'User$i', '+91-$i', 'TRAPPED', 'Help $i', 
                        0.0, 0.0, ${i * 1000}, 0)
                """)
            }
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 2, true,
            AppDatabase::class.java.getDeclaredField("MIGRATION_1_2").let { field ->
                field.isAccessible = true
                field.get(AppDatabase.Companion) as androidx.room.migration.Migration
            }
        )

        val cursor = migratedDb.query("SELECT COUNT(*) FROM messages")
        cursor.moveToFirst()
        assertEquals("All 5 rows should survive migration", 5, cursor.getInt(0))
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun `database_version_2_can_be_opened_fresh`() {
        // Verify that a fresh v2 database can be created without migration
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        assertNotNull(db.messageDao())
        db.close()
    }
}
