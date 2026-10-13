package com.novelarrow.reader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Novel(
    val id: String,
    val title: String,
    val rootTreeUri: String,
    val chapterCount: Int,
    val completedCount: Int = 0,
    val currentChapterIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isWebNovel: Boolean = false,
    val webUrl: String? = null
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
data class PronunciationRule(
    val from: String,
    val to: String,
    val exactMatch: Boolean = false
)

@Serializable
data class SkipWord(
    val text: String,
    val exactMatch: Boolean = false
)

@Serializable
data class ReaderSettings(
    val theme: ThemeMode = ThemeMode.SEPIA,
    val fontSizeSp: Float = 16f,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val selectedVoiceName: String? = null,
    val autoNextChapter: Boolean = true,
    val autoScroll: Boolean = true,
    val rememberProgress: Boolean = true,
    val loopCount: Int = 10,
    val countdownDelayMs: Long = 1000L,
    val autoNextOnScrollBottom: Boolean = false,
    val contentSelector: String = "",
    val contentSelectorEnabled: Boolean = true,
    val headerSkipString: String = "",
    val headerSkipEnabled: Boolean = true,
    val footerSkipString: String = "",
    val footerSkipEnabled: Boolean = true,
    val skipWords: List<SkipWord> = emptyList(),
    val skipWordsEnabled: Boolean = true,
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val pronunciationRulesEnabled: Boolean = true,
    val sleepTimerMinutes: Int = 0
)

@Serializable
data class WebBrowserSettings(
    val lastUrl: String = "https://www.google.com",
    val nextSelector: String = "button[title*=\"Next Chapter\"], a[title*=\"Next Chapter\"], button[title=\"Next chapter\"], a[title=\"Next chapter\"], #next_chap, .next-chap, .next-chapter, .btn-next, a.next",
    val contentSelector: String = "",
    val countdownDelayMs: Long = 1000L,
    val loopCount: Int = 100,
    val autoClickOnScroll: Boolean = false,
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
    val pronunciationRules: List<PronunciationRule> = emptyList()
)
