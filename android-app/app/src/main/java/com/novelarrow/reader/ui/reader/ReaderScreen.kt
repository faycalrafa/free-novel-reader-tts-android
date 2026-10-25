package com.novelarrow.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.data.model.isTextBased
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.common.AppLoadingIndicator
import com.novelarrow.reader.ui.reader.components.ReaderTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToBrowser: (createNewTab: Boolean) -> Unit,
    onOpenDrawer: () -> Unit = {},
    onAddFolder: () -> Unit = {},
    onNavigateToVoiceEngine: (() -> Unit)? = null,
    onNavigateToPronunciationRules: (() -> Unit)? = null,
    onNavigateToSkipWords: (() -> Unit)? = null,
    onNavigateToHeaderFooterSkips: (() -> Unit)? = null,
    onNavigateToContentSelector: (() -> Unit)? = null,
    onOpenAudioStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showUnifiedAudioSettings by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val progressText = if (uiState.chapters.isNotEmpty()) {
                val chapIdx = (uiState.currentChapter?.orderIndex ?: 0) + 1
                val base = "$chapIdx/${uiState.chapters.size}"
                val totalParas = uiState.paragraphs.size
                if (totalParas > 0) {
                    val curPara = (uiState.currentParagraphIndex + 1).coerceIn(1, totalParas)
                    val pct = ((curPara.toFloat() / totalParas.toFloat()) * 100).toInt().coerceIn(0, 100)
                    "$base • ¶ $curPara/$totalParas ($pct%)"
                } else {
                    base
                }
            } else {
                ""
            }
            ReaderTopBar(
                progressText = progressText,
                isTtsDockVisible = uiState.isTtsDockVisible,
                isTextChapter = uiState.currentChapter?.isTextBased() == true,
                currentParagraphIndex = uiState.currentParagraphIndex,
                totalParagraphs = uiState.paragraphs.size,
                onSeekParagraph = { viewModel.seekToParagraph(it) },
                onMenuClick = onOpenDrawer,
                onTtsToggleClick = {
                    viewModel.toggleTtsDock()
                },
                onRawCodeClick = { viewModel.openRawChapterEditor() },
                onSettingsClick = { showSettingsDialog = true }
            )
        },
        floatingActionButton = {
            val hasNext = uiState.currentChapter != null &&
                    (uiState.currentChapter?.orderIndex ?: 0) < uiState.chapters.size - 1
            if (hasNext && !uiState.isTtsDockVisible) {
                FloatingActionButton(
                    onClick = { viewModel.nextChapter() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter")
                }
            }
        },
        bottomBar = {
            val playbackProgress by AudioPlaybackCoordinator.playbackProgressState.collectAsState()
            val isBrowserPlaying = playbackProgress.source == PlaybackSource.BROWSER
            val isAudioFilePlaying = playbackProgress.source == PlaybackSource.AUDIO_FILE

            AnimatedVisibility(
                visible = uiState.isTtsDockVisible || isBrowserPlaying || isAudioFilePlaying || uiState.flashHandleRed,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                if (isAudioFilePlaying) {
                    TtsDock(
                        currentParagraph = playbackProgress.currentParagraphIndex,
                        totalParagraphs = playbackProgress.totalParagraphs,
                        currentChapterIndex = 0,
                        totalChapters = 1,
                        isPlaying = playbackProgress.isPlaying,
                        speechRate = uiState.settings.speechRate,
                        sleepTimerMinutes = uiState.sleepTimerMinutes,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        onPlayPauseClick = { AudioPlaybackCoordinator.togglePlayPause() },
                        onPrevChapterClick = { AudioPlaybackCoordinator.previousParagraph() },
                        onNextChapterClick = { AudioPlaybackCoordinator.nextParagraph() },
                        onSleepTimerClick = { showSleepTimerDialog = true },
                        onSpeedClick = { AudioPlaybackCoordinator.cycleSpeechRate() },
                        onSeekParagraph = { paraIdx -> AudioPlaybackCoordinator.seekToParagraph(paraIdx) },
                        isBrowserMode = false,
                        onOpenAudioSettings = { showUnifiedAudioSettings = true },
                        isAudioFileMode = true,
                        audioFilePositionMs = playbackProgress.audioFilePositionMs,
                        audioFileDurationMs = playbackProgress.audioFileDurationMs,
                        onSeekAudioPosition = { posMs -> AudioPlaybackCoordinator.seekAudioFile(posMs) },
                        onCloseClick = { AudioPlaybackCoordinator.stop() }
                    )
                } else if (isBrowserPlaying) {
                    TtsDock(
                        currentParagraph = playbackProgress.currentParagraphIndex,
                        totalParagraphs = playbackProgress.totalParagraphs,
                        currentChapterIndex = 0,
                        totalChapters = 1,
                        isPlaying = playbackProgress.isPlaying,
                        speechRate = uiState.settings.speechRate,
                        sleepTimerMinutes = uiState.sleepTimerMinutes,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        onPlayPauseClick = { AudioPlaybackCoordinator.togglePlayPause() },
                        onPrevChapterClick = { AudioPlaybackCoordinator.previousChapter() },
                        onNextChapterClick = { AudioPlaybackCoordinator.nextChapter() },
                        onSleepTimerClick = { showSleepTimerDialog = true },
                        onSpeedClick = { AudioPlaybackCoordinator.cycleSpeechRate() },
                        onSeekParagraph = { paraIdx -> AudioPlaybackCoordinator.seekToParagraph(paraIdx) },
                        isBrowserMode = true,
                        onOpenAudioSettings = { showUnifiedAudioSettings = true },
                        onCloseClick = { AudioPlaybackCoordinator.stop() }
                    )
                } else {
                    TtsDock(
                        currentParagraph = uiState.currentParagraphIndex,
                        totalParagraphs = uiState.paragraphs.size,
                        currentChapterIndex = uiState.currentChapter?.orderIndex ?: 0,
                        totalChapters = uiState.chapters.size,
                        isPlaying = uiState.isPlaying,
                        speechRate = uiState.settings.speechRate,
                        sleepTimerMinutes = uiState.sleepTimerMinutes,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onPrevChapterClick = { viewModel.previousChapter() },
                        onNextChapterClick = { viewModel.nextChapter() },
                        onSleepTimerClick = { showSleepTimerDialog = true },
                        onSpeedClick = { viewModel.cycleSpeechRate() },
                        onSeekParagraph = { paraIdx -> viewModel.speakAtParagraph(paraIdx) },
                        isBrowserMode = false,
                        onOpenAudioSettings = { showUnifiedAudioSettings = true },
                        onCloseClick = { viewModel.stopTts() },
                        activeRuleImpacts = uiState.activeRuleImpacts,
                        onToggleRule = { rule, enabled -> viewModel.toggleActiveRule(rule, enabled) },
                        flashHandleRed = uiState.flashHandleRed,
                        onResetFlashHandle = { viewModel.resetFlashHandleRed() }
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.currentNovel == null || uiState.paragraphs.isEmpty()) {
                EmptyState(
                    novels = uiState.novels,
                    lastActiveNovelId = uiState.lastActiveNovelId,
                    onOpenFolder = onAddFolder,
                    onLoadSampleNovel = { viewModel.loadSampleNovel() },
                    onResumeNovel = { viewModel.selectNovel(it) },
                    onOpenDrawer = onOpenDrawer,
                    onOpenBrowser = { onNavigateToBrowser(false) }
                )
            } else {
                ReaderContentView(
                    paragraphs = uiState.paragraphs,
                    currentIndex = uiState.currentParagraphIndex,
                    isPlaying = uiState.isPlaying,
                    fontSizeSp = uiState.settings.fontSizeSp,
                    themeMode = uiState.settings.theme,
                    autoScroll = uiState.settings.autoScroll,
                    chapterTitle = uiState.currentChapter?.title ?: "Chapter",
                    speechRate = uiState.settings.speechRate,
                    onOpenAudioStudio = onOpenAudioStudio,
                    onParagraphClick = { index -> viewModel.speakAtParagraph(index) }
                )
            }

            if (uiState.isLoading && !uiState.isLibraryScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppLoadingIndicator(modifier = Modifier.size(72.dp))
                }
            }
        }
    }

    ReaderDialogs(
        uiState = uiState,
        viewModel = viewModel,
        showSettingsDialog = showSettingsDialog,
        onDismissSettingsDialog = { showSettingsDialog = false },
        showSleepTimerDialog = showSleepTimerDialog,
        onDismissSleepTimerDialog = { showSleepTimerDialog = false },
        showUnifiedAudioSettings = showUnifiedAudioSettings,
        onDismissUnifiedAudioSettings = { showUnifiedAudioSettings = false },
        onNavigateToVoiceEngine = onNavigateToVoiceEngine,
        onNavigateToPronunciationRules = onNavigateToPronunciationRules,
        onNavigateToSkipWords = onNavigateToSkipWords,
        onNavigateToHeaderFooterSkips = onNavigateToHeaderFooterSkips,
        onNavigateToContentSelector = onNavigateToContentSelector
    )
}
