package com.novelarrow.reader.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material.icons.filled.Tab
import com.novelarrow.reader.ui.browser.BrowserTab
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
            ReaderTopBar(
                title = uiState.currentChapter?.title ?: "NovelArrow Reader Pro",
                subtitle = uiState.currentNovel?.let { novel ->
                    val chapIdx = (uiState.currentChapter?.orderIndex ?: 0) + 1
                    "${novel.title} • $chapIdx/${uiState.chapters.size}"
                } ?: "Ready to read",
                isTtsDockVisible = uiState.isTtsDockVisible,
                isTextChapter = uiState.currentChapter?.isTextBased() == true,
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

            AnimatedVisibility(
                visible = uiState.isTtsDockVisible || isBrowserPlaying,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                if (isBrowserPlaying) {
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
                        onOpenAudioSettings = { showUnifiedAudioSettings = true }
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
                        onOpenAudioSettings = { showUnifiedAudioSettings = true }
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            onDismiss = { showUnifiedAudioSettings = false }
        )
    }
}

@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String,
    isTtsDockVisible: Boolean,
    isTextChapter: Boolean,
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
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    onParagraphClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (autoScroll && currentIndex in paragraphs.indices) {
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

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
            val isCurrent = index == currentIndex
            val backgroundColor by animateColorAsState(
                targetValue = if (isCurrent) highlightColor else Color.Transparent,
                label = "highlight_anim"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onParagraphClick(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = paragraph.rawText,
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
    onOpenAudioSettings: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragParaIndex by remember(currentParagraph) { mutableFloatStateOf(currentParagraph.toFloat()) }

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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Slider strictly controls progress within the current chapter, with centralized options button to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = if (isDragging) dragParaIndex else currentParagraph.toFloat().coerceIn(0f, (totalParagraphs - 1).coerceAtLeast(1).toFloat()),
                    onValueChange = { newVal ->
                        isDragging = true
                        dragParaIndex = newVal
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        val target = dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
                        onSeekParagraph(target)
                    },
                    valueRange = 0f .. (totalParagraphs - 1).coerceAtLeast(1).toFloat(),
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

                IconButton(
                    onClick = onOpenAudioSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "TTS and Audio Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata row: Chapter number/total and paragraph number/total (chapter title removed)
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

@Composable
fun EmptyState(
    novels: List<Novel>,
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
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AccentBlue, AccentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NovelArrow Reader Pro",
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
            val lastNovel = novels.first()
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
                        text = "Chapter ${lastNovel.currentChapterIndex + 1} / ${lastNovel.chapterCount}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
    onCloseDrawer: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
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
                IconButton(onClick = { onOpenBrowser(false) }) {
                    Icon(Icons.Default.Language, contentDescription = "Open Web Browser")
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectNovel(novel)
                                        selectedTab = 1
                                    }
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
                                IconButton(onClick = {
                                    novelToDelete = novel
                                    deleteFilesFromDisk = false
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
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
                                    Icons.Default.Tab,
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
                            val isCurrent = chapter.id == currentChapter?.id
                            val isDraggingThis = draggingIndex == i
                            val isTargeted = dragOverIndex == i && !isDraggingThis

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
                                    .clickable { onSelectChapter(i) }
                                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 6 Dots Drag & Drop Reordering Handle on the FAR LEFT
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

                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }

                                // Actions: Read status toggle & Delete Chapter
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = "About App",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Stable",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "v$installedVersionName",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Clear all button
                    OutlinedButton(
                        onClick = onClearAllData,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Library & Progress")
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
                        CircularProgressIndicator(
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSaveToPathClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save APK...")
                }
                Button(
                    onClick = {
                        if (downloadedApk != null && downloadedApk.exists()) {
                            onInstallClick()
                        } else {
                            onDownloadClick()
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
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
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
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
