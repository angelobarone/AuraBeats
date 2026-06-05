package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("UPDATE workout_sessions SET isActive = 0, endTime = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: BiometricSnapshotEntity)

    @Query("SELECT * FROM biometric_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSnapshotsForSession(sessionId: Long): Flow<List<BiometricSnapshotEntity>>
}
