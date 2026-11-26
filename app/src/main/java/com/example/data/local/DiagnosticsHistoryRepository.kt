package com.example.data.local

import kotlinx.coroutines.flow.Flow

class DiagnosticsHistoryRepository(private val diagnosticDao: DiagnosticDao) {
    val recentRecords: Flow<List<DiagnosticRecordEntity>> = diagnosticDao.observeRecentRecords()

    suspend fun saveRecord(record: DiagnosticRecordEntity) {
        diagnosticDao.insertRecord(record)
    }

    suspend fun deleteById(id: Long) {
        diagnosticDao.deleteRecordById(id)
    }

    suspend fun clearAll() {
        diagnosticDao.clearAllRecords()
    }
}
