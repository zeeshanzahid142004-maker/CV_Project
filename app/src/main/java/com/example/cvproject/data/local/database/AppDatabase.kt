package com.example.cvproject.data.local.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cvproject.data.local.dao.TripDao
import com.example.cvproject.data.local.dao.TripHistoryDao
import com.example.cvproject.data.local.entity.TripHistory
import com.example.cvproject.data.local.entity.TripSession

@Database(
    entities = [TripHistory::class, TripSession::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripHistoryDao(): TripHistoryDao
    abstract fun tripDao(): TripDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trip_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `maxSpeed` REAL NOT NULL,
                        `avgSpeed` REAL NOT NULL,
                        `distance` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
