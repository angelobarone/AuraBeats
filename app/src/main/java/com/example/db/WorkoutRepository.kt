package com.example.db

import android.content.Context
import android.util.Log
import com.example.network.NetworkClient
import com.example.network.StartRequest
import com.example.network.UpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class WorkoutRepository(
    private val context: Context,
    private val sessionDao: SessionDao
) {
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    // Maps local sessionId to FastAPI UUID string session ID
    private val apiSessionIdMap = ConcurrentHashMap<Long, String>()
    private val downloadedFileNames = ConcurrentHashMap.newKeySet<String>()

    private fun unzipToCache(zipFile: File, prefix: String): List<File> {
        val extractedFiles = mutableListOf<File>()
        try {
            java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zipFile))).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".wav", ignoreCase = true)) {
                        val entryName = File(entry.name).name
                        val outFile = File(context.cacheDir, "${prefix}_$entryName")
                        java.io.FileOutputStream(outFile).use { fos ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                            fos.flush()
                        }
                        extractedFiles.add(outFile)
                        Log.d("WorkoutRepository", "Extracted zip entry: ${outFile.name}")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error unzipping file: ${e.message}", e)
        }
        return extractedFiles.sortedBy { it.name }
    }

    fun getSnapshotsForSession(sessionId: Long): Flow<List<BiometricSnapshotEntity>> {
        return sessionDao.getSnapshotsForSession(sessionId)
    }

    suspend fun getActiveSession(): SessionEntity? {
        return sessionDao.getActiveSession()
    }

    suspend fun logSnapshot(
        sessionId: Long,
        heartRate: Double,
        speed: Double,
        steps: Int,
        calories: Double,
        tempoMultiplier: Float
    ) {
        val snapshot = BiometricSnapshotEntity(
            sessionId = sessionId,
            heartRate = heartRate,
            speed = speed,
            steps = steps,
            calories = calories,
            currentTempoMultiplier = tempoMultiplier
        )
        sessionDao.insertSnapshot(snapshot)
    }

    // Call Fase 1: start workout session, contact FastAPI, write WAV to cache
    suspend fun startWorkoutSession(
        activityType: String,
        preferences: String,
        baseUrl: String
    ): Pair<Long, List<File>?> = withContext(Dispatchers.IO) {
        // 1. Log session start in Room
        val sessionEntity = SessionEntity(
            activityType = activityType,
            initialPreferences = preferences,
            serverUrl = baseUrl,
            isActive = true
        )
        val sessionId = sessionDao.insertSession(sessionEntity)

        // 2. Call FastAPI and download initial WAV / ZIP track
        var downloadedFiles: List<File>? = null
        try {
            val service = NetworkClient.getService(baseUrl)
            
            // Map activity type to API format
            val apiActivityName = when (activityType) {
                "Running" -> "running"
                "Weightlifting" -> "weightlifting"
                "Yoga" -> "yoga_cooldown"
                else -> activityType.lowercase()
            }

            // Create initial payload
            val payload = when (apiActivityName) {
                "running" -> mapOf(
                    "activity" to "running",
                    "user_initial_intent" to preferences,
                    "current_avg_hr" to 70,
                    "cadence_spm" to 0,
                    "performance_trend" to "stabile",
                    "target_bpm" to 145
                )
                "weightlifting" -> mapOf(
                    "activity" to "weightlifting",
                    "user_initial_intent" to preferences,
                    "current_state" to "recovery",
                    "last_hr_peak" to 70,
                    "current_hr" to 70,
                    "time_in_current_state_sec" to 0
                )
                else -> mapOf(
                    "activity" to "yoga_cooldown",
                    "user_initial_intent" to preferences,
                    "current_avg_hr" to 70,
                    "hr_trend" to "stabile",
                    "target_goal" to "relaxation"
                )
            }

            Log.d("WorkoutRepository", "Contacting FastAPI start: $baseUrl with payload: $payload")
            val response = service.startSession(StartRequest(payload))
            
            if (response.isSuccessful) {
                val startBody = response.body()
                if (startBody != null) {
                    val serverSessionId = startBody.session_id
                    Log.d("WorkoutRepository", "Start session success. Session ID on server: $serverSessionId. Polling status...")
                    
                    // Track map association
                    apiSessionIdMap[sessionId] = serverSessionId

                    // Poll status until ready
                    var isReady = false
                    var isZip = false
                    var audioFilename: String? = null
                    var attempts = 0
                    val maxAttempts = 80
                    while (!isReady && attempts < maxAttempts) {
                        attempts++
                        kotlinx.coroutines.delay(10000L) // Wait 2s between polls for high responsiveness
                        
                        val statusResponse = service.getStatus(serverSessionId)
                        if (statusResponse.isSuccessful) {
                            val statusBody = statusResponse.body()
                            if (statusBody != null) {
                                Log.d("WorkoutRepository", "Polling $attempts: status = ${statusBody.status}")
                                isZip = statusBody.audio_type == "zip" || statusBody.audio_filename?.endsWith(".zip") == true
                                audioFilename = statusBody.audio_filename
                                when (statusBody.status) {
                                    "ready" -> {
                                        isReady = true
                                    }
                                    "error" -> {
                                        Log.e("WorkoutRepository", "AI Generation Error on server: ${statusBody.error}")
                                        break
                                    }
                                    "generating" -> {
                                        // continue waiting
                                    }
                                }
                            }
                        } else {
                            Log.e("WorkoutRepository", "Failed status poll response, code: ${statusResponse.code()}")
                        }
                    }

                    if (isReady) {
                        Log.d("WorkoutRepository", "Status ready! Downloading audio stream...")
                        val audioResponse = service.getAudio(serverSessionId)
                        if (audioResponse.isSuccessful) {
                            val body = audioResponse.body()
                            if (body != null) {
                                audioFilename?.let { downloadedFileNames.add(it) }
                                if (isZip) {
                                    val zipFile = saveAudioStream(body, "session_${sessionId}_track_1.zip")
                                    val unzipped = unzipToCache(zipFile, "session_${sessionId}_track_1")
                                    try {
                                        zipFile.delete()
                                    } catch (e: Exception) {}
                                    downloadedFiles = unzipped
                                    
                                    // Update Room
                                    sessionDao.updateSession(
                                        sessionEntity.copy(
                                            id = sessionId,
                                            songTitle = "Dual Track (Weightlifting ZIP)"
                                        )
                                    )
                                    Log.d("WorkoutRepository", "Successfully downloaded and extracted start ZIP file: ${unzipped.map { it.name }}")
                                } else {
                                    val downloadedFile = saveAudioStream(body, "session_${sessionId}_track_1.wav")
                                    downloadedFiles = listOf(downloadedFile)
                                    // Record updated song title
                                    sessionDao.updateSession(
                                        sessionEntity.copy(
                                            id = sessionId,
                                            songTitle = downloadedFile.name
                                        )
                                    )
                                    Log.d("WorkoutRepository", "Successfully downloaded start track .wav file")
                                }
                            }
                        } else {
                            Log.e("WorkoutRepository", "Failed downloading audio, response code: ${audioResponse.code()}")
                        }
                    } else {
                        Log.e("WorkoutRepository", "Timeout waiting for music generation.")
                    }
                }
            } else {
                Log.w("WorkoutRepository", "FastAPI response failed: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error contacting FastAPI server (Normal if URL is mockup/offline): ${e.message}", e)
        }

        Pair(sessionId, downloadedFiles)
    }

    // Call Fase 2: update session on interval, send biometric summary to FastAPI, download queued music
    suspend fun updateWorkoutSession(
        sessionId: Long,
        averageHr: Double,
        stepsDiff: Int,
        speedKmh: Double,
        calories: Double,
        newPreferences: String?,
        baseUrl: String,
        trackIndex: Int
    ): List<File>? = withContext(Dispatchers.IO) {
        // 1. Log the biometric snapshot
        logSnapshot(
            sessionId = sessionId,
            heartRate = averageHr,
            speed = speedKmh,
            steps = stepsDiff,
            calories = calories,
            tempoMultiplier = 1.0f // Will be updated by UI
        )

        // Find the active session to check original activity and fallback intents
        val activeSession = sessionDao.getSession(sessionId)
        val activityType = activeSession?.activityType
        val initialPref = activeSession?.initialPreferences ?: ""

        val apiActivityName = when (activityType) {
            "Running" -> "running"
            "Weightlifting" -> "weightlifting"
            "Yoga" -> "yoga_cooldown"
            else -> activityType?.lowercase()
        }

        val payload = when (apiActivityName) {
            "running" -> {
                val cadence = (stepsDiff * 3).coerceIn(0, 200) // 20s window -> spm
                val trend = when {
                    stepsDiff > 55 -> "accelerazione"
                    stepsDiff < 15 -> "rallentamento"
                    else -> "stabile"
                }
                mapOf(
                    "activity" to "running",
                    "user_initial_intent" to (newPreferences ?: initialPref),
                    "current_avg_hr" to averageHr.toInt(),
                    "cadence_spm" to cadence,
                    "performance_trend" to trend,
                    "target_bpm" to 145
                )
            }
            "weightlifting" -> {
                val workoutState = if (averageHr > 120.0) "lifting" else "recovery"
                val peak = (averageHr * 1.1).toInt().coerceIn(80, 160)
                mapOf(
                    "activity" to "weightlifting",
                    "user_initial_intent" to (newPreferences ?: initialPref),
                    "current_state" to workoutState,
                    "last_hr_peak" to peak,
                    "current_hr" to averageHr.toInt(),
                    "time_in_current_state_sec" to 20
                )
            }
            else -> {
                val trend = if (averageHr < 68.0) "in calo" else if (averageHr > 75.0) "in aumento" else "stabile"
                mapOf(
                    "activity" to "yoga_cooldown",
                    "user_initial_intent" to (newPreferences ?: initialPref),
                    "current_avg_hr" to averageHr.toInt(),
                    "hr_trend" to trend,
                    "target_goal" to "relaxation"
                )
            }
        }

        val serverSessionId = apiSessionIdMap[sessionId]
        if (serverSessionId == null) {
            Log.e("WorkoutRepository", "No backend Session ID tracked for local session $sessionId")
            return@withContext null
        }

        // 2. Contact FastAPI to post biometrics & obtain updated .wav / .zip
        var downloadedFiles: List<File>? = null
        try {
            val service = NetworkClient.getService(baseUrl)
            Log.d("WorkoutRepository", "Contacting FastAPI update: Sending biometrics $payload for backend session $serverSessionId")
            
            val response = service.updateSession(
                sessionId = serverSessionId,
                request = UpdateRequest(payload)
            )

            if (response.isSuccessful) {
                Log.d("WorkoutRepository", "Update request successful. Checking status exactly once...")
                
                // Done exactly once (no continuous looping / polling)
                val statusResponse = service.getStatus(serverSessionId)
                if (statusResponse.isSuccessful) {
                    val statusBody = statusResponse.body()
                    if (statusBody != null) {
                        Log.d("WorkoutRepository", "Single Status Check for Update: status = ${statusBody.status}, filename = ${statusBody.audio_filename}")
                        val audioUrl = statusBody.audio_url
                        val audioFilename = statusBody.audio_filename
                        val audioType = statusBody.audio_type
                        
                        if (audioFilename != null && audioUrl != null) {
                            // Check if already in memory
                            val isAlreadyDownloaded = downloadedFileNames.contains(audioFilename) || 
                                                     File(context.cacheDir, audioFilename).exists() ||
                                                     (audioFilename.endsWith(".zip") && File(context.cacheDir, "session_${sessionId}_track_${trackIndex}_1.wav").exists())
                            
                            if (isAlreadyDownloaded) {
                                Log.d("WorkoutRepository", "Audio chunk $audioFilename is already in memory. Skipping download.")
                                downloadedFiles = emptyList() // special code for skipped because already downloaded
                            } else {
                                Log.d("WorkoutRepository", "New audio chunk found: $audioFilename. Securing download...")
                                val audioResponse = service.getAudio(serverSessionId)
                                if (audioResponse.isSuccessful) {
                                    val body = audioResponse.body()
                                    if (body != null) {
                                        downloadedFileNames.add(audioFilename)
                                        val isZip = audioType == "zip" || audioFilename.endsWith(".zip")
                                        if (isZip) {
                                            val zipFile = saveAudioStream(body, "session_${sessionId}_track_${trackIndex}.zip")
                                            val unzipped = unzipToCache(zipFile, "session_${sessionId}_track_${trackIndex}")
                                            try {
                                                zipFile.delete()
                                            } catch (e: Exception) {}
                                            downloadedFiles = unzipped
                                            Log.d("WorkoutRepository", "Successfully downloaded and extracted updated ZIP files: ${unzipped.map { it.name }}")
                                            cleanOldTracksFromCache(6)
                                        } else {
                                            val downloadedFile = saveAudioStream(body, "session_${sessionId}_track_${trackIndex}.wav")
                                            downloadedFiles = listOf(downloadedFile)
                                            Log.d("WorkoutRepository", "Successfully downloaded updated WAV file: ${downloadedFile.name}")
                                            cleanOldTracksFromCache(6)
                                        }
                                    }
                                } else {
                                    Log.e("WorkoutRepository", "Failed downloading updated audio, code: ${audioResponse.code()}")
                                }
                            }
                        } else {
                            Log.d("WorkoutRepository", "No audio URL or filename returned by the server yet.")
                        }
                    }
                } else {
                    Log.e("WorkoutRepository", "Failed status check on update, code: ${statusResponse.code()}")
                }
            } else {
                Log.w("WorkoutRepository", "FastAPI update response failed: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error contacting FastAPI server update: ${e.message}", e)
        }

        downloadedFiles
    }

    suspend fun endActiveWorkoutSession(sessionId: Long, baseUrl: String? = null) = withContext(Dispatchers.IO) {
        sessionDao.endSession(sessionId)
        Log.d("WorkoutRepository", "Successfully closed session $sessionId in Room")

        val serverSessionId = apiSessionIdMap.remove(sessionId)
        if (serverSessionId != null && baseUrl != null) {
            try {
                val service = NetworkClient.getService(baseUrl)
                Log.d("WorkoutRepository", "Contacting FastAPI delete to clean up session: $serverSessionId")
                service.deleteSession(serverSessionId)
            } catch (e: java.lang.Exception) {
                Log.w("WorkoutRepository", "Could not request backend cleanup: ${e.message}")
            }
        }
    }

    suspend fun updateSessionPreferences(sessionId: Long, newPreferences: String) {
        // Recuperiamo la sessione attuale tramite il DAO
        val actSession = sessionDao.getSession(sessionId)

        if (actSession != null) {
            // Creiamo una copia aggiornata dell'entità
            val updatedSession = actSession.copy(initialPreferences = newPreferences)

            // Salviamo la modifica nel database
            sessionDao.updateSession(updatedSession)

            Log.d("WorkoutRepository", "Preferenze aggiornate con successo per la sessione $sessionId")
        } else {
            Log.e("WorkoutRepository", "Impossibile aggiornare: nessuna sessione trovata con ID $sessionId")
        }
    }

    private fun saveAudioStream(body: ResponseBody, fileName: String): File {
        val targetFile = File(context.cacheDir, fileName)
        body.byteStream().use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }
        }
        return targetFile
    }

    private fun cleanOldTracksFromCache(maxFiles: Int = 6) {
        try {
            // 1. Trova tutti i file nella cache che sono tracce audio delle sessioni
            val cacheFiles = context.cacheDir.listFiles { file ->
                file.isFile && file.name.startsWith("session_") && file.name.endsWith(".wav")
            }

            // 2. Se abbiamo più file del limite consentito
            if (cacheFiles != null && cacheFiles.size > maxFiles) {
                // Ordina i file dal più vecchio al più recente in base all'ultima modifica
                val sortedFiles = cacheFiles.sortedBy { it.lastModified() }

                // Calcola quanti file dobbiamo eliminare
                val filesToDeleteCount = sortedFiles.size - maxFiles

                // 3. Elimina i file più vecchi
                for (i in 0 until filesToDeleteCount) {
                    val fileToDelete = sortedFiles[i]
                    val deleted = fileToDelete.delete()

                    if (deleted) {
                        Log.d("WorkoutRepository", "Cache cleanup: eliminata vecchia traccia ${fileToDelete.name}")
                        // FONDAMENTALE: Rimuoviamo il nome anche dalla lista in memoria
                        downloadedFileNames.remove(fileToDelete.name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Errore durante la pulizia della cache audio: ${e.message}", e)
        }
    }
}

