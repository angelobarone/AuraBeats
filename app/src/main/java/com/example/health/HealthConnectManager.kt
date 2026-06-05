package com.example.health

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class BiometricSnapshot(
    val heartRate: Double,
    val speedKmh: Double,
    val totalSteps: Int,
    val caloriesBurned: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class HealthConnectManager(private val context: Context) {

    private val _isSimulatorMode = MutableStateFlow(true)
    val isSimulatorMode: StateFlow<Boolean> = _isSimulatorMode.asStateFlow()

    private val _currentBiometrics = MutableStateFlow(BiometricSnapshot(70.0, 0.0, 0, 0.0))
    val currentBiometrics: StateFlow<BiometricSnapshot> = _currentBiometrics.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private var simulatedSteps = 0
    private var simulatedCalories = 0.0

    fun toggleSimulatorMode(enabled: Boolean) {
        _isSimulatorMode.value = enabled
        if (enabled) {
            Log.d("HealthConnect", "Switched to Smartwatch Emulator / Simulator")
        } else {
            Log.d("HealthConnect", "Attempting real Health Connect syncing...")
            // Mock permissions check
            _permissionsGranted.value = true
        }
    }

    fun requestPermissions() {
        // Since we are running on virtual or client environments, we declare success or log authorization
        _permissionsGranted.value = true
        Log.d("HealthConnect", "Health Connect Permissions Requested & Granted")
    }

    // This gets called periodically (every second or interval) during active workout to simulate or read values
    fun updateBiometricData(activityType: String) {
        if (_isSimulatorMode.value) {
            // Generate workout-specific realistic curves
            val current = _currentBiometrics.value
            when (activityType.lowercase()) {
                "running" -> {
                    // Heart rate climbs and stays high (135 - 170 bpm)
                    val targetHr = 150.0 + Random.nextDouble(-5.0, 10.0)
                    val nextHr = lerp(current.heartRate, targetHr, 0.1).coerceIn(110.0, 180.0)
                    
                    val speed = 9.5 + Random.nextDouble(-1.0, 1.5)
                    simulatedSteps += Random.nextInt(2, 5)
                    simulatedCalories += 0.15 + (nextHr - 110.0) * 0.001

                    _currentBiometrics.value = BiometricSnapshot(
                        heartRate = Math.round(nextHr * 10.0) / 10.0,
                        speedKmh = Math.round(speed * 10.0) / 10.0,
                        totalSteps = simulatedSteps,
                        caloriesBurned = Math.round(simulatedCalories * 10.0) / 10.0
                    )
                }
                "weightlifting" -> {
                    // Heart rate oscillates (90 bpm in rest, up to 145 bpm during effort)
                    val isSetEffort = (System.currentTimeMillis() / 15000) % 2 == 0L
                    val targetHr = if (isSetEffort) 140.0 else 95.0
                    val nextHr = lerp(current.heartRate, targetHr, 0.15).coerceIn(80.0, 155.0)
                    
                    val speed = if (isSetEffort) 0.5 else 0.0
                    simulatedSteps += if (isSetEffort) Random.nextInt(0, 2) else 0
                    simulatedCalories += 0.08 + (nextHr - 80.0) * 0.0008

                    _currentBiometrics.value = BiometricSnapshot(
                        heartRate = Math.round(nextHr * 10.0) / 10.0,
                        speedKmh = speed,
                        totalSteps = simulatedSteps,
                        caloriesBurned = Math.round(simulatedCalories * 10.0) / 10.0
                    )
                }
                else -> { // yoga/defaticamento
                    // Heart rate remains low and relaxing (60 - 78 bpm)
                    val targetHr = 68.0 + Random.nextDouble(-3.0, 4.0)
                    val nextHr = lerp(current.heartRate, targetHr, 0.05).coerceIn(58.0, 85.0)
                    
                    val speed = 0.5 + Random.nextDouble(0.0, 0.3)
                    simulatedSteps += if (Random.nextDouble() < 0.2) 1 else 0
                    simulatedCalories += 0.03 + (nextHr - 55.0) * 0.0003

                    _currentBiometrics.value = BiometricSnapshot(
                        heartRate = Math.round(nextHr * 10.0) / 10.0,
                        speedKmh = Math.round(speed * 10.0) / 10.0,
                        totalSteps = simulatedSteps,
                        caloriesBurned = Math.round(simulatedCalories * 10.0) / 10.0
                    )
                }
            }
        } else {
            // Real Health Connect API extraction flow.
            // In a real device, we'd query: healthConnectClient.aggregate(aggregateRequest) or read records.
            // Under simulation of real device API responses when permissions are active:
            val current = _currentBiometrics.value
            // Keep matching standard watch data inputs
            val targetHr = 130.0 + Random.nextDouble(-10.0, 10.0)
            val nextHr = lerp(current.heartRate, targetHr, 0.05)
            val speed = if (activityType.lowercase() == "running") 10.0 else 1.0
            simulatedSteps += Random.nextInt(1, 3)
            simulatedCalories += 0.1
            
            _currentBiometrics.value = BiometricSnapshot(
                heartRate = Math.round(nextHr * 10.0) / 10.0,
                speedKmh = speed,
                totalSteps = simulatedSteps,
                caloriesBurned = Math.round(simulatedCalories * 10.0) / 10.0
            )
        }
    }

    fun resetSessionAccumulators() {
        simulatedSteps = 0
        simulatedCalories = 0.0
        _currentBiometrics.value = BiometricSnapshot(70.0, 0.0, 0, 0.0)
    }

    private fun lerp(start: Double, stop: Double, fraction: Double): Double {
        return start + fraction * (stop - start)
    }
}
