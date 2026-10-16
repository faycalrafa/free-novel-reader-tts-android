package com.novelarrow.reader.ui.browser

import android.app.Application
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelarrow.reader.browser.engine.ExtractedContent
import com.novelarrow.reader.browser.engine.RunAt
import com.novelarrow.reader.browser.engine.WebContentExtractor
import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.userscript.UserscriptEntity
import com.novelarrow.reader.data.userscript.UserscriptMetadataParser
import com.novelarrow.reader.data.userscript.UserscriptRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

sealed interface BrowserAction {
    data class LoadUrl(val url: String) : BrowserAction
    object GoBack : BrowserAction
    object GoForward : BrowserAction
    object Reload : BrowserAction
    data class EvaluateJavascript(val script: String, val callback: ((String?) -> Unit)? = null) : BrowserAction
}

typealias WebViewCommand = BrowserAction

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "https://www.google.com",
    val title: String = "Google"
)

data class BrowserUiState(
    // Multi-tab state
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val currentTabId: String = tabs.first().id,
    val showTabSwitcher: Boolean = false,

    // Current page state
    val currentUrl: String = "https://www.google.com",
    val inputUrl: String = "https://www.google.com",
    val pageTitle: String = "Google",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val settings: WebBrowserSettings = WebBrowserSettings(),
    val toastMessage: String? = null,

    // Autodetection & Setup Dialog
    val showDownloadDialog: Boolean = false,
    val detectedTitle: String = "",
    val detectedChapter: Int = 1,
    val dialogNovelTitle: String = "",
    val dialogStartingChapter: Int = 1,
    val dialogLoopCount: Int = 100,
    val dialogDelayMs: Long = 1000L,

    // Batch Download Loop state
    val isDownloading: Boolean = false,
    val downloadNovelTitle: String = "",
    val downloadCurrentChapter: Int = 1,
    val downloadRemainingCount: Int = 0,
    val downloadTotalCount: Int = 0,
    val downloadCountdownSeconds: Int = 0,
    val downloadStatus: String = "",

    // Userscript Auto-Save Loop state
    val isAutoLoopActive: Boolean = false,
    val autoLoopCountdownSeconds: Int = 0,
    val autoLoopRemainingCount: Int = 0,
    val autoLoopStatus: String = "",

    // Live Reading state (matches ReaderScreen TtsDock)
    val isLiveReading: Boolean = false,
    val isLivePlaying: Boolean = false,
    val liveParagraphs: List<Paragraph> = emptyList(),
    val liveCurrentParagraphIndex: Int = 0,
    val liveChapterTitle: String = "",
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val availableVoices: List<Voice> = emptyList(),

    // Browser Settings Dialog
    val showSettingsDialog: Boolean = false,
    val isVoiceTesting: Boolean = false,

    // Sleep Timer Dialog
    val showSleepTimerDialog: Boolean = false,

    // Raw Code Editor Dialog
    val showRawEditor: Boolean = false,
    val rawHtmlContent: String? = null,

    // Last downloaded novel reference
    val lastDownloadedNovel: Novel? = null,

    // Extensions & Userscripts
    val showExtensionsSheet: Boolean = false,
    val showUserscriptManager: Boolean = false,
    val showCodeMirrorEditor: Boolean = false,
    val showDocumentationDialog: Boolean = false,
    val editingUserscript: UserscriptEntity? = null,
    val editorPrefilledCode: String = "",
    val editorPrefilledTitle: String = "",
    val allUserscripts: List<UserscriptEntity> = emptyList(),
    val activeUserscripts: List<UserscriptEntity> = emptyList(),

    // Reading Mode (Reader View)
    val isReaderMode: Boolean = false,
    val readerModeTitle: String = "",
    val readerModeParagraphs: List<Paragraph> = emptyList(),
    val readerModeCurrentParagraphIndex: Int = 0,
    val readerModeIsLoadingNext: Boolean = false
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val userscriptRepository = UserscriptRepository(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    // Decoupled one-off action channel to WebView Composable (prevents WebView memory leaks)
    private val _browserActions = Channel<BrowserAction>(Channel.BUFFERED)
    val browserActions: Flow<BrowserAction> = _browserActions.receiveAsFlow()
    val webViewCommands: Flow<WebViewCommand> get() = browserActions

    private var downloadCountdownJob: Job? = null
    private var autoLoopJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var retryExtractionJob: Job? = null

    private var lastBatchDownloadedUrl: String = ""
    private var lastBatchDownloadedChapterNum: Int = -1
    private var duplicatePageRetryCount: Int = 0

    companion object {
        private const val TAG = "BrowserViewModel"
        private const val DEFAULT_URL = "https://www.google.com"
    }

    private fun dispatchLoadUrl(url: String) {
        _browserActions.trySend(BrowserAction.LoadUrl(url))
    }

    private fun dispatchEvaluateJs(script: String, callback: ((String?) -> Unit)? = null) {
        _browserActions.trySend(BrowserAction.EvaluateJavascript(script, callback))
    }

    fun playClickSound() {
        // Compose UI emits native view sound effects without ToneGenerator allocations
    }

    init {
        AudioPlaybackCoordinator.initialize(application)
        val initialSettings = settingsRepository.getBrowserSettings()
        val startUrl = if (initialSettings.lastUrl.isNotBlank() && initialSettings.lastUrl != "https://novelbin.me") {
            initialSettings.lastUrl
        } else {
            DEFAULT_URL
        }
        val initialTab = BrowserTab(url = startUrl, title = if (startUrl.contains("google")) "Google" else "Web Page")

        _uiState.update {
            it.copy(
                settings = initialSettings,
                currentUrl = startUrl,
                inputUrl = startUrl,
                tabs = listOf(initialTab),
                currentTabId = initialTab.id
            )
        }

        viewModelScope.launch {
            settingsRepository.browserSettingsFlow.collect { newSettings ->
                _uiState.update {
                    it.copy(
                        settings = newSettings,
                        sleepTimerMinutes = newSettings.sleepTimerMinutes,
                        sleepTimerRemainingSeconds = if (it.sleepTimerRemainingSeconds == 0) newSettings.sleepTimerMinutes * 60 else it.sleepTimerRemainingSeconds
                    )
                }
            }
        }

        viewModelScope.launch {
            userscriptRepository.scriptsFlow.collect { scripts ->
                val curUrl = _uiState.value.currentUrl
                val active = scripts.filter { script ->
                    script.isEnabled && script.matchPatterns.any { pattern ->
                        WebScriptInjector.matchesPattern(curUrl, pattern)
                    }
                }
                _uiState.update { it.copy(allUserscripts = scripts, activeUserscripts = active) }
            }
        }

        observePlaybackService()

        AudioPlaybackCoordinator.browserNextChapterCallback = { clickNextChapter() }
        AudioPlaybackCoordinator.browserPrevChapterCallback = { clickPrevChapter() }
    }

    private fun observePlaybackService() {
        viewModelScope.launch {
            AudioPlaybackCoordinator.playbackProgressState.collect { progress ->
                if (progress.source == PlaybackSource.BROWSER) {
                    val idx = progress.currentParagraphIndex
                    _uiState.update {
                        it.copy(
                            isLivePlaying = progress.isPlaying,
                            liveCurrentParagraphIndex = idx
                        )
                    }
                    highlightWebParagraph(idx)
                    val paras = _uiState.value.liveParagraphs
                    if (!progress.isPlaying && idx >= paras.size - 1 && paras.isNotEmpty()) {
                        handleLiveChapterEnd()
                    }
                }
            }
        }
        viewModelScope.launch {
            AudioPlaybackCoordinator.availableVoicesState.collect { voices ->
                if (voices.isNotEmpty()) {
                    _uiState.update { it.copy(availableVoices = voices) }
                }
            }
        }
        viewModelScope.launch {
            AudioPlaybackCoordinator.sleepTimerMinutesState.collect { mins ->
                _uiState.update { it.copy(sleepTimerMinutes = mins) }
            }
        }
        viewModelScope.launch {
            AudioPlaybackCoordinator.sleepTimerRemainingSecondsState.collect { secs ->
                _uiState.update { it.copy(sleepTimerRemainingSeconds = secs) }
            }
        }
    }

    // =========================================================================
    // Multi-Tab Management
    // =========================================================================

    fun openNewTab(url: String = DEFAULT_URL) {
        val newTab = BrowserTab(url = url, title = if (url.contains("google")) "Google" else "New Tab")
        val updatedTabs = _uiState.value.tabs + newTab
        _uiState.update {
            it.copy(
                tabs = updatedTabs,
                currentTabId = newTab.id,
                currentUrl = url,
                inputUrl = url,
                pageTitle = newTab.title,
                showTabSwitcher = false
            )
        }
        dispatchLoadUrl(url)
    }

    fun selectTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                currentTabId = tabId,
                currentUrl = tab.url,
                inputUrl = tab.url,
                pageTitle = tab.title,
                showTabSwitcher = false
            )
        }
        dispatchLoadUrl(tab.url)
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.size <= 1) {
            val resetTab = BrowserTab(url = DEFAULT_URL, title = "Google")
            _uiState.update {
                it.copy(
                    tabs = listOf(resetTab),
                    currentTabId = resetTab.id,
                    currentUrl = DEFAULT_URL,
                    inputUrl = DEFAULT_URL,
                    pageTitle = "Google",
                    showTabSwitcher = false
                )
            }
            dispatchLoadUrl(DEFAULT_URL)
            return
        }

        val updatedTabs = currentTabs.filterNot { it.id == tabId }
        val newActiveTab = if (_uiState.value.currentTabId == tabId) {
            updatedTabs.last()
        } else {
            updatedTabs.firstOrNull { it.id == _uiState.value.currentTabId } ?: updatedTabs.last()
        }

        _uiState.update {
            it.copy(
                tabs = updatedTabs,
                currentTabId = newActiveTab.id,
                currentUrl = newActiveTab.url,
                inputUrl = newActiveTab.url,
                pageTitle = newActiveTab.title
            )
        }

        if (_uiState.value.currentTabId != newActiveTab.id) {
            dispatchLoadUrl(newActiveTab.url)
        }
    }

    fun toggleTabSwitcher() {
        _uiState.update { it.copy(showTabSwitcher = !it.showTabSwitcher) }
    }

    fun dismissTabSwitcher() {
        _uiState.update { it.copy(showTabSwitcher = false) }
    }

    // =========================================================================
    // URL and Navigation
    // =========================================================================

    fun setInputUrl(url: String) {
        _uiState.update { it.copy(inputUrl = url) }
    }

    fun navigateTo(url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = if (cleanUrl.contains(".") && !cleanUrl.contains(" ")) {
                "https://$cleanUrl"
            } else {
                "https://www.google.com/search?q=${java.net.URLEncoder.encode(cleanUrl, "UTF-8")}"
            }
        }
        _uiState.update {
            it.copy(
                currentUrl = cleanUrl,
                inputUrl = cleanUrl
            )
        }
        updateCurrentTabUrl(cleanUrl, _uiState.value.pageTitle)
        dispatchLoadUrl(cleanUrl)
        saveLastUrl(cleanUrl)
    }

    fun goHome() {
        navigateTo(DEFAULT_URL)
    }

    fun goBack() {
        _browserActions.trySend(BrowserAction.GoBack)
    }

    fun goForward() {
        _browserActions.trySend(BrowserAction.GoForward)
    }

    fun reload() {
        _browserActions.trySend(BrowserAction.Reload)
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    private fun updateCurrentTabUrl(url: String, title: String) {
        val currentTabId = _uiState.value.currentTabId
        val updatedTabs = _uiState.value.tabs.map { tab ->
            if (tab.id == currentTabId) {
                tab.copy(url = url, title = title.ifBlank { tab.title })
            } else tab
        }
        _uiState.update { it.copy(tabs = updatedTabs) }
    }

    private fun saveLastUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(lastUrl = url) }
        }
    }

    fun onPageStarted(url: String, canGoBack: Boolean = false, canGoForward: Boolean = false) {
        if (_uiState.value.isLiveReading) {
            AudioPlaybackCoordinator.pause()
            clearWebHighlight()
            _uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0, isLivePlaying = false) }
        }
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                isLoading = true,
                progress = 15,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        updateCurrentTabUrl(url, _uiState.value.pageTitle)

        // Inject userscripts configured to run at document start
        val startScripts = WebScriptInjector.buildUserscriptInjectionCode(url, RunAt.DOCUMENT_START)
        if (startScripts.isNotBlank()) {
            dispatchEvaluateJs(startScripts)
        }
        injectFileUserscripts(url, "document-start")
    }

    fun onPageProgress(progress: Int) {
        _uiState.update {
            it.copy(
                progress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean = false, canGoForward: Boolean = false) {
        val safeTitle = title.orEmpty().ifBlank { if (url.contains("google")) "Google" else "Web Page" }
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                pageTitle = safeTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        updateCurrentTabUrl(url, safeTitle)
        saveLastUrl(url)

        // Inject JS helper and document-end userscripts
        injectHelperScript(url)

        // 1. If batch download is active, trigger sequence and NEVER trigger TTS
        if (_uiState.value.isDownloading) {
            triggerBatchDownloadSequence()
            return
        }

        // 2. If in Reading Mode, automatically re-extract the new chapter content for distraction-free reading
        if (_uiState.value.isReaderMode) {
            viewModelScope.launch {
                delay(500)
                enterReadingMode()
                if (_uiState.value.isLiveReading || _uiState.value.settings.autoReadNextChapter) {
                    startLiveReadingInternal(forceStartAtBeginning = true)
                }
            }
        } else if (_uiState.value.settings.autoReadNextChapter && (_uiState.value.isLiveReading || _uiState.value.settings.ttsAutoStart)) {
            viewModelScope.launch {
                delay(700)
                startLiveReadingInternal(forceStartAtBeginning = true)
            }
        }
    }

    // =========================================================================
    // JavaScript Bridge & Helpers
    // =========================================================================

    private fun injectHelperScript(url: String) {
        dispatchEvaluateJs(WebScriptInjector.buildHelperScript())
        val endScripts = WebScriptInjector.buildUserscriptInjectionCode(url, RunAt.DOCUMENT_END)
        if (endScripts.isNotBlank()) {
            dispatchEvaluateJs(endScripts)
        }
        injectFileUserscripts(url, "document-end")
        injectFileUserscripts(url, "document-idle")
    }

    private fun injectFileUserscripts(url: String, runAtTarget: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val scripts = userscriptRepository.getScriptsForUrl(url).filter {
                it.runAt.equals(runAtTarget, ignoreCase = true)
            }
            for (script in scripts) {
                val code = userscriptRepository.getScriptCode(script)
                if (code.isNotBlank()) {
                    val safeName = script.name.replace("*/", "* /")
                    val wrapped = "(function() {\n  try {\n    /* Userscript: $safeName */\n$code\n  } catch (err) {\n    console.error('Error executing userscript [$safeName]:', err);\n  }\n})();"
                    dispatchEvaluateJs(wrapped)
                }
            }
        }
    }

    fun buildContentExtractionScript(contentSelector: String, ignoredSelectors: List<String>): String {
        return WebContentExtractor.buildContentExtractionScript(contentSelector, ignoredSelectors)
    }

    private fun highlightWebParagraph(index: Int) {
        val autoScroll = _uiState.value.settings.ttsAutoScroll
        dispatchEvaluateJs(WebScriptInjector.buildHighlightScript(index, autoScroll))
    }

    private fun clearWebHighlight() {
        dispatchEvaluateJs(WebScriptInjector.buildClearHighlightScript())
    }

    fun clickNextChapter() {
        playClickSound()
        if (_uiState.value.isLiveReading) {
            clearWebHighlight()
            _uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0) }
        }
        val nextSelector = _uiState.value.settings.nextSelector
        dispatchEvaluateJs(WebScriptInjector.buildNextChapterScript(nextSelector))
    }

    fun clickPrevChapter() {
        playClickSound()
        if (_uiState.value.isLiveReading) {
            clearWebHighlight()
            _uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0) }
        }
        dispatchEvaluateJs(WebScriptInjector.buildPrevChapterScript())
    }

    // =========================================================================
    // Autodetection & Setup Dialog
    // =========================================================================

    fun openDownloadDialog() {
        val currentUrl = _uiState.value.currentUrl
        val pageTitle = _uiState.value.pageTitle
        val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
        val settings = _uiState.value.settings

        _uiState.update {
            it.copy(
                showDownloadDialog = true,
                detectedTitle = detected.novelTitle,
                detectedChapter = detected.chapterNumber,
                dialogNovelTitle = detected.novelTitle,
                dialogStartingChapter = detected.chapterNumber,
                dialogLoopCount = settings.loopCount,
                dialogDelayMs = settings.countdownDelayMs
            )
        }
    }

    fun dismissDownloadDialog() {
        _uiState.update { it.copy(showDownloadDialog = false) }
    }

    fun setDialogNovelTitle(title: String) {
        _uiState.update { it.copy(dialogNovelTitle = title) }
    }

    fun setDialogStartingChapter(chapter: Int) {
        _uiState.update { it.copy(dialogStartingChapter = chapter.coerceAtLeast(1)) }
    }

    fun setDialogLoopCount(count: Int) {
        _uiState.update { it.copy(dialogLoopCount = count.coerceAtLeast(1)) }
    }

    fun setDialogDelayMs(delayMs: Long) {
        _uiState.update { it.copy(dialogDelayMs = delayMs.coerceAtLeast(200L)) }
    }

    // =========================================================================
    // Batch Download Loop
    // =========================================================================

    fun onLoopButtonClicked() {
        if (_uiState.value.isDownloading) {
            stopDownloadLoop()
        } else {
            openDownloadDialog()
        }
    }

    fun toggleAutoLoop() = onLoopButtonClicked()

    fun startDownloadLoop() {
        val state = _uiState.value
        val title = state.dialogNovelTitle.trim().ifBlank { state.detectedTitle }
        val startingChapter = state.dialogStartingChapter
        val loopCount = state.dialogLoopCount
        val delayMs = state.dialogDelayMs.coerceAtLeast(200L)

        lastBatchDownloadedUrl = ""
        lastBatchDownloadedChapterNum = -1
        duplicatePageRetryCount = 0

        _uiState.update {
            it.copy(
                showDownloadDialog = false,
                isDownloading = true,
                downloadNovelTitle = title,
                downloadCurrentChapter = startingChapter,
                downloadRemainingCount = loopCount,
                downloadTotalCount = loopCount,
                downloadCountdownSeconds = 0,
                downloadStatus = "Starting download loop..."
            )
        }

        viewModelScope.launch {
            settingsRepository.updateBrowserSettings {
                it.copy(loopCount = loopCount, countdownDelayMs = delayMs)
            }
        }

        if (!state.isLoading && state.progress == 100) {
            triggerBatchDownloadSequence()
        } else {
            _uiState.update { it.copy(downloadStatus = "Waiting for page to fully load...") }
        }
    }

    fun stopDownloadLoop() {
        downloadCountdownJob?.cancel()
        downloadCountdownJob = null
        duplicatePageRetryCount = 0
        lastBatchDownloadedUrl = ""
        lastBatchDownloadedChapterNum = -1
        val novelTitle = _uiState.value.downloadNovelTitle
        if (novelTitle.isNotBlank()) {
            viewModelScope.launch {
                libraryRepository.commitBatch(novelTitle)
            }
        }
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadStatus = "Stopped",
                downloadCountdownSeconds = 0,
                toastMessage = "Batch download stopped."
            )
        }
    }

    fun stopAutoLoop() = stopDownloadLoop()

    private fun triggerBatchDownloadSequence() {
        if (!_uiState.value.isDownloading) return
        downloadCountdownJob?.cancel()

        downloadCountdownJob = viewModelScope.launch {
            val currentUrl = _uiState.value.currentUrl
            val pageTitle = _uiState.value.pageTitle
            val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
            val novelTitle = _uiState.value.downloadNovelTitle.ifBlank { detected.novelTitle }

            val chapterNum = if (_uiState.value.downloadCurrentChapter > 0) {
                _uiState.value.downloadCurrentChapter
            } else {
                detected.chapterNumber
            }

            val isDuplicateUrl = (lastBatchDownloadedUrl.isNotBlank() && lastBatchDownloadedUrl == currentUrl)
            val chapterAlreadyExists = WebNovelDownloader.chapterAlreadyExists(getApplication(), novelTitle, chapterNum, currentUrl) ||
                    (detected.chapterNumber > 0 && WebNovelDownloader.chapterAlreadyExists(getApplication(), novelTitle, detected.chapterNumber, currentUrl))

            if (isDuplicateUrl) {
                duplicatePageRetryCount++
                if (duplicatePageRetryCount == 1) {
                    for (sec in 5 downTo 1) {
                        if (!_uiState.value.isDownloading) return@launch
                        _uiState.update {
                            it.copy(
                                downloadCountdownSeconds = sec,
                                downloadStatus = "Page URL unchanged. Retrying next chapter in ${sec}s..."
                            )
                        }
                        delay(1000)
                    }
                    if (!_uiState.value.isDownloading) return@launch
                    _uiState.update { it.copy(downloadStatus = "Retrying next chapter...") }
                    clickNextChapter()
                    delay(3000)
                    if (_uiState.value.isDownloading && _uiState.value.currentUrl == currentUrl) {
                        triggerBatchDownloadSequence()
                    }
                    return@launch
                } else if (duplicatePageRetryCount == 2) {
                    for (sec in 10 downTo 1) {
                        if (!_uiState.value.isDownloading) return@launch
                        _uiState.update {
                            it.copy(
                                downloadCountdownSeconds = sec,
                                downloadStatus = "Page still unchanged. Waiting ${sec}s before 2nd retry..."
                            )
                        }
                        delay(1000)
                    }
                    if (!_uiState.value.isDownloading) return@launch
                    _uiState.update { it.copy(downloadStatus = "Retrying next chapter (attempt 2)...") }
                    clickNextChapter()
                    delay(3000)
                    if (_uiState.value.isDownloading && _uiState.value.currentUrl == currentUrl) {
                        triggerBatchDownloadSequence()
                    }
                    return@launch
                } else {
                    stopDownloadLoop()
                    _uiState.update {
                        it.copy(
                            downloadStatus = "Stopped: Chapter did not advance",
                            toastMessage = "Batch download stopped: end of chapters reached or page did not advance."
                        )
                    }
                    return@launch
                }
            }

            if (chapterAlreadyExists) {
                Log.d(TAG, "Chapter $chapterNum already exists on disk. Skipping download to avoid duplicates.")
                duplicatePageRetryCount = 0
                val remaining = _uiState.value.downloadRemainingCount
                val newRemaining = remaining - 1
                val nextChapterNum = chapterNum + 1
                lastBatchDownloadedUrl = currentUrl
                lastBatchDownloadedChapterNum = chapterNum

                _uiState.update {
                    it.copy(
                        downloadRemainingCount = newRemaining,
                        downloadCurrentChapter = nextChapterNum,
                        downloadStatus = "Chapter $chapterNum already exists, advancing to next..."
                    )
                }

                if (newRemaining <= 0) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Completed",
                            toastMessage = "Batch download completed for $novelTitle!"
                        )
                    }
                } else {
                    delay(600)
                    clickNextChapter()
                }
                return@launch
            }

            duplicatePageRetryCount = 0

            val delayMs = _uiState.value.settings.countdownDelayMs.coerceAtLeast(200L)
            val totalSteps = (delayMs / 100).toInt().coerceAtLeast(1)
            for (step in totalSteps downTo 1) {
                if (!_uiState.value.isDownloading) return@launch
                val remainingMs = step * 100L
                _uiState.update {
                    it.copy(
                        downloadCountdownSeconds = ((remainingMs + 999) / 1000).toInt(),
                        downloadStatus = "Page loaded. Waiting ${remainingMs}ms..."
                    )
                }
                delay(100)
            }
            if (!_uiState.value.isDownloading) return@launch
            _uiState.update {
                it.copy(
                    downloadCountdownSeconds = 0,
                    downloadStatus = "Downloading Chapter ${_uiState.value.downloadCurrentChapter}..."
                )
            }
            extractAndSaveChapter(isBatch = true)
        }
    }

    fun extractAndSaveChapter(isBatch: Boolean) {
        if (!isBatch) {
            _uiState.update { it.copy(toastMessage = "Extracting chapter content...") }
        }
        val settings = _uiState.value.settings
        val script = WebContentExtractor.buildContentExtractionScript(settings.contentSelector, settings.ignoredSelectors)
        dispatchEvaluateJs(script) { result ->
            handleExtractedChapter(result, isBatch)
        }
    }

    private fun handleExtractedChapter(result: String?, isBatch: Boolean) {
        val currentUrl = _uiState.value.currentUrl
        val pageTitle = _uiState.value.pageTitle
        val extracted = WebContentExtractor.extractChapterContent(result, pageTitle)
        val title = extracted.title
        val paragraphs = extracted.paragraphs

        if (!isBatch) {
            val detected = WebNovelDownloader.detectNovelInfo(currentUrl, title)
            if (WebNovelDownloader.chapterAlreadyExists(getApplication(), detected.novelTitle, detected.chapterNumber, currentUrl)) {
                _uiState.update {
                    it.copy(toastMessage = "Chapter ${detected.chapterNumber} already downloaded for ${detected.novelTitle}")
                }
                return
            }
            val chapterTitle = title.ifBlank { "${detected.novelTitle} Chapter ${detected.chapterNumber}" }
            viewModelScope.launch {
                try {
                    val savedFile = WebNovelDownloader.saveChapter(
                        context = getApplication(),
                        novelTitle = detected.novelTitle,
                        chapterTitle = chapterTitle,
                        chapterNumber = detected.chapterNumber,
                        paragraphs = paragraphs,
                        sourceUrl = currentUrl
                    )
                    val updatedNovel = libraryRepository.addDownloadedChapter(
                        novelTitle = detected.novelTitle,
                        chapterFile = savedFile,
                        chapterTitle = chapterTitle,
                        chapterNumber = detected.chapterNumber,
                        sourceUrl = currentUrl,
                        commitNow = true
                    )
                    _uiState.update {
                        it.copy(
                            lastDownloadedNovel = updatedNovel,
                            toastMessage = "Saved Chapter ${detected.chapterNumber} to Downloads/NovelReader/${detected.novelTitle}!"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving single page: ${e.message}")
                    _uiState.update { it.copy(toastMessage = "Error saving page: ${e.message}") }
                }
            }
        } else {
            if (!_uiState.value.isDownloading) return
            val state = _uiState.value
            val novelTitle = state.downloadNovelTitle
            val chapterNum = state.downloadCurrentChapter
            val remaining = state.downloadRemainingCount
            val chapterTitle = title.ifBlank { "$novelTitle Chapter $chapterNum" }

            if (WebNovelDownloader.chapterAlreadyExists(getApplication(), novelTitle, chapterNum, currentUrl)) {
                Log.d(TAG, "Batch download: Chapter $chapterNum already exists on disk. Skipping file write.")
                val newRemaining = remaining - 1
                val nextChapterNum = chapterNum + 1
                _uiState.update {
                    it.copy(
                        downloadRemainingCount = newRemaining,
                        downloadCurrentChapter = nextChapterNum,
                        downloadStatus = "Chapter $chapterNum already saved, advancing to next..."
                    )
                }
                if (newRemaining <= 0) {
                    viewModelScope.launch {
                        libraryRepository.commitBatch(novelTitle)
                    }
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Completed",
                            toastMessage = "Finished batch download for $novelTitle!"
                        )
                    }
                } else {
                    clickNextChapter()
                }
                return
            }

            viewModelScope.launch {
                try {
                    val savedFile = WebNovelDownloader.saveChapter(
                        context = getApplication(),
                        novelTitle = novelTitle,
                        chapterTitle = chapterTitle,
                        chapterNumber = chapterNum,
                        paragraphs = paragraphs,
                        sourceUrl = currentUrl
                    )
                    val updatedNovel = libraryRepository.addDownloadedChapter(
                        novelTitle = novelTitle,
                        chapterFile = savedFile,
                        chapterTitle = chapterTitle,
                        chapterNumber = chapterNum,
                        sourceUrl = currentUrl,
                        commitNow = false
                    )
                    lastBatchDownloadedUrl = currentUrl
                    lastBatchDownloadedChapterNum = chapterNum

                    val newRemaining = remaining - 1
                    val nextChapterNum = chapterNum + 1

                    _uiState.update {
                        it.copy(
                            downloadRemainingCount = newRemaining,
                            downloadCurrentChapter = nextChapterNum,
                            lastDownloadedNovel = updatedNovel,
                            downloadStatus = "Saved Chapter $chapterNum (100%)"
                        )
                    }

                    if (newRemaining <= 0) {
                        libraryRepository.commitBatch(novelTitle)
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadStatus = "Completed",
                                toastMessage = "Successfully downloaded ${it.downloadTotalCount} chapters for $novelTitle!"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(downloadStatus = "Loading next chapter...") }
                        clickNextChapter()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save downloaded chapter: ${e.message}")
                    _uiState.update { it.copy(toastMessage = "Error saving chapter: ${e.message}") }
                }
            }
        }
    }

    fun saveSingleChapter() {
        val currentUrl = _uiState.value.currentUrl
        val pageTitle = _uiState.value.pageTitle
        val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
        if (WebNovelDownloader.chapterAlreadyExists(getApplication(), detected.novelTitle, detected.chapterNumber, currentUrl)) {
            _uiState.update {
                it.copy(toastMessage = "Chapter ${detected.chapterNumber} already downloaded for ${detected.novelTitle}")
            }
            return
        }
        extractAndSaveChapter(isBatch = false)
    }

    fun saveFrozenHtml() = saveSingleChapter()

    // =========================================================================
    // Live Reading via Native TTS (Matching ReaderScreen TtsDock behavior)
    // =========================================================================

    fun toggleLiveReading() {
        if (_uiState.value.isLivePlaying) {
            pauseLiveReading()
        } else if (_uiState.value.isLiveReading && _uiState.value.liveParagraphs.isNotEmpty()) {
            resumeLiveReading()
        } else {
            startLiveReading()
        }
    }

    // =========================================================================
    // In-Browser Reading Mode (Reader View)
    // =========================================================================

    fun toggleReadingMode() {
        if (_uiState.value.isReaderMode) {
            exitReadingMode()
        } else {
            enterReadingMode()
        }
    }

    fun enterReadingMode() {
        val settings = _uiState.value.settings
        val script = WebContentExtractor.buildContentExtractionScript(settings.contentSelector, settings.ignoredSelectors)

        dispatchEvaluateJs(script) { result ->
            val extracted = WebContentExtractor.extractChapterContent(result, _uiState.value.pageTitle)
            val title = extracted.title
            val rawList = extracted.paragraphs

            val paragraphs = mutableListOf<Paragraph>()
            for (raw in rawList) {
                val trimmed = raw.trim()
                if (trimmed.isNotBlank()) {
                    paragraphs.add(
                        Paragraph(
                            index = paragraphs.size,
                            rawText = trimmed,
                            spokenText = applyRulesAndSkips(trimmed),
                            isHeading = paragraphs.isEmpty() && trimmed.length < 60
                        )
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isReaderMode = true,
                    readerModeTitle = title,
                    readerModeParagraphs = paragraphs,
                    readerModeCurrentParagraphIndex = 0,
                    readerModeIsLoadingNext = false,
                    liveParagraphs = if (paragraphs.isNotEmpty()) paragraphs else it.liveParagraphs
                )
            }
        }
    }

    fun exitReadingMode() {
        _uiState.update { it.copy(isReaderMode = false) }
    }

    fun onReaderModeParagraphClicked(index: Int) {
        val paras = _uiState.value.readerModeParagraphs
        if (index in paras.indices) {
            _uiState.update { it.copy(readerModeCurrentParagraphIndex = index) }
            if (_uiState.value.isLiveReading) {
                seekToParagraph(index)
            } else {
                startLiveReadingInternal(forceStartAtBeginning = false)
            }
        }
    }

    fun onReaderModeEndReached() {
        if (_uiState.value.readerModeIsLoadingNext) return
        val autoNext = _uiState.value.settings.autoClickOnScroll || _uiState.value.isLiveReading || _uiState.value.settings.autoReadNextChapter
        if (autoNext) {
            _uiState.update { it.copy(readerModeIsLoadingNext = true, toastMessage = "Loading next chapter...") }
            clickNextChapter()
        }
    }

    fun startLiveReading() {
        AudioPlaybackCoordinator.saveCurrentReaderProgress()
        AudioPlaybackCoordinator.stop()
        _uiState.update { it.copy(isLiveReading = true, isLivePlaying = true) }
        startLiveReadingInternal(forceStartAtBeginning = false)
    }

    private fun startLiveReadingInternal(forceStartAtBeginning: Boolean = false, retryCount: Int = 0) {
        val settings = _uiState.value.settings
        val script = WebContentExtractor.buildContentExtractionScript(settings.contentSelector, settings.ignoredSelectors)

        dispatchEvaluateJs(script) { result ->
            val extracted = WebContentExtractor.extractChapterContent(result, _uiState.value.pageTitle)
            val title = extracted.title
            val rawList = extracted.paragraphs

            if (rawList.isEmpty() && result.isNullOrBlank()) {
                if (retryCount < 2) {
                    retryExtractionJob?.cancel()
                    retryExtractionJob = viewModelScope.launch {
                        delay(800)
                        startLiveReadingInternal(forceStartAtBeginning, retryCount + 1)
                    }
                }
                return@dispatchEvaluateJs
            }

            try {
                val paragraphs = mutableListOf<Paragraph>()
                for (raw in rawList) {
                    val trimmed = raw.trim()
                    if (trimmed.isNotBlank()) {
                        paragraphs.add(
                            Paragraph(
                                index = paragraphs.size,
                                rawText = trimmed,
                                spokenText = applyRulesAndSkips(trimmed),
                                isHeading = paragraphs.isEmpty() && trimmed.length < 60
                            )
                        )
                    }
                }

                if (paragraphs.isNotEmpty()) {
                    val targetIndex = if (forceStartAtBeginning) 0 else 0
                    _uiState.update {
                        it.copy(
                            liveParagraphs = paragraphs,
                            liveChapterTitle = title,
                            liveCurrentParagraphIndex = targetIndex,
                            isLiveReading = true,
                            isLivePlaying = true
                        )
                    }
                    AudioPlaybackCoordinator.playBrowserParagraphs(
                        context = getApplication(),
                        chapterTitle = title,
                        paragraphs = paragraphs,
                        startParagraphIndex = targetIndex,
                        rate = settings.ttsRate,
                        pitch = settings.ttsPitch,
                        voiceName = settings.ttsVoice
                    )
                } else {
                    if (retryCount < 2) {
                        retryExtractionJob?.cancel()
                        retryExtractionJob = viewModelScope.launch {
                            delay(1000)
                            startLiveReadingInternal(forceStartAtBeginning, retryCount + 1)
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                toastMessage = "No readable content found on page.",
                                isLivePlaying = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting live reading: ${e.message}")
            }
        }
    }

    private fun applyRulesAndSkips(text: String): String {
        val settings = _uiState.value.settings
        val rules = if (settings.pronunciationRulesEnabled) settings.pronunciationRules else emptyList()
        val skips = if (settings.skipWordsEnabled) settings.skipWords else emptyList()
        val url = _uiState.value.currentUrl
        return com.novelarrow.reader.data.parser.ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = rules,
            skipWords = skips,
            folderPath = null,
            novelUrl = url
        )
    }

    private fun handleLiveChapterEnd() {
        if (_uiState.value.liveParagraphs.isEmpty()) return

        val autoNext = _uiState.value.settings.autoClickOnScroll || _uiState.value.isLiveReading
        if (autoNext) {
            _uiState.update {
                it.copy(toastMessage = "End of chapter reached. Loading next chapter...")
            }
            viewModelScope.launch(Dispatchers.Main) {
                clickNextChapter()
            }
        } else {
            stopLiveReading()
        }
    }

    fun pauseLiveReading() {
        AudioPlaybackCoordinator.pause()
        _uiState.update { it.copy(isLivePlaying = false) }
    }

    fun resumeLiveReading() {
        if (_uiState.value.liveParagraphs.isEmpty()) {
            startLiveReading()
            return
        }
        AudioPlaybackCoordinator.play()
        _uiState.update { it.copy(isLivePlaying = true, isLiveReading = true) }
    }

    fun stopLiveReading() {
        AudioPlaybackCoordinator.stop()
        clearWebHighlight()
        _uiState.update {
            it.copy(
                isLiveReading = false,
                isLivePlaying = false,
                liveParagraphs = emptyList()
            )
        }
    }

    fun seekToParagraph(index: Int) {
        val paras = _uiState.value.liveParagraphs
        if (paras.isEmpty()) return
        val target = index.coerceIn(0, paras.size - 1)
        AudioPlaybackCoordinator.seekToParagraph(target)
        _uiState.update { it.copy(liveCurrentParagraphIndex = target, isLivePlaying = true, isLiveReading = true) }
    }

    fun nextLiveParagraph() {
        val paras = _uiState.value.liveParagraphs
        val current = _uiState.value.liveCurrentParagraphIndex
        if (current + 1 < paras.size) {
            seekToParagraph(current + 1)
        } else {
            clickNextChapter()
        }
    }

    fun prevLiveParagraph() {
        val current = _uiState.value.liveCurrentParagraphIndex
        if (current - 1 >= 0) {
            seekToParagraph(current - 1)
        }
    }

    fun nextLiveChapterOrParagraph() {
        clickNextChapter()
    }

    fun prevLiveChapterOrParagraph() {
        goBack()
    }

    fun cycleSpeechRate() {
        AudioPlaybackCoordinator.cycleSpeechRate()
    }

    // =========================================================================
    // Sleep Timer
    // =========================================================================

    fun openSleepTimerDialog() {
        _uiState.update { it.copy(showSleepTimerDialog = true) }
    }

    fun dismissSleepTimerDialog() {
        _uiState.update { it.copy(showSleepTimerDialog = false) }
    }

    fun setSleepTimer(minutes: Int) {
        AudioPlaybackCoordinator.setSleepTimer(minutes)
        _uiState.update {
            it.copy(
                showSleepTimerDialog = false,
                toastMessage = if (minutes > 0) "Sleep timer set for $minutes minutes" else "Sleep timer turned off"
            )
        }
    }

    // =========================================================================
    // Browser Settings Dialog & Voice Test
    // =========================================================================

    fun openSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun dismissSettingsDialog() {
        stopVoiceTest()
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun testVoicePreview() {
        if (_uiState.value.isVoiceTesting) {
            stopVoiceTest()
            return
        }
        _uiState.update { it.copy(isVoiceTesting = true) }
        val sampleText = "Hello, this is a preview of the selected voice for NovelArrow Reader."
        val settings = _uiState.value.settings
        val previewPara = Paragraph(
            index = 0,
            rawText = sampleText,
            spokenText = sampleText,
            isHeading = false
        )
        AudioPlaybackCoordinator.playBrowserParagraphs(
            context = getApplication(),
            chapterTitle = "Voice Preview",
            paragraphs = listOf(previewPara),
            startParagraphIndex = 0,
            rate = settings.ttsRate,
            pitch = settings.ttsPitch,
            voiceName = settings.ttsVoice
        )
        viewModelScope.launch {
            delay(4000)
            stopVoiceTest()
        }
    }

    private fun stopVoiceTest() {
        if (_uiState.value.isVoiceTesting) {
            AudioPlaybackCoordinator.stop()
            _uiState.update { it.copy(isVoiceTesting = false) }
        }
    }

    fun updateNextSelector(selector: String) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(nextSelector = selector) }
        }
    }

    fun updateContentSelector(selector: String) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(contentSelector = selector) }
        }
    }

    fun toggleAutoClickOnScroll(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(autoClickOnScroll = enabled) }
        }
    }

    fun toggleAutoReadNextChapter(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReadNextChapter(enabled)
        }
        _uiState.update { it.copy(settings = it.settings.copy(autoReadNextChapter = enabled)) }
    }

    fun toggleTtsAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBrowserTtsAutoStart(enabled)
        }
    }

    fun toggleTtsAutoScroll(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBrowserAutoScroll(enabled)
        }
    }

    fun toggleTtsSynthByParagraph(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBrowserSynthByParagraph(enabled)
        }
    }

    fun updateVoice(voiceName: String?) {
        AudioPlaybackCoordinator.updateVoice(voiceName)
    }

    fun updateRate(rate: Float) {
        AudioPlaybackCoordinator.updateSpeechRate(rate)
    }

    fun updatePitch(pitch: Float) {
        AudioPlaybackCoordinator.updateSpeechPitch(pitch)
    }

    fun updateLoopCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(loopCount = count) }
        }
    }

    fun updateCountdownDelayMs(ms: Long) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(countdownDelayMs = ms) }
        }
    }

    fun addIgnoredSelector(selector: String) {
        viewModelScope.launch {
            settingsRepository.addBrowserIgnoredSelector(selector)
        }
    }

    fun removeIgnoredSelector(selector: String) {
        viewModelScope.launch {
            settingsRepository.removeBrowserIgnoredSelector(selector)
        }
    }

    fun resetIgnoredSelectors() {
        viewModelScope.launch {
            settingsRepository.resetBrowserIgnoredSelectors()
        }
    }

    fun addSkipWord(text: String, exact: Boolean) {
        if (text.isBlank()) return
        viewModelScope.launch {
            settingsRepository.addBrowserSkipWord(SkipWord(text.trim(), exact))
        }
    }

    fun removeSkipWord(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeBrowserSkipWord(index)
        }
    }

    fun addPronunciationRule(from: String, to: String, exact: Boolean) {
        if (from.isBlank()) return
        viewModelScope.launch {
            settingsRepository.addBrowserPronunciationRule(PronunciationRule(from.trim(), to.trim(), exact))
        }
    }

    fun removePronunciationRule(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeBrowserPronunciationRule(index)
        }
    }

    fun togglePronunciationRulesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBrowserPronunciationRulesEnabled(enabled)
        }
    }

    fun toggleSkipWordsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBrowserSkipWordsEnabled(enabled)
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetBrowserSettingsToDefaults()
        }
    }

    fun extractAndShowRawHtml() {
        val settings = _uiState.value.settings
        val selector = settings.contentSelector.trim()
        val safeSelector = org.json.JSONObject.quote(selector)
        val script = """
            (function() {
                try {
                    var sel = $safeSelector;
                    var el = sel ? document.querySelector(sel) : null;
                    if (!el) {
                        var candidates = ['article', '.chapter-content', '.entry-content', '#chapter-content', '#content', '.content', 'main', '.text-left', '#chapter-c'];
                        for (var i = 0; i < candidates.length; i++) {
                            var found = document.querySelector(candidates[i]);
                            if (found && (found.innerText || '').length > 200) {
                                el = found;
                                break;
                            }
                        }
                    }
                    if (el) {
                        return el.outerHTML;
                    }
                    return document.documentElement.outerHTML;
                } catch(e) {
                    return document.documentElement.outerHTML;
                }
            })()
        """.trimIndent()

        dispatchEvaluateJs(script) { result ->
            val rawHtml = if (!result.isNullOrBlank() && result != "null") {
                try {
                    org.json.JSONTokener(result).nextValue().toString()
                } catch (_: Exception) {
                    result
                }
            } else {
                "<html><body><p>No content detected</p></body></html>"
            }
            _uiState.update {
                it.copy(
                    rawHtmlContent = rawHtml,
                    showRawEditor = true
                )
            }
        }
    }

    fun dismissRawEditor() {
        _uiState.update { it.copy(showRawEditor = false, rawHtmlContent = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // =========================================================================
    // Extensions & Userscripts Management
    // =========================================================================

    fun openExtensionsSheet() {
        val curUrl = _uiState.value.currentUrl
        val active = _uiState.value.allUserscripts.filter { script ->
            script.isEnabled && script.matchPatterns.any { pattern ->
                WebScriptInjector.matchesPattern(curUrl, pattern)
            }
        }
        _uiState.update { it.copy(showExtensionsSheet = true, activeUserscripts = active) }
    }

    fun dismissExtensionsSheet() {
        _uiState.update { it.copy(showExtensionsSheet = false) }
    }

    fun openUserscriptManager() {
        _uiState.update { it.copy(showUserscriptManager = true, showExtensionsSheet = false) }
    }

    fun dismissUserscriptManager() {
        _uiState.update { it.copy(showUserscriptManager = false) }
    }

    fun openCodeMirrorEditor(script: UserscriptEntity? = null) {
        viewModelScope.launch {
            if (script != null) {
                val code = userscriptRepository.getScriptCode(script)
                _uiState.update {
                    it.copy(
                        showCodeMirrorEditor = true,
                        editingUserscript = script,
                        editorPrefilledCode = code,
                        editorPrefilledTitle = script.name,
                        showExtensionsSheet = false,
                        showUserscriptManager = false
                    )
                }
            } else {
                val currentUrl = _uiState.value.currentUrl
                val template = UserscriptMetadataParser.generateDefaultTemplate(currentUrl, "New Userscript")
                _uiState.update {
                    it.copy(
                        showCodeMirrorEditor = true,
                        editingUserscript = null,
                        editorPrefilledCode = template,
                        editorPrefilledTitle = "New Userscript",
                        showExtensionsSheet = false,
                        showUserscriptManager = false
                    )
                }
            }
        }
    }

    fun dismissCodeMirrorEditor() {
        _uiState.update { it.copy(showCodeMirrorEditor = false, editingUserscript = null) }
    }

    fun saveUserscriptFromEditor(code: String) {
        viewModelScope.launch {
            try {
                val existing = _uiState.value.editingUserscript
                val saved = userscriptRepository.saveScript(code, preferredName = existing?.name, existingId = existing?.id)
                _uiState.update {
                    it.copy(
                        showCodeMirrorEditor = false,
                        editingUserscript = null,
                        toastMessage = "Saved ${saved.name} to Downloads/NovelArrow_Scripts"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(toastMessage = "Failed to save script: ${e.message}") }
            }
        }
    }

    fun toggleUserscript(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            userscriptRepository.toggleScriptEnabled(id, isEnabled)
        }
    }

    fun deleteUserscript(id: String) {
        viewModelScope.launch {
            val success = userscriptRepository.deleteScript(id)
            if (success) {
                _uiState.update { it.copy(toastMessage = "Deleted script and physical file") }
            }
        }
    }

    fun syncUserscriptsFromDisk() {
        viewModelScope.launch {
            val list = userscriptRepository.syncFromDisk()
            _uiState.update { it.copy(toastMessage = "Synced ${list.size} scripts from Downloads") }
        }
    }

    fun openDocumentationDialog() {
        _uiState.update { it.copy(showDocumentationDialog = true, showExtensionsSheet = false) }
    }

    fun dismissDocumentationDialog() {
        _uiState.update { it.copy(showDocumentationDialog = false) }
    }

    override fun onCleared() {
        super.onCleared()
        downloadCountdownJob?.cancel()
        autoLoopJob?.cancel()
        sleepTimerJob?.cancel()
        retryExtractionJob?.cancel()
    }
}
