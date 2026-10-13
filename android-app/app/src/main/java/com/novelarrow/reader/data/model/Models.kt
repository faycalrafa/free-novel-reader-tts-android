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
    val lastUpdated: Long = System.currentTimeMillis()
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
    val sizeBytes: Long = 0
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
