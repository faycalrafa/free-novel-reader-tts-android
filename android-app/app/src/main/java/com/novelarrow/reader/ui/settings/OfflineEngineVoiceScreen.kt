package com.novelarrow.reader.ui.settings

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.NeuralVoiceCatalog
import com.novelarrow.reader.data.model.NeuralVoiceInfo
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.NeuralModelDownloader
import com.novelarrow.reader.playback.SupportedLanguage
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineEngineVoiceScreen(
    engineType: String, // "PIPER", "KOKORO", "KITTEN"
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settingsFlow.collectAsState()
    val previewingVoiceName by AudioPlaybackCoordinator.previewingVoiceName.collectAsState()

    val engineTitle = when (engineType.uppercase()) {
        "PIPER" -> "Piper TTS"
        "KOKORO" -> "Kokoro TTS (82M)"
        "KITTEN" -> "KittenTTS Nano"
        else -> engineType
    }

    // Supported languages per engine
    val supportedLanguages = remember(engineType) {
        when (engineType.uppercase()) {
            "PIPER" -> listOf(SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH, SupportedLanguage.ARABIC)
            "KOKORO" -> listOf(SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH)
            "KITTEN" -> listOf(SupportedLanguage.ENGLISH)
            else -> listOf(SupportedLanguage.ENGLISH)
        }
    }

    var selectedLanguage by remember { mutableStateOf(supportedLanguages.first()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val allVoices = remember(engineType) {
        NeuralVoiceCatalog.getVoicesForEngine(engineType)
    }

    // Currently selected voice
    var selectedVoiceId by remember(settings.selectedVoiceName, allVoices) {
        val initialId = if (settings.selectedEngineType.equals(engineType, ignoreCase = true) && settings.selectedVoiceName != null) {
            settings.selectedVoiceName
        } else {
            allVoices.firstOrNull()?.id
        }
        mutableStateOf(initialId)
    }

    val selectedVoice = remember(selectedVoiceId, allVoices) {
        allVoices.firstOrNull { it.id == selectedVoiceId } ?: allVoices.firstOrNull()
    }

    // Download state tracking
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytesText by remember { mutableStateOf("") }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadStatusRefreshKey by remember { mutableStateOf(0) }

    val isModelDownloaded = remember(selectedVoice, downloadStatusRefreshKey) {
        selectedVoice != null && NeuralModelDownloader.isModelDownloaded(context, selectedVoice)
    }

    // Voice preview pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Stop preview on exit
    DisposableEffect(Unit) {
        onDispose {
            AudioPlaybackCoordinator.stopVoicePreview()
            downloadJob?.cancel()
        }
    }

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
                            text = engineTitle,
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
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }

                    // Save as default voice button
                    Button(
                        onClick = {
                            val voiceToSave = selectedVoice
                            if (voiceToSave == null) return@Button

                            if (!isModelDownloaded) {
                                Toast.makeText(
                                    context,
                                    "Please download the model first before setting it as default.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }

                            scope.launch {
                                try {
                                    settingsRepo.setSelectedEngineType(engineType.uppercase())
                                    settingsRepo.setVoice(voiceToSave.id)
                                    settingsRepo.setFavoriteVoice(voiceToSave.language.code, voiceToSave.id)
                                    AudioPlaybackCoordinator.setSelectedEngineType(engineType.uppercase())
                                    AudioPlaybackCoordinator.setVoice(voiceToSave.id)
                                    AppLogger.i("OfflineEngineVoiceScreen", "Saved ${voiceToSave.name} as default voice for $engineType")
                                    Toast.makeText(
                                        context,
                                        "Saved ${voiceToSave.name} as active voice",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    AudioPlaybackCoordinator.stopVoicePreview()
                                    onNavigateBack()
                                } catch (e: Exception) {
                                    AppLogger.e("OfflineEngineVoiceScreen", "Failed to save voice settings", e)
                                    Toast.makeText(context, "Failed to save settings: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Language selection tabs
            if (supportedLanguages.size > 1) {
                TabRow(
                    selectedTabIndex = supportedLanguages.indexOf(selectedLanguage).coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    supportedLanguages.forEach { lang ->
                        val selected = (selectedLanguage == lang)
                        Tab(
                            selected = selected,
                            onClick = {
                                selectedLanguage = lang
                                AudioPlaybackCoordinator.stopVoicePreview()
                            },
                            text = {
                                Text(
                                    text = lang.displayName,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Model Download & Prompt Card
                item(key = "model_prompt_card_${selectedVoice?.id}") {
                    selectedVoice?.let { voice ->
                        ModelDownloadPromptCard(
                            voiceInfo = voice,
                            isDownloaded = isModelDownloaded,
                            isDownloading = isDownloading,
                            downloadProgress = downloadProgress,
                            downloadedBytesText = downloadedBytesText,
                            onStartDownload = {
                                if (isDownloading) return@ModelDownloadPromptCard
                                isDownloading = true
                                downloadProgress = 0f
                                downloadedBytesText = "Connecting..."
                                downloadJob = scope.launch {
                                    val result = NeuralModelDownloader.downloadVoiceModel(
                                        context = context,
                                        voiceInfo = voice,
                                        onProgress = { downloaded, total ->
                                            if (total > 0) {
                                                downloadProgress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                                val dlMb = downloaded.toDouble() / (1024.0 * 1024.0)
                                                val totalMb = total.toDouble() / (1024.0 * 1024.0)
                                                downloadedBytesText = String.format("%.1f MB / %.1f MB (%.0f%%)", dlMb, totalMb, downloadProgress * 100f)
                                            } else {
                                                val dlMb = downloaded.toDouble() / (1024.0 * 1024.0)
                                                downloadedBytesText = String.format("%.1f MB downloaded", dlMb)
                                            }
                                        }
                                    )
                                    isDownloading = false
                                    if (result.isSuccess) {
                                        downloadStatusRefreshKey++
                                        Toast.makeText(context, "Downloaded ${voice.name} successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Download failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onCancelDownload = {
                                downloadJob?.cancel()
                                isDownloading = false
                                downloadedBytesText = "Cancelled"
                            },
                            onDeleteModel = {
                                NeuralModelDownloader.deleteModel(context, voice)
                                downloadStatusRefreshKey++
                                Toast.makeText(context, "Model deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Voices Section Header
                item(key = "voices_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "AVAILABLE VOICES (${selectedLanguage.displayName.uppercase()})",
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
                            val filteredCount = allVoices.count {
                                it.language == selectedLanguage &&
                                        (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true))
                            }
                            Text(
                                text = "$filteredCount ${if (filteredCount == 1) "voice" else "voices"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Voice List
                val filteredVoices = allVoices.filter {
                    it.language == selectedLanguage &&
                            (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true))
                }

                if (filteredVoices.isEmpty()) {
                    item(key = "empty_voices") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No voices matching \"$searchQuery\"" else "No voices found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredVoices, key = { it.id }) { voice ->
                        val isSelected = (selectedVoiceId == voice.id)
                        val isPreviewing = (previewingVoiceName == voice.id)

                        NeuralVoiceCard(
                            voice = voice,
                            isSelected = isSelected,
                            isPreviewing = isPreviewing,
                            pulseAlpha = pulseAlpha,
                            onSelect = {
                                selectedVoiceId = voice.id
                            },
                            onTogglePreview = {
                                if (isPreviewing) {
                                    AudioPlaybackCoordinator.stopVoicePreview()
                                } else {
                                    voice.sampleAudioUrl?.let { url ->
                                        AudioPlaybackCoordinator.previewNeuralAudio(voice.id, url)
                                    } ?: Toast.makeText(context, "No preview sample available for this voice", Toast.LENGTH_SHORT).show()
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
private fun ModelDownloadPromptCard(
    voiceInfo: NeuralVoiceInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedBytesText: String,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isDownloaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDownloaded) "Model Downloaded & Ready" else "Download Neural Model",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isDownloaded) {
                            "Model file: ${voiceInfo.modelFileName} (${voiceInfo.formattedSize})"
                        } else {
                            "Download ${voiceInfo.name} (${voiceInfo.formattedSize}) for offline neural speech"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = downloadedBytesText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (!isDownloading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDownloaded) {
                        OutlinedButton(
                            onClick = onDeleteModel,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Model")
                        }
                    } else {
                        Button(
                            onClick = onStartDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download (${voiceInfo.formattedSize})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeuralVoiceCard(
    voice: NeuralVoiceInfo,
    isSelected: Boolean,
    isPreviewing: Boolean,
    pulseAlpha: Float,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
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
                    Text(
                        text = voice.gender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = voice.quality,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = voice.formattedSize,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                if (voice.description.isNotBlank()) {
                    Text(
                        text = voice.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
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
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Playing sample preview...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // Audio Sample Preview Button
            IconButton(
                onClick = onTogglePreview,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isPreviewing) "Stop preview" else "Listen to sample",
                    tint = if (isPreviewing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
