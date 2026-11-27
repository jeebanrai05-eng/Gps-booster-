package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DiagnosticRecordEntity::class,
        WorkSessionEntity::class,
        SessionTimelineEventEntity::class,
        ZoneObservationEntity::class,
        BatteryHistoryEntity::class,
        SignalHandoverEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun riderIntelligenceDao(): RiderIntelligenceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER,
                        `status` TEXT NOT NULL,
                        `activeDurationMs` INTEGER NOT NULL,
                        `lastResumedAtMs` INTEGER,
                        `totalSamples` INTEGER NOT NULL,
                        `gpsFixedSamples` INTEGER NOT NULL,
                        `totalAccuracySumMeters` REAL NOT NULL,
                        `accuracySampleCount` INTEGER NOT NULL,
                        `networkOkSamples` INTEGER NOT NULL,
                        `networkLossCount` INTEGER NOT NULL,
                        `overheatEventCount` INTEGER NOT NULL,
                        `startBatteryPct` INTEGER NOT NULL,
                        `endBatteryPct` INTEGER,
                        `maxBatteryTempC` REAL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_timeline_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `detail` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `zone_observations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER,
                        `timestampMs` INTEGER NOT NULL,
                        `zoneKey` TEXT NOT NULL,
                        `approximateLat` REAL NOT NULL,
                        `approximateLon` REAL NOT NULL,
                        `gpsAccuracyMeters` REAL,
                        `satellitesUsed` INTEGER NOT NULL,
                        `gpsFixOk` INTEGER NOT NULL,
                        `networkAvailable` INTEGER NOT NULL,
                        `networkValidated` INTEGER NOT NULL,
                        `transportType` TEXT NOT NULL,
                        `signalDbm` INTEGER,
                        `isWeakSignal` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `battery_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER,
                        `timestampMs` INTEGER NOT NULL,
                        `levelPercent` INTEGER NOT NULL,
                        `temperatureC` REAL,
                        `isCharging` INTEGER NOT NULL,
                        `chargingSource` TEXT NOT NULL,
                        `voltageMv` INTEGER,
                        `currentMicroAmps` INTEGER,
                        `isPowerSave` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_handovers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `slotIndex` INTEGER NOT NULL,
                        `subscriptionId` INTEGER NOT NULL,
                        `operatorName` TEXT NOT NULL,
                        `previousNetworkType` TEXT,
                        `newNetworkType` TEXT NOT NULL,
                        `rssiDbm` INTEGER,
                        `signalLevel` INTEGER NOT NULL,
                        `alertMessage` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rider_booster_diagnostics.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
