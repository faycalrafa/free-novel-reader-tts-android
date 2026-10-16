package com.novelarrow.reader.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.novelarrow.reader.ui.common.AppLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.export.AudioFileItem
import com.novelarrow.reader.data.export.AudioFolderItem
import com.novelarrow.reader.data.export.AudioLibraryContent
import com.novelarrow.reader.data.export.AudioLibraryScanner
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.TtsDock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AudioStudioMode {
    LIBRARY,
    GENERATOR
}

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
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(AudioStudioMode.LIBRARY) }

    BackHandler {
        if (currentMode == AudioStudioMode.GENERATOR) {
            currentMode = AudioStudioMode.LIBRARY
        } else {
            onNavigateBack()
        }
    }

    val exportState by AudioExportService.exportProgressState.collectAsState()
    val playbackProgress by AudioPlaybackCoordinator.playbackProgressState.collectAsState()
    val coordinatorSpeechRate by AudioPlaybackCoordinator.currentSpeechRateState.collectAsState()
    val sleepTimerMinutes by AudioPlaybackCoordinator.sleepTimerMinutesState.collectAsState()
    val sleepTimerRemainingSeconds by AudioPlaybackCoordinator.sleepTimerRemainingSecondsState.collectAsState()

    var libraryContent by remember { mutableStateOf(AudioLibraryContent()) }
    var isScanning by remember { mutableStateOf(false) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }

    fun refreshLibrary() {
        isScanning = true
        scope.launch(Dispatchers.IO) {
            val content = AudioLibraryScanner.scan(context)
            withContext(Dispatchers.Main) {
                libraryContent = content
                isScanning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLibrary()
    }

    // Refresh library when export successfully finishes
    LaunchedEffect(exportState) {
        if (exportState is AudioExportService.ExportState.Success) {
            refreshLibrary()
        }
    }

    // Generator Form State
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
    var generatorSpeechRate by remember { mutableFloatStateOf(1.0f) }
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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentMode == AudioStudioMode.GENERATOR) {
                        IconButton(onClick = { currentMode = AudioStudioMode.LIBRARY }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Audio Library",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Navigation Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentMode == AudioStudioMode.GENERATOR) "Audio Generator" else "Audio Studio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (currentMode == AudioStudioMode.LIBRARY) {
                        IconButton(onClick = { refreshLibrary() }) {
                            if (isScanning) {
                                AppLoadingIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh Audio Library",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(onClick = { currentMode = AudioStudioMode.GENERATOR }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Generate Audio",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentMode == AudioStudioMode.LIBRARY && !libraryContent.isEmpty) {
                FloatingActionButton(
                    onClick = { currentMode = AudioStudioMode.GENERATOR },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            if (playbackProgress.source != PlaybackSource.NONE) {
                TtsDock(
                    currentParagraph = playbackProgress.currentParagraphIndex,
                    totalParagraphs = playbackProgress.totalParagraphs,
                    currentChapterIndex = playbackProgress.chapterIndex,
                    totalChapters = playbackProgress.totalChapters,
                    isPlaying = playbackProgress.isPlaying,
                    speechRate = coordinatorSpeechRate,
                    sleepTimerMinutes = sleepTimerMinutes,
                    sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                    onPlayPauseClick = { AudioPlaybackCoordinator.togglePlayPause() },
                    onPrevChapterClick = {
                        if (playbackProgress.source == PlaybackSource.AUDIO_FILE) {
                            AudioPlaybackCoordinator.previousParagraph()
                        } else {
                            AudioPlaybackCoordinator.previousChapter()
                        }
                    },
                    onNextChapterClick = {
                        if (playbackProgress.source == PlaybackSource.AUDIO_FILE) {
                            AudioPlaybackCoordinator.nextParagraph()
                        } else {
                            AudioPlaybackCoordinator.nextChapter()
                        }
                    },
                    onSleepTimerClick = { AudioPlaybackCoordinator.cycleSleepTimer() },
                    onSpeedClick = { AudioPlaybackCoordinator.cycleSpeechRate() },
                    onSeekParagraph = { target -> AudioPlaybackCoordinator.seekToParagraph(target) },
                    isBrowserMode = (playbackProgress.source == PlaybackSource.BROWSER),
                    onOpenAudioSettings = { showAudioSettingsDialog = true },
                    isAudioFileMode = (playbackProgress.source == PlaybackSource.AUDIO_FILE),
                    audioFilePositionMs = playbackProgress.audioFilePositionMs,
                    audioFileDurationMs = playbackProgress.audioFileDurationMs,
                    onSeekAudioPosition = { posMs -> AudioPlaybackCoordinator.seekAudioFile(posMs) },
                    onCloseClick = { AudioPlaybackCoordinator.stop() }
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentMode == AudioStudioMode.LIBRARY) {
                // LIBRARY MODE
                if (libraryContent.isEmpty && !isScanning) {
                    // Empty State View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No Audio Files Found",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Convert novels and chapters to MP3 audio files to listen offline anytime with natural Text-to-Speech.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { currentMode = AudioStudioMode.GENERATOR },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Audio", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Folders Category ordered based on parent folder in the library
                    val orderedFolders = remember(libraryContent.folders, novels) {
                        val novelTitleOrder = novels.map { it.title.trim().lowercase() }
                        libraryContent.folders.sortedWith { f1, f2 ->
                            val idx1 = novelTitleOrder.indexOf(f1.name.trim().lowercase())
                            val idx2 = novelTitleOrder.indexOf(f2.name.trim().lowercase())
                            when {
                                idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
                                idx1 != -1 -> -1
                                idx2 != -1 -> 1
                                else -> com.novelarrow.reader.data.util.NaturalOrderComparator.compare(f1.name, f2.name)
                            }
                        }
                    }

                    // Audio Library List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Live Export Progress Card if running
                        if (exportState is AudioExportService.ExportState.Running && !isProgressDismissedLocally) {
                            val runningState = exportState as AudioExportService.ExportState.Running
                            item {
                                ExportProgressCard(
                                    runningState = runningState,
                                    onHide = { isProgressDismissedLocally = true },
                                    onCancel = { AudioExportService.cancelExport(context) }
                                )
                            }
                        }

                        if (orderedFolders.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Audio Folders (${orderedFolders.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            items(orderedFolders, key = { it.folder.absolutePath }) { folderItem ->
                                FolderAudioCard(
                                    folder = folderItem,
                                    isPlayingThis = (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.novelTitle == folderItem.name && playbackProgress.isPlaying),
                                    onPlayFolder = { AudioPlaybackCoordinator.playAudioFolder(folderItem.folder) }
                                )
                            }
                        }

                        // Standalone Tracks Category
                        if (libraryContent.standaloneFiles.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Standalone Tracks (${libraryContent.standaloneFiles.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            items(libraryContent.standaloneFiles, key = { it.file.absolutePath }) { fileItem ->
                                FileAudioCard(
                                    file = fileItem,
                                    isPlayingThis = (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name && playbackProgress.isPlaying),
                                    onPlayFile = { AudioPlaybackCoordinator.playAudioFile(fileItem.file) }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            } else {
                // GENERATOR MODE
                LazyColumn(
                    modifier = Modifier
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
                                onHide = { isProgressDismissedLocally = true },
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
                                        onClick = { currentMode = AudioStudioMode.LIBRARY },
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
                                        text = "4. Reading Speed: ${String.format(java.util.Locale.US, "%.2f", generatorSpeechRate)}x",
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
                                    speed = generatorSpeechRate
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
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAudioSettingsDialog) {
        val settings = AudioPlaybackCoordinator.settingsRepository?.getSettings()
            ?: com.novelarrow.reader.data.model.ReaderSettings()
        val availableVoices by AudioPlaybackCoordinator.availableVoicesState.collectAsState()

        UnifiedAudioSettingsDialog(
            speechRate = coordinatorSpeechRate,
            speechPitch = AudioPlaybackCoordinator.currentSpeechPitch,
            selectedVoiceName = AudioPlaybackCoordinator.currentVoiceName,
            availableVoices = availableVoices,
            sleepTimerMinutes = sleepTimerMinutes,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
            pronunciationRules = settings.pronunciationRules,
            skipWordsEnabled = settings.skipWordsEnabled,
            skipWords = settings.skipWords,
            headerSkipRulesEnabled = settings.headerSkipEnabled,
            headerSkipRules = settings.headerSkipRules,
            footerSkipRulesEnabled = settings.footerSkipEnabled,
            footerSkipRules = settings.footerSkipRules,
            contentSelectorEnabled = settings.contentSelectorEnabled,
            contentSelector = settings.contentSelector,
            onSpeechRateChange = { AudioPlaybackCoordinator.updateSpeechRate(it) },
            onSpeechPitchChange = { AudioPlaybackCoordinator.updateSpeechPitch(it) },
            onVoiceChange = { AudioPlaybackCoordinator.updateVoice(it) },
            onSleepTimerChange = { AudioPlaybackCoordinator.setSleepTimer(it) },
            onPronunciationRulesEnabledChange = {},
            onAddPronunciationRule = { _, _, _ -> },
            onRemovePronunciationRule = {},
            onSkipWordsEnabledChange = {},
            onAddSkipWord = { _, _ -> },
            onRemoveSkipWord = {},
            onDismiss = { showAudioSettingsDialog = false }
        )
    }
}

@Composable
private fun ExportProgressCard(
    runningState: AudioExportService.ExportState.Running,
    onHide: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLoadingIndicator(modifier = Modifier.size(22.dp))
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

            // Granular Chapter & Paragraph Progression
            if (runningState.totalChapters > 0 || runningState.totalParagraphs > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (runningState.totalChapters > 0) {
                        Text(
                            text = "Chapter ${runningState.currentChapter} / ${runningState.totalChapters}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (runningState.totalParagraphs > 0) {
                        Text(
                            text = "Paragraph ${runningState.currentParagraph} / ${runningState.totalParagraphs}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

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
                    onClick = onHide,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hide", maxLines = 1)
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FolderAudioCard(
    folder: AudioFolderItem,
    isPlayingThis: Boolean,
    onPlayFolder: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isPlayingThis) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayFolder() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isPlayingThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.fileCount} tracks • ${folder.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlayFolder) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play folder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun FileAudioCard(
    file: AudioFileItem,
    isPlayingThis: Boolean,
    onPlayFile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPlayingThis) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isPlayingThis) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayFile() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPlayingThis) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = if (isPlayingThis) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${file.formattedSize} • MP3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlayFile) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play file",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
