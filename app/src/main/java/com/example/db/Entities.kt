package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val initialPreferences: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val serverUrl: String,
    val songTitle: String = "Sinfonia Biometrica.wav",
    val isActive: Boolean = true
)

@Entity(tableName = "biometric_snapshots")
data class BiometricSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Double,
    val speed: Double,
    val steps: Int,
    val calories: Double,
    val currentTempoMultiplier: Float
)
