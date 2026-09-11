package com.example.lifeline.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from v1 to v2:
         * Adds mesh networking fields, medical profile, rescue coordination,
         * and role metadata columns to the messages table.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Mesh networking fields
                db.execSQL("ALTER TABLE messages ADD COLUMN hopCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN maxHops INTEGER NOT NULL DEFAULT 7")
                db.execSQL("ALTER TABLE messages ADD COLUMN messageType TEXT NOT NULL DEFAULT 'SOS'")
                db.execSQL("ALTER TABLE messages ADD COLUMN priority INTEGER NOT NULL DEFAULT 3")

                // Medical profile
                db.execSQL("ALTER TABLE messages ADD COLUMN bloodType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN allergies TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN medicalConditions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN emergencyContact TEXT NOT NULL DEFAULT ''")

                // Rescue coordination
                db.execSQL("ALTER TABLE messages ADD COLUMN rescueStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN rescuerId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN rescuerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN ackForMessageId TEXT NOT NULL DEFAULT ''")

                // Role & metadata
                db.execSQL("ALTER TABLE messages ADD COLUMN senderRole TEXT NOT NULL DEFAULT 'VICTIM'")
                db.execSQL("ALTER TABLE messages ADD COLUMN isRelayed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifeline_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
