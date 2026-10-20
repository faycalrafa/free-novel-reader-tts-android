package com.novelarrow.reader.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.novelarrow.reader.R
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.io.File
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.ui.common.NovelFolderShareDialog
import com.novelarrow.reader.ui.common.ChapterShareDialog
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.common.MonacoCodeEditorDialog
import com.novelarrow.reader.data.model.isTextBased
import com.novelarrow.reader.data.model.detectEditorLanguage
import androidx.compose.material.icons.filled.Code
import com.novelarrow.reader.data.repository.ChapterSortPreset
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import com.novelarrow.reader.data.model.BrowserTab
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.novelarrow.reader.ui.common.AppLoadingIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.theme.AccentBlue
import com.novelarrow.reader.theme.AccentPurple
import com.novelarrow.reader.theme.DarkHighlight
import com.novelarrow.reader.theme.LightHighlight
import com.novelarrow.reader.theme.SepiaHighlight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

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
                    val playbackProgress = AudioPlaybackCoordinator.playbackProgressState.value
                    if (playbackProgress.source == PlaybackSource.BROWSER && playbackProgress.isPlaying) {
                        AudioPlaybackCoordinator.stop()
                    }
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

    if (uiState.isLibraryScanning) {
        LibraryScanningOverlay(
            progress = uiState.scanProgress,
            currentIndex = uiState.scanCurrentIndex,
            totalItems = uiState.scanTotalItems,
            currentItem = uiState.scanCurrentItem
        )
    }

    if (uiState.showRawEditor && uiState.rawChapterContent != null) {
        val chapter = uiState.currentChapter
        MonacoCodeEditorDialog(
            title = chapter?.title ?: "Raw Chapter Code",
            initialContent = uiState.rawChapterContent ?: "",
            language = chapter?.detectEditorLanguage() ?: "html",
            onDismiss = { viewModel.dismissRawEditor() },
            onSave = { newContent -> viewModel.saveRawChapterContent(newContent) }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = uiState.settings,
            onDismiss = { showSettingsDialog = false },
            onThemeChange = { viewModel.updateTheme(it) },
            onFontSizeChange = { viewModel.updateFontSize(it) },
            onAutoScrollChange = { viewModel.updateAutoScroll(it) },
            onAutoNextChange = { viewModel.updateAutoNext(it) },
            onClearAllData = {
                viewModel.clearAllData()
                showSettingsDialog = false
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutes,
            remainingSeconds = uiState.sleepTimerRemainingSeconds,
            isPlaying = uiState.isPlaying,
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { viewModel.setSleepTimer(it) }
        )
    }

    if (showUnifiedAudioSettings) {
        UnifiedAudioSettingsDialog(
            speechRate = uiState.settings.speechRate,
            speechPitch = uiState.settings.speechPitch,
            selectedVoiceName = uiState.settings.selectedVoiceName,
            availableVoices = uiState.availableVoices,
            sleepTimerMinutes = uiState.sleepTimerMinutes,
            sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
            pronunciationRulesEnabled = uiState.settings.pronunciationRulesEnabled,
            pronunciationRules = uiState.settings.pronunciationRules,
            skipWordsEnabled = uiState.settings.skipWordsEnabled,
            skipWords = uiState.settings.skipWords,
            headerSkipRulesEnabled = uiState.settings.headerSkipEnabled,
            headerSkipRules = uiState.settings.headerSkipRules,
            footerSkipRulesEnabled = uiState.settings.footerSkipEnabled,
            footerSkipRules = uiState.settings.footerSkipRules,
            contentSelectorEnabled = uiState.settings.contentSelectorEnabled,
            contentSelector = uiState.settings.contentSelector,
            onSpeechRateChange = { viewModel.updateSpeechRate(it) },
            onSpeechPitchChange = { viewModel.updateSpeechPitch(it) },
            onVoiceChange = { viewModel.updateVoice(it) },
            onSleepTimerChange = { viewModel.setSleepTimer(it) },
            onPronunciationRulesEnabledChange = { viewModel.updatePronunciationRulesEnabled(it) },
            onAddPronunciationRule = { from, to, exact -> viewModel.addPronunciationRule(from, to, exact) },
            onRemovePronunciationRule = { viewModel.removePronunciationRule(it) },
            onSkipWordsEnabledChange = { viewModel.updateSkipWordsEnabled(it) },
            onAddSkipWord = { word, exact -> viewModel.addSkipWord(word, exact) },
            onRemoveSkipWord = { viewModel.removeSkipWord(it) },
            onHeaderSkipRulesEnabledChange = { viewModel.updateHeaderSkipEnabled(it) },
            onAddHeaderSkipRule = { text, exact -> viewModel.addHeaderSkipRule(text, exact) },
            onRemoveHeaderSkipRule = { viewModel.removeHeaderSkipRule(it) },
            onFooterSkipRulesEnabledChange = { viewModel.updateFooterSkipEnabled(it) },
            onAddFooterSkipRule = { text, exact -> viewModel.addFooterSkipRule(text, exact) },
            onRemoveFooterSkipRule = { viewModel.removeFooterSkipRule(it) },
            onContentSelectorEnabledChange = { viewModel.updateContentSelectorEnabled(it) },
            onContentSelectorChange = { viewModel.updateContentSelector(it) },
            onNavigateToPronunciationRules = onNavigateToPronunciationRules?.let { nav ->
                {
                    showUnifiedAudioSettings = false
                    nav()
                }
            },
            onNavigateToSkipWords = onNavigateToSkipWords?.let { nav ->
                {
                    showUnifiedAudioSettings = false
                    nav()
                }
            },
            onNavigateToVoiceEngine = onNavigateToVoiceEngine?.let { nav ->
                {
                    showUnifiedAudioSettings = false
                    nav()
                }
            },
            onNavigateToHeaderFooterSkips = onNavigateToHeaderFooterSkips?.let { nav ->
                {
                    showUnifiedAudioSettings = false
                    nav()
                }
            },
            onNavigateToContentSelector = onNavigateToContentSelector?.let { nav ->
                {
                    showUnifiedAudioSettings = false
                    nav()
                }
            },
            onDismiss = { showUnifiedAudioSettings = false }
        )
    }
}

