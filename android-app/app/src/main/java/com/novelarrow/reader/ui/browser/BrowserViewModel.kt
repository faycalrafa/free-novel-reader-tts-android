package com.novelarrow.reader.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelarrow.reader.data.model.*
import com.novelarrow.reader.data.repository.BrowserHistoryRepository
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.userscript.UserscriptEntity
import com.novelarrow.reader.data.userscript.UserscriptRepository
import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.browser.delegate.*
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val userscriptRepository = UserscriptRepository(application)
    private val browserHistoryRepository = BrowserHistoryRepository(application)
    private val savedReadingPositions = mutableMapOf<String, Int>()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _browserActions = Channel<BrowserAction>(Channel.BUFFERED)
    val browserActions: Flow<BrowserAction> = _browserActions.receiveAsFlow()
    val webViewCommands: Flow<WebViewCommand> get() = browserActions

    private var preExtractedNextChapterUrl: String? = null

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

    // Delegates
    private val downloadDelegate: BrowserDownloadDelegate = BrowserDownloadDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState,
        libraryRepository = libraryRepository,
        settingsRepository = settingsRepository,
        dispatchEvaluateJs = { s, cb -> dispatchEvaluateJs(s, cb) },
        dispatchLoadUrl = { dispatchLoadUrl(it) },
        evaluateJsWithTimeout = { s, t -> evaluateJsWithTimeout(s, t) },
        playClickSound = { playClickSound() },
        onClearLiveHighlight = { playbackDelegate.clearWebHighlight() },
        preExtractedNextChapterUrlProvider = { preExtractedNextChapterUrl },
        setPreExtractedNextChapterUrl = { preExtractedNextChapterUrl = it },
        clearPreExtractedNextChapterUrl = { preExtractedNextChapterUrl = null }
    )

    private val playbackDelegate: BrowserReadingPlaybackDelegate = BrowserReadingPlaybackDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState,
        dispatchEvaluateJs = { s, cb -> dispatchEvaluateJs(s, cb) },
        clickNextChapter = { downloadDelegate.clickNextChapter() },
        goBack = { navigationDelegate.goBack() },
        savedReadingPositions = savedReadingPositions,
        setPreExtractedNextChapterUrl = { preExtractedNextChapterUrl = it }
    )

    private val userscriptDelegate: BrowserUserscriptDelegate = BrowserUserscriptDelegate(
        scope = viewModelScope,
        uiState = _uiState,
        userscriptRepository = userscriptRepository,
        settingsRepository = settingsRepository,
        dispatchEvaluateJs = { s, cb -> dispatchEvaluateJs(s, cb) }
    )

    private val settingsDelegate: BrowserSettingsDelegate = BrowserSettingsDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState,
        settingsRepository = settingsRepository,
        dispatchEvaluateJs = { s, cb -> dispatchEvaluateJs(s, cb) },
        reparseActivePage = {
            if (_uiState.value.isLiveReading) {
                playbackDelegate.startLiveReadingInternal(forceStartAtBeginning = false)
            } else if (_uiState.value.isReaderMode) {
                playbackDelegate.enterReadingMode()
            }
        }
    )

    private val navigationDelegate: BrowserNavigationDelegate = BrowserNavigationDelegate(
        scope = viewModelScope,
        uiState = _uiState,
        settingsRepository = settingsRepository,
        browserHistoryRepository = browserHistoryRepository,
        savedReadingPositions = savedReadingPositions,
        dispatchLoadUrl = { dispatchLoadUrl(it) },
        dispatchAction = { _browserActions.trySend(it) },
        dispatchEvaluateJs = { dispatchEvaluateJs(it) },
        injectFileUserscripts = { url, target -> injectFileUserscripts(url, target) },
        clearWebHighlight = { playbackDelegate.clearWebHighlight() },
        onPageFinishedDownloadHook = { downloadDelegate.onPageFinishedHook(it) },
        onPageFinishedReadingHook = { url ->
            playbackDelegate.pageAutoReadJob?.cancel()
            playbackDelegate.pageAutoReadJob = viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                if (!isActive) return@launch
                val currentAudio = AudioPlaybackCoordinator.playbackProgressState.value
                if (currentAudio.isPlaying && currentAudio.source == PlaybackSource.READER) {
                    AppLogger.d(TAG, "Audio is actively playing from Reader (Library), skipping browser auto-read on page finish for $url")
                    return@launch
                }
                val isSameUrl = (playbackDelegate.lastAutoReadUrl == url)
                if (isSameUrl && !_uiState.value.isLivePlaying && _uiState.value.isLiveReading) {
                    AppLogger.d(TAG, "Browser live reading is currently paused on $url, preserving paused position")
                    return@launch
                }
                val shouldAutoRead = _uiState.value.isLiveReading ||
                        _uiState.value.settings.autoReadNextChapter ||
                        _uiState.value.settings.ttsAutoStart
                if (shouldAutoRead) {
                    if (isSameUrl && _uiState.value.isLivePlaying) {
                        AppLogger.d(TAG, "Page $url is already actively playing, skipping duplicate auto-read")
                        return@launch
                    }
                    playbackDelegate.lastAutoReadUrl = url
                    playbackDelegate.startLiveReadingInternal(forceStartAtBeginning = !isSameUrl)
                } else if (_uiState.value.isReaderMode) {
                    playbackDelegate.enterReadingMode()
                }
            }
        },
        cancelPageAutoReadJob = {
            playbackDelegate.pageAutoReadJob?.cancel()
            playbackDelegate.lastAutoReadUrl = null
        }
    )

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

        AudioPlaybackCoordinator.browserNextChapterCallback = { downloadDelegate.clickNextChapter() }
        AudioPlaybackCoordinator.browserPrevChapterCallback = { downloadDelegate.clickPrevChapter() }
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
                    playbackDelegate.highlightWebParagraph(idx)
                    val paras = _uiState.value.liveParagraphs
                    if (!progress.isPlaying && idx >= paras.size - 1 && paras.isNotEmpty()) {
                        playbackDelegate.handleLiveChapterEnd()
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

    // Navigation facade
    fun openNewTab(url: String = DEFAULT_URL) = navigationDelegate.openNewTab(url)
    fun selectTab(tabId: String) = navigationDelegate.selectTab(tabId)
    fun closeTab(tabId: String) = navigationDelegate.closeTab(tabId)
    fun toggleTabSwitcher() = navigationDelegate.toggleTabSwitcher()
    fun dismissTabSwitcher() = navigationDelegate.dismissTabSwitcher()
    fun openHistoryDialog() = navigationDelegate.openHistoryDialog()
    fun dismissHistoryDialog() = navigationDelegate.dismissHistoryDialog()
    fun deleteHistoryEntry(id: String) = navigationDelegate.deleteHistoryEntry(id)
    fun clearHistory() = navigationDelegate.clearHistory()
    fun setInputUrl(url: String) = navigationDelegate.setInputUrl(url)
    fun navigateTo(url: String) = navigationDelegate.navigateTo(url)
    fun goHome() = navigationDelegate.goHome()
    fun goBack() = navigationDelegate.goBack()
    fun goForward() = navigationDelegate.goForward()
    fun reload() = navigationDelegate.reload()
    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) = navigationDelegate.onNavigationStateChanged(canGoBack, canGoForward)
    fun onReceivedTitle(title: String?) = navigationDelegate.onReceivedTitle(title)
    fun onPageStarted(url: String, canGoBack: Boolean = false, canGoForward: Boolean = false) = navigationDelegate.onPageStarted(url, canGoBack, canGoForward)
    fun onPageProgress(progress: Int) = navigationDelegate.onPageProgress(progress)
    fun onPageFinished(url: String, title: String?, canGoBack: Boolean = false, canGoForward: Boolean = false) = navigationDelegate.onPageFinished(url, title, canGoBack, canGoForward)

    // Download facade
    fun clickNextChapter() = downloadDelegate.clickNextChapter()
    fun clickPrevChapter() = downloadDelegate.clickPrevChapter()
    fun openDownloadDialog() = downloadDelegate.openDownloadDialog()
    fun dismissDownloadDialog() = downloadDelegate.dismissDownloadDialog()
    fun setDialogNovelTitle(title: String) = downloadDelegate.setDialogNovelTitle(title)
    fun setDialogStartingChapter(chapter: Int) = downloadDelegate.setDialogStartingChapter(chapter)
    fun setDialogLoopCount(count: Int) = downloadDelegate.setDialogLoopCount(count)
    fun setDialogDelayMs(delayMs: Long) = downloadDelegate.setDialogDelayMs(delayMs)
    fun saveDownloadedChapterFromScript(novelTitle: String, chapterTitle: String, chapterNumber: Int, htmlContent: String) = downloadDelegate.saveDownloadedChapterFromScript(novelTitle, chapterTitle, chapterNumber, htmlContent)
    fun onLoopButtonClicked() = downloadDelegate.onLoopButtonClicked()
    fun toggleAutoLoop() = downloadDelegate.onLoopButtonClicked()
    fun startDownloadLoop() = downloadDelegate.startDownloadLoop()
    fun stopDownloadLoop() = downloadDelegate.stopDownloadLoop()
    fun stopAutoLoop() = downloadDelegate.stopDownloadLoop()
    fun extractAndSaveChapter(isBatch: Boolean) = downloadDelegate.extractAndSaveChapter(isBatch)
    fun saveSingleChapter() = downloadDelegate.saveSingleChapter()
    fun saveFrozenHtml() = downloadDelegate.saveSingleChapter()

    // Reading & Playback facade
    fun toggleLiveReading() = playbackDelegate.toggleLiveReading()
    fun toggleReadingMode() = playbackDelegate.toggleReadingMode()
    fun enterReadingMode() = playbackDelegate.enterReadingMode()
    fun exitReadingMode() = playbackDelegate.exitReadingMode()
    fun onReaderModeParagraphClicked(index: Int) = playbackDelegate.onReaderModeParagraphClicked(index)
    fun onReaderModeEndReached() = playbackDelegate.onReaderModeEndReached()
    fun startLiveReading() = playbackDelegate.startLiveReading()
    fun pauseLiveReading() = playbackDelegate.pauseLiveReading()
    fun resumeLiveReading() = playbackDelegate.resumeLiveReading()
    fun stopLiveReading() = playbackDelegate.stopLiveReading()
    fun seekToWebParagraph(domIndex: Int) = playbackDelegate.seekToWebParagraph(domIndex)
    fun seekToParagraph(index: Int) = playbackDelegate.seekToParagraph(index)
    fun nextLiveParagraph() = playbackDelegate.nextLiveParagraph()
    fun prevLiveParagraph() = playbackDelegate.prevLiveParagraph()
    fun nextLiveChapterOrParagraph() = playbackDelegate.nextLiveChapterOrParagraph()
    fun prevLiveChapterOrParagraph() = playbackDelegate.prevLiveChapterOrParagraph()
    fun cycleSpeechRate() = playbackDelegate.cycleSpeechRate()
    fun openSleepTimerDialog() = playbackDelegate.openSleepTimerDialog()
    fun dismissSleepTimerDialog() = playbackDelegate.dismissSleepTimerDialog()
    fun setSleepTimer(minutes: Int) = playbackDelegate.setSleepTimer(minutes)

    // Settings facade
    fun playClickSound() = settingsDelegate.playClickSound()
    fun buildContentExtractionScript(contentSelector: String, ignoredSelectors: List<String>): String = settingsDelegate.buildContentExtractionScript(contentSelector, ignoredSelectors)
    fun openSettingsDialog() = settingsDelegate.openSettingsDialog()
    fun dismissSettingsDialog() = settingsDelegate.dismissSettingsDialog()
    fun testVoicePreview() = settingsDelegate.testVoicePreview()
    fun updateNextSelector(selector: String) = settingsDelegate.updateNextSelector(selector)
    fun updateContentSelector(selector: String) = settingsDelegate.updateContentSelector(selector)
    fun toggleAutoClickOnScroll(enabled: Boolean) = settingsDelegate.toggleAutoClickOnScroll(enabled)
    fun toggleAutoNextOnFinishedPage(enabled: Boolean) = settingsDelegate.toggleAutoNextOnFinishedPage(enabled)
    fun toggleAutoReadNextChapter(enabled: Boolean) = settingsDelegate.toggleAutoReadNextChapter(enabled)
    fun toggleTtsAutoStart(enabled: Boolean) = settingsDelegate.toggleTtsAutoStart(enabled)
    fun toggleTtsAutoScroll(enabled: Boolean) = settingsDelegate.toggleTtsAutoScroll(enabled)
    fun toggleTtsSynthByParagraph(enabled: Boolean) = settingsDelegate.toggleTtsSynthByParagraph(enabled)
    fun updateVoice(voiceName: String?) = settingsDelegate.updateVoice(voiceName)
    fun updateRate(rate: Float) = settingsDelegate.updateRate(rate)
    fun updatePitch(pitch: Float) = settingsDelegate.updatePitch(pitch)
    fun updateLoopCount(count: Int) = settingsDelegate.updateLoopCount(count)
    fun updateCountdownDelayMs(ms: Long) = settingsDelegate.updateCountdownDelayMs(ms)
    fun addIgnoredSelector(selector: String) = settingsDelegate.addIgnoredSelector(selector)
    fun removeIgnoredSelector(selector: String) = settingsDelegate.removeIgnoredSelector(selector)
    fun resetIgnoredSelectors() = settingsDelegate.resetIgnoredSelectors()
    fun addSkipWord(text: String, exact: Boolean) = settingsDelegate.addSkipWord(text, exact)
    fun removeSkipWord(index: Int) = settingsDelegate.removeSkipWord(index)
    fun addPronunciationRule(from: String, to: String, exact: Boolean) = settingsDelegate.addPronunciationRule(from, to, exact)
    fun removePronunciationRule(index: Int) = settingsDelegate.removePronunciationRule(index)
    fun togglePronunciationRulesEnabled(enabled: Boolean) = settingsDelegate.togglePronunciationRulesEnabled(enabled)
    fun toggleSkipWordsEnabled(enabled: Boolean) = settingsDelegate.toggleSkipWordsEnabled(enabled)
    fun resetSettingsToDefaults() = settingsDelegate.resetSettingsToDefaults()
    fun extractAndShowRawHtml() = settingsDelegate.extractAndShowRawHtml()
    fun dismissRawEditor() = settingsDelegate.dismissRawEditor()
    fun clearToast() = settingsDelegate.clearToast()
    fun resetFlashHandleRed() = settingsDelegate.resetFlashHandleRed()
    fun toggleActiveRule(rule: RuleImpact, enabled: Boolean) = settingsDelegate.toggleActiveRule(rule, enabled)

    // Userscripts & Clickables facade
    fun openExtensionsSheet() = userscriptDelegate.openExtensionsSheet()
    fun dismissExtensionsSheet() = userscriptDelegate.dismissExtensionsSheet()
    fun openUserscriptManager() = userscriptDelegate.openUserscriptManager()
    fun dismissUserscriptManager() = userscriptDelegate.dismissUserscriptManager()
    fun openCodeMirrorEditor(script: UserscriptEntity? = null) = userscriptDelegate.openCodeMirrorEditor(script)
    fun dismissCodeMirrorEditor() = userscriptDelegate.dismissCodeMirrorEditor()
    fun saveUserscriptFromEditor(code: String) = userscriptDelegate.saveUserscriptFromEditor(code)
    fun toggleUserscript(id: String, isEnabled: Boolean) = userscriptDelegate.toggleUserscript(id, isEnabled)
    fun deleteUserscript(id: String) = userscriptDelegate.deleteUserscript(id)
    fun renameUserscript(id: String, newName: String) = userscriptDelegate.renameUserscript(id, newName)
    fun syncUserscriptsFromDisk() = userscriptDelegate.syncUserscriptsFromDisk()
    fun openDocumentationDialog() = userscriptDelegate.openDocumentationDialog()
    fun dismissDocumentationDialog() = userscriptDelegate.dismissDocumentationDialog()
    fun onLongPressClickable(selector: String, text: String, hostname: String) = userscriptDelegate.onLongPressClickable(selector, text, hostname)
    fun dismissAddNextSelectorDialog() = userscriptDelegate.dismissAddNextSelectorDialog()
    fun confirmAddNextSelector(ruleName: String, selector: String, hostname: String) = userscriptDelegate.confirmAddNextSelector(ruleName, selector, hostname)
    fun addNextChapterSelectorRule(rule: NextChapterSelectorRule) = userscriptDelegate.addNextChapterSelectorRule(rule)
    fun updateNextChapterSelectorRule(rule: NextChapterSelectorRule) = userscriptDelegate.updateNextChapterSelectorRule(rule)
    fun toggleNextChapterSelectorRule(id: String, enabled: Boolean) = userscriptDelegate.toggleNextChapterSelectorRule(id, enabled)
    fun deleteNextChapterSelectorRule(id: String) = userscriptDelegate.deleteNextChapterSelectorRule(id)
    fun resetNextChapterSelectorRules() = userscriptDelegate.resetNextChapterSelectorRules()
    fun inspectPageClickables() = userscriptDelegate.inspectPageClickables()
    fun dismissClickablesInspector() = userscriptDelegate.dismissClickablesInspector()
    fun openShortcutsManager() = userscriptDelegate.openShortcutsManager()
    fun dismissShortcutsManager() = userscriptDelegate.dismissShortcutsManager()
    fun addNavbarShortcut(shortcut: BrowserNavbarShortcut) = userscriptDelegate.addNavbarShortcut(shortcut)
    fun toggleNavbarShortcut(id: String, enabled: Boolean) = userscriptDelegate.toggleNavbarShortcut(id, enabled)
    fun deleteNavbarShortcut(id: String) = userscriptDelegate.deleteNavbarShortcut(id)
    fun clickElementBySelector(selector: String) = userscriptDelegate.clickElementBySelector(selector)
    fun registerCustomToolbarButton(payload: String) = userscriptDelegate.registerCustomToolbarButton(payload)
    fun onCustomToolbarButtonClicked(buttonId: String) = userscriptDelegate.onCustomToolbarButtonClicked(buttonId)
    fun updateToolbarButtonOrder(order: List<String>) = userscriptDelegate.updateToolbarButtonOrder(order)

    override fun onCleared() {
        super.onCleared()
        AudioPlaybackCoordinator.browserNextChapterCallback = null
        AudioPlaybackCoordinator.browserPrevChapterCallback = null
        downloadDelegate.onCleared()
        playbackDelegate.onCleared()
    }
}
