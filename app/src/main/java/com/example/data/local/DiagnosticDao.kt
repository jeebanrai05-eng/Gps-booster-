package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostic_history ORDER BY timestampMs DESC LIMIT 50")
    fun observeRecentRecords(): Flow<List<DiagnosticRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DiagnosticRecordEntity)

    @Query("DELETE FROM diagnostic_history WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM diagnostic_history")
    suspend fun clearAllRecords()
}
