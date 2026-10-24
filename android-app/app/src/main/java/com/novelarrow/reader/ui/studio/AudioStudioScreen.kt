package com.novelarrow.reader.ui.studio

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.core.content.FileProvider
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
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
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.TtsDock
import com.novelarrow.reader.util.AppLogger
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
    onNavigateToVoiceEngine: (() -> Unit)? = null,
    onNavigateToPronunciationRules: (() -> Unit)? = null,
    onNavigateToSkipWords: (() -> Unit)? = null,
    onNavigateToHeaderFooterSkips: (() -> Unit)? = null,
    onNavigateToContentSelector: (() -> Unit)? = null,
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
    var isProgressDismissedLocally by remember { mutableStateOf(false) }

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
                AudioStudioLibraryTab(
                    libraryContent = libraryContent,
                    novels = novels,
                    isScanning = isScanning,
                    exportState = exportState,
                    isProgressDismissedLocally = isProgressDismissedLocally,
                    onDismissProgressLocally = { isProgressDismissedLocally = true },
                    playbackProgress = playbackProgress,
                    onNavigateToGenerator = { currentMode = AudioStudioMode.GENERATOR },
                    onRefreshLibrary = { refreshLibrary() }
                )
            } else {
                AudioStudioGeneratorTab(
                    novels = novels,
                    currentNovel = currentNovel,
                    exportState = exportState,
                    isProgressDismissedLocally = isProgressDismissedLocally,
                    onDismissProgressLocally = { isProgressDismissedLocally = true },
                    onSwitchToLibrary = { currentMode = AudioStudioMode.LIBRARY }
                )
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
            onNavigateToVoiceEngine = {
                showAudioSettingsDialog = false
                onNavigateToVoiceEngine?.invoke()
            },
            onNavigateToPronunciationRules = {
                showAudioSettingsDialog = false
                onNavigateToPronunciationRules?.invoke()
            },
            onNavigateToSkipWords = {
                showAudioSettingsDialog = false
                onNavigateToSkipWords?.invoke()
            },
            onNavigateToHeaderFooterSkips = {
                showAudioSettingsDialog = false
                onNavigateToHeaderFooterSkips?.invoke()
            },
            onNavigateToContentSelector = {
                showAudioSettingsDialog = false
                onNavigateToContentSelector?.invoke()
            },
            onDismiss = { showAudioSettingsDialog = false }
        )
    }
}

