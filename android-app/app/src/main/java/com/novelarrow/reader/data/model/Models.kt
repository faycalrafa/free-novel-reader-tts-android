package com.novelarrow.reader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Novel(
    val id: String,
    val title: String,
    val rootTreeUri: String = "",
    val chapterCount: Int = 0,
    val completedCount: Int = 0,
    val currentChapterIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isWebNovel: Boolean = false,
    val webUrl: String? = null,
    val isArchived: Boolean = false
)

@Serializable
data class Chapter(
    val id: String,
    val novelId: String,
    val title: String,
    val fileUri: String,
    val orderIndex: Int,
    val isCompleted: Boolean = false,
    val lastReadParagraphIndex: Int = 0,
    val lastScrollOffset: Int = 0,
    val sizeBytes: Long = 0,
    val webSourceUrl: String? = null
)

fun Chapter.isTextBased(): Boolean {
    val uri = fileUri.lowercase()
    val t = title.lowercase()
    val nonText = listOf(".pdf", ".epub", ".mobi", ".azw", ".cbz", ".cbr", ".zip", ".rar")
    if (nonText.any { uri.endsWith(it) || t.endsWith(it) }) return false
    val textExts = listOf(".html", ".htm", ".xhtml", ".mhtml", ".txt", ".text", ".md", ".markdown", ".xml", ".json", ".csv", ".log", ".rtf")
    return textExts.any { uri.endsWith(it) || t.endsWith(it) } || (!uri.contains(".") && !t.contains("."))
}

fun Chapter.detectEditorLanguage(): String {
    val uri = fileUri.lowercase()
    val t = title.lowercase()
    return when {
        uri.endsWith(".html") || uri.endsWith(".htm") || uri.endsWith(".xhtml") || uri.endsWith(".mhtml") || t.endsWith(".html") || t.endsWith(".htm") -> "html"
        uri.endsWith(".md") || uri.endsWith(".markdown") || t.endsWith(".md") -> "markdown"
        uri.endsWith(".json") || t.endsWith(".json") -> "json"
        uri.endsWith(".xml") || t.endsWith(".xml") -> "xml"
        else -> "plaintext"
    }
}

data class Paragraph(
    val index: Int,
    val rawText: String,
    val spokenText: String,
    val isHeading: Boolean = false
)

enum class ThemeMode {
    DARK,
    SEPIA,
    LIGHT
}

@Serializable
enum class PronunciationScopeType {
    GLOBAL,
    FOLDER,
    URL_PATTERN
}

@Serializable
data class PronunciationRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val from: String,
    val to: String,
    val exactMatch: Boolean = true,
    val enabled: Boolean = true,
    val scopeType: PronunciationScopeType = PronunciationScopeType.GLOBAL,
    val scopePattern: String = ""
) {
    constructor(from: String, to: String, exactMatch: Boolean = true) : this(
        id = java.util.UUID.randomUUID().toString(),
        from = from,
        to = to,
        exactMatch = exactMatch,
        enabled = true,
        scopeType = PronunciationScopeType.GLOBAL,
        scopePattern = ""
    )
}

@Serializable
data class SkipWord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val exactMatch: Boolean = true,
    val enabled: Boolean = true
) {
    constructor(text: String, exactMatch: Boolean = true) : this(
        id = java.util.UUID.randomUUID().toString(),
        text = text,
        exactMatch = exactMatch,
        enabled = true
    )
}

@Serializable
data class SkipRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val exactMatch: Boolean = false,
    val enabled: Boolean = true
) {
    constructor(text: String, exactMatch: Boolean = false) : this(
        id = java.util.UUID.randomUUID().toString(),
        text = text,
        exactMatch = exactMatch,
        enabled = true
    )
}

@Serializable
data class ContentSelectorRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val selector: String,
    val enabled: Boolean = true,
    val scopeType: PronunciationScopeType = PronunciationScopeType.GLOBAL,
    val scopePattern: String = ""
) {
    constructor(selector: String, enabled: Boolean = true) : this(
        id = java.util.UUID.randomUUID().toString(),
        selector = selector,
        enabled = enabled,
        scopeType = PronunciationScopeType.GLOBAL,
        scopePattern = ""
    )
}

@Serializable
data class QuickToolbarSettings(
    val enabled: Boolean = true,
    val showSpeedButton: Boolean = true,
    val showSleepTimerButton: Boolean = true,
    val showPrevChapButton: Boolean = true,
    val showPlayPauseButton: Boolean = true,
    val showNextChapButton: Boolean = true,
    val showSiteNextButton: Boolean = true,
    val showCloseButton: Boolean = true
)

