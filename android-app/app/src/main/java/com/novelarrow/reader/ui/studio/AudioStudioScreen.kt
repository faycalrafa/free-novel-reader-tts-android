package com.novelarrow.reader.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.playback.AudioExportService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioScreen(
    novels: List<Novel>,
    currentNovel: Novel?,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }

    val exportState by AudioExportService.exportProgressState.collectAsState()

    var selectedNovel by remember(currentNovel, novels) {
        mutableStateOf(currentNovel ?: novels.firstOrNull())
    }
    var novelDropdownExpanded by remember { mutableStateOf(false) }

    val totalChapters = selectedNovel?.chapterCount ?: 0
    var startChapterIndex by remember(selectedNovel) { mutableIntStateOf(0) }
    var endChapterIndex by remember(selectedNovel) {
        mutableIntStateOf((totalChapters - 1).coerceAtLeast(0))
    }

    var splitMode by remember { mutableStateOf(AudioExportEngine.SplitMode.BY_CHAPTER) }
    var maxMinutes by remember { mutableIntStateOf(30) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }

    var isProgressDismissedLocally by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Library Menu",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Text to MP3 Studio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Export Progress Card (if actively exporting and not hidden)
            if (exportState is AudioExportService.ExportState.Running && !isProgressDismissedLocally) {
                val runningState = exportState as AudioExportService.ExportState.Running
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Audifying in Progress",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = "${runningState.percent}%",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { runningState.percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = runningState.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isProgressDismissedLocally = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Hide / Run in Background")
                                }
                                Button(
                                    onClick = { AudioExportService.cancelExport(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
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
                                text = "Saved ${success.files.size} file(s) into Downloads/BookReader. Check your system notification to share or view.",
                                style = MaterialTheme.typography.bodySmall
                            )
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
                                                    Text(novel.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${novel.chapterCount} chapters", style = MaterialTheme.typography.bodySmall)
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
                                label = { Text("All (${totalChapters})") }
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
                                text = "4. Reading Speed: ${String.format("%.2f", speechRate)}x",
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
                                    selected = kotlin.math.abs(speechRate - rate) < 0.05f,
                                    onClick = { speechRate = rate },
                                    label = { Text("${rate}x") }
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
            }

            // Action Button
            item {
                Button(
                    onClick = {
                        val novel = selectedNovel ?: return@Button
                        isProgressDismissedLocally = false
                        AudioExportService.startExportNovel(
                            context = context,
                            novelId = novel.id,
                            startChap = startChapterIndex,
                            endChap = endChapterIndex,
                            splitMode = splitMode,
                            maxMinutes = maxMinutes,
                            speed = speechRate
                        )
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
                        text = "Generate MP3s (${endChapterIndex - startChapterIndex + 1} Chapters)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
