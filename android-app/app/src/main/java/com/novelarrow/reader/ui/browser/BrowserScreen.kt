package com.novelarrow.reader.ui.browser

import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.browser.components.CenteredDownloadProgressCard
import com.novelarrow.reader.ui.reader.TtsDock

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
    onNavigateToHistory: (() -> Unit)? = null,
    onNavigateToDownloads: (() -> Unit)? = null,
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
                is BrowserAction.LoadDataWithBaseUrl -> wv.loadDataWithBaseURL(action.baseUrl, action.data, action.mimeType, action.encoding, action.historyUrl)
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
                onHistoryClicked = { onNavigateToHistory?.invoke() ?: viewModel.openHistoryDialog() },
                onDownloadsClicked = { onNavigateToDownloads?.invoke() }
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

            // WebView Box
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
                        wv.keepAliveInBackground = uiState.isLiveReading || uiState.isDownloading
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
                        autoNextOnScroll = uiState.settings.autoClickOnScroll,
                        onParagraphClick = { index -> viewModel.onReaderModeParagraphClicked(index) },
                        onNextChapterClick = { viewModel.clickNextChapter() },
                        onEndReached = { viewModel.onReaderModeEndReached() },
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

        // TTS Player Dock
        AnimatedVisibility(
            visible = isMediaDockVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (uiState.isLiveReading || (uiState.flashHandleRed && !isReaderActive)) {
                TtsDock(
                    currentParagraph = if (uiState.isLiveReading) uiState.liveCurrentParagraphIndex else uiState.readerModeCurrentParagraphIndex,
                    totalParagraphs = if (uiState.readerModeParagraphs.isNotEmpty()) uiState.readerModeParagraphs.size else uiState.liveParagraphs.size,
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
                    onSeekParagraph = { para ->
                        if (uiState.isReaderMode) {
                            viewModel.onReaderModeParagraphClicked(para)
                        } else {
                            viewModel.seekToParagraph(para)
                        }
                    },
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

        // Multi-Tab Switcher: slides the library sidebar open
        LaunchedEffect(uiState.showTabSwitcher) {
            if (uiState.showTabSwitcher) {
                onOpenDrawer()
                viewModel.dismissTabSwitcher()
            }
        }

        BrowserDialogs(
            uiState = uiState,
            viewModel = viewModel,
            showAudioSettingsDialog = showAudioSettingsDialog,
            onDismissAudioSettingsDialog = { showAudioSettingsDialog = false },
            onNavigateToPronunciationRules = onNavigateToPronunciationRules,
            onNavigateToSkipWords = onNavigateToSkipWords,
            onNavigateToVoiceEngine = onNavigateToVoiceEngine,
            onNavigateToHeaderFooterSkips = onNavigateToHeaderFooterSkips,
            onNavigateToContentSelector = onNavigateToContentSelector,
            onNavigateToNextSelector = onNavigateToNextSelector,
            onNavigateToIgnoredSelectors = onNavigateToIgnoredSelectors,
            onNavigateToReorderButtons = onNavigateToReorderButtons
        )
    }
}
