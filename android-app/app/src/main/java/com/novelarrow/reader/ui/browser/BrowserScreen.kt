package com.novelarrow.reader.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import com.novelarrow.reader.ui.common.AppLoadingIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.BrowserTab
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.reader.SleepTimerDialog
import com.novelarrow.reader.ui.reader.TtsDock
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.common.MonacoCodeEditorDialog

@Composable
fun BrowserScreen(
    isActive: Boolean = true,
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenDownloadedNovel: (novelId: String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToPronunciationRules: (() -> Unit)? = null,
    onNavigateToSkipWords: (() -> Unit)? = null,
    onNavigateToVoiceEngine: (() -> Unit)? = null,
    onNavigateToHeaderFooterSkips: (() -> Unit)? = null,
    onNavigateToContentSelector: (() -> Unit)? = null,
    onNavigateToNextSelector: (() -> Unit)? = null,
    onNavigateToIgnoredSelectors: (() -> Unit)? = null,
    onNavigateToReorderButtons: (() -> Unit)? = null,
    onNavigateToDevLogs: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackProgress by AudioPlaybackCoordinator.playbackProgressState.collectAsState()
    val isReaderActive = playbackProgress.source == PlaybackSource.READER && (playbackProgress.isPlaying || playbackProgress.novelId != null)
    val isMediaDockVisible = uiState.isLiveReading || isReaderActive || uiState.flashHandleRed
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    BackHandler(enabled = isActive) {
        if (uiState.isDownloading) {
            viewModel.stopDownloadLoop()
        } else if (uiState.isReaderMode) {
            viewModel.exitReadingMode()
        } else if (uiState.isLiveReading) {
            viewModel.stopLiveReading()
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onNavigateBack()
        }
    }

    val currentView = androidx.compose.ui.platform.LocalView.current

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
            webViewInstance = null
        }
    }

    LaunchedEffect(webViewInstance) {
        val wv = webViewInstance ?: return@LaunchedEffect
        viewModel.browserActions.collect { action ->
            when (action) {
                is BrowserAction.LoadUrl -> wv.loadUrl(action.url)
                is BrowserAction.GoBack -> if (wv.canGoBack()) wv.goBack()
                is BrowserAction.GoForward -> if (wv.canGoForward()) wv.goForward()
                is BrowserAction.Reload -> wv.reload()
                is BrowserAction.EvaluateJavascript -> wv.evaluateJavascript(action.script, action.callback)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowserTopBar(
                inputUrl = uiState.inputUrl,
                isLoading = uiState.isLoading,
                canGoBack = uiState.canGoBack,
                canGoForward = uiState.canGoForward,
                isLiveReading = uiState.isLiveReading,
                isDownloading = uiState.isDownloading,
                isReaderMode = uiState.isReaderMode,
                totalParagraphs = if (uiState.isLiveReading) uiState.liveParagraphs.size else if (uiState.isReaderMode) uiState.readerModeParagraphs.size else 0,
                currentParagraphIndex = if (uiState.isLiveReading) uiState.liveCurrentParagraphIndex else if (uiState.isReaderMode) uiState.readerModeCurrentParagraphIndex else 0,
                currentUrl = uiState.currentUrl,
                navbarShortcuts = uiState.settings.navbarShortcuts,
                onBackClicked = { viewModel.goBack() },
                onForwardClicked = { viewModel.goForward() },
                onReloadClicked = { viewModel.reload() },
                onReadingModeClicked = { viewModel.toggleReadingMode() },
                onLoopClicked = { viewModel.onLoopButtonClicked() },
                onSaveFrozenHtmlClicked = { viewModel.saveSingleChapter() },
                onNextChapterClicked = { viewModel.clickNextChapter() },
                onLiveReadClicked = { viewModel.toggleLiveReading() },
                onSeekParagraph = { viewModel.seekToParagraph(it) },
                onTriggerShortcut = { selector -> viewModel.clickElementBySelector(selector) },
                onInspectClickables = { viewModel.inspectPageClickables() },
                onManageShortcuts = { viewModel.openShortcutsManager() },
                onRawCodeClicked = { viewModel.extractAndShowRawHtml() },
                onNextSelectorClicked = onNavigateToNextSelector,
                onIgnoredSelectorsClicked = onNavigateToIgnoredSelectors,
                onExtensionsClicked = { viewModel.openExtensionsSheet() },
                onSettingsClicked = { viewModel.openSettingsDialog() },
                onDevLogsClicked = onNavigateToDevLogs,
                onUrlChanged = { viewModel.setInputUrl(it) },
                onUrlSubmitted = {
                    keyboardController?.hide()
                    viewModel.navigateTo(it)
                },
                onOpenDrawer = onOpenDrawer,
                toolbarButtonOrder = uiState.settings.toolbarButtonOrder,
                customButtons = uiState.customToolbarButtons,
                onCustomButtonClicked = { viewModel.onCustomToolbarButtonClicked(it) },
                onReorderButtonsClicked = { onNavigateToReorderButtons?.invoke() },
                onHistoryClicked = { viewModel.openHistoryDialog() }
            )

            // Linear Progress Indicator
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    progress = { uiState.progress.toFloat() / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // WebView Box with Dark Themed Background (no blocking overlay so web elements and next buttons remain fully clickable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AndroidView(
                    factory = { ctx ->
                        createConfiguredWebView(ctx, viewModel).also { wv ->
                            webViewInstance = wv
                            wv.loadUrl(uiState.currentUrl)
                        }
                    },
                    update = { wv ->
                        webViewInstance = wv
                        (wv as? BackgroundAwareWebView)?.keepAliveInBackground = uiState.isLiveReading || uiState.isDownloading
                        wv.isEnabled = isActive
                        wv.visibility = if (isActive) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (uiState.isReaderMode) {
                    BrowserReaderView(
                        title = uiState.readerModeTitle,
                        paragraphs = uiState.readerModeParagraphs,
                        currentIndex = if (uiState.isLiveReading) uiState.liveCurrentParagraphIndex else uiState.readerModeCurrentParagraphIndex,
                        isLoadingNext = uiState.readerModeIsLoadingNext,
                        autoScroll = uiState.settings.ttsAutoScroll,
                        onParagraphClick = { index ->
                            viewModel.onReaderModeParagraphClicked(index)
                        },
                        onNextChapterClick = {
                            viewModel.clickNextChapter()
                        },
                        onEndReached = {
                            viewModel.onReaderModeEndReached()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }

        // Centered Batch Download Modal Overlay
        AnimatedVisibility(
            visible = uiState.isDownloading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                CenteredDownloadProgressCard(
                    novelTitle = uiState.downloadNovelTitle,
                    currentChapter = uiState.downloadCurrentChapter,
                    remaining = uiState.downloadRemainingCount,
                    total = uiState.downloadTotalCount,
                    countdown = uiState.downloadCountdownSeconds,
                    status = uiState.downloadStatus,
                    onStop = { viewModel.stopDownloadLoop() }
                )
            }
        }

        // TTS Player Dock - Reuses the exact same TtsDock component from ReaderScreen
        AnimatedVisibility(
            visible = isMediaDockVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (uiState.isLiveReading || (uiState.flashHandleRed && !isReaderActive)) {
                TtsDock(
                    currentParagraph = uiState.liveCurrentParagraphIndex,
                    totalParagraphs = uiState.liveParagraphs.size,
                    currentChapterIndex = 0,
                    totalChapters = 0,
                    isPlaying = uiState.isLivePlaying,
                    speechRate = uiState.settings.ttsRate,
                    sleepTimerMinutes = uiState.sleepTimerMinutes,
                    sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                    onPlayPauseClick = { viewModel.toggleLiveReading() },
                    onPrevChapterClick = { viewModel.clickPrevChapter() },
                    onNextChapterClick = { viewModel.clickNextChapter() },
                    onSleepTimerClick = { viewModel.openSleepTimerDialog() },
                    onSpeedClick = { viewModel.cycleSpeechRate() },
                    onSeekParagraph = { viewModel.seekToParagraph(it) },
                    isBrowserMode = true,
                    onOpenAudioSettings = { showAudioSettingsDialog = true },
                    onCloseClick = {
                        viewModel.stopLiveReading()
                        viewModel.resetFlashHandleRed()
                        AudioPlaybackCoordinator.stop()
                    },
                    activeRuleImpacts = uiState.activeRuleImpacts,
                    onToggleRule = { rule, enabled -> viewModel.toggleActiveRule(rule, enabled) },
                    flashHandleRed = uiState.flashHandleRed,
                    onResetFlashHandle = { viewModel.resetFlashHandleRed() }
                )
            } else if (isReaderActive) {
                TtsDock(
                    currentParagraph = playbackProgress.currentParagraphIndex,
                    totalParagraphs = playbackProgress.totalParagraphs,
                    currentChapterIndex = playbackProgress.chapterIndex,
                    totalChapters = playbackProgress.totalChapters,
                    isPlaying = playbackProgress.isPlaying,
                    speechRate = AudioPlaybackCoordinator.currentSpeechRate,
                    sleepTimerMinutes = uiState.sleepTimerMinutes,
                    sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                    onPlayPauseClick = {
                        if (playbackProgress.isPlaying) AudioPlaybackCoordinator.pause()
                        else AudioPlaybackCoordinator.play()
                    },
                    onPrevChapterClick = { AudioPlaybackCoordinator.previousChapter() },
                    onNextChapterClick = { AudioPlaybackCoordinator.nextChapter() },
                    onSleepTimerClick = { viewModel.openSleepTimerDialog() },
                    onSpeedClick = { viewModel.cycleSpeechRate() },
                    onSeekParagraph = { AudioPlaybackCoordinator.seekToParagraph(it) },
                    isBrowserMode = false,
                    onOpenAudioSettings = { showAudioSettingsDialog = true },
                    onCloseClick = { AudioPlaybackCoordinator.stop() },
                    activeRuleImpacts = uiState.activeRuleImpacts,
                    onToggleRule = { rule, enabled -> viewModel.toggleActiveRule(rule, enabled) },
                    flashHandleRed = uiState.flashHandleRed,
                    onResetFlashHandle = { viewModel.resetFlashHandleRed() }
                )
            }
        }

        // Multi-Tab Switcher: slides the library sidebar open instead of popup dialog
        LaunchedEffect(uiState.showTabSwitcher) {
            if (uiState.showTabSwitcher) {
                onOpenDrawer()
                viewModel.dismissTabSwitcher()
            }
        }

        // Sleep Timer Dialog - Reuses SleepTimerDialog from ReaderScreen
        if (uiState.showSleepTimerDialog) {
            SleepTimerDialog(
                currentMinutes = uiState.sleepTimerMinutes,
                remainingSeconds = uiState.sleepTimerRemainingSeconds,
                isPlaying = uiState.isLivePlaying,
                onDismiss = { viewModel.dismissSleepTimerDialog() },
                onSetTimer = { viewModel.setSleepTimer(it) }
            )
        }

        if (uiState.showRawEditor && uiState.rawHtmlContent != null) {
            MonacoCodeEditorDialog(
                title = "HTML Source: ${uiState.detectedTitle ?: uiState.currentUrl}",
                initialContent = uiState.rawHtmlContent ?: "",
                language = "html",
                onDismiss = { viewModel.dismissRawEditor() }
            )
        }

        if (showAudioSettingsDialog) {
            UnifiedAudioSettingsDialog(
                speechRate = uiState.settings.ttsRate,
                speechPitch = uiState.settings.ttsPitch,
                selectedVoiceName = uiState.settings.ttsVoice,
                availableVoices = uiState.availableVoices,
                sleepTimerMinutes = uiState.sleepTimerMinutes,
                sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                pronunciationRulesEnabled = uiState.settings.pronunciationRulesEnabled,
                pronunciationRules = uiState.settings.pronunciationRules,
                skipWordsEnabled = uiState.settings.skipWordsEnabled,
                skipWords = uiState.settings.skipWords,
                contentSelector = uiState.settings.contentSelector,
                onSpeechRateChange = { viewModel.updateRate(it) },
                onSpeechPitchChange = { viewModel.updatePitch(it) },
                onVoiceChange = { viewModel.updateVoice(it) },
                onSleepTimerChange = { viewModel.setSleepTimer(it) },
                onPronunciationRulesEnabledChange = { viewModel.togglePronunciationRulesEnabled(it) },
                onAddPronunciationRule = { from, to, exact -> viewModel.addPronunciationRule(from, to, exact) },
                onRemovePronunciationRule = { viewModel.removePronunciationRule(it) },
                onSkipWordsEnabledChange = { viewModel.toggleSkipWordsEnabled(it) },
                onAddSkipWord = { word, exact -> viewModel.addSkipWord(word, exact) },
                onRemoveSkipWord = { viewModel.removeSkipWord(it) },
                onContentSelectorChange = { viewModel.updateContentSelector(it) },
                onNavigateToPronunciationRules = onNavigateToPronunciationRules?.let { nav ->
                    {
                        showAudioSettingsDialog = false
                        nav()
                    }
                },
                onNavigateToSkipWords = onNavigateToSkipWords?.let { nav ->
                    {
                        showAudioSettingsDialog = false
                        nav()
                    }
                },
                onNavigateToVoiceEngine = onNavigateToVoiceEngine?.let { nav ->
                    {
                        showAudioSettingsDialog = false
                        nav()
                    }
                },
                onNavigateToHeaderFooterSkips = onNavigateToHeaderFooterSkips?.let { nav ->
                    {
                        showAudioSettingsDialog = false
                        nav()
                    }
                },
                onNavigateToContentSelector = onNavigateToContentSelector?.let { nav ->
                    {
                        showAudioSettingsDialog = false
                        nav()
                    }
                },
                onDismiss = { showAudioSettingsDialog = false }
            )
        }

        // Userscript Automation & Reading Settings Modal (TTS voice/speed/pitch/rules moved to Control Panel)
        if (uiState.showSettingsDialog) {
            BrowserSettingsDialog(
                settings = uiState.settings,
                onToggleAutoReadNextChapter = { viewModel.toggleAutoReadNextChapter(it) },
                onToggleTtsAutoScroll = { viewModel.toggleTtsAutoScroll(it) },
                onToggleTtsAutoStart = { viewModel.toggleTtsAutoStart(it) },
                onToggleAutoClickOnScroll = { viewModel.toggleAutoClickOnScroll(it) },
                onToggleAutoNextOnFinishedPage = { viewModel.toggleAutoNextOnFinishedPage(it) },
                onUpdateLoopCount = { viewModel.updateLoopCount(it) },
                onUpdateDelay = { viewModel.updateCountdownDelayMs(it) },
                onNavigateToContentSelector = onNavigateToContentSelector,
                onNavigateToNextSelector = onNavigateToNextSelector,
                onNavigateToIgnoredSelectors = onNavigateToIgnoredSelectors,
                onNavigateToReorderButtons = onNavigateToReorderButtons,
                onResetDefaults = { viewModel.resetSettingsToDefaults() },
                onDismiss = { viewModel.dismissSettingsDialog() }
            )
        }

        // Extensions & Userscripts Bottom Sheet
        if (uiState.showExtensionsSheet) {
            UserscriptBottomSheet(
                currentUrl = uiState.currentUrl,
                activeScripts = uiState.activeUserscripts,
                onToggleScript = { id, enabled -> viewModel.toggleUserscript(id, enabled) },
                onManageScripts = { viewModel.openUserscriptManager() },
                onCreateScript = { viewModel.openCodeMirrorEditor(null) },
                onOpenDocs = { viewModel.openDocumentationDialog() },
                onDismiss = { viewModel.dismissExtensionsSheet() }
            )
        }

        // Userscript Manager Dialog
        if (uiState.showUserscriptManager) {
            UserscriptManagerDialog(
                scripts = uiState.allUserscripts,
                onToggleEnabled = { id, enabled -> viewModel.toggleUserscript(id, enabled) },
                onEditScript = { script -> viewModel.openCodeMirrorEditor(script) },
                onDeleteScript = { id -> viewModel.deleteUserscript(id) },
                onRenameScript = { id, newName -> viewModel.renameUserscript(id, newName) },
                onCreateNewScript = { viewModel.openCodeMirrorEditor(null) },
                onSyncDisk = { viewModel.syncUserscriptsFromDisk() },
                onDismiss = { viewModel.dismissUserscriptManager() }
            )
        }

        // Userscript Monaco Editor Dialog (bundled offline editor, single instance)
        if (uiState.showCodeMirrorEditor) {
            MonacoCodeEditorDialog(
                title = uiState.editorPrefilledTitle.ifBlank { "New Userscript" },
                initialContent = uiState.editorPrefilledCode,
                language = "javascript",
                onDismiss = { viewModel.dismissCodeMirrorEditor() },
                onSave = { code -> viewModel.saveUserscriptFromEditor(code) }
            )
        }

        // Userscript Documentation Dialog
        if (uiState.showDocumentationDialog) {
            DocumentationDialog(
                onDismiss = { viewModel.dismissDocumentationDialog() }
            )
        }

        // Browser History Dialog
        if (uiState.showHistoryDialog) {
            BrowserHistoryDialog(
                history = uiState.historyEntries,
                onSelectUrl = { url ->
                    viewModel.navigateTo(url)
                },
                onDeleteEntry = { id ->
                    viewModel.deleteHistoryEntry(id)
                },
                onClearHistory = {
                    viewModel.clearHistory()
                },
                onDismiss = {
                    viewModel.dismissHistoryDialog()
                }
            )
        }

        // Long-Pressed Clickable Element Detection Prompt
        if (uiState.showAddNextSelectorDialog && uiState.longPressedClickable != null) {
            val clickable = uiState.longPressedClickable!!
            var ruleName by remember(clickable) { mutableStateOf(clickable.text.take(30).ifBlank { "Next Chapter (${clickable.hostname})" }) }
            var selector by remember(clickable) { mutableStateOf(clickable.selector) }
            var domain by remember(clickable) { mutableStateOf(clickable.hostname) }

            AlertDialog(
                onDismissRequest = { viewModel.dismissAddNextSelectorDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mouse, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Next Chapter Selector", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Detected clickable element. Bind it as an Auto Clicker / Next Chapter Selector for ${clickable.hostname}?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Rule Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = selector,
                            onValueChange = { selector = it },
                            label = { Text("CSS Selector") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Target Domain (or blank for global)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.confirmAddNextSelector(ruleName, selector, domain)
                        }
                    ) {
                        Text("Add Target")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissAddNextSelectorDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Full Screen DOM Clickables Inspector
        if (uiState.showClickablesInspector) {
            BrowserClickablesScreen(
                clickables = uiState.scannedClickables,
                currentUrl = uiState.currentUrl,
                onAddNextRule = { rule ->
                    viewModel.addNextChapterSelectorRule(rule)
                },
                onAddNavbarShortcut = { shortcut ->
                    viewModel.addNavbarShortcut(shortcut)
                },
                onOpenShortcutsManager = { viewModel.openShortcutsManager() },
                onNavigateBack = { viewModel.dismissClickablesInspector() }
            )
        }

        // Full Screen Navbar Shortcuts Manager
        if (uiState.showShortcutsManager) {
            BrowserShortcutsManagerScreen(
                shortcuts = uiState.settings.navbarShortcuts,
                currentUrl = uiState.currentUrl,
                onToggleShortcut = { id, enabled -> viewModel.toggleNavbarShortcut(id, enabled) },
                onDeleteShortcut = { id -> viewModel.deleteNavbarShortcut(id) },
                onTriggerShortcut = { selector -> viewModel.clickElementBySelector(selector) },
                onNavigateBack = { viewModel.dismissShortcutsManager() }
            )
        }
    }
}

private data class TopBarActionItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val background: Color = Color.Transparent,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

private fun getShortcutIcon(iconName: String): ImageVector {
    return when (iconName) {
        "Next" -> Icons.Default.SkipNext
        "Star" -> Icons.Default.Star
        "Play" -> Icons.Default.PlayArrow
        "Link" -> Icons.Default.Link
        "Bookmark" -> Icons.Default.Bookmark
        "Code" -> Icons.Default.Code
        else -> Icons.Default.Mouse
    }
}

/**
 * Top Toolbar cleanly divided into 2 well-spaced rows:
 * Row 1: App Back button, Full-width URL bar with Clear & Go
 * Under Row 1: Interactive Scrubbable Slider under chapter/paragraph progress (when totalParagraphs > 0)
 * Row 2: Clean action row: Back, Forward, Reload, Reading Mode, Multi Download, Single Download, Auto Clicker, Start TTS + Domain Shortcuts + 3-Dot Overflow Menu
 */
@Composable
private fun BrowserTopBar(
    inputUrl: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLiveReading: Boolean,
    isDownloading: Boolean,
    isReaderMode: Boolean,
    totalParagraphs: Int,
    currentParagraphIndex: Int,
    currentUrl: String,
    navbarShortcuts: List<BrowserNavbarShortcut>,
    onBackClicked: () -> Unit,
    onForwardClicked: () -> Unit,
    onReloadClicked: () -> Unit,
    onReadingModeClicked: () -> Unit,
    onLoopClicked: () -> Unit,
    onSaveFrozenHtmlClicked: () -> Unit,
    onNextChapterClicked: () -> Unit,
    onLiveReadClicked: () -> Unit,
    onSeekParagraph: (Int) -> Unit,
    onTriggerShortcut: (String) -> Unit,
    onInspectClickables: () -> Unit,
    onManageShortcuts: () -> Unit,
    onRawCodeClicked: () -> Unit,
    onNextSelectorClicked: (() -> Unit)? = null,
    onIgnoredSelectorsClicked: (() -> Unit)? = null,
    onExtensionsClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onDevLogsClicked: (() -> Unit)? = null,
    onUrlChanged: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    toolbarButtonOrder: List<String> = emptyList(),
    customButtons: List<com.novelarrow.reader.data.model.CustomToolbarButton> = emptyList(),
    onCustomButtonClicked: (String) -> Unit = {},
    onReorderButtonsClicked: () -> Unit = {},
    onHistoryClicked: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // ROW 1: Full-width URL bar with Drawer Menu button and Clear/Go buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open Library Drawer
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Library Menu",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Full-width URL field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    TextField(
                        value = inputUrl,
                        onValueChange = onUrlChanged,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Search Google or enter URL",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onUrlSubmitted(inputUrl) }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    )

                    if (inputUrl.isNotBlank()) {
                        IconButton(
                            onClick = { onUrlChanged("") },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }

                    IconButton(
                        onClick = { onUrlSubmitted(inputUrl) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ROW 2: Clean action toolbar with 3-dot overflow menu for small screens & extra options
            val currentDomain = remember(currentUrl) {
                try {
                    java.net.URI(currentUrl).host.orEmpty().lowercase()
                } catch (_: Exception) {
                    ""
                }
            }

            val activeShortcuts = remember(navbarShortcuts, currentDomain) {
                navbarShortcuts.filter { sc ->
                    sc.enabled && (sc.domain.isBlank() || (currentDomain.isNotBlank() && currentDomain.contains(sc.domain.trim().lowercase())))
                }
            }

            val primaryColor = MaterialTheme.colorScheme.primary
            val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
            val secondaryColor = MaterialTheme.colorScheme.secondary

            val allAvailableMap = remember(
                canGoBack,
                canGoForward,
                isReaderMode,
                isDownloading,
                isLiveReading,
                customButtons,
                activeShortcuts,
                primaryColor,
                primaryContainerColor,
                secondaryColor
            ) {
                val map = mutableMapOf<String, TopBarActionItem>()
                map["tts"] = TopBarActionItem(
                    id = "tts",
                    title = "TTS Read Aloud",
                    icon = Icons.Default.Headphones,
                    tint = if (isLiveReading) primaryColor else null,
                    background = if (isLiveReading) primaryContainerColor else Color.Transparent,
                    onClick = onLiveReadClicked
                )
                map["reader_mode"] = TopBarActionItem(
                    id = "reader_mode",
                    title = if (isReaderMode) "Exit Reading Mode" else "Reading Mode",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    tint = if (isReaderMode) primaryColor else null,
                    background = if (isReaderMode) primaryContainerColor else Color.Transparent,
                    onClick = onReadingModeClicked
                )
                map["auto_clicker"] = TopBarActionItem(
                    id = "auto_clicker",
                    title = "Next Chapter (Click in Page)",
                    icon = Icons.Default.Mouse,
                    onClick = onNextChapterClicked
                )
                map["multi_download"] = TopBarActionItem(
                    id = "multi_download",
                    title = "Batch Download Loop",
                    icon = Icons.Default.CloudDownload,
                    tint = if (isDownloading) primaryColor else null,
                    background = if (isDownloading) primaryContainerColor else Color.Transparent,
                    onClick = onLoopClicked
                )
                map["loop"] = map["multi_download"]!!
                map["reload"] = TopBarActionItem(
                    id = "reload",
                    title = "Reload",
                    icon = Icons.Default.Refresh,
                    onClick = onReloadClicked
                )
                map["forward"] = TopBarActionItem(
                    id = "forward",
                    title = "Forward",
                    icon = Icons.Default.ChevronRight,
                    enabled = canGoForward,
                    onClick = onForwardClicked
                )
                map["back"] = TopBarActionItem(
                    id = "back",
                    title = "Back",
                    icon = Icons.Default.ChevronLeft,
                    enabled = canGoBack,
                    onClick = onBackClicked
                )
                map["save"] = TopBarActionItem(
                    id = "save",
                    title = "Save Chapter HTML",
                    icon = Icons.Default.Save,
                    onClick = onSaveFrozenHtmlClicked
                )
                map["extensions"] = TopBarActionItem(
                    id = "extensions",
                    title = "Extensions & Userscripts",
                    icon = Icons.Default.Extension,
                    tint = primaryColor,
                    onClick = onExtensionsClicked
                )
                map["inspect_clickables"] = TopBarActionItem(
                    id = "inspect_clickables",
                    title = "Inspect Page Clickables",
                    icon = Icons.Default.FindInPage,
                    tint = primaryColor,
                    onClick = onInspectClickables
                )

                // Add custom toolbar buttons from userscripts
                customButtons.forEach { btn ->
                    map[btn.id] = TopBarActionItem(
                        id = btn.id,
                        title = btn.title,
                        icon = resolveToolbarIcon(btn.icon),
                        tint = primaryColor,
                        onClick = { onCustomButtonClicked(btn.id) }
                    )
                }

                // Add active shortcuts
                activeShortcuts.forEach { sc ->
                    val scId = "shortcut_${sc.id}"
                    map[scId] = TopBarActionItem(
                        id = scId,
                        title = sc.name,
                        icon = getShortcutIcon(sc.iconName),
                        tint = secondaryColor,
                        onClick = { onTriggerShortcut(sc.selector) }
                    )
                }
                map
            }

            // Order buttons according to user's saved preferences
            val effectiveOrder = remember(toolbarButtonOrder) {
                if (toolbarButtonOrder.isEmpty()) DEFAULT_TOOLBAR_BUTTON_ORDER else toolbarButtonOrder
            }

            val orderedButtons = remember(allAvailableMap, effectiveOrder) {
                val list = mutableListOf<TopBarActionItem>()
                val seen = mutableSetOf<String>()
                effectiveOrder.forEach { id ->
                    val actualId = if (id == "loop") "multi_download" else id
                    allAvailableMap[actualId]?.let {
                        if (seen.add(it.id)) list.add(it)
                    }
                }
                allAvailableMap.values.forEach { item ->
                    if (item.id != "loop" && seen.add(item.id)) list.add(item)
                }
                list
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                val buttonWidth = 36.dp
                val overflowMenuWidth = 38.dp
                val screenFit = ((maxWidth - overflowMenuWidth) / buttonWidth).toInt().coerceAtLeast(1)
                val maxDirectFit = minOf(8, screenFit)

                val directlyShownItems = if (orderedButtons.size <= maxDirectFit) {
                    orderedButtons
                } else {
                    orderedButtons.take(maxDirectFit)
                }

                val overflowedVisibleItems = if (orderedButtons.size <= maxDirectFit) {
                    emptyList()
                } else {
                    orderedButtons.drop(maxDirectFit)
                }

                var showMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Display ordered right-to-left: #1 is closest to the 3-dot menu on the right
                        directlyShownItems.reversed().forEach { item ->
                            IconButton(
                                onClick = item.onClick,
                                enabled = item.enabled,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(item.background, shape = RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.tint ?: (if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // 3-Dot Overflow Menu pinned to the right
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Overflowed primary buttons (positions 9+)
                            if (overflowedVisibleItems.isNotEmpty()) {
                                overflowedVisibleItems.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.title) },
                                        leadingIcon = {
                                            Icon(
                                                item.icon,
                                                contentDescription = null,
                                                tint = item.tint ?: MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            item.onClick()
                                        }
                                    )
                                }
                                HorizontalDivider()
                            }

                            // Dedicated Reorder Buttons option
                            DropdownMenuItem(
                                text = { Text("Reorder Buttons") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onReorderButtonsClicked()
                                }
                            )

                            // History option
                            DropdownMenuItem(
                                text = { Text("Browsing History") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onHistoryClicked()
                                }
                            )

                            HorizontalDivider()

                            // Secondary browser features
                            DropdownMenuItem(
                                text = { Text("Inspect Page Clickables") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FindInPage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onInspectClickables()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Manage Navbar Shortcuts") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onManageShortcuts()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("View Raw HTML") },
                                leadingIcon = {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onRawCodeClicked()
                                }
                            )

                            if (onNextSelectorClicked != null) {
                                DropdownMenuItem(
                                    text = { Text("Next Chapter Selectors") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        showMenu = false
                                        onNextSelectorClicked()
                                    }
                                )
                            }

                            if (onIgnoredSelectorsClicked != null) {
                                DropdownMenuItem(
                                    text = { Text("Ignored Elements Filter") },
                                    leadingIcon = {
                                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        showMenu = false
                                        onIgnoredSelectorsClicked()
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Extensions & Userscripts") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onExtensionsClicked()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Browser Settings") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onSettingsClicked()
                                }
                            )

                            if (onDevLogsClicked != null) {
                                DropdownMenuItem(
                                    text = { Text("Developer Logs") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.BugReport,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDevLogsClicked()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Page Loading Overlay to completely avoid the blank white flash
 */
@Composable
private fun PageLoadingOverlay(
    url: String,
    progress: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppLoadingIndicator(
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Loading page...",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = url.replace("https://", "").replace("http://", "").take(35),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Centered modal card for web novel multi-chapter downloads.
 */
@Composable
private fun CenteredDownloadProgressCard(
    novelTitle: String,
    currentChapter: Int,
    remaining: Int,
    total: Int,
    countdown: Int,
    status: String,
    onStop: () -> Unit
) {
    val completed = (total - remaining).coerceAtLeast(0)
    val progressFraction = if (total > 0) (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = (progressFraction * 100).toInt()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Downloading Novel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = novelTitle.ifBlank { "Web Novel" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chapter Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Chapter $currentChapter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Progress Display
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$completed / $total Chapters ($percent%)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Detailed status text
            Text(
                text = status.ifBlank { "Processing..." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Prominent "Stop Download" Button
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stop Download",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

/**
 * Multi-Tab Switcher Dialog
 */
@Composable
private fun TabsSwitcherDialog(
    tabs: List<BrowserTab>,
    currentTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Open Tabs (${tabs.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = "New Tab", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabs) { _, tab ->
                    val isSelected = tab.id == currentTabId
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTab(tab.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tab.url,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (tabs.size > 1) {
                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Tab", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}



/**
 * Complete Userscript Settings Dialog:
 * - Auto-Run / Loop Tab
 * - TTS Reader Tab (Voice dropdown, Test Preview, Speed, Pitch, Auto-Scroll, Auto-Start)
 * - Selectors & Filters Tab (Ignored elements chips manager)
 * - Skip Words & Pronunciation Rules Tab
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BrowserSettingsDialog(
    settings: WebBrowserSettings,
    onToggleAutoReadNextChapter: (Boolean) -> Unit = {},
    onToggleTtsAutoScroll: (Boolean) -> Unit,
    onToggleTtsAutoStart: (Boolean) -> Unit,
    onToggleAutoClickOnScroll: (Boolean) -> Unit,
    onToggleAutoNextOnFinishedPage: (Boolean) -> Unit = {},
    onUpdateLoopCount: (Int) -> Unit,
    onUpdateDelay: (Long) -> Unit,
    onNavigateToContentSelector: (() -> Unit)? = null,
    onNavigateToNextSelector: (() -> Unit)? = null,
    onNavigateToIgnoredSelectors: (() -> Unit)? = null,
    onNavigateToReorderButtons: (() -> Unit)? = null,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 16.dp),
        title = {
            Text(
                "Browser Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SELECTORS & EXTRACTION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Content Container Selector Button
                BrowserSettingsNavCard(
                    icon = Icons.Default.Code,
                    title = "Content Container Selector",
                    subtitle = if (settings.contentSelector.isBlank()) "Autodetect main article container" else settings.contentSelector,
                    onClick = {
                        onDismiss()
                        onNavigateToContentSelector?.invoke()
                    }
                )

                // Next Chapter Selector Button
                BrowserSettingsNavCard(
                    icon = Icons.Default.SkipNext,
                    title = "Next Chapter Selector",
                    subtitle = if (settings.nextSelector.isBlank()) "Default multi-selector active" else settings.nextSelector,
                    onClick = {
                        onDismiss()
                        onNavigateToNextSelector?.invoke()
                    }
                )

                // Ignored Elements (Filter) Button
                BrowserSettingsNavCard(
                    icon = Icons.Default.FilterList,
                    title = "Ignored Elements (Filter)",
                    subtitle = if (settings.ignoredSelectors.isEmpty()) "Filter unwanted elements, ads, and navigation from speech" else "${settings.ignoredSelectors.size} elements filtered from TTS",
                    badge = if (settings.ignoredSelectors.isNotEmpty()) "${settings.ignoredSelectors.size}" else null,
                    onClick = {
                        onDismiss()
                        onNavigateToIgnoredSelectors?.invoke()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "BATCH LOOP & DELAYS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "Auto-Loop Chapters Count: ${settings.loopCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 25, 50, 100, 200).forEach { preset ->
                        FilterChip(
                            selected = settings.loopCount == preset,
                            onClick = { onUpdateLoopCount(preset) },
                            label = { Text("$preset") }
                        )
                    }
                }

                Text(
                    text = "Delay Between Chapters: ${settings.countdownDelayMs / 1000f}s",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500L, 1000L, 1500L, 2000L, 3000L).forEach { ms ->
                        FilterChip(
                            selected = settings.countdownDelayMs == ms,
                            onClick = { onUpdateDelay(ms) },
                            label = { Text("${ms / 1000f}s") }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "READING AUTOMATION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Auto-scroll switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Scroll to Text", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Scrolls page to follow currently spoken sentence", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.ttsAutoScroll,
                        onCheckedChange = onToggleTtsAutoScroll
                    )
                }

                // Auto-read when next chapter loads switch (default Off)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-read when next chapter loads", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Wait for user input before reading new page when Off", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.autoReadNextChapter,
                        onCheckedChange = onToggleAutoReadNextChapter
                    )
                }

                // Auto-start TTS switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start TTS on Page Load", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Immediately begins reading aloud after page finishes loading", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.ttsAutoStart,
                        onCheckedChange = onToggleTtsAutoStart
                    )
                }

                // Auto-next on scroll bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-next on scroll bottom",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Click next when scrolling to bottom of page",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = settings.autoClickOnScroll,
                        onCheckedChange = onToggleAutoClickOnScroll
                    )
                }

                // Auto-next on finishing last paragraph switch (default On)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-next on finished reading",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Auto-advance to next page when reading the last paragraph",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = settings.autoNextOnFinishedPage,
                        onCheckedChange = onToggleAutoNextOnFinishedPage
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "TOOLBAR & NAVIGATION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                BrowserSettingsNavCard(
                    icon = Icons.Default.DragHandle,
                    title = "Reorder Toolbar Buttons",
                    subtitle = "Drag to reorder right-to-left buttons and control overflow",
                    onClick = {
                        onDismiss()
                        onNavigateToReorderButtons?.invoke()
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onResetDefaults) {
                Text("Reset Defaults", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun BrowserSettingsNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * A custom WebView that preserves timer and script execution when the app is backgrounded
 * during active live reading or batch downloading.
 */
class BackgroundAwareWebView(context: android.content.Context) : WebView(context) {
    var keepAliveInBackground: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && (visibility == android.view.View.GONE || visibility == android.view.View.INVISIBLE)) {
            // Keep Chromium rendering pipeline and JS timers running while backgrounded
            super.onWindowVisibilityChanged(android.view.View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: android.content.Context,
    viewModel: BrowserViewModel
): BackgroundAwareWebView {
    return BackgroundAwareWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Give the webview dark background initially to prevent white flashing
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Modern Chrome user agent
            userAgentString = userAgentString.replace("wv", "")
        }

        val bridge = com.novelarrow.reader.browser.engine.BrowserBridge(
            context = context,
            onParagraphClickedCallback = { index -> viewModel.seekToParagraph(index) },
            onScrolledToBottomCallback = {
                if (viewModel.uiState.value.settings.autoClickOnScroll) {
                    viewModel.clickNextChapter()
                }
            },
            onLongPressClickableCallback = { selector, text, hostname ->
                viewModel.onLongPressClickable(selector, text, hostname)
            },
            onRegisterCustomButtonCallback = { payload ->
                viewModel.registerCustomToolbarButton(payload)
            },
            onSaveDownloadedChapterCallback = { novelTitle, chTitle, chNum, html ->
                viewModel.saveDownloadedChapterFromScript(novelTitle, chTitle, chNum, html)
            }
        )
        addJavascriptInterface(bridge, com.novelarrow.reader.browser.engine.BrowserBridge.INTERFACE_NAME)
        addJavascriptInterface(bridge, com.novelarrow.reader.browser.engine.BrowserBridge.INTERFACE_NAME_BROWSER)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { viewModel.onPageStarted(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    viewModel.onPageFinished(
                        url = it,
                        title = view?.title,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                viewModel.onPageProgress(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val currentUrl = view?.url ?: ""
                viewModel.onPageFinished(
                    url = currentUrl,
                    title = title,
                    canGoBack = view?.canGoBack() == true,
                    canGoForward = view?.canGoForward() == true
                )
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                if (resultMsg != null) {
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        transport.webView = view
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }
        }
    }
}

@Composable
private fun BrowserReaderView(
    title: String,
    paragraphs: List<Paragraph>,
    currentIndex: Int,
    isLoadingNext: Boolean,
    autoScroll: Boolean,
    onParagraphClick: (Int) -> Unit,
    onNextChapterClick: () -> Unit,
    onEndReached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (autoScroll && currentIndex in paragraphs.indices) {
            val itemIndex = if (title.isNotBlank()) currentIndex + 1 else currentIndex
            listState.animateScrollToItem(
                index = itemIndex,
                scrollOffset = -150
            )
        }
    }

    LaunchedEffect(listState, paragraphs.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 1 && lastVisible >= totalItems - 1
        }.collect { isAtBottom ->
            if (isAtBottom && !isLoadingNext && paragraphs.isNotEmpty()) {
                onEndReached()
            }
        }
    }

    if (paragraphs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppLoadingIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = "Extracting clean chapter text...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (title.isNotBlank()) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, top = 6.dp)
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
                val isCurrent = index == currentIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
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
                    Row(verticalAlignment = Alignment.Top) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        val textToDisplay = paragraph.spokenText.ifBlank { paragraph.rawText }
                        Text(
                            text = textToDisplay,
                            style = if (paragraph.isHeading) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp
                                )
                            },
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoadingNext) {
                        AppLoadingIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Loading next chapter...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        Button(
                            onClick = onNextChapterClick,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Next Chapter")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }
}

