package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_history")
data class DiagnosticRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val overallScore: Int,
    val gpsStatus: String,
    val horizontalAccuracyMeters: Float?,
    val satellitesTotal: Int,
    val satellitesUsedInFix: Int,
    val networkStatus: String,
    val batteryStatus: String,
    val issuesCount: Int,
    val summaryNote: String
)
