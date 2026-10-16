package com.novelarrow.reader.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel

@Composable
fun NovelFolderShareDialog(
    novel: Novel,
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onFuseAndShare: () -> Unit,
    onZipAndShare: () -> Unit,
    onAudifyAndShare: (startChap: Int, endChap: Int, speed: Float, splitMode: AudioExportEngine.SplitMode) -> Unit
) {
    var showAudifyRangePicker by remember { mutableStateOf(false) }
    var startChap by remember { mutableIntStateOf(0) }
    var endChap by remember { mutableIntStateOf((chapters.size - 1).coerceAtLeast(0)) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var splitMode by remember { mutableStateOf(AudioExportEngine.SplitMode.BY_CHAPTER) }

    val fusionType = remember(chapters) { FileFusionManager.detectFusionType(chapters) }
    val fusionLabel = when (fusionType) {
        FileFusionManager.FusionType.HTML -> "Fuse into Single HTML (.html)"
        FileFusionManager.FusionType.TXT -> "Fuse into Single Text (.txt)"
        FileFusionManager.FusionType.MD -> "Fuse into Single Markdown (.md)"
        FileFusionManager.FusionType.MIXED -> "Fuse into Single Document (.html)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showAudifyRangePicker) "Audify to MP3: Chapter Range" else "Share Novel: ${novel.title}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            if (!showAudifyRangePicker) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how you want to share this novel (${chapters.size} chapters):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        onClick = {
                            onDismiss()
                            onFuseAndShare()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = fusionLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Combines all chapters in order into one consolidated readable file", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        onClick = {
                            onDismiss()
                            onZipAndShare()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Zip Folder & Send (.zip)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Compresses all chapter files into a standard archive", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        onClick = { showAudifyRangePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Audify into MP3 & Send", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Synthesizes chapters to audio with custom range & speed", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pick the chapter range to convert (${endChap - startChap + 1} chapters):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("From Ch. ${startChap + 1}", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = startChap.toFloat(),
                                onValueChange = { startChap = it.toInt().coerceAtMost(endChap) },
                                valueRange = 0f..(chapters.size - 1).coerceAtLeast(0).toFloat()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("To Ch. ${endChap + 1}", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = endChap.toFloat(),
                                onValueChange = { endChap = it.toInt().coerceAtLeast(startChap) },
                                valueRange = 0f..(chapters.size - 1).coerceAtLeast(0).toFloat()
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Reading Speed: ${String.format("%.2f", speechRate)}x", style = MaterialTheme.typography.labelSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { r ->
                            FilterChip(
                                selected = kotlin.math.abs(speechRate - r) < 0.05f,
                                onClick = { speechRate = r },
                                label = { Text("${r}x", fontSize = 11.sp) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Partition Format", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = splitMode == AudioExportEngine.SplitMode.BY_CHAPTER,
                            onClick = { splitMode = AudioExportEngine.SplitMode.BY_CHAPTER },
                            label = { Text("By Chapter", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = splitMode == AudioExportEngine.SplitMode.SINGLE_FILE,
                            onClick = { splitMode = AudioExportEngine.SplitMode.SINGLE_FILE },
                            label = { Text("Single MP3", fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showAudifyRangePicker) {
                Button(
                    onClick = {
                        onDismiss()
                        onAudifyAndShare(startChap, endChap, speechRate, splitMode)
                    }
                ) {
                    Text("Start Audifying")
                }
            }
        },
        dismissButton = {
            if (showAudifyRangePicker) {
                TextButton(onClick = { showAudifyRangePicker = false }) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun ChapterShareDialog(
    chapter: Chapter,
    onDismiss: () -> Unit,
    onShareDirectly: () -> Unit,
    onAudifyAndShare: (speed: Float) -> Unit
) {
    var showSpeedPicker by remember { mutableStateOf(false) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share Chapter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            if (!showSpeedPicker) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        onClick = {
                            onDismiss()
                            onShareDirectly()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Share File Directly", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Sends the chapter file in its original format", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        onClick = { showSpeedPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Audify to MP3 & Send", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Converts this chapter into an audio MP3 file before sending", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Reading Speed: ${String.format("%.2f", speechRate)}x", style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { r ->
                            FilterChip(
                                selected = kotlin.math.abs(speechRate - r) < 0.05f,
                                onClick = { speechRate = r },
                                label = { Text("${r}x") }
                            )
                        }
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = { speechRate = it },
                        valueRange = 0.5f..3.0f,
                        steps = 24
                    )
                }
            }
        },
        confirmButton = {
            if (showSpeedPicker) {
                Button(
                    onClick = {
                        onDismiss()
                        onAudifyAndShare(speechRate)
                    }
                ) {
                    Text("Audify to MP3")
                }
            }
        },
        dismissButton = {
            if (showSpeedPicker) {
                TextButton(onClick = { showSpeedPicker = false }) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
