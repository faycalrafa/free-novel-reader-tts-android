package com.novelarrow.reader.ui.browser.delegate

import com.novelarrow.reader.browser.engine.RunAt
import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.data.model.BrowserTab
import com.novelarrow.reader.data.repository.BrowserHistoryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.ui.browser.BrowserAction
import com.novelarrow.reader.ui.browser.BrowserUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

class BrowserNavigationDelegate(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val settingsRepository: SettingsRepository,
    private val browserHistoryRepository: BrowserHistoryRepository,
    private val savedReadingPositions: MutableMap<String, Int>,
    private val dispatchLoadUrl: (String) -> Unit,
    private val dispatchAction: (BrowserAction) -> Unit,
    private val dispatchEvaluateJs: (String) -> Unit,
    private val injectFileUserscripts: (String, String) -> Unit,
    private val clearWebHighlight: () -> Unit,
    private val onPageFinishedDownloadHook: (String) -> Unit,
    private val onPageFinishedReadingHook: (String) -> Unit,
    private val cancelPageAutoReadJob: () -> Unit
) {
    companion object {
        const val DEFAULT_URL = "https://www.google.com"
    }

    fun saveTabsState() {
        val s = uiState.value
        scope.launch(Dispatchers.IO) {
            settingsRepository.updateBrowserTabs(s.tabs, s.currentTabId)
        }
    }

    fun openNewTab(url: String = DEFAULT_URL) {
        val newTab = BrowserTab(url = url, title = if (url.contains("google")) "Google" else "New Tab")
        val updatedTabs = uiState.value.tabs + newTab
        uiState.update {
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
        val tab = uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        uiState.update {
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
        val currentTabs = uiState.value.tabs
        if (currentTabs.size <= 1) {
            val resetTab = BrowserTab(url = DEFAULT_URL, title = "Google")
            uiState.update {
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
        val newActiveTab = if (uiState.value.currentTabId == tabId) {
            updatedTabs.last()
        } else {
            updatedTabs.firstOrNull { it.id == uiState.value.currentTabId } ?: updatedTabs.last()
        }

        uiState.update {
            it.copy(
                tabs = updatedTabs,
                currentTabId = newActiveTab.id,
                currentUrl = newActiveTab.url,
                inputUrl = newActiveTab.url,
                pageTitle = newActiveTab.title
            )
        }
        saveTabsState()
        scope.launch(Dispatchers.IO) {
            browserHistoryRepository.clearHistoryForTab(tabId)
        }

        if (uiState.value.currentTabId != newActiveTab.id) {
            dispatchLoadUrl(newActiveTab.url)
        }
    }

    fun toggleTabSwitcher() {
        uiState.update { it.copy(showTabSwitcher = !it.showTabSwitcher) }
    }

    fun dismissTabSwitcher() {
        uiState.update { it.copy(showTabSwitcher = false) }
    }

    fun openHistoryDialog() {
        uiState.update { it.copy(showHistoryDialog = true) }
    }

    fun dismissHistoryDialog() {
        uiState.update { it.copy(showHistoryDialog = false) }
    }

    fun deleteHistoryEntry(id: String) {
        scope.launch(Dispatchers.IO) {
            browserHistoryRepository.deleteEntry(id)
        }
    }

    fun clearHistory() {
        val activeTabId = uiState.value.currentTabId
        scope.launch(Dispatchers.IO) {
            browserHistoryRepository.clearHistoryForTab(activeTabId)
        }
    }

    fun setInputUrl(url: String) {
        uiState.update { it.copy(inputUrl = url) }
    }

    fun navigateTo(url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = if (cleanUrl.contains(".") && !cleanUrl.contains(" ")) {
                "https://$cleanUrl"
            } else {
                "https://www.google.com/search?q=${URLEncoder.encode(cleanUrl, "UTF-8")}"
            }
        }
        uiState.update {
            it.copy(
                currentUrl = cleanUrl,
                inputUrl = cleanUrl
            )
        }
        updateCurrentTabUrl(cleanUrl, uiState.value.pageTitle)
        dispatchLoadUrl(cleanUrl)
        saveLastUrl(cleanUrl)
    }

    fun goHome() {
        navigateTo(DEFAULT_URL)
    }

    fun goBack() {
        dispatchAction(BrowserAction.GoBack)
    }

    fun goForward() {
        dispatchAction(BrowserAction.GoForward)
    }

    fun reload() {
        dispatchAction(BrowserAction.Reload)
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun updateCurrentTabUrl(url: String, title: String) {
        val currentTabId = uiState.value.currentTabId
        val updatedTabs = uiState.value.tabs.map { tab ->
            if (tab.id == currentTabId) {
                tab.copy(url = url, title = title.ifBlank { tab.title })
            } else tab
        }
        uiState.update { it.copy(tabs = updatedTabs) }
        saveTabsState()
    }

    fun saveLastUrl(url: String) {
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(lastUrl = url) }
        }
    }

    fun onReceivedTitle(title: String?) {
        val safeTitle = title.orEmpty().ifBlank { if (uiState.value.currentUrl.contains("google")) "Google" else "Web Page" }
        uiState.update { it.copy(pageTitle = safeTitle) }
        updateCurrentTabUrl(uiState.value.currentUrl, safeTitle)
    }

    fun onPageStarted(url: String, canGoBack: Boolean = false, canGoForward: Boolean = false) {
        cancelPageAutoReadJob()
        if (uiState.value.isLiveReading) {
            AudioPlaybackCoordinator.pause()
            clearWebHighlight()
            uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0, isLivePlaying = false) }
        }
        uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                isLoading = true,
                progress = 15,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        updateCurrentTabUrl(url, uiState.value.pageTitle)

        // Inject userscripts configured to run at document start
        val startScripts = WebScriptInjector.buildUserscriptInjectionCode(url, RunAt.DOCUMENT_START)
        if (startScripts.isNotBlank()) {
            dispatchEvaluateJs(startScripts)
        }
        injectFileUserscripts(url, "document-start")
    }

    fun onPageProgress(progress: Int) {
        uiState.update {
            it.copy(
                progress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean = false, canGoForward: Boolean = false) {
        val safeTitle = title.orEmpty().ifBlank { if (url.contains("google")) "Google" else "Web Page" }
        uiState.update {
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
            val activeTabId = uiState.value.currentTabId
            scope.launch(Dispatchers.IO) {
                browserHistoryRepository.recordVisit(url, safeTitle, activeTabId)
            }
        }

        // Inject JS helper and document-end userscripts
        injectHelperScript(url)

        // 1. If batch download is active, trigger sequence/verification and NEVER trigger TTS
        if (uiState.value.isDownloading) {
            onPageFinishedDownloadHook(url)
            return
        }

        // 2. Reading mode and live reading debounced handling
        onPageFinishedReadingHook(url)
    }

    fun injectHelperScript(url: String) {
        dispatchEvaluateJs(WebScriptInjector.buildHelperScript())
        val endScripts = WebScriptInjector.buildUserscriptInjectionCode(url, RunAt.DOCUMENT_END)
        if (endScripts.isNotBlank()) {
            dispatchEvaluateJs(endScripts)
        }
        injectFileUserscripts(url, "document-end")
        injectFileUserscripts(url, "document-idle")
    }
}
