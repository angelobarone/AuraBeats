package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

class WorkoutAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    // Store all received wav tracks so we can loop and transition them
    private var receivedWavs = mutableListOf<File>()
    private var currentWavIndex = -1
    private var isTransitioning = false

    // Weightlifting dual tracks variables
    private var weightliftingTracks: Pair<File, File>? = null
    private var isWeightliftingActive = false
    private var currentWeightliftingTrackIndex = -1

    private var bassBoost: BassBoost? = null

    private var equalizer: Equalizer? = null

    private var tempoJob: Job? = null

    fun setWeightliftingTracks(track1: File, track2: File) {
        this.weightliftingTracks = Pair(track1, track2)
        this.isWeightliftingActive = true
        this.currentWavIndex = -1
        this.currentWeightliftingTrackIndex = -1
        Log.d("AudioPlayer", "Weightlifting: Loaded dual tracks: ${track1.name} and ${track2.name}")
    }

    fun updateWeightliftingHeartRate(heartRate: Double) {
        if (!isWeightliftingActive) return
        val tracks = weightliftingTracks ?: return

        // Choose track based on heart rate threshold (e.g. 115 bpm)
        val targetIndex = if (heartRate >= 115.0) 1 else 0
        if (targetIndex != currentWeightliftingTrackIndex) {
            currentWeightliftingTrackIndex = targetIndex
            val targetFile = if (targetIndex == 0) tracks.first else tracks.second
            Log.d("AudioPlayer", "Weightlifting: Heart rate is $heartRate, crossfading to track ${targetIndex + 1}: ${targetFile.name}")
            playWeightliftingTrack(targetFile)
        }
    }

    private fun playWeightliftingTrack(file: File) {
        stopSynth()
        _isSynthFallback.value = false

        val prevPlayer = mediaPlayer

        // 1. Se non c'è un player attivo, avvia semplicemente la traccia
        if (prevPlayer == null) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    isLooping = true
                    prepare()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        playbackParams = PlaybackParams().apply {
                            speed = _currentTempo.value
                            pitch = _currentTempo.value
                        }
                    }
                }

                mediaPlayer = player
                player.start()
                _isPlaying.value = true
                Log.d("AudioPlayer", "Started first Weightlifting WAV: ${file.name}")
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Failed playing weightlifting WAV, falling back to synth", e)
                startSynthFallback("weightlifting")
            }
            return // Esce dalla funzione
        }

        // 2. Se c'è già un player, eseguiamo il crossfade
        if (isTransitioning) return // Evita sovrapposizioni pericolose di crossfade
        isTransitioning = true

        // Usiamo Default o IO per non bloccare il Main thread durante la preparazione
        synthScope.launch(Dispatchers.Default) {
            val newPlayer = MediaPlayer()
            try {
                newPlayer.apply {
                    setDataSource(file.absolutePath)
                    isLooping = true
                    prepare()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        playbackParams = PlaybackParams().apply {
                            speed = _currentTempo.value
                            pitch = _currentTempo.value
                        }
                    }

                    setVolume(0f, 0f)
                    start()
                }

                // Aggiorniamo subito il riferimento in modo che comandi come "pause" o "stop" agiscano su di esso
                mediaPlayer = newPlayer

                val fadeDurationMs = 1500L
                val steps = 15
                val stepDelay = fadeDurationMs / steps

                // Ciclo di fade pulito
                for (i in 1..steps) {
                    val fraction = i.toFloat() / steps

                    // Alziamo il nuovo
                    try {
                        if (newPlayer.isPlaying) newPlayer.setVolume(fraction, fraction)
                    } catch (e: Exception) { /* Ignora eventuali stati illegali transitori */ }

                    // Abbassiamo il vecchio
                    try {
                        if (prevPlayer.isPlaying) {
                            val oldVolume = 1.0f - fraction
                            prevPlayer.setVolume(oldVolume, oldVolume)
                        }
                    } catch (e: Exception) { /* Ignora */ }

                    delay(stepDelay)
                }

            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error preparing/playing weightlifting crossfade: ${e.message}")
                try { newPlayer.release() } catch (ex: Exception) {}

                // Ripristino di sicurezza in caso di fallimento
                if (mediaPlayer == newPlayer) {
                    mediaPlayer = prevPlayer
                }
            } finally {
                // FINALLY garantisce che la pulizia avvenga sempre, sia con successo che con errore
                try {
                    if (prevPlayer.isPlaying) {
                        prevPlayer.stop()
                    }
                    prevPlayer.release()
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Error releasing old player: ${e.message}")
                }
                isTransitioning = false
            }
        }
    }


    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTempo = MutableStateFlow(1.0f)
    val currentTempo: StateFlow<Float> = _currentTempo.asStateFlow()

    private val _isSynthFallback = MutableStateFlow(true)
    val isSynthFallback: StateFlow<Boolean> = _isSynthFallback.asStateFlow()

    // Synthesis members for dynamic beat generation when wav isn't available
    private var synthJob: Job? = null
    private val synthScope = CoroutineScope(Dispatchers.Default)
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    private var monitoringJob: Job? = null

    init {
        initSynthTrack()
        startMonitoringPlayback()
    }

    private fun initSynthTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error initializing synth audio track: ${e.message}")
        }
    }

    private fun startMonitoringPlayback() {
        monitoringJob?.cancel()

        // Specifica il dispatcher per garantire che giri sempre in background
        monitoringJob = synthScope.launch(Dispatchers.Default) {
            // Usa isActive per permettere la cancellazione corretta del loop
            while (isActive) {
                delay(500)

                // 1. Early exit: se non deve monitorare, salta al prossimo ciclo
                if (!_isPlaying.value || _isSynthFallback.value || isTransitioning) {
                    continue
                }

                val player = mediaPlayer ?: continue
                val wavSize = receivedWavs.size

                try {
                    // 2. Assicurati che il player sia effettivamente in esecuzione
                    if (!player.isPlaying) continue

                    if (wavSize > 0) {
                        val duration = player.duration
                        val pos = player.currentPosition
                        val timeRemaining = duration - pos

                        // Se mancano meno di 2 secondi, triggera la transizione
                        if (duration > 0 && timeRemaining < 2000) {
                            Log.d("AudioPlayer", "Track near end ($timeRemaining ms remaining), triggering next...")
                            triggerNextTrackTransition()
                        }
                    }
                } catch (e: IllegalStateException) {
                    // Cattura specificamente l'errore di stato del MediaPlayer (es. se viene rilasciato mentre lo si legge)
                    Log.w("AudioPlayer", "MediaPlayer is in an invalid state during monitoring: ${e.message}")
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Error checking playback position: ${e.message}")
                }
            }
        }
    }


    private fun triggerNextTrackTransition() {
        // 1. Blocco immediato se stiamo già facendo una transizione o la lista è vuota
        if (receivedWavs.isEmpty() || isTransitioning) return

        isTransitioning = true

        // 2. Usiamo Default per non bloccare la UI durante l'accesso al file system e preparazione
        synthScope.launch(Dispatchers.Default) {
            try {
                // Catturiamo la dimensione localmente per evitare crash (divisione per zero)
                // nel caso in cui la lista venga svuotata concorrentemente
                val listSize = receivedWavs.size
                if (listSize == 0) {
                    isTransitioning = false
                    return@launch
                }

                val nextIndex = (currentWavIndex + 1) % listSize
                val nextFile = receivedWavs[nextIndex]

                // 3. Sicurezza aggiuntiva: verifichiamo che il file esista ancora fisicamente
                if (!nextFile.exists()) {
                    Log.e("AudioPlayer", "Transition failed: file does not exist -> ${nextFile.name}")
                    isTransitioning = false
                    return@launch
                }

                Log.d("AudioPlayer", "Auto-transition triggered to track $nextIndex: ${nextFile.name}")

                // Chiamiamo crossfadeTo.
                // nel suo blocco `finally`, esattamente come abbiamo fatto in `playWeightliftingTrack`.
                crossfadeTo(nextFile, nextIndex)

            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error in next track transition: ${e.message}")
                // Ripristina il flag solo se si verifica un'eccezione prima del completamento
                isTransitioning = false
            }
        }
    }

    private fun crossfadeTo(nextFile: File, nextIndex: Int) {
        val oldPlayer = mediaPlayer

        // Usiamo Dispatchers.Default per sgravare il Main Thread
        synthScope.launch(Dispatchers.Default) {
            val newPlayer = MediaPlayer()
            try {
                newPlayer.apply {
                    setDataSource(nextFile.absolutePath)
                    isLooping = false
                    prepare()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        playbackParams = PlaybackParams().apply {
                            speed = _currentTempo.value
                            pitch = _currentTempo.value
                        }
                    }

                    setVolume(0f, 0f)
                    start()
                }

                // Scambio dei riferimenti e aggiornamento dello stato
                mediaPlayer = newPlayer
                currentWavIndex = nextIndex
                _isSynthFallback.value = false
                _isPlaying.value = true

                val fadeDurationMs = 1500L
                val steps = 15
                val stepDelay = fadeDurationMs / steps

                // Ciclo di crossfade
                for (i in 1..steps) {
                    val fraction = i.toFloat() / steps

                    val safeVolume = fraction * 0.85f

                    // Alziamo il volume del nuovo player
                    try {
                        if (newPlayer.isPlaying) {
                            newPlayer.setVolume(safeVolume, safeVolume)
                        }
                    } catch (e: Exception) { /* Ignora stati transitori illegali */ }

                    // Abbassiamo il volume del vecchio player
                    if (oldPlayer != null) {
                        try {
                            if (oldPlayer.isPlaying) {
                                val oldVolume = (1.0f - fraction) * 0.85f
                                oldPlayer.setVolume(oldVolume, oldVolume)
                            }
                        } catch (e: Exception) { /* Ignora */ }
                    }

                    delay(stepDelay)
                }

            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error preparing/playing crossfade: ${e.message}")
                try {
                    newPlayer.release()
                } catch (ex: Exception) {}

                // Se avevamo già scambiato i player prima del crash, facciamo rollback
                if (mediaPlayer == newPlayer) {
                    mediaPlayer = oldPlayer
                }
            } finally {
                // FINALLY assicura che risorse sensibili e semafori vengano ripristinati
                if (oldPlayer != null) {
                    try {
                        if (oldPlayer.isPlaying) {
                            oldPlayer.stop()
                        }
                        oldPlayer.release()
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Error releasing old player during crossfade: ${e.message}")
                    }
                }

                // Libera il semaforo per future transizioni, sia in caso di successo che di errore
                isTransitioning = false
            }
        }
    }


    fun playWav(file: File, newStyle: Boolean) {
        stopSynth()
        if (newStyle) {
            receivedWavs = mutableListOf<File>()
            receivedWavs.add(file)
        } else {
            // 1. Aggiungiamo alla coda se non presente (in modo sicuro)
            if (!receivedWavs.contains(file)) {
                receivedWavs.add(file)
            }
        }

        _isSynthFallback.value = false

        val prevPlayer = mediaPlayer
        val newIndex = receivedWavs.indexOf(file)

        // 2. Se non c'è un player attivo, avvia semplicemente la traccia
        if (prevPlayer == null) {
            currentWavIndex = newIndex

            try {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    isLooping = false//(receivedWavs.size == 1)
                    prepare()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // FIX APPLICATO QUI: Impostiamo SIA speed CHE pitch!
                        playbackParams = PlaybackParams().apply {
                            speed = _currentTempo.value
                            pitch = _currentTempo.value
                        }
                    }
                }

                mediaPlayer = player
                player.start()
                _isPlaying.value = true
                Log.d("AudioPlayer", "Started first WAV: ${file.name}")
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Failed playing first WAV, falling back to synth", e)
                startSynthFallback()
            }
        } else {
            // 3. Se c'è già un player, eseguiamo il crossfade
            if (newIndex >= 0) {
                if (isTransitioning) {
                    Log.w("AudioPlayer", "Ignored playWav for ${file.name}: crossfade already in progress.")
                    return
                }

                isTransitioning = true
                crossfadeTo(file, newIndex)
            }
        }
    }

    fun setTempo(multiplier: Float, smoothTransition: Boolean = true) {
        val bounded = multiplier.coerceIn(0.5f, 2.0f)
        val targetTempo = Math.round(bounded * 100f) / 100f

        // 1. Evita aggiornamenti ridondanti
        if (_currentTempo.value == targetTempo) return

        Log.d("AudioPlayer", "Requesting tempo shift: ${_currentTempo.value} -> $targetTempo")

        tempoJob?.cancel()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _currentTempo.value = targetTempo
            return
        }

        if (!smoothTransition) {
            applyTempoToPlayer(targetTempo)
        } else {
            tempoJob = synthScope.launch(Dispatchers.Default) {
                val startTempo = _currentTempo.value
                val diff = targetTempo - startTempo

                // Inviare nuovi PlaybackParams ogni 50ms faceva impazzire il buffer
                // del MediaPlayer causando micro-scatti. 125ms dà il tempo di ricalcolare l'audio.
                val steps = 4
                val delayMs = 125L // 4 steps * 125ms = 500ms totali

                for (i in 1..steps) {
                    val currentStepTempo = startTempo + (diff * (i.toFloat() / steps))
                    applyTempoToPlayer(currentStepTempo)
                    delay(delayMs)
                }

                // Ultimo step per garantire la precisione
                applyTempoToPlayer(targetTempo)
            }
        }
    }


    private fun applyTempoToPlayer(tempo: Float) {
        val rounded = Math.round(tempo * 100f) / 100f
        _currentTempo.value = rounded

        try {
            val player = mediaPlayer
            if (player?.isPlaying == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = player.playbackParams ?: PlaybackParams()

                params.speed = rounded

                // leghiamo il pitch alla speed. Questo crea un effetto "Vinile" o "CDJ".
                params.pitch = rounded

                player.playbackParams = params
            }
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Ignored tempo adjustment during invalid player state.")
        }
    }

    fun togglePlayPause(activityType: String = "Running") {
        if (_isPlaying.value) {
            pause()
        } else {
            play(activityType)
        }
    }

    fun play(activityType: String = "Running") {
        if (_isSynthFallback.value) {
            startSynth(activityType)
        } else {
            try {
                mediaPlayer?.let { player ->
                    player.start()
                    _isPlaying.value = true
                } ?: run {
                    startSynthFallback(activityType)
                }
            } catch (e: Exception) {
                startSynthFallback(activityType)
            }
        }
    }

    fun pause() {
        if (_isSynthFallback.value) {
            synthJob?.cancel()
            audioTrack?.pause()
        } else {
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "MediaPlayer pause failed: ${e.message}")
            }
        }
        _isPlaying.value = false
    }

    fun stop() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {}
        stopSynth()
        stopMediaPlayer()
        receivedWavs.clear()
        currentWavIndex = -1
        isTransitioning = false
        _isPlaying.value = false
        // Reset weightlifting flags
        isWeightliftingActive = false
        weightliftingTracks = null
        currentWeightliftingTrackIndex = -1
    }

    private fun startSynthFallback(activityType: String = "Running") {
        _isSynthFallback.value = true
        startSynth(activityType)
    }

    private fun startSynth(activityType: String) {
        synthJob?.cancel()
        audioTrack?.play()
        _isPlaying.value = true

        synthJob = synthScope.launch {
            while (true) {
                // Determine beat frequency based on activity type and active tempo
                // Running -> High energy energetic dual-beeps, Weightlifting -> heavy deep drums, Yoga -> peaceful single warm note
                val tempoMult = _currentTempo.value
                val baseIntervalMs = when (activityType.lowercase()) {
                    "running" -> 450L // 133 bpm default
                    "weightlifting" -> 650L // 92 bpm default
                    else -> 1200L // 50 bpm relaxing tone
                }
                val intervalMs = (baseIntervalMs / tempoMult).toLong().coerceIn(150, 3000)

                // Synthesize tone
                val toneFreq = when (activityType.lowercase()) {
                    "running" -> 440.0 // A4 beep
                    "weightlifting" -> 120.0 // Heavy low thud
                    else -> 293.66 // D4 mellow ring
                }
                val durationMs = when (activityType.lowercase()) {
                    "running" -> 80
                    "weightlifting" -> 200
                    else -> 600
                }

                writeSynthTone(toneFreq, durationMs)
                delay(intervalMs)
            }
        }
    }

    private fun writeSynthTone(frequency: Double, durationMs: Int) {
        val track = audioTrack ?: return
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val generatedSnd = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            // Generate basic amplitude modulated sine wave to simulate clean instrument punch
            val t = i.toDouble() / sampleRate
            val amplitude = Short.MAX_VALUE * Math.exp(-4.0 * t / (durationMs / 1000.0))
            generatedSnd[i] = (amplitude * Math.sin(2.0 * Math.PI * frequency * t)).toInt().toShort()
        }

        try {
            track.write(generatedSnd, 0, generatedSnd.size)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error writing synth: ${e.message}")
        }
    }

    private fun stopSynth() {
        synthJob?.cancel()
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // ignore
        }
    }
}
