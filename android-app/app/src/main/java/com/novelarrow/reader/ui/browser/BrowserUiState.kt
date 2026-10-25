package com.novelarrow.reader.ui.browser

import android.speech.tts.Voice
import com.novelarrow.reader.data.model.BrowserHistoryEntry
import com.novelarrow.reader.data.model.BrowserTab
import com.novelarrow.reader.data.model.CustomToolbarButton
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.data.userscript.UserscriptEntity

sealed interface BrowserAction {
    data class LoadUrl(val url: String) : BrowserAction
    data class LoadDataWithBaseUrl(
        val baseUrl: String?,
        val data: String,
        val mimeType: String = "text/html",
        val encoding: String = "UTF-8",
        val historyUrl: String? = null
    ) : BrowserAction
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
    val customToolbarButtons: List<CustomToolbarButton> = emptyList(),
    val showHistoryDialog: Boolean = false,
    val historyEntries: List<BrowserHistoryEntry> = emptyList()
)
