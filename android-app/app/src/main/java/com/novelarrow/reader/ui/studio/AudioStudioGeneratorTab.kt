package com.novelarrow.reader.ui.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioStudioGeneratorTab(
    novels: List<Novel>,
    currentNovel: Novel?,
    exportState: AudioExportService.ExportState,
    isProgressDismissedLocally: Boolean,
    onDismissProgressLocally: () -> Unit,
    onSwitchToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedNovel by remember(novels, currentNovel) {
        mutableStateOf(currentNovel ?: novels.firstOrNull())
    }
    var startChapterIndex by remember { mutableIntStateOf(0) }
    var endChapterIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedNovel) {
        val totalChaps = selectedNovel?.chapterCount ?: 0
        startChapterIndex = 0
        endChapterIndex = (totalChaps - 1).coerceAtLeast(0)
    }

    var splitMode by remember { mutableStateOf(AudioExportEngine.SplitMode.BY_CHAPTER) }
    var maxMinutes by remember { mutableIntStateOf(15) }
    var exportAudioFormat by remember { mutableStateOf(AudioExportEngine.AudioExportFormat.MP3) }
    var novelDropdownExpanded by remember { mutableStateOf(false) }

    val currentSpeed by AudioPlaybackCoordinator.currentSpeechRateState.collectAsState()
    var generatorSpeechRate by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    val totalChapters = selectedNovel?.chapterCount ?: 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Export Progress Card
        if (exportState is AudioExportService.ExportState.Running && !isProgressDismissedLocally) {
            val runningState = exportState as AudioExportService.ExportState.Running
            item {
                ExportProgressCard(
                    runningState = runningState,
                    onHide = onDismissProgressLocally,
                    onCancel = { AudioExportService.cancelExport(context) }
                )
            }
        } else if (exportState is AudioExportService.ExportState.Success) {
            val success = exportState as AudioExportService.ExportState.Success
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Complete!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Saved ${success.files.size} file(s) into Downloads/BookReader.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onSwitchToLibrary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("View in Audio Library")
                        }
                    }
                }
            }
        }

        // Novel / Folder Picker Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Select Novel / Folder",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (novels.isEmpty()) {
                        Text(
                            text = "No novels found in library. Import a folder first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = novelDropdownExpanded,
                            onExpandedChange = { novelDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedNovel?.title ?: "Select novel",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = novelDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = novelDropdownExpanded,
                                onDismissRequest = { novelDropdownExpanded = false }
                            ) {
                                novels.forEach { novel ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    novel.title,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "${novel.chapterCount} chapters",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedNovel = novel
                                            startChapterIndex = 0
                                            endChapterIndex = (novel.chapterCount - 1).coerceAtLeast(0)
                                            novelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chapter Range Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Chapter Range (${endChapterIndex - startChapterIndex + 1} selected)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("From Chapter: ${startChapterIndex + 1}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = startChapterIndex.toFloat(),
                                onValueChange = {
                                    startChapterIndex = it.toInt().coerceAtMost(endChapterIndex)
                                },
                                valueRange = 0f..(totalChapters - 1).coerceAtLeast(0).toFloat()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("To Chapter: ${endChapterIndex + 1}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = endChapterIndex.toFloat(),
                                onValueChange = {
                                    endChapterIndex = it.toInt().coerceAtLeast(startChapterIndex)
                                },
                                valueRange = 0f..(totalChapters - 1).coerceAtLeast(0).toFloat()
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = startChapterIndex == 0 && endChapterIndex == (totalChapters - 1).coerceAtLeast(0),
                            onClick = {
                                startChapterIndex = 0
                                endChapterIndex = (totalChapters - 1).coerceAtLeast(0)
                            },
                            label = { Text("All ($totalChapters)") }
                        )
                        FilterChip(
                            selected = startChapterIndex == 0 && endChapterIndex == 4.coerceAtMost(totalChapters - 1),
                            onClick = {
                                startChapterIndex = 0
                                endChapterIndex = 4.coerceAtMost(totalChapters - 1)
                            },
                            label = { Text("First 5") }
                        )
                        FilterChip(
                            selected = startChapterIndex == 0 && endChapterIndex == 9.coerceAtMost(totalChapters - 1),
                            onClick = {
                                startChapterIndex = 0
                                endChapterIndex = 9.coerceAtMost(totalChapters - 1)
                            },
                            label = { Text("First 10") }
                        )
                    }
                }
            }
        }

        // Partition & Output Mode
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "3. MP3 Partition & Format",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = splitMode == AudioExportEngine.SplitMode.BY_CHAPTER,
                            onClick = { splitMode = AudioExportEngine.SplitMode.BY_CHAPTER },
                            label = { Text("By Chapter") }
                        )
                        FilterChip(
                            selected = splitMode == AudioExportEngine.SplitMode.BY_MINUTES,
                            onClick = { splitMode = AudioExportEngine.SplitMode.BY_MINUTES },
                            label = { Text("By Minutes") }
                        )
                        FilterChip(
                            selected = splitMode == AudioExportEngine.SplitMode.SINGLE_FILE,
                            onClick = { splitMode = AudioExportEngine.SplitMode.SINGLE_FILE },
                            label = { Text("Single File") }
                        )
                    }

                    if (splitMode == AudioExportEngine.SplitMode.BY_MINUTES) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Max Duration: $maxMinutes minutes per MP3", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = maxMinutes.toFloat(),
                            onValueChange = { maxMinutes = it.toInt() },
                            valueRange = 5f..60f,
                            steps = 10
                        )
                    } else if (splitMode == AudioExportEngine.SplitMode.BY_CHAPTER) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Creates individual MP3 per chapter + an .m3u playlist file in Downloads/BookReader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Creates one continuous large MP3 file for all selected chapters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Reading Speed Picker
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "4. Reading Speed: ${String.format(Locale.US, "%.2f", generatorSpeechRate)}x",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { rate ->
                            FilterChip(
                                selected = kotlin.math.abs(generatorSpeechRate - rate) < 0.05f,
                                onClick = { generatorSpeechRate = rate },
                                label = { Text("${rate}x") }
                            )
                        }
                    }

                    Slider(
                        value = generatorSpeechRate,
                        onValueChange = { generatorSpeechRate = it },
                        valueRange = 0.5f..3.0f,
                        steps = 24
                    )
                }
            }
        }

        // Audio Format Picker (MP3 vs WAV)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "5. Audio Format",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = exportAudioFormat == AudioExportEngine.AudioExportFormat.MP3,
                            onClick = { exportAudioFormat = AudioExportEngine.AudioExportFormat.MP3 },
                            label = { Text("MP3 (Compressed)") }
                        )
                        FilterChip(
                            selected = exportAudioFormat == AudioExportEngine.AudioExportFormat.WAV,
                            onClick = { exportAudioFormat = AudioExportEngine.AudioExportFormat.WAV },
                            label = { Text("WAV (Fast Export)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (exportAudioFormat == AudioExportEngine.AudioExportFormat.WAV) {
                            "Lossless WAV bypasses MP3 encoding for near-instant rendering."
                        } else {
                            "MP3 compresses audio into smaller files, requiring an encoding step."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Button
        item {
            Button(
                onClick = {
                    val novel = selectedNovel ?: return@Button
                    onDismissProgressLocally()
                    AudioExportService.startExportNovel(
                        context = context,
                        novelId = novel.id,
                        startChap = startChapterIndex,
                        endChap = endChapterIndex,
                        splitMode = splitMode,
                        maxMinutes = maxMinutes,
                        speed = generatorSpeechRate,
                        audioFormat = exportAudioFormat
                    )
                    onSwitchToLibrary()
                },
                enabled = selectedNovel != null && totalChapters > 0 && exportState !is AudioExportService.ExportState.Running,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Audiotrack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Audio Conversion",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
