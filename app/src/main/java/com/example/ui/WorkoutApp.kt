package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.db.SessionEntity
import java.text.SimpleDateFormat
import java.util.*

// Colors matching Sports Tech neon aesthetic (Premium Dark Neon Theme!)
val DarkBackground = Color(0xFF09080F)  // Deep obsidian pitch black
val SurfaceCard = Color(0xFF151324)     // Tech-slate dark card container
val NeonGreen = Color(0xFF00FF9D)       // Dynamic electric neon green highlight
val BrightCoral = Color(0xFFFF2266)     // High-intensity warm coral alert pulse
val ElectricBlue = Color(0xFF00D2FF)    // Cyan/Blue laser stream
val LightText = Color(0xFFF1F5F9)       // Clean high contrast pure white text
val MutedText = Color(0xFF94A3B8)       // Elegant cool slate gray text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutApp(
    viewModel: WorkoutViewModel = viewModel()
) {
    val isWorkoutActive by viewModel.isWorkoutActive.collectAsState()
    val isServerLoading by viewModel.isServerLoading.collectAsState()
    val serverFeedback by viewModel.serverFeedback.collectAsState()

    var currentTab by remember { mutableStateOf("Workout") }

    // Edge to edge container with custom theme styling
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Base Content
        if (currentTab == "Workout") {
            AnimatedVisibility(
                visible = !isWorkoutActive,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                ConfigurationScreen(
                    viewModel = viewModel,
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }

            AnimatedVisibility(
                visible = isWorkoutActive,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                ActiveSessionScreen(
                    viewModel = viewModel,
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        } else if (currentTab == "Health") {
            HealthScreen(
                viewModel = viewModel,
                onTabSelected = { currentTab = it }
            )
        } else if (currentTab == "Settings") {
            SettingsScreen(
                onTabSelected = { currentTab = it }
            )
        }

        // Overlay Server feedback & API loading indicators
        if (isServerLoading || serverFeedback != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("server_status_card"),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isServerLoading) {
                            CircularProgressIndicator(
                                color = NeonGreen,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = ElectricBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isServerLoading) "Sincronizzazione FastAPI..." else "Stato Connessione",
                                color = LightText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = serverFeedback ?: "",
                                color = MutedText,
                                fontSize = 13.sp
                            )
                        }

                        if (!isServerLoading) {
                            IconButton(onClick = { viewModel.clearFeedback() }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Chiudi",
                                    tint = NeonGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigurationScreen(
    viewModel: WorkoutViewModel,
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val selectedActivity by viewModel.selectedActivity.collectAsState()
    val textPreferences by viewModel.textPreferences.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val sessionHistory by viewModel.sessionHistory.collectAsState()
    val simulatorEnabled by viewModel.healthManager.isSimulatorMode.collectAsState()

    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(NeonGreen, ElectricBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = Color(0xFF09080F),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "AuraBeats",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LightText
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF9D))
                            )
                            Text(
                                text = "FASTAPI CONNECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }
                    }
                }

                // Smartwatch Connection State Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (simulatorEnabled) BrightCoral.copy(alpha = 0.1f) else NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, if (simulatorEnabled) BrightCoral else NeonGreen, RoundedCornerShape(20.dp))
                        .clickable { viewModel.toggleSimulator(!simulatorEnabled) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (simulatorEnabled) Icons.Default.Build else Icons.Default.Favorite,
                            contentDescription = "Watch Connesso",
                            tint = if (simulatorEnabled) BrightCoral else NeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (simulatorEnabled) "SIMWATCH" else "SMARTWATCH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (simulatorEnabled) BrightCoral else NeonGreen
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Section 1: Server URL Config
            item {
                Text(
                    text = "CONFIGURAZIONE SERVER FASTAPI",
                    color = MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp))
                        .testTag("server_url_input"),
                    placeholder = { Text("E.g., http://10.0.2.2:8000", color = MutedText.copy(alpha = 0.5f)) },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = "URL", tint = ElectricBlue)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        cursorColor = NeonGreen,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
            }

            // Section 2: Activity Selection
            item {
                Text(
                    text = "SELEZIONA ATTIVITÀ ALLENAMENTO",
                    color = MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val activities = listOf(
                        Triple("Running", Icons.Default.PlayArrow, "Corsa"),
                        Triple("Weightlifting", Icons.Default.Star, "Pesi"),
                        Triple("Yoga", Icons.Default.Favorite, "Relax")
                    )

                    activities.forEach { (type, icon, label) ->
                        val isSelected = selectedActivity == type
                        val cardBg = if (isSelected) NeonGreen.copy(alpha = 0.12f) else SurfaceCard
                        val cardBorder = if (isSelected) NeonGreen else Color(0xFF1F1C36)

                        Card(
                            onClick = {
                                viewModel.selectActivity(type)
                                focusManager.clearFocus()
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(95.dp)
                                .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                                .testTag("activity_btn_$type"),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = type,
                                    tint = if (isSelected) NeonGreen else MutedText,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) NeonGreen else LightText,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Text Notes / Initial preference
            item {
                Text(
                    text = "PREFERENZE MUSICALI (AI PROMPT)",
                    color = MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextField(
                    value = textPreferences,
                    onValueChange = { viewModel.updatePreferences(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .border(1.dp, Color(0xFF1F1C36), RoundedCornerShape(24.dp))
                        .testTag("preferences_text_input"),
                    placeholder = { Text("E.g., Genera un beat energico sincronizzato sul mio cuore...", color = MutedText.copy(alpha = 0.5f)) },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = "Pref", tint = NeonGreen)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        cursorColor = NeonGreen,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
            }

            // Section 4: Trigger Active Button
            item {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.startWorkout()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.horizontalGradient(listOf(NeonGreen, ElectricBlue)))
                        .testTag("start_workout_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF09080F)
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Avvia",
                            tint = Color(0xFF09080F)
                        )
                        Text(
                            text = "AVVIA ALLENAMENTO",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun ActiveSessionScreen(
    viewModel: WorkoutViewModel,
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val selectedActivity by viewModel.selectedActivity.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val countdown by viewModel.syncCountdown.collectAsState()
    val biometrics by viewModel.healthManager.currentBiometrics.collectAsState()
    val livePreference by viewModel.livePreferencesNote.collectAsState()
    val tempoMultiplier by viewModel.audioPlayer.currentTempo.collectAsState()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsState()
    val isSynthBackground by viewModel.audioPlayer.isSynthFallback.collectAsState()
    val snapshots by viewModel.currentSessionSnapshots.collectAsState()

    val formattedTime = remember(timerSeconds) {
        val minutes = timerSeconds / 60
        val seconds = timerSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Circle Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(NeonGreen, ElectricBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = Color(0xFF09080F),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = "AuraBeats",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LightText
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF9D))
                            )
                            Text(
                                text = "FASTAPI CONNECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }
                    }
                }

                // Stop Button
                Button(
                    onClick = { viewModel.stopWorkout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightCoral,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp).testTag("stop_workout_button")
                ) {
                    Text("FERMA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Clock Timer + Status Header (with the high density Active Session pill combined)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp))
                        .testTag("timer_card"),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "SESSIONE ATTIVA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreen,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(NeonGreen.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "🏃 $selectedActivity",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = NeonGreen
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = "LIVE TRACKING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MutedText
                            )
                        }
                        
                        Divider(color = Color(0xFF1F1C36), modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = formattedTime,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            color = LightText,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color(0xFF00FF9D) else BrightCoral)
                            )
                            Text(
                                text = if (isPlaying) "CONDUZIONE MUSICA: ${if (isSynthBackground) "Sintetizzatore" else "FastAPI WAV"}" else "RIPRODUZIONE SOSPESA",
                                color = MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Biometric Telemetry Speedometer & Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cardiac Telemetry Gauge Card
                    Card(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(130.dp)
                            .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp))
                            .testTag("cardiac_telemetry_card"),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "HEART RATE",
                                    color = BrightCoral,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                // Pulsing Heart icon
                                val infiniteTransition = rememberInfiniteTransition()
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.9f,
                                    targetValue = 1.25f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "HR",
                                    tint = BrightCoral,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .scale(if (isPlaying) scale else 1.0f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${biometrics.heartRate}",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "BPM • 82% MAX",
                                color = MutedText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Numeric Telemetry indicators Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Steps Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("AVG. CADENCE", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${biometrics.totalSteps}", color = LightText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("🏃", fontSize = 18.sp)
                            }
                        }

                        // Kcal Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("STIMA KCAL", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${biometrics.caloriesBurned}", color = LightText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("🔥", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            // Interactive dynamic Audio Waveform reacting to active Playback speed (Tempo ratio)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("NOW PLAYING", color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text("Musica generata con AI", color = LightText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElectricBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE ADAPTIVE AUDIO", color = ElectricBlue, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TIME STRETCH", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text("x${tempoMultiplier}", color = LightText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            // Visual wave
                            Box(modifier = Modifier.width(180.dp).height(32.dp)) {
                                val wavePhaseTransition = rememberInfiniteTransition()
                                val phase by wavePhaseTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 2f * Math.PI.toFloat(),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path()
                                    val width = size.width
                                    val height = size.height
                                    val midY = height / 2

                                    path.moveTo(0f, midY)
                                    val amplitude = (6f + 8f * (tempoMultiplier - 0.7f)).coerceIn(4f, 15f)
                                    val frequency = 0.05f + 0.03f * tempoMultiplier

                                    for (x in 0..width.toInt() step 3) {
                                        val y = midY + amplitude * Math.sin((x * frequency + phase).toDouble()).toFloat()
                                        path.lineTo(x.toFloat(), y)
                                    }

                                    drawPath(
                                        path = path,
                                        color = ElectricBlue,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Playback controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Pause Toggle button
                    Button(
                        onClick = { viewModel.audioPlayer.togglePlayPause(selectedActivity) },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.horizontalGradient(listOf(NeonGreen, ElectricBlue)))
                            .testTag("play_pause_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF09080F)
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color(0xFF09080F)
                            )
                            Text(if (isPlaying) "PAUSA MUSICA" else "RIPRENDI MUSICA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Live speed parameters display card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(26.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("PITCH SHIFT", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            val pitchSt = remember(tempoMultiplier) {
                                String.format(Locale.US, "%+.1f ST", (tempoMultiplier - 1.0) * 12.0)
                            }
                            Text(pitchSt, color = LightText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Real-time chart visualization showing history graph
            if (snapshots.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text("GRAFICO CARDIO FREQUENZA (BPM)", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Simple canvas drawing list history nodes
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val drawWidth = size.width
                                val drawHeight = size.height
                                val points = snapshots.takeLast(15) // last 15 ticks

                                if (points.size > 1) {
                                    val hrMax = (points.maxOf { it.heartRate } + 10).coerceAtLeast(120.0)
                                    val hrMin = (points.minOf { it.heartRate } - 10).coerceAtMost(60.0)
                                    val hrDiff = hrMax - hrMin

                                    val stepX = drawWidth / (points.size - 1)
                                    val strokePath = Path()

                                    points.forEachIndexed { idx, point ->
                                        val x = idx * stepX
                                        val rawYScale = (point.heartRate - hrMin) / hrDiff
                                        val y = drawHeight - (rawYScale * drawHeight).toFloat()

                                        if (idx == 0) {
                                            strokePath.moveTo(x, y)
                                        } else {
                                            strokePath.lineTo(x, y)
                                        }

                                        // Draw specific point indicators
                                        drawCircle(
                                            color = BrightCoral,
                                            radius = 3.dp.toPx(),
                                            center = Offset(x, y)
                                        )
                                    }

                                    // Draw line trace
                                    drawPath(
                                        path = strokePath,
                                        color = BrightCoral.copy(alpha = 0.8f),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FASE 2: LIVE FEEDBACK INPUT DRAWER & UPDATE COUNTDOWN
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp))
                        .testTag("live_preferences_card"),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FASTAPI SYNC & CODA",
                                color = ElectricBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Queue countdown tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElectricBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Prossimo: ${countdown}s",
                                    color = ElectricBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // API Sync progress bar
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("FASTAPI SYNC PROGRESS", color = MutedText, fontSize = 10.sp)
                                val syncPercent = ((15 - countdown) * 100 / 15).coerceIn(0, 100)
                                Text("$syncPercent%", color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = (15 - countdown).toFloat() / 15f)
                                        .background(ElectricBlue)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = livePreference,
                                onValueChange = { viewModel.updateLivePreferences(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .testTag("live_preference_input"),
                                placeholder = { Text("E.g., Alza i bassi ora!", fontSize = 12.sp, color = MutedText.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF09080F),
                                    unfocusedContainerColor = Color(0xFF09080F),
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    cursorColor = NeonGreen,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Nota: Al termine del countdown le preferenze e la telemetria vengono inviate a FastAPI caricando i wav.",
                            color = MutedText,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(session: SessionEntity) {
    val dateString = remember(session.startTime) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(session.startTime))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(24.dp))
            .testTag("history_item_${session.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular activity decorative index
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ElectricBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(session.activityType.lowercase()) {
                            "running" -> Icons.Default.PlayArrow
                            "weightlifting" -> Icons.Default.Star
                            else -> Icons.Default.Favorite
                        },
                        contentDescription = session.activityType,
                        tint = ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Sessione " + when(session.activityType) {
                            "Running" -> "Corsa"
                            "Weightlifting" -> "Pesi"
                            else -> "Relax"
                        },
                        color = LightText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateString,
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "ID: #${session.id}",
                    color = LightText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (session.isActive) "Attivo" else "Archiviato",
                    color = if (session.isActive) NeonGreen else MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun AppBottomNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF0A090F),
        tonalElevation = 8.dp,
        modifier = Modifier
            .height(82.dp)
            .border(BorderStroke(1.dp, Color(0xFF1F1C36)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        NavigationBarItem(
            selected = currentTab == "Workout",
            onClick = { onTabSelected("Workout") },
            icon = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (currentTab == "Workout") NeonGreen.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏠", fontSize = 20.sp)
                }
            },
            label = {
                Text(
                    text = "Workout",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == "Workout") NeonGreen else MutedText
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )

        NavigationBarItem(
            selected = currentTab == "Health",
            onClick = { onTabSelected("Health") },
            icon = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (currentTab == "Health") ElectricBlue.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📊", fontSize = 20.sp)
                }
            },
            label = {
                Text(
                    text = "Health",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == "Health") ElectricBlue else MutedText
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )

        NavigationBarItem(
            selected = currentTab == "Settings",
            onClick = { onTabSelected("Settings") },
            icon = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (currentTab == "Settings") MutedText.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙️", fontSize = 20.sp)
                }
            },
            label = {
                Text(
                    text = "Settings",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == "Settings") LightText else MutedText
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun HealthScreen(
    viewModel: WorkoutViewModel,
    onTabSelected: (String) -> Unit
) {
    val sessionHistory by viewModel.sessionHistory.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AuraBeats Health",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentTab = "Health",
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "STORICO ANALITICI RECENTI",
                    color = ElectricBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dettagli sull'attività e telemetria cardiovascolare salvata sul database locale.",
                    color = MutedText,
                    fontSize = 13.sp
                )
            }

            if (sessionHistory.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "💔",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nessun dato registrato",
                            color = LightText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Avvia un allenamento nella scheda precedente per raccogliere metrics!",
                            color = MutedText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(sessionHistory) { session ->
                    HistoryItemCard(session)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onTabSelected: (String) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Impostazioni App",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentTab = "Settings",
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚙️",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Configurazione AuraBeats",
                color = LightText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Versione 1.2.0 • Progettato per l'integrazione con FastAPI.",
                color = MutedText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