@Serializable
enum class HardwareButtonAction(val label: String) {
    NONE("None / Disabled"),
    DEFAULT_VOLUME("Default Volume Control"),
    PLAY_PAUSE("Play / Pause"),
    NEXT_PARAGRAPH("Next Paragraph"),
    PREV_PARAGRAPH("Previous Paragraph"),
    NEXT_CHAPTER("Next Chapter"),
    PREV_CHAPTER("Previous Chapter"),
    INCREASE_SPEED("Increase Speed (+0.1x)"),
    DECREASE_SPEED("Decrease Speed (-0.1x)"),
    SKIP_FORWARD_5("Forward 5 Paragraphs"),
    SKIP_BACKWARD_5("Rewind 5 Paragraphs")
}

@Serializable
enum class HardwareButtonScreenCondition(val label: String) {
    ALWAYS("Always (Screen On & Off)"),
    SCREEN_OFF_ONLY("Only when screen is OFF"),
    SCREEN_ON_ONLY("Only when screen is ON")
}

@Serializable
data class HardwareButtonSettings(
    val enabled: Boolean = false,
    val screenCondition: HardwareButtonScreenCondition = HardwareButtonScreenCondition.ALWAYS,
    val hapticFeedback: Boolean = true,
    // Volume Up (+)
    val volUpSinglePress: HardwareButtonAction = HardwareButtonAction.DEFAULT_VOLUME,
    val volUpDoublePress: HardwareButtonAction = HardwareButtonAction.NEXT_PARAGRAPH,
    val volUpLongPress: HardwareButtonAction = HardwareButtonAction.NEXT_CHAPTER,
    // Volume Down (-)
    val volDownSinglePress: HardwareButtonAction = HardwareButtonAction.DEFAULT_VOLUME,
    val volDownDoublePress: HardwareButtonAction = HardwareButtonAction.PREV_PARAGRAPH,
    val volDownLongPress: HardwareButtonAction = HardwareButtonAction.PREV_CHAPTER
)

@Serializable
data class ReaderSettings(
    val theme: ThemeMode = ThemeMode.SEPIA,
    val fontSizeSp: Float = 16f,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val selectedVoiceName: String? = null,
    val favoriteVoiceEnglish: String? = null,
    val favoriteVoiceArabic: String? = null,
    val favoriteVoiceFrench: String? = null,
    val selectedEngineType: String = "ANDROID_SYSTEM",
    val selectedTtsEnginePackage: String? = null,
    val autoNextChapter: Boolean = true,
    val autoScroll: Boolean = true,
    val rememberProgress: Boolean = true,
    val loopCount: Int = 10,
    val countdownDelayMs: Long = 1000L,
    val autoNextOnScrollBottom: Boolean = false,
    val contentSelector: String = "",
    val contentSelectorEnabled: Boolean = true,
    val contentSelectors: List<ContentSelectorRule> = emptyList(),
    val headerSkipString: String = "",
    val headerSkipEnabled: Boolean = true,
    val footerSkipString: String = "",
    val footerSkipEnabled: Boolean = true,
    val headerSkipRules: List<SkipRule> = emptyList(),
    val footerSkipRules: List<SkipRule> = emptyList(),
    val skipWords: List<SkipWord> = emptyList(),
    val skipWordsEnabled: Boolean = true,
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val pronunciationRulesEnabled: Boolean = true,
    val sleepTimerMinutes: Int = 0,
    val quickToolbarSettings: QuickToolbarSettings = QuickToolbarSettings(),
    val hardwareButtonSettings: HardwareButtonSettings = HardwareButtonSettings()
)

@Serializable
data class WebBrowserSettings(
    val lastUrl: String = "https://www.google.com",
    val nextSelector: String = "button[title*=\"Next Chapter\"], a[title*=\"Next Chapter\"], button[title=\"Next chapter\"], a[title=\"Next chapter\"], #next_chap, .next-chap, .next-chapter, .btn-next, a.next",
    val contentSelector: String = "",
    val countdownDelayMs: Long = 1000L,
    val loopCount: Int = 100,
    val autoClickOnScroll: Boolean = false,
    val autoReadNextChapter: Boolean = false,
    val ttsAutoStart: Boolean = false,
    val ttsVoice: String? = null,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsAutoScroll: Boolean = true,
    val ttsSynthByParagraph: Boolean = true,
    val sleepTimerMinutes: Int = 0,
    val ignoredSelectors: List<String> = listOf(
        "nav", "footer", "header", "aside", "button", "a",
        "[role=\"navigation\"]", ".ads", ".ad", ".advertisement",
        "script", "style", "noscript", "iframe",
        "form", "input", "textarea", "select",
        "#custom-floating-bar", "#na-root", "#na-floating-next",
        "#na-player-bar", "#na-modal-backdrop-el", ".na-tts-highlight",
        ".comments", "#comments", ".sidebar", "#sidebar"
    ),
    val skipWords: List<SkipWord> = emptyList(),
    val skipWordsEnabled: Boolean = true,
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val pronunciationRulesEnabled: Boolean = true
)
