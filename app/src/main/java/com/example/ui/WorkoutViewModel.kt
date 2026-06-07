package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.WorkoutAudioPlayer
import com.example.db.BiometricSnapshotEntity
import com.example.db.SessionDao
import com.example.db.SessionDao_Impl
import com.example.db.SessionEntity
import com.example.db.WorkoutDatabase
import com.example.db.WorkoutRepository
import com.example.health.BiometricSnapshot
import com.example.health.HealthConnectManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WorkoutDatabase.getDatabase(application)
    private val repository = WorkoutRepository(application, db.sessionDao())
    val healthManager = HealthConnectManager(application)
    val audioPlayer = WorkoutAudioPlayer(application)

    // UI Configuration Inputs
    private val _selectedActivity = MutableStateFlow("Running")
    val selectedActivity: StateFlow<String> = _selectedActivity.asStateFlow()

    private val _textPreferences = MutableStateFlow("Aumenta il ritmo quando accelero!")
    val textPreferences: StateFlow<String> = _textPreferences.asStateFlow()

    private val _serverUrl = MutableStateFlow("https://tissue-feast-stabilize.ngrok-free.dev")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // Real-time parameters
    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _isServerLoading = MutableStateFlow(false)
    val isServerLoading: StateFlow<Boolean> = _isServerLoading.asStateFlow()

    private val _serverFeedback = MutableStateFlow<String?>(null)
    val serverFeedback: StateFlow<String?> = _serverFeedback.asStateFlow()

    // Historical sessions and current snap lists
    val sessionHistory: StateFlow<List<SessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionSnapshots = MutableStateFlow<List<BiometricSnapshotEntity>>(emptyList())
    val currentSessionSnapshots: StateFlow<List<BiometricSnapshotEntity>> = _currentSessionSnapshots.asStateFlow()

    // Note input during session (Fase 2 preferences)
    private val _livePreferencesNote = MutableStateFlow("")
    val livePreferencesNote: StateFlow<String> = _livePreferencesNote.asStateFlow()

    // Average biometrics over periodic interval
    private val intervalHeartRateSnapshot = mutableListOf<Double>()
    private var baseStepsValueAtIntervalStart = 0
    private var baseCaloriesValueAtIntervalStart = 0.0

    // Coroutine Jobs for periodic loops
    private var primaryWorkoutJob: Job? = null
    private var syncIntervalSeconds = 30 // FastAPI query frequency
    private var nextSyncCountdown = MutableStateFlow(syncIntervalSeconds)
    val syncCountdown: StateFlow<Int> = nextSyncCountdown.asStateFlow()

    private var activeTrackIndex = 1

    fun selectActivity(activity: String) {
        _selectedActivity.value = activity
    }

    fun updatePreferences(text: String) {
        _textPreferences.value = text
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    fun updateLivePreferences(note: String) {
        _livePreferencesNote.value = note
    }

    fun toggleSimulator(enabled: Boolean) {
        healthManager.toggleSimulatorMode(enabled)
    }

    fun clearFeedback() {
        _serverFeedback.value = null
    }

    // FASE 1: AVVIA SESSIONE
    fun startWorkout() {
        val activity = _selectedActivity.value
        val initialNotes = _textPreferences.value
        val url = _serverUrl.value

        viewModelScope.launch {
            _isServerLoading.value = true
            _serverFeedback.value = "Connessione a $url in corso..."
            healthManager.resetSessionAccumulators()
            
            // Invoke DB and Call FastAPI session start
            val (sessionId, downloadedFiles) = repository.startWorkoutSession(activity, initialNotes, url)
            _activeSessionId.value = sessionId
            _isWorkoutActive.value = true
            _isServerLoading.value = false

            // Set state based on download status
            if (!downloadedFiles.isNullOrEmpty()) {
                _serverFeedback.value = "Successo! Traccia ricevuta da FastAPI."
                if (downloadedFiles.size > 1) {
                    audioPlayer.setWeightliftingTracks(downloadedFiles[0], downloadedFiles[1])
                    audioPlayer.updateWeightliftingHeartRate(70.0) // Initial HR
                } else {
                    audioPlayer.playWav(downloadedFiles[0], false)
                }
            } else {
                _serverFeedback.value = "Server offline. Modalità Sintetizzatore Intelligente attivata!"
                audioPlayer.play(activity) // Fall back to high quality live synth beats
            }

            // Begin real-time tickers
            activeTrackIndex = 1
            _timerSeconds.value = 0
            nextSyncCountdown.value = syncIntervalSeconds
            baseStepsValueAtIntervalStart = 0
            baseCaloriesValueAtIntervalStart = 0.0
            intervalHeartRateSnapshot.clear()
            _livePreferencesNote.value = ""

            // Subscribe to DB snaps for visual chart rendering
            launch {
                repository.getSnapshotsForSession(sessionId).collect { list ->
                    _currentSessionSnapshots.value = list
                }
            }

            startRealtimeLoops(sessionId)
        }
    }

    // FASE 2: RIPRODUZIONE ADATTIVA E INVIO PERIODICO DEI DATI BIOMETRICI
    private fun startRealtimeLoops(sessionId: Long) {
        primaryWorkoutJob?.cancel()
        primaryWorkoutJob = viewModelScope.launch {
            while (_isWorkoutActive.value) {
                delay(1000L) // 1 second tick
                _timerSeconds.value += 1
                
                // 1. Update biometric telemetry (simulator or real)
                val activity = _selectedActivity.value
                healthManager.updateBiometricData(activity)
                
                val currentBiometrics = healthManager.currentBiometrics.value
                // Record telemetry in our temporary interval buffer
                intervalHeartRateSnapshot.add(currentBiometrics.heartRate)

                // 2. ADAPTIVE MUSIC playback: Speed up/slow down rate dynamically based on current HR vs average
                // Default heart rates: Running (140 avg), Weightlifting (115 avg), Yoga (68 avg)
                val targetBpmRatio = when(activity.lowercase()) {
                    "running" -> currentBiometrics.heartRate / 145.0
                    "weightlifting" -> currentBiometrics.heartRate / 110.0
                    else -> currentBiometrics.heartRate / 72.0
                }
                // Convert ratio to playback speed modifier (e.g. 0.8f to 1.6f)
                val adjustedTempo = targetBpmRatio.coerceIn(0.70, 1.6).toFloat()
                audioPlayer.setTempo(adjustedTempo)
                audioPlayer.updateWeightliftingHeartRate(currentBiometrics.heartRate)

                // Log raw snapshot into database every 5 seconds for display analytics
                if (_timerSeconds.value % 5 == 0) {
                    repository.logSnapshot(
                        sessionId = sessionId,
                        heartRate = currentBiometrics.heartRate,
                        speed = currentBiometrics.speedKmh,
                        steps = currentBiometrics.totalSteps,
                        calories = currentBiometrics.caloriesBurned,
                        tempoMultiplier = adjustedTempo
                    )
                }

                // 3. Periodic FastAPI Trigger countdown
                nextSyncCountdown.value -= 1
                if (nextSyncCountdown.value <= 0) {
                    triggerPeriodicFastApiUpdate(sessionId)
                    nextSyncCountdown.value = syncIntervalSeconds
                }
            }
        }
    }

    var newStyle = false
    private suspend fun triggerPeriodicFastApiUpdate(sessionId: Long) {
        val currentBiometrics = healthManager.currentBiometrics.value
        val url = _serverUrl.value
        val activity = _selectedActivity.value

        // Compute averages over the sync window
        val avgHr = if (intervalHeartRateSnapshot.isNotEmpty()) {
            intervalHeartRateSnapshot.average()
        } else {
            currentBiometrics.heartRate
        }
        intervalHeartRateSnapshot.clear()

        val stepsDiff = currentBiometrics.totalSteps - baseStepsValueAtIntervalStart
        baseStepsValueAtIntervalStart = currentBiometrics.totalSteps

        val caloriesDiff = currentBiometrics.caloriesBurned - baseCaloriesValueAtIntervalStart
        baseCaloriesValueAtIntervalStart = currentBiometrics.caloriesBurned

        val liveNote = _livePreferencesNote.value.trim().ifEmpty { null }

        if (liveNote != null) {
            newStyle = true
            viewModelScope.launch {
                repository.updateSessionPreferences(sessionId, liveNote)
            }
        }

        Log.d("WorkoutViewModel", "Sync timer triggered: sending biometrics to FastAPI...")
        _isServerLoading.value = true
        _serverFeedback.value = "Invio biometrici al server..."

        activeTrackIndex += 1
        val downloadedFiles = repository.updateWorkoutSession(
            sessionId = sessionId,
            averageHr = Math.round(avgHr * 10.0) / 10.0,
            stepsDiff = stepsDiff,
            speedKmh = currentBiometrics.speedKmh,
            calories = Math.round(caloriesDiff * 10.0) / 10.0,
            newPreferences = liveNote,
            baseUrl = url,
            trackIndex = activeTrackIndex
        )

        _isServerLoading.value = false
        if (downloadedFiles != null) {
            if (downloadedFiles.isNotEmpty()) {
                _serverFeedback.value = "Nuova traccia ricevuta e applicata!"
                if (downloadedFiles.size > 1) {
                    audioPlayer.setWeightliftingTracks(downloadedFiles[0], downloadedFiles[1])
                    audioPlayer.updateWeightliftingHeartRate(currentBiometrics.heartRate)
                } else {
                    audioPlayer.playWav(downloadedFiles[0], newStyle)
                    newStyle = false
                }
                _livePreferencesNote.value = "" // Reset post submission
            } else {
                _serverFeedback.value = "Traccia aggiornata (già presente in memoria)."
            }
        } else {
            _serverFeedback.value = "Impossibile aggiornare traccia. Continuo con l'audio dinamico..."
            Log.e("WorkoutViewModel", "FastAPI update failed, continuing current stream")
        }
    }

    // TERMINA / INTERROMPI SESSIONE
    fun stopWorkout() {
        val sessionId = _activeSessionId.value
        _isWorkoutActive.value = false
        primaryWorkoutJob?.cancel()
        audioPlayer.stop()

        if (sessionId != null) {
            val url = _serverUrl.value
            viewModelScope.launch {
                repository.endActiveWorkoutSession(sessionId, url)
                _activeSessionId.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}
