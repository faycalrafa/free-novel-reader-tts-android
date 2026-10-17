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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.novelarrow.reader.data.model.NeuralVoiceCatalog
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.SupportedLanguage
import com.novelarrow.reader.playback.VoiceGroupingHelper
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

enum class VoiceEngineScreenMode {
    ENGINE_SELECTION,
    VOICE_SELECTION,
    PIPER_VOICES,
    KOKORO_VOICES,
    KITTEN_VOICES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEngineScreen(
    onNavigateBack: () -> Unit,
    initialMode: VoiceEngineScreenMode = VoiceEngineScreenMode.ENGINE_SELECTION,
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(initialMode) }

    when (currentMode) {
        VoiceEngineScreenMode.PIPER_VOICES -> {
            OfflineEngineVoiceScreen(
                engineType = "PIPER",
                onNavigateBack = { currentMode = VoiceEngineScreenMode.ENGINE_SELECTION },
                modifier = modifier
            )
            return
        }
        VoiceEngineScreenMode.KOKORO_VOICES -> {
            OfflineEngineVoiceScreen(
                engineType = "KOKORO",
                onNavigateBack = { currentMode = VoiceEngineScreenMode.ENGINE_SELECTION },
                modifier = modifier
            )
            return
        }
        VoiceEngineScreenMode.KITTEN_VOICES -> {
            OfflineEngineVoiceScreen(
                engineType = "KITTEN",
                onNavigateBack = { currentMode = VoiceEngineScreenMode.ENGINE_SELECTION },
                modifier = modifier
            )
            return
        }
        else -> Unit
    }

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

