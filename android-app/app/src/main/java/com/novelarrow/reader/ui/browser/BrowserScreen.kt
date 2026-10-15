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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenDownloadedNovel: (novelId: String) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackProgress by AudioPlaybackCoordinator.playbackProgressState.collectAsState()
    val isReaderActive = playbackProgress.source == PlaybackSource.READER && (playbackProgress.isPlaying || playbackProgress.novelId != null)
    val isMediaDockVisible = uiState.isLiveReading || isReaderActive
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

    BackHandler {
        if (uiState.isDownloading) {
            viewModel.stopDownloadLoop()
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
            // Re-designed 2-Row Top Bar: full width URL on row 1, clean action controls on row 2
            BrowserTopBar(
                inputUrl = uiState.inputUrl,
                isLoading = uiState.isLoading,
                canGoBack = uiState.canGoBack,
                canGoForward = uiState.canGoForward,
                isLiveReading = uiState.isLiveReading,
                isDownloading = uiState.isDownloading,
                tabCount = uiState.tabs.size,
                onBackClicked = { viewModel.goBack() },
                onForwardClicked = { viewModel.goForward() },
                onReloadClicked = { viewModel.reload() },
                onHomeClicked = { viewModel.goHome() },
                onNewTabClicked = { viewModel.openNewTab() },
                onTabsBadgeClicked = onOpenDrawer,
                onExtensionsClicked = { viewModel.openExtensionsSheet() },
                onLoopClicked = { viewModel.onLoopButtonClicked() },
                onSaveFrozenHtmlClicked = { viewModel.saveSingleChapter() },
                onRawCodeClicked = { viewModel.extractAndShowRawHtml() },
                onNextChapterClicked = { viewModel.clickNextChapter() },
                onUrlChanged = { viewModel.setInputUrl(it) },
                onUrlSubmitted = {
                    keyboardController?.hide()
                    viewModel.navigateTo(it)
                },
                onLiveReadClicked = { viewModel.toggleLiveReading() },
                onSettingsClicked = { viewModel.openSettingsDialog() },
                onOpenDrawer = onOpenDrawer
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
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Floating Batch Download Banner
        AnimatedVisibility(
            visible = uiState.isDownloading,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 100.dp, start = 16.dp, end = 16.dp)
        ) {
            DownloadProgressBanner(
                novelTitle = uiState.downloadNovelTitle,
                currentChapter = uiState.downloadCurrentChapter,
                remaining = uiState.downloadRemainingCount,
                total = uiState.downloadTotalCount,
                countdown = uiState.downloadCountdownSeconds,
                status = uiState.downloadStatus,
                onStop = { viewModel.stopDownloadLoop() }
            )
        }

        // TTS Player Dock - Reuses the exact same TtsDock component from ReaderScreen
        AnimatedVisibility(
            visible = isMediaDockVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (uiState.isLiveReading) {
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
                    onOpenAudioSettings = { showAudioSettingsDialog = true }
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
                    onOpenAudioSettings = { showAudioSettingsDialog = true }
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

        // Batch Download Setup Dialog
        if (uiState.showDownloadDialog) {
            DownloadSetupDialog(
                detectedTitle = uiState.detectedTitle,
                detectedChapter = uiState.detectedChapter,
                novelTitle = uiState.dialogNovelTitle,
                startingChapter = uiState.dialogStartingChapter,
                loopCount = uiState.dialogLoopCount,
                delayMs = uiState.dialogDelayMs,
                onTitleChange = { viewModel.setDialogNovelTitle(it) },
                onChapterChange = { viewModel.setDialogStartingChapter(it) },
                onLoopCountChange = { viewModel.setDialogLoopCount(it) },
                onDelayMsChange = { viewModel.setDialogDelayMs(it) },
                onConfirm = { viewModel.startDownloadLoop() },
                onDismiss = { viewModel.dismissDownloadDialog() }
            )
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
                onToggleTtsSynthByParagraph = { viewModel.toggleTtsSynthByParagraph(it) },
                onUpdateNextSelector = { viewModel.updateNextSelector(it) },
                onUpdateContentSelector = { viewModel.updateContentSelector(it) },
                onToggleAutoClickOnScroll = { viewModel.toggleAutoClickOnScroll(it) },
                onUpdateLoopCount = { viewModel.updateLoopCount(it) },
                onUpdateDelay = { viewModel.updateCountdownDelayMs(it) },
                onAddIgnoredSelector = { viewModel.addIgnoredSelector(it) },
                onRemoveIgnoredSelector = { viewModel.removeIgnoredSelector(it) },
                onResetIgnoredSelectors = { viewModel.resetIgnoredSelectors() },
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
                onCreateNewScript = { viewModel.openCodeMirrorEditor(null) },
                onSyncDisk = { viewModel.syncUserscriptsFromDisk() },
                onDismiss = { viewModel.dismissUserscriptManager() }
            )
        }

        // CodeMirror Editor Dialog
        if (uiState.showCodeMirrorEditor) {
            CodeMirrorEditorDialog(
                initialTitle = uiState.editorPrefilledTitle,
                initialContent = uiState.editorPrefilledCode,
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
    }
}

/**
 * Top Toolbar cleanly divided into 2 well-spaced rows:
 * Row 1: App Back button, Full-width URL bar, Tab count badge [N]
 * Row 2: Clean action row: Back, Forward, Reload, Home, New Tab, Loop, Save HTML, Download, Next Chapter, Read Aloud, Settings
 */
@Composable
private fun BrowserTopBar(
    inputUrl: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLiveReading: Boolean,
    isDownloading: Boolean,
    tabCount: Int,
    onBackClicked: () -> Unit,
    onForwardClicked: () -> Unit,
    onReloadClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    onNewTabClicked: () -> Unit,
    onTabsBadgeClicked: () -> Unit,
    onExtensionsClicked: () -> Unit,
    onLoopClicked: () -> Unit,
    onSaveFrozenHtmlClicked: () -> Unit,
    onRawCodeClicked: () -> Unit,
    onNextChapterClicked: () -> Unit,
    onUrlChanged: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onLiveReadClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onOpenDrawer: () -> Unit
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
            // ROW 1: Full-width URL bar with Menu button and Tab badge
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

                Spacer(modifier = Modifier.width(6.dp))

                // Extensions (🧩) Icon Button
                IconButton(
                    onClick = onExtensionsClicked,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = "Extensions & Userscripts",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ROW 2: Clean action toolbar with all browser & userscript controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // History Back
                IconButton(
                    onClick = onBackClicked,
                    enabled = canGoBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // History Forward
                IconButton(
                    onClick = onForwardClicked,
                    enabled = canGoForward,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Forward",
                        modifier = Modifier.size(20.dp),
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Reload
                IconButton(
                    onClick = onReloadClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                }

                // Home (Google)
                IconButton(
                    onClick = onHomeClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home (Google)", modifier = Modifier.size(18.dp))
                }

                // New Tab
                IconButton(
                    onClick = onNewTabClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Tab", modifier = Modifier.size(18.dp))
                }

                // Tab Switcher Badge [N]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTabsBadgeClicked() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "$tabCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Batch Download Loop (placed directly next to New Tab +)
                IconButton(
                    onClick = onLoopClicked,
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (isDownloading) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Batch Download Loop",
                        tint = if (isDownloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )

                // Single Chapter Download (Clean HTML)
                IconButton(
                    onClick = onSaveFrozenHtmlClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save Chapter HTML", modifier = Modifier.size(18.dp))
                }

                // Raw HTML Code Viewer (Monaco Editor)
                IconButton(
                    onClick = onRawCodeClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = "View Raw HTML (Monaco Editor)",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Next Chapter
                IconButton(
                    onClick = onNextChapterClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter", modifier = Modifier.size(20.dp))
                }

                // Read Aloud / TTS - Matching ReaderTopBar design
                IconButton(
                    onClick = onLiveReadClicked,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = "TTS Read Aloud",
                        tint = if (isLiveReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings Modal
                IconButton(
                    onClick = onSettingsClicked,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Browser Settings", modifier = Modifier.size(18.dp))
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
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (progress.coerceAtLeast(10)).toFloat() / 100f },
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(28.dp)
                    )
                }

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
 * Floating banner for batch downloading
 */
@Composable
private fun DownloadProgressBanner(
    novelTitle: String,
    currentChapter: Int,
    remaining: Int,
    total: Int,
    countdown: Int,
    status: String,
    onStop: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloading: $novelTitle",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Chapter $currentChapter • Remaining: $remaining / $total • $status",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Stop", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
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
 * Batch Download Setup Dialog
 */
@Composable
private fun DownloadSetupDialog(
    detectedTitle: String,
    detectedChapter: Int,
    novelTitle: String,
    startingChapter: Int,
    loopCount: Int,
    delayMs: Long,
    onTitleChange: (String) -> Unit,
    onChapterChange: (Int) -> Unit,
    onLoopCountChange: (Int) -> Unit,
    onDelayMsChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .padding(vertical = 16.dp),
        icon = {
            Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                "Batch Download Novel",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Autodetected from webpage. Saved cleanly into Downloads/NovelReader/$novelTitle/ in chronological order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Editable Novel Name
                OutlinedTextField(
                    value = novelTitle,
                    onValueChange = onTitleChange,
                    label = { Text("Novel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Editable Starting Chapter Number
                OutlinedTextField(
                    value = startingChapter.toString(),
                    onValueChange = { str ->
                        str.toIntOrNull()?.let { onChapterChange(it) }
                    },
                    label = { Text("Starting Chapter #") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Chapters to Download (Quantity / Iterations)
                OutlinedTextField(
                    value = loopCount.toString(),
                    onValueChange = { str ->
                        str.toIntOrNull()?.let { onLoopCountChange(it) }
                    },
                    label = { Text("Chapters to Download (Quantity)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Preset quantity chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 25, 50, 100, 200).forEach { preset ->
                        FilterChip(
                            selected = loopCount == preset,
                            onClick = { onLoopCountChange(preset) },
                            label = { Text("$preset") }
                        )
                    }
                }

                // Delay between chapters in ms
                OutlinedTextField(
                    value = delayMs.toString(),
                    onValueChange = { str ->
                        str.toLongOrNull()?.let { onDelayMsChange(it) }
                    },
                    label = { Text("Delay Between Chapters (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Delay preset chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500L, 1000L, 1500L, 2000L, 3000L).forEach { ms ->
                        FilterChip(
                            selected = delayMs == ms,
                            onClick = { onDelayMsChange(ms) },
                            label = { Text("${ms}ms") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Batch Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
    onToggleTtsSynthByParagraph: (Boolean) -> Unit,
    onUpdateNextSelector: (String) -> Unit,
    onUpdateContentSelector: (String) -> Unit,
    onToggleAutoClickOnScroll: (Boolean) -> Unit,
    onUpdateLoopCount: (Int) -> Unit,
    onUpdateDelay: (Long) -> Unit,
    onAddIgnoredSelector: (String) -> Unit,
    onRemoveIgnoredSelector: (String) -> Unit,
    onResetIgnoredSelectors: () -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var nextSelectorText by remember(settings.nextSelector) { mutableStateOf(settings.nextSelector) }
    var contentSelectorText by remember(settings.contentSelector) { mutableStateOf(settings.contentSelector) }

    // Ignored selector input
    var newIgnoredSelector by remember { mutableStateOf("") }

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
            ) {
                // Scrollable Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Auto-Run & Loop", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Reading", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Filter (Ignored)", fontSize = 13.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // --- AUTO-RUN / LOOP TAB ---
                            OutlinedTextField(
                                value = nextSelectorText,
                                onValueChange = {
                                    nextSelectorText = it
                                    onUpdateNextSelector(it)
                                },
                                label = { Text("Next Chapter Selector") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 2,
                                maxLines = 3
                            )

                            OutlinedTextField(
                                value = contentSelectorText,
                                onValueChange = {
                                    contentSelectorText = it
                                    onUpdateContentSelector(it)
                                },
                                label = { Text("Content Container (Optional)") },
                                placeholder = { Text("e.g. #chapter-content or blank") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

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

                            HorizontalDivider()

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
                        }

                        1 -> {
                            // --- READING AUTOMATION TAB ---
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
                        }

                        2 -> {
                            // --- FILTER / IGNORED SELECTORS TAB ---
                            Text(
                                text = "Ignored Elements (Skipped by TTS)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Elements matching these CSS selectors will be stripped out when extracting chapter text.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            // Add selector input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newIgnoredSelector,
                                    onValueChange = { newIgnoredSelector = it },
                                    label = { Text("CSS Selector (e.g. .author-note)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (newIgnoredSelector.isNotBlank()) {
                                            onAddIgnoredSelector(newIgnoredSelector.trim())
                                            newIgnoredSelector = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add")
                                }
                            }

                            // Chips list of ignored selectors
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                settings.ignoredSelectors.forEach { selector ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                        ) {
                                            Text(selector, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { onRemoveIgnoredSelector(selector) },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = onResetIgnoredSelectors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset to Default Ignored Elements")
                            }
                        }
                    }
                }
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

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: android.content.Context,
    viewModel: BrowserViewModel
): WebView {
    return WebView(context).apply {
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
        }
    }
}
