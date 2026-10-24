package com.novelarrow.reader.ui.settings

import android.content.Context
import android.content.Intent
import android.speech.tts.Voice
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.SupportedLanguage
import com.novelarrow.reader.playback.VoiceGroupingHelper
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

enum class VoiceEngineScreenMode {
    VOICE_SELECTION,
    ENGINE_SELECTION,
    PIPER_VOICES,
    KOKORO_VOICES,
    KITTEN_VOICES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEngineScreen(
    onNavigateBack: () -> Unit,
    initialMode: VoiceEngineScreenMode = VoiceEngineScreenMode.VOICE_SELECTION,
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(initialMode) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val availableVoices by AudioPlaybackCoordinator.availableVoicesState.collectAsState()
    val previewingVoiceName by AudioPlaybackCoordinator.previewingVoiceName.collectAsState()

    val settings by (AudioPlaybackCoordinator.settingsRepository?.settingsFlow ?: emptyFlow()).collectAsState(initial = null)
    val savedVoiceName = settings?.selectedVoiceName ?: AudioPlaybackCoordinator.currentVoiceName

    var selectedLanguage by remember { mutableStateOf(SupportedLanguage.ENGLISH) }

    // Pending voice states for English, Arabic, and French
    var pendingVoiceEnglish by remember { mutableStateOf<String?>(null) }
    var pendingVoiceArabic by remember { mutableStateOf<String?>(null) }
    var pendingVoiceFrench by remember { mutableStateOf<String?>(null) }
    var hasInitializedPending by remember { mutableStateOf(false) }

    // Track whether any voice changes are pending
    var hasPendingChanges by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        val s = settings
        if (s != null && !hasInitializedPending) {
            pendingVoiceEnglish = s.favoriteVoiceEnglish ?: savedVoiceName
            pendingVoiceArabic = s.favoriteVoiceArabic
            pendingVoiceFrench = s.favoriteVoiceFrench
            hasInitializedPending = true
        }
    }

    // Always stop preview when exiting the screen
    DisposableEffect(Unit) {
        onDispose {
            AudioPlaybackCoordinator.stopVoicePreview()
        }
    }

    // Intercept back button when search is active
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search voices...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Voice Engine",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                AudioPlaybackCoordinator.stopVoicePreview()
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Search toggle button
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Voices")
                        }
                    }

                    // Save Button when pending voice changes exist
                    AnimatedVisibility(visible = hasPendingChanges) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val repo = AudioPlaybackCoordinator.settingsRepository
                                    repo?.setFavoriteVoice("en", pendingVoiceEnglish)
                                    repo?.setFavoriteVoice("ar", pendingVoiceArabic)
                                    repo?.setFavoriteVoice("fr", pendingVoiceFrench)

                                    val activeForCurrent = when (selectedLanguage) {
                                        SupportedLanguage.ARABIC -> pendingVoiceArabic
                                        SupportedLanguage.FRENCH -> pendingVoiceFrench
                                        SupportedLanguage.ENGLISH -> pendingVoiceEnglish
                                    }
                                    AudioPlaybackCoordinator.updateVoice(activeForCurrent)

                                    hasPendingChanges = false
                                    Toast.makeText(context, "Voice preferences saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            VoiceSelectionContent(
                availableVoices = availableVoices,
                selectedLanguage = selectedLanguage,
                onSelectLanguage = { selectedLanguage = it },
                pendingVoiceEnglish = pendingVoiceEnglish,
                pendingVoiceArabic = pendingVoiceArabic,
                pendingVoiceFrench = pendingVoiceFrench,
                onUpdatePendingVoice = { lang, voiceName ->
                    when (lang) {
                        SupportedLanguage.ENGLISH -> pendingVoiceEnglish = voiceName
                        SupportedLanguage.ARABIC -> pendingVoiceArabic = voiceName
                        SupportedLanguage.FRENCH -> pendingVoiceFrench = voiceName
                    }
                    hasPendingChanges = true
                },
                previewingVoiceName = previewingVoiceName,
                searchQuery = searchQuery,
                onPreviewVoice = { voice ->
                    AudioPlaybackCoordinator.previewVoice(voice)
                },
                onStopPreview = {
                    AudioPlaybackCoordinator.stopVoicePreview()
                },
                onOpenSystemTtsSettings = {
                    openSystemTtsSettings(context)
                },
                onSelectEnginePackage = { pkg ->
                    AudioPlaybackCoordinator.switchTtsEngine(pkg)
                }
            )
        }
    }
}

private fun openSystemTtsSettings(context: Context) {
    try {
        val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLogger.w("VoiceEngineScreen", "Failed to open TTS settings directly, falling back to general settings: ${e.message}", e)
        try {
            val fallback = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (e2: Exception) {
            AppLogger.e("VoiceEngineScreen", "Failed to open device settings: ${e2.message}", e2)
            Toast.makeText(context, "Could not open system settings", Toast.LENGTH_SHORT).show()
        }
    }
}