    // Intercept back button when in voice selection mode to return to engine selection
    BackHandler(enabled = currentMode == VoiceEngineScreenMode.VOICE_SELECTION || isSearchActive) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (currentMode == VoiceEngineScreenMode.VOICE_SELECTION) {
            AudioPlaybackCoordinator.stopVoicePreview()
            currentMode = VoiceEngineScreenMode.ENGINE_SELECTION
        }
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
                            text = if (currentMode == VoiceEngineScreenMode.ENGINE_SELECTION) "Voice Engine" else "Select Voice",
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
                            } else if (currentMode == VoiceEngineScreenMode.VOICE_SELECTION) {
                                AudioPlaybackCoordinator.stopVoicePreview()
                                currentMode = VoiceEngineScreenMode.ENGINE_SELECTION
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
                    if (!isSearchActive && currentMode == VoiceEngineScreenMode.VOICE_SELECTION) {
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
            when (currentMode) {
                VoiceEngineScreenMode.ENGINE_SELECTION -> {
                    EngineSelectionContent(
                        voiceCount = availableVoices.size,
                        selectedVoiceName = savedVoiceName,
                        selectedEngineType = settings?.selectedEngineType ?: "SYSTEM",
                        selectedEnginePackage = settings?.selectedTtsEnginePackage,
                        onSelectEngine = { engineType ->
                            coroutineScope.launch {
                                AudioPlaybackCoordinator.settingsRepository?.setSelectedEngineType(engineType)
                            }
                        },
                        onSelectEnginePackage = { pkg ->
                            AudioPlaybackCoordinator.switchTtsEngine(pkg)
                        },
                        onSelectBuiltIn = {
                            currentMode = VoiceEngineScreenMode.VOICE_SELECTION
                        },
                        onOpenPiper = {
                            currentMode = VoiceEngineScreenMode.PIPER_VOICES
                        },
                        onOpenKokoro = {
                            currentMode = VoiceEngineScreenMode.KOKORO_VOICES
                        },
                        onOpenKitten = {
                            currentMode = VoiceEngineScreenMode.KITTEN_VOICES
                        },
                        onOpenSystemTtsSettings = {
                            openSystemTtsSettings(context)
                        }
                    )
                }
                VoiceEngineScreenMode.VOICE_SELECTION -> {
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
                        }
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun EngineSelectionContent(
    voiceCount: Int,
    selectedVoiceName: String?,
    selectedEngineType: String,
    selectedEnginePackage: String?,
    onSelectEngine: (String) -> Unit,
    onSelectEnginePackage: (String) -> Unit,
    onSelectBuiltIn: () -> Unit,
    onOpenPiper: () -> Unit,
    onOpenKokoro: () -> Unit,
    onOpenKitten: () -> Unit,
    onOpenSystemTtsSettings: () -> Unit
) {
    val context = LocalContext.current
    val defaultEngine = AudioPlaybackCoordinator.getDefaultTtsEngine() ?: "com.google.android.tts"
    val activeEnginePkg = AudioPlaybackCoordinator.getActiveTtsEngine() ?: selectedEnginePackage ?: defaultEngine

    val isLocalSherpaActive = selectedEngineType == SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL ||
            selectedEngineType == "PIPER" ||
            selectedEngineType == "KOKORO" ||
            selectedEngineType == "KITTEN"

    val isPiperVoiceActive = isLocalSherpaActive && (NeuralVoiceCatalog.PIPER_VOICES.any { it.id == selectedVoiceName } || selectedEngineType == "PIPER")
    val isKokoroVoiceActive = isLocalSherpaActive && (NeuralVoiceCatalog.KOKORO_VOICES.any { it.id == selectedVoiceName } || selectedEngineType == "KOKORO")
    val isKittenVoiceActive = isLocalSherpaActive && (NeuralVoiceCatalog.KITTEN_VOICES.any { it.id == selectedVoiceName } || selectedEngineType == "KITTEN")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SPEECH SYNTHESIS ENGINES",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )

        // Primary Built-in Android TTS Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedEngineType == "SYSTEM") {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            ),
            border = BorderStroke(
                1.5.dp,
                if (selectedEngineType == "SYSTEM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onSelectEngine("SYSTEM")
                    onSelectBuiltIn()
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Built-in Android TTS",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (selectedEngineType == "SYSTEM") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Engine: $activeEnginePkg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (voiceCount > 0) "$voiceCount voices available" else "Querying voices...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Configure Voices",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Active voice: ${if (selectedEngineType == "SYSTEM") (selectedVoiceName ?: "System Default") else "None (Neural Active)"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Embedded Offline Neural TTS (sherpa-onnx) Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLocalSherpaActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            ),
            border = BorderStroke(
                1.5.dp,
                if (isLocalSherpaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onSelectEngine(SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL)
                    onOpenPiper()
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Embedded Neural TTS",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isLocalSherpaActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = if (isLocalSherpaActive) "Active" else "Embedded",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isLocalSherpaActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Engine: sherpa-onnx (Offline C++)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Piper, Kokoro & Kitten neural models • 0 external APKs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Configure Neural Voices",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val activeNeuralVoice = if (isLocalSherpaActive) {
                    val v = NeuralVoiceCatalog.getVoiceById(selectedVoiceName ?: "", context)
                    v?.name ?: selectedVoiceName ?: "Lessac (US)"
                } else {
                    "None (System TTS Active)"
                }
                Text(
                    text = "Active voice: $activeNeuralVoice",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "OFFLINE NEURAL VOICES",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )

        // Piper TTS Card (Embedded sherpa-onnx)
        OpenSourceEngineCard(
            title = "Piper TTS",
            subtitle = "Ultra-fast neural text-to-speech with English, French, and Arabic voices (<65MB each).",
            status = "Embedded • Ready",
            isEngineReady = true,
            isSelected = isPiperVoiceActive,
            onClick = {
                onSelectEngine(SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL)
                onOpenPiper()
            }
        )

        // Kokoro TTS Card
        OpenSourceEngineCard(
            title = "Kokoro TTS",
            subtitle = "State-of-the-art 82M open-weight neural voice bundle delivering human-grade prosody.",
            status = "Embedded • Ready",
            isEngineReady = true,
            isSelected = isKokoroVoiceActive,
            onClick = {
                onSelectEngine(SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL)
                onOpenKokoro()
            }
        )

        // KittenTTS Card
        OpenSourceEngineCard(
            title = "KittenTTS",
            subtitle = "Ultra-compact embedded on-device models (<40MB) designed for minimal battery consumption.",
            status = "Embedded • Ready",
            isEngineReady = true,
            isSelected = isKittenVoiceActive,
            onClick = {
                onSelectEngine(SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL)
                onOpenKitten()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System Settings Launch Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Need more voices or languages?",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You can download high-definition neural voices directly in Android device settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenSystemTtsSettings,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Android TTS Settings")
                }
            }
        }
    }
}

@Composable
private fun OpenSourceEngineCard(
    title: String,
    subtitle: String,
    status: String,
    isEngineReady: Boolean = false,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isEngineReady) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEngineReady) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Configure Voices",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun VoiceSelectionContent(
    availableVoices: List<Voice>,
    selectedLanguage: SupportedLanguage,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    pendingVoiceEnglish: String?,
    pendingVoiceArabic: String?,
    pendingVoiceFrench: String?,
    onUpdatePendingVoice: (SupportedLanguage, String?) -> Unit,
    previewingVoiceName: String?,
    searchQuery: String,
    onPreviewVoice: (Voice) -> Unit,
    onStopPreview: () -> Unit
) {
    val currentPendingVoice = when (selectedLanguage) {
        SupportedLanguage.ENGLISH -> pendingVoiceEnglish
        SupportedLanguage.ARABIC -> pendingVoiceArabic
        SupportedLanguage.FRENCH -> pendingVoiceFrench
    }

    // Get voices for active language
    val languageVoices = remember(availableVoices, selectedLanguage) {
        VoiceGroupingHelper.getVoicesForLanguage(availableVoices, selectedLanguage)
    }

    // Filter by search query if active
    val filteredVoices = remember(languageVoices, searchQuery) {
        if (searchQuery.isBlank()) {
            languageVoices
        } else {
            val query = searchQuery.trim()
            languageVoices.filter { voice ->
                voice.name.contains(query, ignoreCase = true) ||
                voice.locale?.displayCountry?.contains(query, ignoreCase = true) == true ||
                voice.locale?.toLanguageTag()?.contains(query, ignoreCase = true) == true
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulsePreviewTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Supported Languages 3-Tab Row
        val languages = listOf(
            SupportedLanguage.ENGLISH,
            SupportedLanguage.ARABIC,
            SupportedLanguage.FRENCH
        )
        val selectedIndex = languages.indexOf(selectedLanguage).coerceAtLeast(0)

        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            languages.forEachIndexed { index, lang ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelectLanguage(lang) },
                    text = {
                        Text(
                            text = lang.displayName,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // System Default Option for this language
            if (searchQuery.isBlank() || "system default".contains(searchQuery.trim(), ignoreCase = true)) {
                item(key = "system_default_${selectedLanguage.code}") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentPendingVoice == null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ),
                        border = if (currentPendingVoice == null) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdatePendingVoice(selectedLanguage, null) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            RadioButton(
                                selected = (currentPendingVoice == null),
                                onClick = { onUpdatePendingVoice(selectedLanguage, null) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System Default (${selectedLanguage.displayName})",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Dynamically routes to Android's default voice for ${selectedLanguage.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            if (filteredVoices.isEmpty()) {
                item(key = "empty_voices_${selectedLanguage.code}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "No ${selectedLanguage.displayName} voices matching \"$searchQuery\""
                                } else {
                                    "No installed voices found for ${selectedLanguage.displayName} on this device"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item(key = "header_${selectedLanguage.code}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "AVAILABLE ${selectedLanguage.displayName.uppercase()} VOICES",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${filteredVoices.size} ${if (filteredVoices.size == 1) "voice" else "voices"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                items(filteredVoices, key = { it.name }) { voice ->
                    val isSelected = (currentPendingVoice == voice.name)
                    val isPreviewing = (previewingVoiceName == voice.name)

                    VoiceItemRow(
                        voice = voice,
                        isSelected = isSelected,
                        isPreviewing = isPreviewing,
                        pulseAlpha = pulseAlpha,
                        onSelect = { onUpdatePendingVoice(selectedLanguage, voice.name) },
                        onTogglePreview = {
                            if (isPreviewing) {
                                onStopPreview()
                            } else {
                                onPreviewVoice(voice)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceItemRow(
    voice: Voice,
    isSelected: Boolean,
    isPreviewing: Boolean,
    pulseAlpha: Float,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
    val country = voice.locale?.displayCountry?.ifBlank { voice.locale?.country.orEmpty() }.orEmpty()
    val qualityDesc = VoiceGroupingHelper.formatQuality(voice.quality)
    val latencyDesc = VoiceGroupingHelper.formatLatency(voice.latency)
    val isOffline = !voice.isNetworkConnectionRequired

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPreviewing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = when {
            isPreviewing -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (country.isNotBlank()) {
                        Text(
                            text = country,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = qualityDesc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Text(
                        text = if (isOffline) "Offline" else "Network",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                }

                if (isPreviewing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Speaking preview sample...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Audio Preview Icon Button
            IconButton(
                onClick = onTogglePreview,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                if (isPreviewing) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop Voice Preview",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Play Voice Preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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
