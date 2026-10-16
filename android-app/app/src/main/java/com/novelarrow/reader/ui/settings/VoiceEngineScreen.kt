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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.VoiceGroupingHelper
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.flow.emptyFlow

enum class VoiceEngineScreenMode {
    ENGINE_SELECTION,
    VOICE_SELECTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEngineScreen(
    onNavigateBack: () -> Unit,
    initialMode: VoiceEngineScreenMode = VoiceEngineScreenMode.ENGINE_SELECTION,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentMode by remember { mutableStateOf(initialMode) }
    var searchQuery by remember { mutableStateOf("") }

    val availableVoices by AudioPlaybackCoordinator.availableVoicesState.collectAsState()
    val previewingVoiceName by AudioPlaybackCoordinator.previewingVoiceName.collectAsState()

    val settings by (AudioPlaybackCoordinator.settingsRepository?.settingsFlow ?: emptyFlow()).collectAsState(initial = null)
    val selectedVoiceName = settings?.selectedVoiceName ?: AudioPlaybackCoordinator.currentVoiceName

    // Always stop preview when exiting the screen
    DisposableEffect(Unit) {
        onDispose {
            AudioPlaybackCoordinator.stopVoicePreview()
        }
    }

    // Intercept back button when in voice selection mode to return to engine selection
    BackHandler(enabled = currentMode == VoiceEngineScreenMode.VOICE_SELECTION) {
        AudioPlaybackCoordinator.stopVoicePreview()
        currentMode = VoiceEngineScreenMode.ENGINE_SELECTION
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentMode == VoiceEngineScreenMode.ENGINE_SELECTION) "Voice Engine" else "Select Voice",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentMode == VoiceEngineScreenMode.VOICE_SELECTION) {
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
                        selectedVoiceName = selectedVoiceName,
                        onSelectBuiltIn = {
                            currentMode = VoiceEngineScreenMode.VOICE_SELECTION
                        },
                        onOpenSystemTtsSettings = {
                            openSystemTtsSettings(context)
                        }
                    )
                }
                VoiceEngineScreenMode.VOICE_SELECTION -> {
                    HierarchicalVoiceSelectionContent(
                        availableVoices = availableVoices,
                        selectedVoiceName = selectedVoiceName,
                        previewingVoiceName = previewingVoiceName,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSelectVoice = { voiceName ->
                            AudioPlaybackCoordinator.updateVoice(voiceName)
                        },
                        onPreviewVoice = { voice ->
                            AudioPlaybackCoordinator.previewVoice(voice)
                        },
                        onStopPreview = {
                            AudioPlaybackCoordinator.stopVoicePreview()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineSelectionContent(
    voiceCount: Int,
    selectedVoiceName: String?,
    onSelectBuiltIn: () -> Unit,
    onOpenSystemTtsSettings: () -> Unit
) {
    val defaultEngine = AudioPlaybackCoordinator.getDefaultTtsEngine() ?: "com.google.android.tts"
    val installedEngines = remember { AudioPlaybackCoordinator.getInstalledTtsEngines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AVAILABLE SPEECH ENGINES",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )

        // Primary Built-in Android TTS Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectBuiltIn() }
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "System Default",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = defaultEngine,
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
                        contentDescription = "Open Voices",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Active voice: ${selectedVoiceName ?: "System Default"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Tap to browse voices grouped by language with instant audio previews.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Additional Installed Engines if detected
        if (installedEngines.size > 1) {
            Text(
                text = "OTHER INSTALLED ENGINES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    installedEngines.filter { it.name != defaultEngine }.forEachIndexed { index, engine ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engine.label.ifBlank { engine.name },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = engine.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (index < installedEngines.size - 2) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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
private fun HierarchicalVoiceSelectionContent(
    availableVoices: List<Voice>,
    selectedVoiceName: String?,
    previewingVoiceName: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onPreviewVoice: (Voice) -> Unit,
    onStopPreview: () -> Unit
) {
    val filteredVoices = remember(availableVoices, searchQuery) {
        if (searchQuery.isBlank()) {
            availableVoices
        } else {
            val query = searchQuery.trim()
            availableVoices.filter { voice ->
                voice.name.contains(query, ignoreCase = true) ||
                voice.locale?.displayLanguage?.contains(query, ignoreCase = true) == true ||
                voice.locale?.displayCountry?.contains(query, ignoreCase = true) == true ||
                voice.locale?.toLanguageTag()?.contains(query, ignoreCase = true) == true
            }
        }
    }

    val groupedVoices = remember(filteredVoices) {
        VoiceGroupingHelper.groupVoicesByLanguage(filteredVoices)
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
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search voices by name, language, or country...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // System Default Option (only shown if not actively filtering or if query matches "system" or "default")
            if (searchQuery.isBlank() || "system default".contains(searchQuery.trim(), ignoreCase = true)) {
                item(key = "system_default_voice_option") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedVoiceName == null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ),
                        border = if (selectedVoiceName == null) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectVoice(null) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            RadioButton(
                                selected = (selectedVoiceName == null),
                                onClick = { onSelectVoice(null) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System Default Voice",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Auto-detects language dynamically and follows system speech engine defaults",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (groupedVoices.isEmpty()) {
                item(key = "empty_voices_result") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
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
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No voices found matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                groupedVoices.forEach { (language, voices) ->
                    item(key = "header_$language") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = language,
                                style = MaterialTheme.typography.titleMedium.copy(
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
                                    text = "${voices.size} ${if (voices.size == 1) "voice" else "voices"}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(voices, key = { it.name }) { voice ->
                        val isSelected = (selectedVoiceName == voice.name)
                        val isPreviewing = (previewingVoiceName == voice.name)

                        VoiceItemRow(
                            voice = voice,
                            isSelected = isSelected,
                            isPreviewing = isPreviewing,
                            pulseAlpha = pulseAlpha,
                            onSelect = { onSelectVoice(voice.name) },
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
