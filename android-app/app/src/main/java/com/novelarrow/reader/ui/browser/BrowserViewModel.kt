package com.novelarrow.reader.ui.browser

import android.app.Application
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.withContext
import com.novelarrow.reader.browser.engine.ExtractedContent
import com.novelarrow.reader.browser.engine.RunAt
import com.novelarrow.reader.browser.engine.WebContentExtractor
import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.browser.DomContentVerifier
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.BrowserTab
import com.novelarrow.reader.data.model.NextChapterSelectorRule
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.userscript.UserscriptEntity
import com.novelarrow.reader.data.userscript.UserscriptMetadataParser
import com.novelarrow.reader.data.userscript.UserscriptRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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

data class LongPressedClickableInfo(
    val selector: String,
    val text: String,
    val hostname: String
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
    val readerModeIsLoadingNext: Boolean = false,

    // Clickable element inspector & next selector creation
    val longPressedClickable: LongPressedClickableInfo? = null,
    val showAddNextSelectorDialog: Boolean = false,
    val scannedClickables: List<ClickableElement> = emptyList(),
    val showClickablesInspector: Boolean = false,
    val showShortcutsManager: Boolean = false,
    val flashHandleRed: Boolean = false,
    val activeRuleImpacts: List<RuleImpact> = emptyList(),
    val customToolbarButtons: List<com.novelarrow.reader.data.model.CustomToolbarButton> = emptyList(),
    val showHistoryDialog: Boolean = false,
    val historyEntries: List<com.novelarrow.reader.data.model.BrowserHistoryEntry> = emptyList()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val userscriptRepository = UserscriptRepository(application)
    private val browserHistoryRepository = com.novelarrow.reader.data.repository.BrowserHistoryRepository(application)
    private val savedReadingPositions = mutableMapOf<String, Int>()

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
    private var pageAutoReadJob: Job? = null
    private var lastAutoReadUrl: String? = null
    private var preExtractedNextChapterUrl: String? = null

    private var lastBatchDownloadedUrl: String = ""
    private var lastBatchDownloadedChapterNum: Int = -1
    private var duplicatePageRetryCount: Int = 0

    private var lastExtractedHeading: String = ""
    private var lastExtractedSample: String = ""
    private var domVerificationJob: Job? = null

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

    private suspend fun evaluateJsWithTimeout(script: String, timeoutMs: Long = 3000L): String? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    dispatchEvaluateJs(script) { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error evaluating JS with timeout: ${e.message}", e)
            null
        }
    }

    fun playClickSound() {
        // Compose UI emits native view sound effects without ToneGenerator allocations
    }

    init {
        AudioPlaybackCoordinator.initialize(application)
        val initialSettings = settingsRepository.getBrowserSettings()
        val loadedTabs = if (initialSettings.tabs.isNotEmpty()) initialSettings.tabs else listOf(
            BrowserTab(url = DEFAULT_URL, title = "Google")
        )
        val activeTab = loadedTabs.firstOrNull { it.id == initialSettings.currentTabId } ?: loadedTabs.first()
        val startUrl = if (activeTab.url.isNotBlank() && activeTab.url != "https://novelbin.me") activeTab.url else DEFAULT_URL

        _uiState.update {
            it.copy(
                settings = initialSettings,
                currentUrl = startUrl,
                inputUrl = startUrl,
                tabs = loadedTabs,
                currentTabId = activeTab.id
            )
        }

        viewModelScope.launch {
            settingsRepository.browserSettingsFlow.collect { newSettings ->
                AudioPlaybackCoordinator.autoNextOnFinishedPage = newSettings.autoNextOnFinishedPage
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

        viewModelScope.launch {
            browserHistoryRepository.historyFlow.collect { history ->
                _uiState.update { it.copy(historyEntries = history) }
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
                    val curUrl = _uiState.value.currentUrl
                    if (curUrl.isNotBlank()) {
                        savedReadingPositions[curUrl] = idx
                    }
                    _uiState.update {
                        it.copy(
                            isLivePlaying = progress.isPlaying,
                            liveCurrentParagraphIndex = idx,
                            readerModeCurrentParagraphIndex = if (it.isReaderMode) idx else it.readerModeCurrentParagraphIndex
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

    private fun saveTabsState() {
        val s = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.updateBrowserTabs(s.tabs, s.currentTabId)
        }
    }

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
        saveTabsState()
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
        saveTabsState()
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
            saveTabsState()
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
        saveTabsState()

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

    fun openHistoryDialog() {
        _uiState.update { it.copy(showHistoryDialog = true) }
    }

    fun dismissHistoryDialog() {
        _uiState.update { it.copy(showHistoryDialog = false) }
    }

    fun deleteHistoryEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            browserHistoryRepository.deleteEntry(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            browserHistoryRepository.clearHistory()
        }
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
        saveTabsState()
    }

    private fun saveLastUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(lastUrl = url) }
        }
    }

    fun onReceivedTitle(title: String?) {
        val safeTitle = title.orEmpty().ifBlank { if (_uiState.value.currentUrl.contains("google")) "Google" else "Web Page" }
        _uiState.update { it.copy(pageTitle = safeTitle) }
        updateCurrentTabUrl(_uiState.value.currentUrl, safeTitle)
    }

    fun onPageStarted(url: String, canGoBack: Boolean = false, canGoForward: Boolean = false) {
        pageAutoReadJob?.cancel()
        retryExtractionJob?.cancel()
        lastAutoReadUrl = null
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

        if (url.isNotBlank() && url != "about:blank") {
            viewModelScope.launch(Dispatchers.IO) {
                browserHistoryRepository.recordVisit(url, safeTitle)
            }
        }

        // Inject JS helper and document-end userscripts
        injectHelperScript(url)

        // 1. If batch download is active, trigger sequence/verification and NEVER trigger TTS
        if (_uiState.value.isDownloading) {
            AppLogger.d(TAG, "onPageFinished during batch download for URL: $url")
            if (lastBatchDownloadedUrl.isNotBlank() && url != lastBatchDownloadedUrl) {
                AppLogger.i(TAG, "onPageFinished: URL changed from '$lastBatchDownloadedUrl' to '$url'. Triggering download directly!")
                domVerificationJob?.cancel()
                _uiState.update { it.copy(downloadStatus = "New chapter URL loaded, downloading...") }
                triggerBatchDownloadSequence(isDomTransitionVerified = true)
                return
            }
            domVerificationJob?.cancel()
            startDomContentVerification()
            return
        }

        // 2. Debounce and serialize content extraction for Reading Mode and Live Reading
        pageAutoReadJob?.cancel()
        pageAutoReadJob = viewModelScope.launch {
            delay(500)
            if (!isActive) return@launch

            val shouldAutoRead = _uiState.value.isLiveReading ||
                    _uiState.value.settings.autoReadNextChapter ||
                    _uiState.value.settings.ttsAutoStart

            if (shouldAutoRead) {
                if (lastAutoReadUrl == url && _uiState.value.isLivePlaying) {
                    AppLogger.d(TAG, "Page $url is already actively playing, skipping duplicate auto-read")
                    return@launch
                }
                lastAutoReadUrl = url
                startLiveReadingInternal(forceStartAtBeginning = true)
            } else if (_uiState.value.isReaderMode) {
                enterReadingMode()
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
        val paras = _uiState.value.liveParagraphs
        val currentPara = paras.getOrNull(index)
        val sourceIndices = currentPara?.sourceIndices ?: listOf(index)
        dispatchEvaluateJs(WebScriptInjector.buildHighlightScript(sourceIndices, autoScroll))
    }

    private fun clearWebHighlight() {
        dispatchEvaluateJs(WebScriptInjector.buildClearHighlightScript())
    }

    fun clickNextChapter() {
        playClickSound()
        com.novelarrow.reader.playback.AudioCueManager.playChapterChange()
        pageAutoReadJob?.cancel()
        retryExtractionJob?.cancel()
        lastAutoReadUrl = null
        if (_uiState.value.isLiveReading) {
            clearWebHighlight()
            _uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0) }
        }
        isNavigatingNextChapter = true
        val settings = _uiState.value.settings
        val currentUrl = _uiState.value.currentUrl

        val directUrl = preExtractedNextChapterUrl
        if (!directUrl.isNullOrBlank() && directUrl != currentUrl && (directUrl.startsWith("http://") || directUrl.startsWith("https://"))) {
            AppLogger.i(TAG, "Navigating to next chapter directly via pre-extracted URL: $directUrl")
            preExtractedNextChapterUrl = null
            dispatchLoadUrl(directUrl)
            return
        }

        dispatchEvaluateJs(
            WebScriptInjector.buildNextChapterScript(
                customSelector = settings.nextSelector,
                rules = settings.nextChapterRules,
                currentUrl = currentUrl
            )
        )
    }

    fun clickPrevChapter() {
        playClickSound()
        pageAutoReadJob?.cancel()
        retryExtractionJob?.cancel()
        lastAutoReadUrl = null
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
    // Batch Download Loop & Userscript Chapter Bridge
    // =========================================================================

    fun saveDownloadedChapterFromScript(
        novelTitle: String,
        chapterTitle: String,
        chapterNumber: Int,
        htmlContent: String
    ) {
        AppLogger.i(TAG, "Received downloaded chapter from script: $novelTitle Ch. $chapterNumber ($chapterTitle), length: ${htmlContent.length}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedFile = WebNovelDownloader.saveCleanHtml(
                    context = getApplication(),
                    novelTitle = novelTitle,
                    chapterTitle = chapterTitle,
                    chapterNumber = chapterNumber,
                    cleanHtml = htmlContent
                )
                val updatedNovel = libraryRepository.addDownloadedChapter(
                    novelTitle = novelTitle,
                    chapterFile = savedFile,
                    chapterTitle = chapterTitle,
                    chapterNumber = chapterNumber,
                    sourceUrl = _uiState.value.currentUrl,
                    commitNow = true
                )
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            lastDownloadedNovel = updatedNovel,
                            toastMessage = "Saved $novelTitle Ch. $chapterNumber to Downloads/NovelReader!"
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error saving downloaded chapter from script: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(toastMessage = "Failed to save Chapter $chapterNumber: ${e.message}")
                    }
                }
            }
        }
    }

    fun onLoopButtonClicked() {
        if (_uiState.value.isDownloading) {
            stopDownloadLoop()
        } else {
            dispatchEvaluateJs("window.__triggerCustomToolbarButton('multi_download');")
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
        lastExtractedHeading = ""
        lastExtractedSample = ""
        domVerificationJob?.cancel()
        domVerificationJob = null

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
        domVerificationJob?.cancel()
        domVerificationJob = null
        lastExtractedHeading = ""
        lastExtractedSample = ""
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

    private var isNavigatingNextChapter = false

    private fun startDomContentVerification() {
        domVerificationJob?.cancel()
        domVerificationJob = viewModelScope.launch {
            AppLogger.d(TAG, "Starting DOM content verification polling...")
            _uiState.update { it.copy(downloadStatus = "Verifying DOM transition...") }

            val timeoutMs = 20000L
            val pollIntervalMs = 700L
            val startTime = System.currentTimeMillis()
            var contentTransitioned = false

            while (isActive && (System.currentTimeMillis() - startTime) < timeoutMs) {
                delay(pollIntervalMs)
                if (!_uiState.value.isDownloading) return@launch

                // If WebView is still actively loading network resources, keep waiting and do not time out
                if (_uiState.value.isLoading) {
                    _uiState.update { it.copy(downloadStatus = "Loading page...") }
                    continue
                }

                val currentUrl = _uiState.value.currentUrl
                val urlChanged = lastBatchDownloadedUrl.isNotBlank() && currentUrl != lastBatchDownloadedUrl

                if (urlChanged) {
                    AppLogger.i(TAG, "DOM transition verified via URL change: '$lastBatchDownloadedUrl' -> '$currentUrl'")
                    contentTransitioned = true
                    isNavigatingNextChapter = false
                    break
                }

                val settings = _uiState.value.settings
                val script = WebContentExtractor.buildContentExtractionScript(
                    contentSelector = settings.contentSelector,
                    ignoredSelectors = settings.ignoredSelectors,
                    contentSelectorRules = settings.contentSelectors,
                    currentUrl = currentUrl
                )
                val rawResult = evaluateJsWithTimeout(script, 2000L)

                if (rawResult != null && rawResult != "null") {
                    val extracted = WebContentExtractor.extractChapterContent(rawResult, _uiState.value.pageTitle)
                    val currentHeading = extracted.title
                    val currentSample = DomContentVerifier.createChapterFingerprint(extracted.paragraphs)

                    if (extracted.paragraphs.isNotEmpty() || currentHeading.isNotBlank()) {
                        val headingChanged = currentHeading.isNotBlank() && currentHeading != lastExtractedHeading
                        val bodyChanged = currentSample.isNotBlank() && currentSample != lastExtractedSample

                        if (headingChanged || bodyChanged) {
                            AppLogger.i(TAG, "DOM transition verified! headingChanged=$headingChanged, bodyChanged=$bodyChanged (heading='$currentHeading')")
                            contentTransitioned = true
                            isNavigatingNextChapter = false
                            break
                        }
                    }
                }

                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                _uiState.update { it.copy(downloadStatus = "Waiting for chapter content (${elapsedSec}s)...") }
            }

            if (!_uiState.value.isDownloading) return@launch

            if (contentTransitioned) {
                _uiState.update { it.copy(downloadStatus = "New chapter detected, preparing download...") }
                triggerBatchDownloadSequence(isDomTransitionVerified = true)
            } else {
                AppLogger.w(TAG, "DOM transition verification timed out after 20s without content change. Last heading: '$lastExtractedHeading'")
                if (duplicatePageRetryCount < 2) {
                    duplicatePageRetryCount++
                    _uiState.update { it.copy(downloadStatus = "Retrying next chapter navigation (attempt $duplicatePageRetryCount)...") }
                    clickNextChapter()
                    startDomContentVerification()
                } else {
                    AppLogger.w(TAG, "Stopping download loop cleanly: DOM content did not advance after retries.")
                    stopDownloadLoop()
                    _uiState.update {
                        it.copy(
                            downloadStatus = "Stopped: Content did not advance",
                            toastMessage = "Batch download stopped: Page content did not transition."
                        )
                    }
                }
            }
        }
    }

    private fun triggerBatchDownloadSequence(isDomTransitionVerified: Boolean = false) {
        if (!_uiState.value.isDownloading) return
        downloadCountdownJob?.cancel()
        domVerificationJob?.cancel()

        downloadCountdownJob = viewModelScope.launch {
            val currentUrl = _uiState.value.currentUrl
            val pageTitle = _uiState.value.pageTitle

            // If we are still on the exact same URL as the chapter we just downloaded, and DOM transition was not verified, wait for navigation
            if (!isDomTransitionVerified && lastBatchDownloadedUrl.isNotBlank() && currentUrl == lastBatchDownloadedUrl) {
                AppLogger.d(TAG, "Page is still on last downloaded URL ($currentUrl). Waiting for navigation...")
                _uiState.update { it.copy(downloadStatus = "Waiting for next chapter...") }
                startDomContentVerification()
                return@launch
            }

            val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
            val novelTitle = _uiState.value.downloadNovelTitle.ifBlank { detected.novelTitle }

            // Sync chapter number: if DOM transition was verified, trust the scheduled downloadCurrentChapter
            val chapterNum = if (isDomTransitionVerified && _uiState.value.downloadCurrentChapter > 0) {
                _uiState.value.downloadCurrentChapter
            } else if (detected.chapterNumber > 0) {
                detected.chapterNumber
            } else if (_uiState.value.downloadCurrentChapter > 0) {
                _uiState.value.downloadCurrentChapter
            } else {
                1
            }
            _uiState.update { it.copy(downloadCurrentChapter = chapterNum) }

            val chapterAlreadyExists = WebNovelDownloader.chapterAlreadyExists(getApplication(), novelTitle, chapterNum, currentUrl)

            if (chapterAlreadyExists) {
                Log.d(TAG, "Chapter $chapterNum already exists on disk. Skipping file write and advancing.")
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
                    startDomContentVerification()
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
        val currentUrl = _uiState.value.currentUrl
        val script = WebContentExtractor.buildContentExtractionScript(
            contentSelector = settings.contentSelector,
            ignoredSelectors = settings.ignoredSelectors,
            contentSelectorRules = settings.contentSelectors,
            currentUrl = currentUrl
        )
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
            val isTitleJustNovelTitle = title.equals(novelTitle, ignoreCase = true) || title.isBlank()
            val chapterTitle = if (isTitleJustNovelTitle) "$novelTitle Chapter $chapterNum" else title

            val urlChanged = lastBatchDownloadedUrl.isNotBlank() && currentUrl != lastBatchDownloadedUrl

            if (paragraphs.isEmpty()) {
                AppLogger.w(TAG, "Extracted 0 paragraphs for Chapter $chapterNum. Waiting for DOM content to load...")
                _uiState.update { it.copy(downloadStatus = "Waiting for chapter content to render...") }
                startDomContentVerification()
                return
            }

            // Dynamic duplicate capture prevention: check if extracted content matches previous chapter signature
            if (lastExtractedHeading.isNotBlank() || lastExtractedSample.isNotBlank()) {
                val currentSample = DomContentVerifier.createChapterFingerprint(paragraphs)
                if (DomContentVerifier.isDuplicateContent(title, currentSample, lastExtractedHeading, lastExtractedSample, urlChanged = urlChanged)) {
                    AppLogger.w(TAG, "Duplicate DOM content detected in handleExtractedChapter for Chapter $chapterNum. Heading='$title'. Content has not transitioned yet. Waiting for DOM update...")
                    _uiState.update { it.copy(downloadStatus = "Waiting for DOM re-render...") }
                    startDomContentVerification()
                    return
                }
            }

            if (WebNovelDownloader.chapterAlreadyExists(getApplication(), novelTitle, chapterNum, currentUrl)) {
                Log.d(TAG, "Batch download: Chapter $chapterNum already exists on disk. Skipping file write.")
                val newRemaining = remaining - 1
                val nextChapterNum = chapterNum + 1
                lastExtractedHeading = title
                lastExtractedSample = DomContentVerifier.createChapterFingerprint(paragraphs)
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
                    startDomContentVerification()
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
                    lastExtractedHeading = title
                    lastExtractedSample = DomContentVerifier.createChapterFingerprint(paragraphs)

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
                        startDomContentVerification()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save downloaded chapter: ${e.message}", e)
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
        val currentUrl = _uiState.value.currentUrl
        val script = WebContentExtractor.buildContentExtractionScript(
            contentSelector = settings.contentSelector,
            ignoredSelectors = settings.ignoredSelectors,
            contentSelectorRules = settings.contentSelectors,
            currentUrl = currentUrl
        )

        dispatchEvaluateJs(script) { result ->
            val extracted = WebContentExtractor.extractChapterContent(result, _uiState.value.pageTitle)
            val title = extracted.title
            val rawList = extracted.paragraphs
            if (!extracted.nextUrl.isNullOrBlank()) {
                preExtractedNextChapterUrl = extracted.nextUrl
                AppLogger.d(TAG, "Cached pre-extracted next chapter URL in reading mode: $preExtractedNextChapterUrl")
            }

            val containerImpact = if (extracted.matchedContainerSelector != null && extracted.containerFilteredChars > 0) {
                RuleImpact(
                    id = "container_${extracted.matchedContainerSelector}",
                    name = extracted.matchedContainerSelector,
                    ruleType = RuleType.CONTENT_CONTAINER,
                    charsFiltered = extracted.containerFilteredChars,
                    enabled = true,
                    details = "${extracted.containerFilteredChars} outer characters excluded by container"
                )
            } else null

            val ignoredImpacts = extracted.ignoredSelectorStats.map { (sel, count) ->
                RuleImpact(
                    id = "ignored_$sel",
                    name = sel,
                    ruleType = RuleType.IGNORED_SELECTOR,
                    charsFiltered = count,
                    enabled = true,
                    details = "$count characters removed"
                )
            }

            val parseResult = ChapterParser.parseWebParagraphsWithDiagnostics(
                rawParagraphs = rawList,
                currentUrl = currentUrl,
                headerSkipRules = settings.headerSkipRules,
                headerSkipEnabled = settings.headerSkipEnabled,
                footerSkipRules = settings.footerSkipRules,
                footerSkipEnabled = settings.footerSkipEnabled,
                pronunciationRules = settings.pronunciationRules,
                pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                skipWords = settings.skipWords,
                skipWordsEnabled = settings.skipWordsEnabled,
                skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols,
                containerImpact = containerImpact,
                ignoredSelectorImpacts = ignoredImpacts
            )

            val rawParagraphs = parseResult.paragraphs
            val paragraphs = ChapterParser.mergeShortParagraphs(rawParagraphs, minWordThreshold = 10)
            val curPos = (if (_uiState.value.isLiveReading) _uiState.value.liveCurrentParagraphIndex else (savedReadingPositions[currentUrl] ?: 0)).coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            _uiState.update {
                it.copy(
                    isReaderMode = true,
                    readerModeTitle = title,
                    readerModeParagraphs = paragraphs,
                    readerModeCurrentParagraphIndex = curPos,
                    readerModeIsLoadingNext = false,
                    liveParagraphs = if (paragraphs.isNotEmpty()) paragraphs else it.liveParagraphs,
                    activeRuleImpacts = parseResult.ruleImpacts
                )
            }
        }
    }

    fun exitReadingMode() {
        _uiState.update { it.copy(isReaderMode = false) }
    }

    fun onReaderModeParagraphClicked(index: Int) {
        val paras = _uiState.value.readerModeParagraphs.ifEmpty { _uiState.value.liveParagraphs }
        if (index in paras.indices) {
            _uiState.update {
                it.copy(
                    readerModeCurrentParagraphIndex = index,
                    liveCurrentParagraphIndex = index
                )
            }
            val currentUrl = _uiState.value.currentUrl
            if (currentUrl.isNotBlank()) {
                savedReadingPositions[currentUrl] = index
            }
            if (_uiState.value.isLiveReading) {
                seekToParagraph(index)
            } else {
                startLiveReadingInternal(forceStartAtBeginning = false, initialIndex = index)
            }
        }
    }

    fun onReaderModeEndReached() {
        if (_uiState.value.readerModeIsLoadingNext) return
        val autoNext = _uiState.value.settings.autoClickOnScroll
        if (autoNext) {
            _uiState.update { it.copy(readerModeIsLoadingNext = true, toastMessage = "Loading next chapter...") }
            clickNextChapter()
        }
    }

    fun startLiveReading() {
        AudioPlaybackCoordinator.saveCurrentReaderProgress()
        AudioPlaybackCoordinator.stop()
        _uiState.update { it.copy(isLiveReading = true, isLivePlaying = true, flashHandleRed = false) }
        startLiveReadingInternal(forceStartAtBeginning = false)
    }

    private fun startLiveReadingInternal(
        forceStartAtBeginning: Boolean = false,
        initialIndex: Int? = null,
        retryCount: Int = 0
    ) {
        val settings = _uiState.value.settings
        val currentUrl = _uiState.value.currentUrl
        val script = WebContentExtractor.buildContentExtractionScript(
            contentSelector = settings.contentSelector,
            ignoredSelectors = settings.ignoredSelectors,
            contentSelectorRules = settings.contentSelectors,
            currentUrl = currentUrl
        )

        dispatchEvaluateJs(script) { result ->
            val extracted = WebContentExtractor.extractChapterContent(result, _uiState.value.pageTitle)
            val title = extracted.title
            val rawList = extracted.paragraphs
            if (!extracted.nextUrl.isNullOrBlank()) {
                preExtractedNextChapterUrl = extracted.nextUrl
                AppLogger.d(TAG, "Cached pre-extracted next chapter URL: $preExtractedNextChapterUrl")
            }

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
                val containerImpact = if (extracted.matchedContainerSelector != null && extracted.containerFilteredChars > 0) {
                    RuleImpact(
                        id = "container_${extracted.matchedContainerSelector}",
                        name = extracted.matchedContainerSelector,
                        ruleType = RuleType.CONTENT_CONTAINER,
                        charsFiltered = extracted.containerFilteredChars,
                        enabled = true,
                        details = "${extracted.containerFilteredChars} outer characters excluded by container"
                    )
                } else null

                val ignoredImpacts = extracted.ignoredSelectorStats.map { (sel, count) ->
                    RuleImpact(
                        id = "ignored_$sel",
                        name = sel,
                        ruleType = RuleType.IGNORED_SELECTOR,
                        charsFiltered = count,
                        enabled = true,
                        details = "$count characters removed"
                    )
                }

                val parseResult = ChapterParser.parseWebParagraphsWithDiagnostics(
                    rawParagraphs = rawList,
                    currentUrl = currentUrl,
                    headerSkipRules = settings.headerSkipRules,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipRules = settings.footerSkipRules,
                    footerSkipEnabled = settings.footerSkipEnabled,
                    pronunciationRules = settings.pronunciationRules,
                    pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                    skipWords = settings.skipWords,
                    skipWordsEnabled = settings.skipWordsEnabled,
                    skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols,
                    containerImpact = containerImpact,
                    ignoredSelectorImpacts = ignoredImpacts
                )

                val rawParagraphs = parseResult.paragraphs
                val readableParagraphs = ChapterParser.mergeShortParagraphs(rawParagraphs, minWordThreshold = 10)

                if (readableParagraphs.isNotEmpty()) {
                    val targetIndex = (initialIndex ?: if (forceStartAtBeginning) {
                        0
                    } else {
                        (savedReadingPositions[currentUrl] ?: _uiState.value.readerModeCurrentParagraphIndex.takeIf { it > 0 } ?: _uiState.value.liveCurrentParagraphIndex)
                    }).coerceIn(0, (readableParagraphs.size - 1).coerceAtLeast(0))
                    _uiState.update {
                        it.copy(
                            liveParagraphs = readableParagraphs,
                            readerModeParagraphs = if (it.readerModeParagraphs.isEmpty()) readableParagraphs else it.readerModeParagraphs,
                            liveChapterTitle = title,
                            liveCurrentParagraphIndex = targetIndex,
                            readerModeCurrentParagraphIndex = targetIndex,
                            isLiveReading = true,
                            isLivePlaying = true,
                            flashHandleRed = false,
                            activeRuleImpacts = parseResult.ruleImpacts
                        )
                    }
                    AudioPlaybackCoordinator.playBrowserParagraphs(
                        context = getApplication(),
                        chapterTitle = title,
                        paragraphs = readableParagraphs,
                        startParagraphIndex = targetIndex,
                        rate = settings.ttsRate,
                        pitch = settings.ttsPitch,
                        voiceName = settings.ttsVoice,
                        isPreMerged = true
                    )
                } else {
                    if (retryCount < 2 && rawList.isEmpty()) {
                        retryExtractionJob?.cancel()
                        retryExtractionJob = viewModelScope.launch {
                            delay(1000)
                            startLiveReadingInternal(forceStartAtBeginning, retryCount + 1)
                        }
                    } else {
                        AppLogger.w(TAG, "No readable text detected on page $currentUrl. Rules applied: ${parseResult.ruleImpacts.size}")
                        _uiState.update {
                            it.copy(
                                toastMessage = "No text detected. Check if filters or selectors are misconfigured.",
                                isLivePlaying = false,
                                flashHandleRed = true,
                                activeRuleImpacts = parseResult.ruleImpacts
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error starting live reading: ${e.message}", e)
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
            novelUrl = url,
            skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols
        )
    }

    private fun handleLiveChapterEnd() {
        if (_uiState.value.liveParagraphs.isEmpty()) return

        val autoNext = _uiState.value.settings.autoReadNextChapter
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

    fun seekToWebParagraph(domIndex: Int) {
        val paras = _uiState.value.liveParagraphs.ifEmpty { _uiState.value.readerModeParagraphs }
        if (paras.isEmpty()) return
        val resolvedIndex = paras.indexOfFirst { domIndex in it.sourceIndices }.takeIf { it >= 0 }
            ?: domIndex.coerceIn(0, paras.size - 1)
        seekToParagraph(resolvedIndex)
    }

    fun seekToParagraph(index: Int) {
        val paras = _uiState.value.liveParagraphs.ifEmpty { _uiState.value.readerModeParagraphs }
        if (paras.isEmpty()) return
        val target = index.coerceIn(0, paras.size - 1)
        val curUrl = _uiState.value.currentUrl
        if (curUrl.isNotBlank()) {
            savedReadingPositions[curUrl] = target
        }
        AudioPlaybackCoordinator.seekToParagraph(target)
        _uiState.update {
            it.copy(
                liveCurrentParagraphIndex = target,
                readerModeCurrentParagraphIndex = target,
                isLivePlaying = true,
                isLiveReading = true
            )
        }
        highlightWebParagraph(target)
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
        val sampleText = "Hello, this is a preview of the selected voice for BookReader."
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

    fun toggleAutoNextOnFinishedPage(enabled: Boolean) {
        AudioPlaybackCoordinator.autoNextOnFinishedPage = enabled
        viewModelScope.launch {
            settingsRepository.updateBrowserSettings { it.copy(autoNextOnFinishedPage = enabled) }
        }
        _uiState.update { it.copy(settings = it.settings.copy(autoNextOnFinishedPage = enabled)) }
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
        val script = WebContentExtractor.buildRawHtmlExtractionScript(settings.contentSelector)

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
                        toastMessage = "Saved ${saved.name} to Downloads/BookReader_Scripts"
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

    fun renameUserscript(id: String, newName: String) {
        viewModelScope.launch {
            val success = userscriptRepository.renameScript(id, newName)
            if (success) {
                _uiState.update { it.copy(toastMessage = "Renamed script to $newName") }
            } else {
                _uiState.update { it.copy(toastMessage = "Failed to rename script") }
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

    fun onLongPressClickable(selector: String, text: String, hostname: String) {
        _uiState.update {
            it.copy(
                longPressedClickable = LongPressedClickableInfo(selector, text, hostname),
                showAddNextSelectorDialog = true
            )
        }
    }

    fun dismissAddNextSelectorDialog() {
        _uiState.update {
            it.copy(
                longPressedClickable = null,
                showAddNextSelectorDialog = false
            )
        }
    }

    fun confirmAddNextSelector(ruleName: String, selector: String, hostname: String) {
        viewModelScope.launch {
            val rule = NextChapterSelectorRule(
                name = ruleName.ifBlank { "Next Chapter ($hostname)" },
                selector = selector,
                domain = hostname,
                enabled = true
            )
            settingsRepository.addNextChapterSelectorRule(rule)
            _uiState.update {
                it.copy(
                    longPressedClickable = null,
                    showAddNextSelectorDialog = false,
                    toastMessage = "Added Next Chapter selector for $hostname"
                )
            }
        }
    }

    fun addNextChapterSelectorRule(rule: NextChapterSelectorRule) {
        viewModelScope.launch {
            settingsRepository.addNextChapterSelectorRule(rule)
        }
    }

    fun updateNextChapterSelectorRule(rule: NextChapterSelectorRule) {
        viewModelScope.launch {
            settingsRepository.updateNextChapterSelectorRule(rule)
        }
    }

    fun toggleNextChapterSelectorRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleNextChapterSelectorRule(id, enabled)
        }
    }

    fun deleteNextChapterSelectorRule(id: String) {
        viewModelScope.launch {
            settingsRepository.removeNextChapterSelectorRule(id)
        }
    }

    fun resetNextChapterSelectorRules() {
        viewModelScope.launch {
            settingsRepository.resetNextChapterSelectorRules()
        }
    }

    fun inspectPageClickables() {
        val script = WebScriptInjector.buildClickableInspectorScript()
        dispatchEvaluateJs(script) { result ->
            val list = mutableListOf<ClickableElement>()
            if (!result.isNullOrBlank() && result != "null") {
                try {
                    val rawJson = if (result.startsWith("\"") && result.endsWith("\"")) {
                        org.json.JSONTokener(result).nextValue().toString()
                    } else result
                    val array = org.json.JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val selector = obj.optString("selector", "")
                        val text = obj.optString("text", "")
                        val tag = obj.optString("tag", "")
                        val href = obj.optString("href", "")
                        if (selector.isNotBlank()) {
                            list.add(
                                ClickableElement(
                                    selector = selector,
                                    text = text,
                                    tagName = tag,
                                    href = href
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed parsing scanned clickables: ${e.message}", e)
                }
            }
            _uiState.update {
                it.copy(
                    scannedClickables = list,
                    showClickablesInspector = true
                )
            }
        }
    }

    fun dismissClickablesInspector() {
        _uiState.update { it.copy(showClickablesInspector = false) }
    }

    fun openShortcutsManager() {
        _uiState.update { it.copy(showShortcutsManager = true, showClickablesInspector = false) }
    }

    fun dismissShortcutsManager() {
        _uiState.update { it.copy(showShortcutsManager = false) }
    }

    fun addNavbarShortcut(shortcut: BrowserNavbarShortcut) {
        viewModelScope.launch {
            settingsRepository.addNavbarShortcut(shortcut)
            _uiState.update { it.copy(toastMessage = "Added shortcut: ${shortcut.name}") }
        }
    }

    fun toggleNavbarShortcut(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleNavbarShortcut(id, enabled)
        }
    }

    fun deleteNavbarShortcut(id: String) {
        viewModelScope.launch {
            settingsRepository.removeNavbarShortcut(id)
        }
    }

    fun clickElementBySelector(selector: String) {
        val script = WebScriptInjector.buildClickElementScript(selector)
        dispatchEvaluateJs(script)
    }

    fun resetFlashHandleRed() {
        _uiState.update { it.copy(flashHandleRed = false) }
    }

    fun toggleActiveRule(rule: RuleImpact, enabled: Boolean) {
        viewModelScope.launch {
            when (rule.ruleType) {
                RuleType.CONTENT_CONTAINER, RuleType.CONTENT_SELECTOR -> {
                    settingsRepository.toggleContentSelector(rule.id, enabled)
                }
                RuleType.IGNORED_SELECTOR -> {
                    val cleanSel = rule.name
                    if (enabled) {
                        settingsRepository.addBrowserIgnoredSelector(cleanSel)
                    } else {
                        settingsRepository.removeBrowserIgnoredSelector(cleanSel)
                    }
                }
                RuleType.SKIP_WORD -> {
                    settingsRepository.toggleSkipWord(rule.id, enabled)
                }
                RuleType.PRONUNCIATION -> {
                    settingsRepository.togglePronunciationRule(rule.id, enabled)
                }
                RuleType.HEADER_SKIP -> {
                    settingsRepository.setHeaderSkipEnabled(enabled)
                }
                RuleType.FOOTER_SKIP -> {
                    settingsRepository.setFooterSkipEnabled(enabled)
                }
            }
            if (_uiState.value.isLiveReading) {
                startLiveReadingInternal(forceStartAtBeginning = false)
            } else if (_uiState.value.isReaderMode) {
                enterReadingMode()
            }
        }
    }

    fun registerCustomToolbarButton(payload: String) {
        try {
            val obj = org.json.JSONObject(payload)
            val id = obj.optString("id", "")
            if (id.isBlank()) return
            val title = obj.optString("title", "Button")
            val icon = obj.optString("icon", "Extension")
            val badge = obj.optString("badge", "")
            val btn = com.novelarrow.reader.data.model.CustomToolbarButton(id = id, title = title, icon = icon, badge = badge)
            _uiState.update { state ->
                val filtered = state.customToolbarButtons.filterNot { it.id == id }
                state.copy(customToolbarButtons = filtered + btn)
            }
            AppLogger.d(TAG, "Registered custom toolbar button: $id ($title)")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse custom toolbar button payload: ${e.message}", e)
        }
    }

    fun onCustomToolbarButtonClicked(buttonId: String) {
        AppLogger.i(TAG, "Custom toolbar button clicked: $buttonId")
        dispatchEvaluateJs("window.__triggerCustomToolbarButton('$buttonId');")
    }

    fun updateToolbarButtonOrder(order: List<String>) {
        viewModelScope.launch {
            settingsRepository.updateToolbarButtonOrder(order)
        }
    }

    override fun onCleared() {
        super.onCleared()
        AudioPlaybackCoordinator.browserNextChapterCallback = null
        AudioPlaybackCoordinator.browserPrevChapterCallback = null
        downloadCountdownJob?.cancel()
        autoLoopJob?.cancel()
        sleepTimerJob?.cancel()
        retryExtractionJob?.cancel()
        pageAutoReadJob?.cancel()
    }
}
