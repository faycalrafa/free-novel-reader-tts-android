package com.novelarrow.reader.ui.settings

import android.speech.tts.Voice
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.SupportedLanguage
import com.novelarrow.reader.playback.VoiceGroupingHelper

@Composable
internal fun VoiceSelectionContent(
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
    onStopPreview: () -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    onSelectEnginePackage: (String) -> Unit
) {
    val context = LocalContext.current
    val installedEngines = remember { AudioPlaybackCoordinator.getInstalledTtsEngines() }
    val activeEnginePkg = AudioPlaybackCoordinator.getActiveTtsEngine() ?: AudioPlaybackCoordinator.getDefaultTtsEngine()
    val activeEngineLabel = installedEngines.firstOrNull { it.name == activeEnginePkg }?.label ?: activeEnginePkg ?: "Built-in System TTS"
    var showEngineDialog by remember { mutableStateOf(false) }

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Select Text-to-Speech Engine", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    installedEngines.forEach { engine ->
                        val isCurrent = (engine.name == activeEnginePkg)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectEnginePackage(engine.name)
                                    showEngineDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isCurrent,
                                onClick = {
                                    onSelectEnginePackage(engine.name)
                                    showEngineDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(engine.label, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                Text(engine.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEngineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
        // Active TTS Engine Banner Card
        if (searchQuery.isBlank()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Engine: $activeEngineLabel",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${availableVoices.size} voices available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (installedEngines.size > 1) {
                            OutlinedButton(
                                onClick = { showEngineDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Switch Engine", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSystemTtsSettings,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("System TTS Settings", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

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