@Composable
fun ReaderTopBar(
    progressText: String,
    isTtsDockVisible: Boolean,
    isTextChapter: Boolean,
    currentParagraphIndex: Int = 0,
    totalParagraphs: Int = 0,
    onSeekParagraph: (Int) -> Unit = {},
    onMenuClick: () -> Unit,
    onTtsToggleClick: () -> Unit,
    onRawCodeClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Library Drawer", modifier = Modifier.size(20.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (progressText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onTtsToggleClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = "Toggle TTS Dock",
                        tint = if (isTtsDockVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isTextChapter) {
                    IconButton(
                        onClick = onRawCodeClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = "Raw Code Editor (Monaco)",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ReaderContentView(
    paragraphs: List<Paragraph>,
    currentIndex: Int,
    isPlaying: Boolean,
    fontSizeSp: Float,
    themeMode: ThemeMode,
    autoScroll: Boolean,
    chapterTitle: String = "Chapter",
    speechRate: Float = 1.0f,
    onOpenAudioStudio: (() -> Unit)? = null,
    onParagraphClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isSelectionMode = selectedIndices.isNotEmpty()

    LaunchedEffect(currentIndex) {
        if (autoScroll && !isSelectionMode && currentIndex in paragraphs.indices) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -150
            )
        }
    }

    val highlightColor = when (themeMode) {
        ThemeMode.DARK -> DarkHighlight
        ThemeMode.SEPIA -> SepiaHighlight
        ThemeMode.LIGHT -> LightHighlight
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
                val isCurrent = index == currentIndex
                val isSelected = selectedIndices.contains(index)
                val backgroundColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        isCurrent -> highlightColor
                        else -> Color.Transparent
                    },
                    label = "highlight_anim"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                } else {
                                    onParagraphClick(index)
                                }
                            },
                            onLongClick = {
                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrent && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        val textToDisplay = paragraph.spokenText.ifBlank { paragraph.rawText }
                        Text(
                            text = textToDisplay,
                            style = if (paragraph.isHeading) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = (fontSizeSp + 4).sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = (fontSizeSp + 10).sp
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.6f).sp
                                )
                            },
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Bottom space so dock doesn't obscure last lines
            item {
                Spacer(modifier = Modifier.height(140.dp))
            }
        }

        // Floating Selection Bar
        if (isSelectionMode) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIndices.size} selected",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    TextButton(
                        onClick = { selectedIndices = paragraphs.indices.toSet() }
                    ) {
                        Text("Select All", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            val text = selectedIndices.sorted()
                                .mapNotNull { paragraphs.getOrNull(it)?.rawText }
                                .joinToString("\n\n")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Paragraphs", text))
                            Toast.makeText(context, "${selectedIndices.size} paragraphs copied", Toast.LENGTH_SHORT).show()
                            selectedIndices = emptySet()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            val text = selectedIndices.sorted()
                                .mapNotNull { paragraphs.getOrNull(it)?.spokenText?.ifBlank { paragraphs[it].rawText } }
                                .joinToString("\n\n")
                            AudioExportService.startExportText(
                                context = context,
                                title = "${chapterTitle}_selection",
                                text = text,
                                speed = speechRate,
                                audioFormat = AudioExportEngine.AudioExportFormat.WAV,
                                directShare = true
                            )
                            Toast.makeText(context, "Exporting ${selectedIndices.size} paragraphs...", Toast.LENGTH_SHORT).show()
                            selectedIndices = emptySet()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = "Share as MP3",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { selectedIndices = emptySet() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun formatTimeSeconds(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMins = minutes % 60
        "%d:%02d:%02d".format(hours, remMins, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
fun TtsDock(
    currentParagraph: Int,
    totalParagraphs: Int,
    currentChapterIndex: Int = 0,
    totalChapters: Int = 0,
    isPlaying: Boolean,
    speechRate: Float = 1.0f,
    sleepTimerMinutes: Int = 0,
    sleepTimerRemainingSeconds: Int = 0,
    onPlayPauseClick: () -> Unit,
    onPrevChapterClick: () -> Unit,
    onNextChapterClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSeekParagraph: (Int) -> Unit,
    isBrowserMode: Boolean = false,
    onOpenAudioSettings: () -> Unit = {},
    isAudioFileMode: Boolean = false,
    audioFilePositionMs: Long = 0L,
    audioFileDurationMs: Long = 0L,
    onSeekAudioPosition: (Long) -> Unit = {},
    onCloseClick: () -> Unit = {},
    activeRuleImpacts: List<com.novelarrow.reader.data.model.RuleImpact> = emptyList(),
    onToggleRule: (com.novelarrow.reader.data.model.RuleImpact, Boolean) -> Unit = { _, _ -> },
    flashHandleRed: Boolean = false,
    onResetFlashHandle: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragParaIndex by remember(currentParagraph) { mutableFloatStateOf(currentParagraph.toFloat()) }
    var dragAudioMs by remember(audioFilePositionMs) { mutableFloatStateOf(audioFilePositionMs.toFloat()) }
    var showActiveFiltersDialog by remember { mutableStateOf(false) }

    val hasActiveRules = activeRuleImpacts.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "flashRedTransition")
    val redFlashColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFFF1744),
        targetValue = Color(0xFFFF8A80),
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handleFlashColor"
    )

    val heartbeatTransition = rememberInfiniteTransition(label = "filterHeartbeat")
    val heartbeatScale by heartbeatTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatScale"
    )
    val heartbeatColor by heartbeatTransition.animateColor(
        initialValue = Color(0xFFFF9800),
        targetValue = Color(0xFFFF5722),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatColor"
    )

    val maxAudioDuration = audioFileDurationMs.coerceAtLeast(1000L).toFloat()

    val displayPara = if (isDragging) {
        dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
    } else currentParagraph

    val progressPercent = if (totalParagraphs > 0) {
        ((displayPara + 1).toFloat() / totalParagraphs.toFloat() * 100).toInt()
    } else 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Media Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Slider controls progress within current chapter/audio, with active filters, settings gear & close button to right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = if (isAudioFileMode) {
                            if (isDragging) dragAudioMs else audioFilePositionMs.toFloat().coerceIn(0f, maxAudioDuration)
                        } else {
                            if (isDragging) dragParaIndex else currentParagraph.toFloat().coerceIn(0f, (totalParagraphs - 1).coerceAtLeast(1).toFloat())
                        },
                        onValueChange = { newVal ->
                            isDragging = true
                            if (isAudioFileMode) {
                                dragAudioMs = newVal
                            } else {
                                dragParaIndex = newVal
                            }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            if (isAudioFileMode) {
                                onSeekAudioPosition(dragAudioMs.toLong().coerceIn(0L, audioFileDurationMs.coerceAtLeast(0L)))
                            } else {
                                val target = dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
                                onSeekParagraph(target)
                            }
                        },
                        valueRange = if (isAudioFileMode) 0f .. maxAudioDuration else 0f .. (totalParagraphs - 1).coerceAtLeast(1).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Dedicated Active Filters button immediately right of the progress bar
                    IconButton(
                        onClick = {
                            showActiveFiltersDialog = true
                            if (flashHandleRed) onResetFlashHandle()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                val scale = if (hasActiveRules || flashHandleRed) heartbeatScale else 1.0f
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = "Active Rules & Filters",
                            tint = if (flashHandleRed) redFlashColor else if (hasActiveRules) heartbeatColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Settings gear button
                    IconButton(
                        onClick = onOpenAudioSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "TTS and Audio Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Close button to close media bar and stop ongoing playback
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close and stop playback",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Metadata row
                if (isAudioFileMode) {
                    val curMs = if (isDragging) dragAudioMs.toLong() else audioFilePositionMs
                    val curFormatted = formatTimeSeconds(curMs)
                    val totalFormatted = formatTimeSeconds(audioFileDurationMs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDragging) "Release to jump to $curFormatted" else if (totalChapters > 0) "Ch. ${currentChapterIndex + 1} / $totalChapters" else "Audio Studio",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$curFormatted / $totalFormatted",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = if (isBrowserMode) "Live Web TTS" else if (totalChapters > 0) "Ch. ${currentChapterIndex + 1} / $totalChapters" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isDragging) {
                                Text(
                                    text = "Release to jump to ¶ ${displayPara + 1} ($progressPercent%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Text(
                            text = "¶ ${displayPara + 1} / $totalParagraphs ($progressPercent%)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Control buttons row:
                // [Sleep Timer] [Prev] [Play/Pause] [Next] [Speed]
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sleep Timer button on the left
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSleepTimerClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val timerLabel = if (sleepTimerMinutes <= 0) {
                                    "Off"
                                } else if (sleepTimerRemainingSeconds > 0) {
                                    val mins = sleepTimerRemainingSeconds / 60
                                    val secs = sleepTimerRemainingSeconds % 60
                                    "$mins:${secs.toString().padStart(2, '0')}"
                                } else {
                                    "${sleepTimerMinutes}m"
                                }
                                Text(
                                    text = timerLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }

                        // Prev button directly next to Play/Pause on the left
                        IconButton(
                            onClick = onPrevChapterClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = if (isBrowserMode) "Prev Paragraph" else "Prev Chapter",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play / Pause round gradient button
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(AccentBlue, AccentPurple)
                                    )
                                )
                                .clickable { onPlayPauseClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Next button directly next to Play/Pause on the right
                        IconButton(
                            onClick = onNextChapterClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = if (isBrowserMode) "Next Paragraph" else "Next Chapter",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Speed button on the right
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSpeedClick() }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${"%.2f".format(speechRate).trimEnd('0').trimEnd('.')}x",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActiveFiltersDialog) {
        ActiveFiltersDialog(
            rules = activeRuleImpacts,
            onToggleRule = onToggleRule,
            onDismiss = { showActiveFiltersDialog = false }
        )
    }
}

@Composable
fun ActiveFiltersDialog(
    rules: List<com.novelarrow.reader.data.model.RuleImpact>,
    onToggleRule: (com.novelarrow.reader.data.model.RuleImpact, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Active Rules & Filters (${rules.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No filters or rules are currently applied to this chapter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val sortedRules = remember(rules) {
                    rules.sortedByDescending { it.charsFiltered }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedRules.forEach { rule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = rule.ruleType.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        if (rule.charsFiltered > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val isContentSelector = rule.ruleType == com.novelarrow.reader.data.model.RuleType.CONTENT_SELECTOR ||
                                                    rule.ruleType == com.novelarrow.reader.data.model.RuleType.CONTENT_CONTAINER
                                            val badgeText = if (isContentSelector) "+${rule.charsFiltered} chars" else "-${rule.charsFiltered} chars"
                                            val badgeColor = if (isContentSelector) Color(0xFF4CAF50) else Color(0xFFFF5722)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = badgeColor.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (rule.details.isNotBlank()) {
                                        Text(
                                            text = rule.details,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { isChecked ->
                                        onToggleRule(rule, isChecked)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun EmptyState(
    novels: List<Novel>,
    lastActiveNovelId: String? = null,
    onOpenFolder: () -> Unit,
    onLoadSampleNovel: () -> Unit,
    onResumeNovel: (Novel) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenBrowser: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app_icon_round),
            contentDescription = "BookReader Icon",
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "BookReader Pro",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pick a root folder on your device. Every subfolder becomes its own playlist with individually tracked progress and offline text-to-speech.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenFolder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Set root folder")
        }

        if (novels.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onLoadSampleNovel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Science, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Sample: Quantum Mechanics")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse & Download Web Novels")
            }
        }

        if (novels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Book, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Library (${novels.size})")
            }

            Spacer(modifier = Modifier.height(16.dp))
            val activeProgress by AudioPlaybackCoordinator.playbackProgressState.collectAsState()
            val isPlayingActive = activeProgress.source == PlaybackSource.READER && activeProgress.novelId != null
            val effectiveNovelId = if (isPlayingActive) activeProgress.novelId else lastActiveNovelId
            val lastNovel = novels.firstOrNull { it.id == effectiveNovelId }
                ?: novels.maxByOrNull { it.lastUpdated }
                ?: novels.first()
            val effectiveChapterIndex = if (isPlayingActive && activeProgress.novelId == lastNovel.id) {
                activeProgress.chapterIndex
            } else {
                lastNovel.currentChapterIndex
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "CONTINUE READING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastNovel.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Chapter ${effectiveChapterIndex + 1} / ${lastNovel.chapterCount}" +
                            if (isPlayingActive && activeProgress.novelId == lastNovel.id && activeProgress.chapterTitle.isNotBlank()) " • ${activeProgress.chapterTitle}" else "",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onResumeNovel(lastNovel) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resume")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryDrawerContent(
    novels: List<Novel>,
    currentNovel: Novel?,
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    browserTabs: List<BrowserTab> = emptyList(),
    currentTabId: String = "",
    installedVersionName: String,
    onSelectNovel: (Novel) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onDeleteNovel: (Novel, deleteFromDisk: Boolean) -> Unit,
    onDeleteChapter: (Chapter, deleteFromDisk: Boolean) -> Unit = { _, _ -> },
    onMoveChapter: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onSortChapters: (ChapterSortPreset) -> Unit = {},
    onToggleChapterReadStatus: (Chapter) -> Unit = {},
    onAddFolder: () -> Unit,
    onRefreshFolders: () -> Unit,
    onOpenBrowser: (createNewTab: Boolean) -> Unit,
    onSelectTab: (String) -> Unit = {},
    onNewTab: () -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onOpenDevLogs: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onArchiveNovel: (Novel) -> Unit = {},
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeActionNovelId by remember { mutableStateOf<String?>(null) }
    var activeActionChapterId by remember { mutableStateOf<String?>(null) }
    var sharingNovel by remember { mutableStateOf<Novel?>(null) }
    var sharingChapter by remember { mutableStateOf<Chapter?>(null) }
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }
    var deleteFilesFromDisk by remember { mutableStateOf(false) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var deleteChapterFromDisk by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val chapterListState = rememberLazyListState()
    val activeChapterIndex = remember(chapters, currentChapter, currentNovel) {
        val idx = chapters.indexOfFirst { it.id == currentChapter?.id }
        if (idx >= 0) idx else (currentNovel?.currentChapterIndex ?: 0)
    }
    LaunchedEffect(selectedTab, activeChapterIndex) {
        activeActionNovelId = null
        activeActionChapterId = null
        if (selectedTab == 1 && activeChapterIndex in chapters.indices) {
            chapterListState.animateScrollToItem((activeChapterIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row {
                IconButton(onClick = onOpenAudioStudio) {
                    Icon(Icons.Default.Audiotrack, contentDescription = "Text to MP3")
                }
                IconButton(onClick = onOpenArchive) {
                    Icon(Icons.Default.Archive, contentDescription = "Archived Novels")
                }
                IconButton(onClick = onAddFolder) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import Folder")
                }
                IconButton(onClick = onRefreshFolders) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh linked folders")
                }
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }

        // Tabs: Library vs Chapters
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Library (${novels.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Chapters (${chapters.size})") }
            )
        }

        HorizontalDivider()

        if (selectedTab == 0) {
            Column(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (novels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Book,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No novels in library yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onAddFolder,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Import Folder")
                                    }
                                }
                            }
                        }
                    } else {
                        items(novels.size) { i ->
                            val novel = novels[i]
                            val isCurrent = novel.id == currentNovel?.id

                            val isActionsVisible = activeActionNovelId == novel.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (activeActionNovelId != null) {
                                                activeActionNovelId = null
                                            } else {
                                                onSelectNovel(novel)
                                                selectedTab = 1
                                            }
                                        },
                                        onLongClick = {
                                            activeActionNovelId = if (activeActionNovelId == novel.id) null else novel.id
                                        }
                                    )
                                    .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = novel.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Text(
                                        text = "${novel.chapterCount} chapters • ${novel.currentChapterIndex + 1}/${novel.chapterCount}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                if (isActionsVisible) {
                                    IconButton(
                                        onClick = {
                                            activeActionNovelId = null
                                            sharingNovel = novel
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share novel",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            activeActionNovelId = null
                                            onArchiveNovel(novel)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Archive,
                                            contentDescription = "Archive novel",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            novelToDelete = novel
                                            deleteFilesFromDisk = false
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }

                    // Demarking Line & Browser Tabs Section
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Browser Tabs (${browserTabs.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = onNewTab,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "New Tab",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (browserTabs.isEmpty()) {
                        item {
                            Text(
                                text = "No open tabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(browserTabs.size) { tIdx ->
                            val tab = browserTabs[tIdx]
                            val isSelectedTab = tab.id == currentTabId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTab(tab.id) }
                                    .background(if (isSelectedTab) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isSelectedTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title.ifBlank { "New Tab" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelectedTab) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url.replace("https://", "").replace("http://", ""),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (browserTabs.size > 1) {
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        } else {
            // Chapters list
            Column(modifier = Modifier.weight(1f)) {
                if (currentNovel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentNovel.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
                LazyColumn(
                    state = chapterListState,
                    modifier = Modifier.weight(1f)
                ) {
                    if (chapters.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chapters in current novel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(chapters.size) { i ->
                            val chapter = chapters[i]
                            val isCurrent = if (currentChapter != null) {
                                chapter.id == currentChapter.id
                            } else {
                                i == activeChapterIndex
                            }
                            val isDraggingThis = draggingIndex == i
                            val isTargeted = dragOverIndex == i && !isDraggingThis

                            val isChapterActionsVisible = activeActionChapterId == chapter.id || isDraggingThis

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        if (isDraggingThis) {
                                            translationY = dragOffsetY
                                            shadowElevation = 16f
                                        }
                                    }
                                    .zIndex(if (isDraggingThis) 10f else 0f)
                                    .background(
                                        when {
                                            isDraggingThis -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            isTargeted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (activeActionChapterId != null && activeActionChapterId != chapter.id) {
                                                activeActionChapterId = null
                                            } else {
                                                onSelectChapter(i)
                                            }
                                        },
                                        onLongClick = {
                                            activeActionChapterId = if (activeActionChapterId == chapter.id) null else chapter.id
                                        }
                                    )
                                    .padding(
                                        start = if (isChapterActionsVisible) 6.dp else 16.dp,
                                        end = if (isChapterActionsVisible) 6.dp else 16.dp,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 6 Dots Drag & Drop Reordering Handle on the FAR LEFT (only when long-pressed)
                                if (isChapterActionsVisible) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Hold and drag to reorder",
                                        tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .pointerInput(i) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingIndex = i
                                                        dragOverIndex = i
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val approxItemHeightPx = 135f
                                                        val diff = (dragOffsetY / approxItemHeightPx).toInt()
                                                        val target = (i + diff).coerceIn(0, chapters.size - 1)
                                                        if (target != dragOverIndex) {
                                                            dragOverIndex = target
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val from = draggingIndex
                                                        val to = dragOverIndex
                                                        if (from != null && to != null && from != to) {
                                                            onMoveChapter(from, to)
                                                        }
                                                        draggingIndex = null
                                                        dragOverIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingIndex = null
                                                        dragOverIndex = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                            .padding(end = 4.dp)
                                    )
                                }

                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (chapter.isCompleted) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Chapter read",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val heartbeatTransition = rememberInfiniteTransition(label = "chapterHeartbeat")
                                            val heartbeatScale by heartbeatTransition.animateFloat(
                                                initialValue = 0.8f,
                                                targetValue = 1.35f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(700, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "heartbeatScale"
                                            )
                                            val heartbeatAlpha by heartbeatTransition.animateFloat(
                                                initialValue = 0.5f,
                                                targetValue = 1.0f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(700, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "heartbeatAlpha"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .scale(heartbeatScale)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF9800).copy(alpha = heartbeatAlpha))
                                            )
                                        }
                                    }
                                }

                                // Actions: Read status toggle & Delete Chapter (only when long-pressed)
                                if (isChapterActionsVisible) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                activeActionChapterId = null
                                                sharingChapter = chapter
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share Chapter",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onToggleChapterReadStatus(chapter) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (chapter.isCompleted) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (chapter.isCompleted) "Mark as unread" else "Mark as read",
                                                tint = if (chapter.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                chapterToDelete = chapter
                                                deleteChapterFromDisk = false
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Chapter",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Drawer Bottom Footer: Settings
        Surface(
            onClick = onOpenSettings,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Toolbar, TTS, CSS & Preferences",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        HorizontalDivider()

        // Drawer Bottom Footer: About App
        Surface(
            onClick = onOpenAbout,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "About App",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "About BookReader",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "v$installedVersionName",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onOpenDevLogs,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = "Developer Logs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (novelToDelete != null) {
            val novel = novelToDelete!!
            AlertDialog(
                onDismissRequest = { novelToDelete = null },
                title = {
                    Text(
                        text = "Remove Folder",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Are you sure you want to remove \"${novel.title}\" from the library?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteFilesFromDisk = !deleteFilesFromDisk }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deleteFilesFromDisk,
                                onCheckedChange = { deleteFilesFromDisk = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete files in memory as well",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deleteFilesFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteNovel(novel, deleteFilesFromDisk)
                            novelToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deleteFilesFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { novelToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (chapterToDelete != null) {
            val chapter = chapterToDelete!!
            AlertDialog(
                onDismissRequest = { chapterToDelete = null },
                title = {
                    Text(
                        text = "Delete Chapter",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Are you sure you want to remove \"${chapter.title}\" from this playlist?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteChapterFromDisk = !deleteChapterFromDisk }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deleteChapterFromDisk,
                                onCheckedChange = { deleteChapterFromDisk = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete file from device storage as well",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deleteChapterFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteChapter(chapter, deleteChapterFromDisk)
                            chapterToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deleteChapterFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chapterToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (sharingNovel != null) {
            val novelToShare = sharingNovel!!
            val novelChapters = if (novelToShare.id == currentNovel?.id) chapters else emptyList()
            NovelFolderShareDialog(
                novel = novelToShare,
                chapters = novelChapters,
                onDismiss = { sharingNovel = null },
                onFuseAndShare = {
                    Toast.makeText(context, "Fusing novel chapters in background...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startFuseNovel(context, novelToShare.id)
                },
                onZipAndShare = {
                    Toast.makeText(context, "Zipping novel folder in background...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startZipNovel(context, novelToShare.id)
                },
                onAudifyAndShare = { start, end, speed, splitMode ->
                    Toast.makeText(context, "Audifying novel in background...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startExportNovel(
                        context = context,
                        novelId = novelToShare.id,
                        startChap = start,
                        endChap = end,
                        splitMode = splitMode,
                        speed = speed
                    )
                    onOpenAudioStudio()
                }
            )
        }

        if (sharingChapter != null) {
            val chapToShare = sharingChapter!!
            ChapterShareDialog(
                chapter = chapToShare,
                onDismiss = { sharingChapter = null },
                onShareDirectly = {
                    val rawFile = File(chapToShare.fileUri)
                    if (rawFile.exists()) {
                        val shareIntent = FileFusionManager.createShareIntent(context, rawFile, "text/*")
                        context.startActivity(Intent.createChooser(shareIntent, "Share chapter"))
                    } else {
                        Toast.makeText(context, "Chapter file not found on disk", Toast.LENGTH_SHORT).show()
                    }
                },
                onAudifyAndShare = { speed ->
                    val novId = currentNovel?.id ?: chapToShare.novelId
                    Toast.makeText(context, "Exporting chapter...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startExportNovel(
                        context = context,
                        novelId = novId,
                        startChap = chapToShare.orderIndex,
                        endChap = chapToShare.orderIndex,
                        splitMode = AudioExportEngine.SplitMode.SINGLE_FILE,
                        speed = speed,
                        audioFormat = AudioExportEngine.AudioExportFormat.WAV,
                        directShare = true
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: com.novelarrow.reader.data.model.ReaderSettings,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onAutoNextChange: (Boolean) -> Unit,
    onClearAllData: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        title = { Text("Settings") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    // Theme selector: each button weighted equally so they never overflow
                    Text("Reading Theme", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ThemeMode.LIGHT to "☀️ Light",
                            ThemeMode.SEPIA to "📜 Sepia",
                            ThemeMode.DARK to "🌙 Dark"
                        ).forEach { (mode, label) ->
                            val isSelected = settings.theme == mode
                            Button(
                                onClick = { onThemeChange(mode) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Font size slider
                    Text("Font Size: ${settings.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..28f,
                        steps = 15
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-scroll toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-scroll to follow speech", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.autoScroll,
                            onCheckedChange = onAutoScrollChange
                        )
                    }

                    // Auto-next chapter toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-advance next chapter", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.autoNextChapter,
                            onCheckedChange = onAutoNextChange
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    remainingSeconds: Int = 0,
    isPlaying: Boolean = false,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit
) {
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sleep Timer")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Audio will automatically pause after this time. If paused mid-timer, it freezes and continues on play. Once finished, playback resumes and resets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (currentMinutes > 0 && remainingSeconds > 0) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    val stateLabel = if (isPlaying) "Counting Down" else "Frozen / Paused"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Current remaining: $mins:${secs.toString().padStart(2, '0')} ($stateLabel)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val presets = listOf(
                    0 to "Turn Off",
                    15 to "15 minutes",
                    20 to "20 minutes",
                    30 to "30 minutes",
                    45 to "45 minutes",
                    60 to "1 hour"
                )

                presets.forEach { (mins, label) ->
                    val isSelected = currentMinutes == mins
                    Button(
                        onClick = {
                            onSetTimer(mins)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Custom minutes input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Custom min") },
                        placeholder = { Text("e.g. 90") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val mins = customText.toIntOrNull() ?: 0
                            if (mins > 0) {
                                onSetTimer(mins)
                                onDismiss()
                            }
                        },
                        enabled = (customText.toIntOrNull() ?: 0) > 0,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Set")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LibraryScanningOverlay(
    progress: Float,
    currentIndex: Int,
    totalItems: Int,
    currentItem: String
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppLoadingIndicator(modifier = Modifier.size(36.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Indexing Novel Library",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scanning folders, chapters & playlists...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (totalItems > 0) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (totalItems > 0) "$currentIndex of $totalItems items" else "Discovering files...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        if (totalItems > 0) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (currentItem.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentItem,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    updateInfo: com.novelarrow.reader.data.updater.UpdateInfo,
    downloadedApk: java.io.File?,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onSaveToPathClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sizeStr = if (updateInfo.formattedSize.isNotEmpty()) " • ${updateInfo.formattedSize}" else ""
                Text(
                    text = "${updateInfo.tagName}$sizeStr",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Release Notes:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        item {
                            Text(
                                text = updateInfo.body,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (downloadedApk != null && downloadedApk.exists()) {
                            onInstallClick()
                        } else {
                            onDownloadClick()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (downloadedApk != null && downloadedApk.exists()) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install Now")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download & Install")
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSaveToPathClick,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save APK to Downloads...")
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text("Later")
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun UpdateDownloadingDialog(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        icon = {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Downloading Update",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                val dlMb = downloadedBytes.toDouble() / (1024.0 * 1024.0)
                val totMb = totalBytes.toDouble() / (1024.0 * 1024.0)
                val percent = (progress * 100).toInt()
                val progressText = if (totalBytes > 0) {
                    "%.1f MB / %.1f MB (%d%%)".format(dlMb, totMb, percent)
                } else {
                    "%.1f MB downloaded...".format(dlMb)
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
