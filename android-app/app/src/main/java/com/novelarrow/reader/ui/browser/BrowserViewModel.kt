package com.novelarrow.reader.ui.browser

import android.app.Application
import android.speech.tts.Voice
import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.NovelAudioPlaybackService
import com.novelarrow.reader.playback.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

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

    // Last downloaded novel reference
    val lastDownloadedNovel: Novel? = null
)

class BrowserViewModel(application: Application) : AndroidViewModel(application), NovelAudioPlaybackService.PlaybackClient {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private var ttsManager: TtsManager? = null
    private var downloadCountdownJob: Job? = null
    private var autoLoopJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var webViewRef: WebView? = null
    private var retryExtractionJob: Job? = null

    companion object {
        private const val TAG = "BrowserViewModel"
        private const val DEFAULT_URL = "https://www.google.com"
    }

    init {
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
                applyVoiceSettings()
            }
        }

        initTts(application)
        setupPlaybackServiceCallbacks()
    }

    private fun initTts(application: Application) {
        ttsManager = TtsManager(application) { success ->
            if (success) {
                val voices = ttsManager?.getAvailableVoices() ?: emptyList()
                _uiState.update { it.copy(availableVoices = voices) }
                applyVoiceSettings()
            }
        }.apply {
            onParagraphStart = { index ->
                _uiState.update { it.copy(liveCurrentParagraphIndex = index, isLivePlaying = true) }
                highlightWebParagraph(index)
                updateLivePlaybackNotification()
            }
            onParagraphDone = { index ->
                handleLiveParagraphDone(index)
            }
            onError = { index, err ->
                Log.w(TAG, "TTS Error on paragraph $index: $err")
                // Advance to next paragraph safely on error
                viewModelScope.launch {
                    val next = index + 1
                    val paras = _uiState.value.liveParagraphs
                    if (next < paras.size && _uiState.value.isLiveReading && _uiState.value.isLivePlaying) {
                        _uiState.update { it.copy(liveCurrentParagraphIndex = next) }
                        speakLiveParagraph(next)
                    }
                }
            }
        }
    }

    private fun applyVoiceSettings() {
        val settings = _uiState.value.settings
        ttsManager?.setRate(settings.ttsRate)
        ttsManager?.setPitch(settings.ttsPitch)
        ttsManager?.setVoiceByName(settings.ttsVoice)
    }

    private fun setupPlaybackServiceCallbacks() {
        // Active client is registered dynamically when live reading starts
    }

    override fun onPlay() { resumeLiveReading() }
    override fun onPause() { pauseLiveReading() }
    override fun onStop() { stopLiveReading() }
    override fun onNextParagraph() { nextLiveParagraph() }
    override fun onPrevParagraph() { prevLiveParagraph() }
    override fun onNextChapter() { nextLiveChapterOrParagraph() }
    override fun onPrevChapter() { prevLiveChapterOrParagraph() }
    override fun onSeekToParagraph(index: Int) { seekToParagraph(index) }

    fun attachWebView(webView: WebView) {
        webViewRef = webView
    }

    fun detachWebView() {
        webViewRef = null
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
        webViewRef?.loadUrl(url)
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
        webViewRef?.loadUrl(tab.url)
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.size <= 1) {
            // Don't close last tab, reset it to Google
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
            webViewRef?.loadUrl(DEFAULT_URL)
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
            webViewRef?.loadUrl(newActiveTab.url)
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
        webViewRef?.loadUrl(cleanUrl)
        saveLastUrl(cleanUrl)
    }

    fun goHome() {
        navigateTo(DEFAULT_URL)
    }

    fun goBack() {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        }
    }

    fun goForward() {
        if (webViewRef?.canGoForward() == true) {
            webViewRef?.goForward()
        }
    }

    fun reload() {
        webViewRef?.reload()
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

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                isLoading = true,
                progress = 15
            )
        }
        updateCurrentTabUrl(url, _uiState.value.pageTitle)
    }

    fun onPageProgress(progress: Int) {
        _uiState.update {
            it.copy(
                progress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun onPageFinished(url: String, title: String?) {
        val safeTitle = title.orEmpty().ifBlank { if (url.contains("google")) "Google" else "Web Page" }
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                pageTitle = safeTitle,
                isLoading = false,
                progress = 100,
                canGoBack = webViewRef?.canGoBack() ?: false,
                canGoForward = webViewRef?.canGoForward() ?: false
            )
        }
        updateCurrentTabUrl(url, safeTitle)
        saveLastUrl(url)

        // Inject JS helper
        injectHelperScript()

        // 1. If batch download is active, trigger sequence (listen until page is loaded, wait timer, then download)
        if (_uiState.value.isDownloading) {
            triggerBatchDownloadSequence()
        }

        // 3. If Live Reading was active across page transitions or ttsAutoStart is enabled
        if (_uiState.value.isLiveReading || _uiState.value.settings.ttsAutoStart) {
            viewModelScope.launch {
                delay(700)
                startLiveReadingInternal(forceStartAtBeginning = true)
            }
        }
    }

    // =========================================================================
    // JavaScript Bridge & Helpers
    // =========================================================================

    private fun injectHelperScript() {
        val webView = webViewRef ?: return
        val js = """
            (function() {
                // 1. Inject highlight and hover styles
                if (!document.getElementById('na-tts-styles')) {
                    var styleEl = document.createElement('style');
                    styleEl.id = 'na-tts-styles';
                    styleEl.textContent = 
                        '.na-tts-highlight { background-color: rgba(255, 235, 59, 0.38) !important; border-radius: 4px !important; box-shadow: 0 0 0 2px rgba(255, 214, 0, 0.4) !important; }' +
                        '[data-novel-para-idx] { cursor: pointer; transition: background-color 0.2s ease; }' +
                        '[data-novel-para-idx]:hover { background-color: rgba(0, 122, 255, 0.08); }';
                    (document.head || document.documentElement).appendChild(styleEl);
                }

                // 2. Interactive paragraph click to seek TTS (do NOT block clicks on links/buttons)
                if (!window.__novelArrowClickBound) {
                    window.__novelArrowClickBound = true;
                    document.addEventListener('click', function(e) {
                        if (e.target && e.target.closest('a, button, input, select, textarea, [role="button"]')) {
                            return; // Let user interact with buttons and links normally
                        }
                        var pEl = e.target.closest('[data-novel-para-idx]');
                        if (pEl) {
                            var idxStr = pEl.getAttribute('data-novel-para-idx');
                            if (idxStr !== null && window.AndroidBridge && window.AndroidBridge.onParagraphClicked) {
                                var idx = parseInt(idxStr, 10);
                                if (!isNaN(idx)) {
                                    window.AndroidBridge.onParagraphClicked(idx);
                                }
                            }
                        }
                    }, true);
                }

                // 3. Scroll to bottom detector for auto-next on scroll
                if (!window.__novelArrowScrollBound) {
                    window.__novelArrowScrollBound = true;
                    window.addEventListener('scroll', function() {
                        if ((window.innerHeight + window.scrollY) >= (document.documentElement.scrollHeight - 16)) {
                            if (window.AndroidBridge && window.AndroidBridge.onScrolledToBottom) {
                                window.AndroidBridge.onScrolledToBottom();
                            }
                        }
                    });
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun buildContentExtractionScript(contentSelector: String, ignoredSelectors: List<String>): String {
        val safeContentSelector = JSONObject.quote(contentSelector.trim())
        val safeIgnoredArray = JSONArray(ignoredSelectors).toString()
        return """
            (function() {
                try {
                    var customSelector = $safeContentSelector;
                    var userIgnored = $safeIgnoredArray;
                    var defaultIgnore = [
                        'a', 'button', 'input', 'select', 'textarea', 'form',
                        'header', 'footer', 'aside', 'nav',
                        'script', 'style', 'noscript', 'iframe',
                        '.sidebar', '#sidebar', '.header', '#header', '.footer', '#footer',
                        '.ad', '.ads', '.advertisement', '#comments', '.comments',
                        '#custom-floating-bar', '#na-root', '#na-player-bar', '#na-floating-next',
                        '.na-tts-highlight'
                    ];
                    var ignored = defaultIgnore.slice();
                    for (var d = 0; d < userIgnored.length; d++) {
                        if (userIgnored[d] && ignored.indexOf(userIgnored[d]) === -1) {
                            ignored.push(userIgnored[d]);
                        }
                    }

                    function isIgnored(el) {
                        if (!el) return true;
                        try {
                            for (var i = 0; i < ignored.length; i++) {
                                var sel = ignored[i];
                                if (sel && (el.matches(sel) || el.closest(sel))) return true;
                            }
                        } catch(e) {}
                        return false;
                    }

                    function extractFrom(container, useFilters) {
                        var allNodes = Array.from(container.querySelectorAll('p, div, li, blockquote, h1, h2, h3, h4, h5, h6'));
                        var paras = [];
                        var elements = [];

                        for (var i = 0; i < allNodes.length; i++) {
                            var el = allNodes[i];
                            if (useFilters && isIgnored(el)) continue;
                            if (!useFilters && (el.matches('script, style, noscript, iframe') || el.closest('script, style, noscript, iframe'))) continue;

                            var tag = el.tagName.toLowerCase();
                            if (['div', 'li', 'blockquote'].indexOf(tag) !== -1) {
                                if (el.querySelector('p, section, article, li, blockquote')) continue;
                            }

                            var cs = window.getComputedStyle(el);
                            if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) continue;
                            var rect = el.getBoundingClientRect();
                            if (rect.width === 0 && rect.height === 0 && el.offsetHeight === 0) continue;

                            var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
                            if (text.length >= 2) {
                                paras.push(text);
                                elements.push(el);
                            }
                        }
                        return { paras: paras, elements: elements };
                    }

                    // 1. Container selection
                    var container = null;
                    if (customSelector && customSelector.length > 0) {
                        try {
                            container = document.querySelector(customSelector);
                        } catch(e) {}
                    }
                    if (!container) {
                        var candidates = [
                            '#chapter-content', '#content', '.chapter-content', '.entry-content',
                            '.post-content', '.read-content', '.reading-content', '.chapter-c',
                            '.text-left', 'article', '.content', '#chapter_content', '.novel-content',
                            'main', '.chapter-container', '#read-area'
                        ];
                        for (var c = 0; c < candidates.length; c++) {
                            var cand = document.querySelector(candidates[c]);
                            if (cand && cand.querySelectorAll('p').length >= 2) {
                                container = cand;
                                break;
                            }
                        }
                    }
                    if (!container) container = document.body;

                    // 2. Heading
                    var heading = '';
                    var h1 = document.querySelector('h1, .chapter-title, .title-chapter, .entry-title');
                    if (h1 && !isIgnored(h1)) {
                        heading = (h1.textContent || '').replace(/\s+/g, ' ').trim();
                    }
                    if (!heading) heading = (document.title || '').trim();

                    // 3. Elements extraction with filters
                    var res = extractFrom(container, true);
                    var paras = res.paras;
                    var elements = res.elements;

                    // 4. FALLBACK: If caught nothing using filters, IGNORE them and start from the first <p> in the page
                    if (paras.length === 0) {
                        var allPs = Array.from(document.body.querySelectorAll('p'));
                        var firstPIndex = -1;
                        for (var p = 0; p < allPs.length; p++) {
                            var pEl = allPs[p];
                            if (pEl.matches('script, style, noscript, iframe') || pEl.closest('script, style, noscript, iframe')) continue;
                            var pText = (pEl.textContent || '').replace(/\s+/g, ' ').trim();
                            if (pText.length >= 2) {
                                firstPIndex = p;
                                break;
                            }
                        }

                        if (firstPIndex !== -1) {
                            paras = [];
                            elements = [];
                            for (var q = firstPIndex; q < allPs.length; q++) {
                                var elQ = allPs[q];
                                if (elQ.matches('script, style, noscript, iframe') || elQ.closest('script, style, noscript, iframe')) continue;
                                var tQ = (elQ.textContent || '').replace(/\s+/g, ' ').trim();
                                if (tQ.length >= 2) {
                                    paras.push(tQ);
                                    elements.push(elQ);
                                }
                            }
                        } else {
                            var fallbackRes = extractFrom(document.body, false);
                            paras = fallbackRes.paras;
                            elements = fallbackRes.elements;
                        }
                    }

                    // 5. Final fallback if still empty: split document.body.innerText by newlines
                    if (paras.length === 0) {
                        var rawLines = (document.body.innerText || '').split('\n');
                        for (var l = 0; l < rawLines.length; l++) {
                            var line = rawLines[l].replace(/\s+/g, ' ').trim();
                            if (line.length >= 2) {
                                paras.push(line);
                            }
                        }
                    }

                    // Tag matched elements for click-to-seek and auto-scroll highlighting
                    for (var idx = 0; idx < elements.length; idx++) {
                        elements[idx].setAttribute('data-novel-para-idx', String(idx));
                    }

                    return JSON.stringify({
                        title: heading,
                        paragraphs: paras
                    });
                } catch(err) {
                    return JSON.stringify({
                        title: document.title || '',
                        paragraphs: [],
                        error: err.toString()
                    });
                }
            })();
        """.trimIndent()
    }

    fun buildCaptureFullPageScript(isBatch: Boolean): String {
        return """
            (async function() {
                try {
                    var isBatchMode = $isBatch;
                    var clonedDoc = document.documentElement.cloneNode(true);

                    // 1. Remove app injected overlays and internal UI
                    var toRemove = [
                        '#na-root', '#na-player-bar', '.na-modal-backdrop', '.na-loading-toast',
                        '#na-tts-styles', '#na-floating-next'
                    ];
                    for (var r = 0; r < toRemove.length; r++) {
                        var items = clonedDoc.querySelectorAll(toRemove[r]);
                        for (var m = 0; m < items.length; m++) items[m].remove();
                    }

                    // 2. Remove script tags from cloned doc to freeze state cleanly
                    var scripts = clonedDoc.querySelectorAll('script');
                    for (var s = 0; s < scripts.length; s++) scripts[s].remove();

                    var clonedHead = clonedDoc.querySelector('head') || clonedDoc;
                    var clonedBody = clonedDoc.querySelector('body') || clonedDoc;

                    // 3. Add standalone next button (from userscript)
                    var standaloneBtn = document.createElement('button');
                    standaloneBtn.id = 'na-floating-next';
                    standaloneBtn.title = 'Increment URL Digit (Next File)';
                    standaloneBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" style="width:20px;height:20px;fill:currentColor;"><path d="M52.5 440.6c-9.5 7.9-22.8 9.7-34.1 4.4S0 428.4 0 416V96C0 83.6 7.2 72.3 18.4 67s24.5-3.6 34.1 4.4L224 214.3V96c0-12.4 7.2-23.7 18.4-29s24.5-3.6 34.1 4.4L448 214.3V96c0-17.7 14.3-32 32-32s32 14.3 32 32v320c0 17.7-14.3 32-32 32s-32-14.3-32-32V297.7L276.5 440.6c-9.5 7.9-22.8 9.7-34.1 4.4S224 428.4 224 416V297.7L52.5 440.6z"/></svg>';

                    var inlineStyle = document.createElement('style');
                    inlineStyle.textContent = '#na-floating-next { position: fixed; bottom: 24px; right: 24px; z-index: 2147483000; background: #2563eb; color: #fff; width: 56px; height: 56px; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; border: none; box-shadow: 0 4px 16px rgba(0,0,0,0.4); transition: transform 0.2s, background 0.2s; } #na-floating-next:hover { transform: scale(1.08); background: #1d4ed8; }';
                    clonedHead.appendChild(inlineStyle);
                    clonedBody.appendChild(standaloneBtn);

                    var standaloneScript = document.createElement('script');
                    standaloneScript.textContent = "document.getElementById('na-floating-next').addEventListener('click', function() { let urlObj; try { urlObj = new URL(window.location.href); } catch(e) { return; } let pathname = urlObj.pathname; let regex = /(\\d+)(?=\\.html?$)/i; if (!regex.test(pathname)) { regex = /(\\d+)(?!.*\\d)/; } if (regex.test(pathname)) { urlObj.pathname = pathname.replace(regex, function(m) { return (parseInt(m, 10) + 1).toString().padStart(m.length, '0'); }); window.location.href = urlObj.href; } else { alert('No digit found in URL to increment.'); } });";
                    clonedBody.appendChild(standaloneScript);

                    // 4. Inline CSS stylesheets
                    var links = Array.from(document.querySelectorAll('link[rel="stylesheet"]'));
                    for (var k = 0; k < links.length; k++) {
                        var link = links[k];
                        try {
                            var cssText = '';
                            for (var ss = 0; ss < document.styleSheets.length; ss++) {
                                var sheet = document.styleSheets[ss];
                                if (sheet.href === link.href) {
                                    try {
                                        var rules = sheet.cssRules || sheet.rules;
                                        if (rules) {
                                            for (var ri = 0; ri < rules.length; ri++) cssText += rules[ri].cssText + '\n';
                                        }
                                    } catch(e) {}
                                    break;
                                }
                            }
                            if (!cssText && link.href && link.href.indexOf('http') === 0) {
                                var res = await fetch(link.href);
                                if (res.ok) cssText = await res.text();
                            }
                            if (cssText) {
                                var styleTag = document.createElement('style');
                                styleTag.textContent = cssText;
                                var cLink = clonedDoc.querySelector('link[href="' + link.getAttribute('href') + '"]');
                                if (cLink) cLink.replaceWith(styleTag);
                            }
                        } catch(e) {}
                    }

                    // 5. Convert images to base64 where possible
                    var images = Array.from(clonedDoc.querySelectorAll('img[src]'));
                    for (var imgIdx = 0; imgIdx < images.length; imgIdx++) {
                        var img = images[imgIdx];
                        var src = img.getAttribute('src');
                        if (src && !src.startsWith('data:') && src.startsWith('http')) {
                            try {
                                var resImg = await fetch(src);
                                var blob = await resImg.blob();
                                var b64 = await new Promise(function(resolve) {
                                    var reader = new FileReader();
                                    reader.onloadend = function() { resolve(reader.result); };
                                    reader.onerror = function() { resolve(src); };
                                    reader.readAsDataURL(blob);
                                });
                                img.setAttribute('src', b64);
                            } catch(e) {}
                        }
                    }

                    var fullHtml = '<!DOCTYPE html>\n' + clonedDoc.outerHTML;
                    var pageTitle = (document.title || '').trim();

                    if (window.AndroidBridge && window.AndroidBridge.onFullPageCaptured) {
                        window.AndroidBridge.onFullPageCaptured(fullHtml, pageTitle, isBatchMode);
                    }
                } catch(err) {
                    console.error('Error in captureFullPage:', err);
                }
            })();
        """.trimIndent()
    }

    private fun parseJavascriptResultToJson(result: String?): JSONObject? {
        if (result == null || result == "null" || result.isBlank()) return null
        return try {
            val tokener = JSONTokener(result)
            val value = tokener.nextValue()
            when (value) {
                is JSONObject -> value
                is String -> {
                    val secondTokener = JSONTokener(value)
                    val secondValue = secondTokener.nextValue()
                    if (secondValue is JSONObject) secondValue else JSONObject(value)
                }
                else -> JSONObject(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding JS result: $e, raw: $result")
            null
        }
    }

    private fun highlightWebParagraph(index: Int) {
        val webView = webViewRef ?: return
        val autoScroll = _uiState.value.settings.ttsAutoScroll
        val script = """
            (function() {
                var prev = document.querySelectorAll('.na-tts-highlight');
                for (var i = 0; i < prev.length; i++) {
                    prev[i].classList.remove('na-tts-highlight');
                }
                var target = document.querySelector('[data-novel-para-idx="$index"]');
                if (target) {
                    target.classList.add('na-tts-highlight');
                    if ($autoScroll) {
                        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun clearWebHighlight() {
        val webView = webViewRef ?: return
        val script = """
            (function() {
                var prev = document.querySelectorAll('.na-tts-highlight');
                for (var i = 0; i < prev.length; i++) {
                    prev[i].classList.remove('na-tts-highlight');
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun clickNextChapter() {
        val webView = webViewRef ?: return
        val nextSelector = JSONObject.quote(_uiState.value.settings.nextSelector.trim())
        val script = """
            (function() {
                var customSel = $nextSelector;
                function getNextEl() {
                    if (customSel && customSel.length > 0) {
                        try {
                            var el = document.querySelector(customSel);
                            if (el) return el;
                        } catch(e) {}
                    }
                    var candidates = Array.from(document.querySelectorAll('a, button'));
                    for (var i = 0; i < candidates.length; i++) {
                        var c = candidates[i];
                        var txt = (c.innerText || c.textContent || c.title || '').trim().toLowerCase();
                        if (txt === 'next chapter' || txt === 'next >' || txt === 'next' || txt.indexOf('next chapter') !== -1) {
                            return c;
                        }
                    }
                    return null;
                }

                function incrementUrl() {
                    try {
                        var urlObj = new URL(window.location.href);
                        var pathname = urlObj.pathname;
                        var regex = /(\d+)(?=\.html?$)/i;
                        if (!regex.test(pathname)) { regex = /(\d+)(?!.*\d)/; }
                        if (regex.test(pathname)) {
                            urlObj.pathname = pathname.replace(regex, function(match) {
                                var len = match.length;
                                var nextNum = parseInt(match, 10) + 1;
                                return nextNum.toString().padStart(len, '0');
                            });
                            window.location.href = urlObj.href;
                            return true;
                        }
                    } catch(e) {}
                    return false;
                }

                var target = getNextEl();
                if (target) {
                    var oldUrl = window.location.href;
                    try { target.click(); } catch(e) {}
                    setTimeout(function() {
                        if (window.location.href === oldUrl && target.href) {
                            window.location.href = target.href;
                        }
                    }, 800);
                } else {
                    incrementUrl();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
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

        // Check if page is currently fully loaded on screen
        if (!state.isLoading && state.progress == 100) {
            triggerBatchDownloadSequence()
        } else {
            _uiState.update { it.copy(downloadStatus = "Waiting for page to fully load...") }
        }
    }

    fun stopDownloadLoop() {
        downloadCountdownJob?.cancel()
        downloadCountdownJob = null
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
        val delayMs = _uiState.value.settings.countdownDelayMs.coerceAtLeast(200L)

        downloadCountdownJob = viewModelScope.launch {
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
            captureFullPage(isBatch = true)
        }
    }

    fun captureFullPage(isBatch: Boolean) {
        val webView = webViewRef ?: return
        if (!isBatch) {
            _uiState.update { it.copy(toastMessage = "Capturing full page...") }
        }
        val script = buildCaptureFullPageScript(isBatch)
        webView.evaluateJavascript(script, null)
    }

    fun onFullPageCaptured(html: String, pageTitle: String, isBatch: Boolean) {
        val currentUrl = _uiState.value.currentUrl
        if (!isBatch) {
            val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
            val chapterTitle = pageTitle.ifBlank { "${detected.novelTitle} Chapter ${detected.chapterNumber}" }
            viewModelScope.launch {
                try {
                    val savedFile = WebNovelDownloader.saveFrozenHtml(
                        context = getApplication(),
                        novelTitle = detected.novelTitle,
                        chapterTitle = chapterTitle,
                        chapterNumber = detected.chapterNumber,
                        frozenHtml = html,
                        sourceUrl = currentUrl
                    )
                    val updatedNovel = libraryRepository.addDownloadedChapter(
                        novelTitle = detected.novelTitle,
                        chapterFile = savedFile,
                        chapterTitle = chapterTitle,
                        chapterNumber = detected.chapterNumber,
                        sourceUrl = currentUrl
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
            val chapterTitle = pageTitle.ifBlank { "$novelTitle Chapter $chapterNum" }

            viewModelScope.launch {
                try {
                    val savedFile = WebNovelDownloader.saveFrozenHtml(
                        context = getApplication(),
                        novelTitle = novelTitle,
                        chapterTitle = chapterTitle,
                        chapterNumber = chapterNum,
                        frozenHtml = html,
                        sourceUrl = currentUrl
                    )
                    val updatedNovel = libraryRepository.addDownloadedChapter(
                        novelTitle = novelTitle,
                        chapterFile = savedFile,
                        chapterTitle = chapterTitle,
                        chapterNumber = chapterNum,
                        sourceUrl = currentUrl
                    )
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

    // =========================================================================
    // Single Chapter Download (Exact same extraction and saving as batch)
    // =========================================================================

    fun saveSingleChapter() {
        captureFullPage(isBatch = false)
    }

    fun saveFrozenHtml() = saveSingleChapter()

    // =========================================================================
    // Live Reading via Native TTS (Matching ReaderScreen TtsDock behavior)
    // =========================================================================

    fun toggleLiveReading() {
        if (_uiState.value.isLiveReading) {
            if (_uiState.value.isLivePlaying) {
                pauseLiveReading()
            } else {
                resumeLiveReading()
            }
        } else {
            startLiveReading()
        }
    }

    fun startLiveReading() {
        NovelAudioPlaybackService.activeClient = this
        _uiState.update { it.copy(isLiveReading = true, isLivePlaying = true) }
        startLiveReadingInternal(forceStartAtBeginning = false)
    }

    private fun startLiveReadingInternal(forceStartAtBeginning: Boolean = false, retryCount: Int = 0) {
        val webView = webViewRef ?: return
        val settings = _uiState.value.settings
        val script = buildContentExtractionScript(settings.contentSelector, settings.ignoredSelectors)

        webView.evaluateJavascript(script) { result ->
            val json = parseJavascriptResultToJson(result)
            if (json == null) {
                if (retryCount < 2) {
                    retryExtractionJob?.cancel()
                    retryExtractionJob = viewModelScope.launch {
                        delay(800)
                        startLiveReadingInternal(forceStartAtBeginning, retryCount + 1)
                    }
                }
                return@evaluateJavascript
            }

            try {
                val title = json.optString("title", _uiState.value.pageTitle)
                val paragraphsArray = json.optJSONArray("paragraphs") ?: JSONArray()
                val paragraphs = mutableListOf<Paragraph>()
                for (i in 0 until paragraphsArray.length()) {
                    val raw = paragraphsArray.getString(i).trim()
                    if (raw.isNotBlank()) {
                        paragraphs.add(
                            Paragraph(
                                index = paragraphs.size,
                                rawText = raw,
                                spokenText = applyRulesAndSkips(raw),
                                isHeading = paragraphs.isEmpty() && raw.length < 60
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
                    speakLiveParagraph(targetIndex)
                } else {
                    if (retryCount < 2) {
                        retryExtractionJob?.cancel()
                        retryExtractionJob = viewModelScope.launch {
                            delay(1000)
                            startLiveReadingInternal(forceStartAtBeginning, retryCount + 1)
                        }
                    } else {
                        // Empty paragraphs: STOP cleanly without jumping chapters
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
        var clean = text
        val settings = _uiState.value.settings

        // Pronunciation replacements
        for (rule in settings.pronunciationRules) {
            if (rule.from.isBlank()) continue
            clean = if (rule.exactMatch) {
                val regex = Regex("""\b${Regex.escape(rule.from)}\b""", RegexOption.IGNORE_CASE)
                clean.replace(regex, rule.to)
            } else {
                clean.replace(rule.from, rule.to, ignoreCase = true)
            }
        }

        // Skip words
        for (skip in settings.skipWords) {
            if (skip.text.isBlank()) continue
            clean = if (skip.exactMatch) {
                val regex = Regex("""\b${Regex.escape(skip.text)}\b""", RegexOption.IGNORE_CASE)
                clean.replace(regex, "")
            } else {
                clean.replace(skip.text, "", ignoreCase = true)
            }
        }

        return clean.replace(Regex("""\s+"""), " ").trim()
    }

    private fun speakLiveParagraph(index: Int) {
        val paras = _uiState.value.liveParagraphs
        if (paras.isEmpty()) return

        if (index !in paras.indices) {
            // End of chapter reached!
            handleLiveChapterEnd()
            return
        }

        val target = paras[index]
        val spoken = target.spokenText.ifBlank { target.rawText }
        if (spoken.isBlank()) {
            handleLiveParagraphDone(index)
            return
        }

        val settings = _uiState.value.settings
        ttsManager?.speak(
            index = index,
            text = spoken,
            rate = settings.ttsRate,
            pitch = settings.ttsPitch
        )
    }

    private fun handleLiveParagraphDone(index: Int) {
        if (!_uiState.value.isLiveReading || !_uiState.value.isLivePlaying) return

        val paras = _uiState.value.liveParagraphs
        val nextIdx = index + 1

        if (nextIdx < paras.size) {
            _uiState.update { it.copy(liveCurrentParagraphIndex = nextIdx) }
            speakLiveParagraph(nextIdx)
        } else {
            handleLiveChapterEnd()
        }
    }

    private fun handleLiveChapterEnd() {
        if (_uiState.value.liveParagraphs.isEmpty()) return

        val autoNext = _uiState.value.settings.autoClickOnScroll || _uiState.value.isLiveReading
        if (autoNext) {
            _uiState.update {
                it.copy(toastMessage = "End of chapter reached. Loading next chapter...")
            }
            clickNextChapter()
        } else {
            stopLiveReading()
        }
    }

    fun pauseLiveReading() {
        ttsManager?.stop()
        _uiState.update { it.copy(isLivePlaying = false) }
        updateLivePlaybackNotification()
    }

    fun resumeLiveReading() {
        NovelAudioPlaybackService.activeClient = this
        if (_uiState.value.liveParagraphs.isEmpty()) {
            startLiveReading()
            return
        }
        val currentIdx = _uiState.value.liveCurrentParagraphIndex
        _uiState.update { it.copy(isLivePlaying = true, isLiveReading = true) }
        speakLiveParagraph(currentIdx)
    }

    fun stopLiveReading() {
        ttsManager?.stop()
        clearWebHighlight()
        _uiState.update {
            it.copy(
                isLiveReading = false,
                isLivePlaying = false,
                liveParagraphs = emptyList()
            )
        }
        NovelAudioPlaybackService.stopService(getApplication())
    }

    fun seekToParagraph(index: Int) {
        NovelAudioPlaybackService.activeClient = this
        val paras = _uiState.value.liveParagraphs
        if (paras.isEmpty()) return
        val target = index.coerceIn(0, paras.size - 1)
        ttsManager?.stop()
        _uiState.update { it.copy(liveCurrentParagraphIndex = target, isLivePlaying = true, isLiveReading = true) }
        speakLiveParagraph(target)
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
        val currentRate = _uiState.value.settings.ttsRate
        val nextRate = when {
            currentRate < 0.74f -> 0.75f
            currentRate < 0.99f -> 1.0f
            currentRate < 1.24f -> 1.25f
            currentRate < 1.49f -> 1.5f
            currentRate < 1.99f -> 2.0f
            else -> 0.5f
        }
        viewModelScope.launch {
            settingsRepository.setBrowserRate(nextRate)
        }
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
        sleepTimerJob?.cancel()
        val totalSecs = minutes * 60
        _uiState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = totalSecs,
                showSleepTimerDialog = false,
                toastMessage = if (minutes > 0) "Sleep timer set for $minutes minutes" else "Sleep timer turned off"
            )
        }

        viewModelScope.launch {
            settingsRepository.setBrowserSleepTimerMinutes(minutes)
        }

        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remaining = totalSecs
                while (remaining > 0) {
                    delay(1000)
                    if (_uiState.value.isLivePlaying) {
                        remaining--
                        _uiState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
                    }
                }
                pauseLiveReading()
                _uiState.update {
                    it.copy(
                        sleepTimerMinutes = 0,
                        sleepTimerRemainingSeconds = 0,
                        toastMessage = "Sleep timer expired. Playback paused."
                    )
                }
            }
        }
    }

    private fun updateLivePlaybackNotification() {
        if (!_uiState.value.isLiveReading) {
            NovelAudioPlaybackService.stopService(getApplication())
            return
        }
        val title = _uiState.value.liveChapterTitle.ifBlank { _uiState.value.pageTitle }
        val current = _uiState.value.liveCurrentParagraphIndex + 1
        val total = _uiState.value.liveParagraphs.size
        NovelAudioPlaybackService.startOrUpdateService(
            context = getApplication(),
            isPlaying = _uiState.value.isLivePlaying,
            novelTitle = "Web Browser Reader",
            chapterTitle = title.ifBlank { "Web Page" },
            progressText = if (total > 0) "¶ $current / $total" else "Ready",
            currentChapterIndex = 0,
            totalChapters = 1,
            currentParagraph = _uiState.value.liveCurrentParagraphIndex,
            totalParagraphs = total
        )
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
        ttsManager?.speak(
            index = 999999,
            text = sampleText,
            rate = settings.ttsRate,
            pitch = settings.ttsPitch
        )
        viewModelScope.launch {
            delay(4000)
            stopVoiceTest()
        }
    }

    private fun stopVoiceTest() {
        if (_uiState.value.isVoiceTesting) {
            ttsManager?.stop()
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
        viewModelScope.launch {
            settingsRepository.setBrowserVoice(voiceName)
        }
    }

    fun updateRate(rate: Float) {
        viewModelScope.launch {
            settingsRepository.setBrowserRate(rate)
        }
    }

    fun updatePitch(pitch: Float) {
        viewModelScope.launch {
            settingsRepository.setBrowserPitch(pitch)
        }
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

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetBrowserSettingsToDefaults()
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveReading()
        ttsManager?.shutdown()
        downloadCountdownJob?.cancel()
        autoLoopJob?.cancel()
        sleepTimerJob?.cancel()
        retryExtractionJob?.cancel()
    }
}
