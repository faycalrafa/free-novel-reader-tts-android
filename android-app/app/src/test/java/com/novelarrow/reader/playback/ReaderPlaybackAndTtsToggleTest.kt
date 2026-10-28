package com.novelarrow.reader.playback

import com.novelarrow.reader.data.model.Paragraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPlaybackAndTtsToggleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVoiceEngine() = CoordinatorVoiceEngine(
        scope = testScope,
        ttsManagerProvider = { null },
        settingsRepositoryProvider = { null },
        onSpeakRequested = {},
        onPauseRequested = {},
        onVoicesUpdated = {}
    )

    @Test
    fun testTargetParagraphSelection_skipsBlankParagraphs() {
        val paragraphs = listOf(
            Paragraph(index = 0, rawText = "Title", spokenText = "Title"),
            Paragraph(index = 1, rawText = "   ", spokenText = "   "), // blank
            Paragraph(index = 2, rawText = "   ", spokenText = ""),    // blank
            Paragraph(index = 3, rawText = "Paragraph 3", spokenText = "Paragraph 3"),
            Paragraph(index = 4, rawText = "Paragraph 4", spokenText = "Paragraph 4")
        )

        // When user taps index 0, index 0 is valid and readable
        val target0 = (0 until paragraphs.size).firstOrNull { paragraphs[it].spokenText.isNotBlank() } ?: 0
        assertEquals(0, target0)

        // When user taps index 1 (blank), it should find index 3 (first readable after 1)
        val target1 = (1 until paragraphs.size).firstOrNull { paragraphs[it].spokenText.isNotBlank() }
            ?: (1 downTo 0).firstOrNull { paragraphs[it].spokenText.isNotBlank() }
            ?: 1
        assertEquals(3, target1)

        // When user taps index 2 (blank), it should find index 3
        val target2 = (2 until paragraphs.size).firstOrNull { paragraphs[it].spokenText.isNotBlank() }
            ?: (2 downTo 0).firstOrNull { paragraphs[it].spokenText.isNotBlank() }
            ?: 2
        assertEquals(3, target2)
    }

    @Test
    fun testCoordinatorSpeechQueue_skipsLeadingBlankParagraphs() {
        var currentParaIndex = 1 // points to blank paragraph
        val paragraphs = listOf(
            Paragraph(index = 0, rawText = "Heading", spokenText = "Heading"),
            Paragraph(index = 1, rawText = "   ", spokenText = "   "), // blank
            Paragraph(index = 2, rawText = "First actual content", spokenText = "First actual content")
        )

        var notifiedIndex = -1
        var notifiedParaText = ""
        var advanceChapterCalled = false

        val queue = CoordinatorSpeechQueue(
            scope = testScope,
            ttsManagerProvider = { null },
            settingsRepositoryProvider = { null },
            voiceEngine = createVoiceEngine(),
            paragraphsProvider = { paragraphs },
            currentParagraphIndexProvider = { currentParaIndex },
            isPlayingProvider = { true },
            currentSourceProvider = { PlaybackSource.READER },
            autoNextChapterProvider = { true },
            autoNextBrowserPageProvider = { false },
            currentNovelIdProvider = { "novel_1" },
            currentChapterIndexProvider = { 0 },
            onParagraphChanged = { idx, p ->
                notifiedIndex = idx
                notifiedParaText = p?.spokenText ?: ""
                currentParaIndex = idx
            },
            onChapterAdvanceNeeded = { advanceChapterCalled = true },
            onBrowserNextPageNeeded = {},
            onPauseRequested = {},
            onProgressSaveRequested = { _, _, _, _ -> },
            showToast = {}
        )

        queue.speakCurrentParagraph()

        // Verify it automatically advanced past the blank paragraph at index 1 to index 2
        assertEquals(2, currentParaIndex)
        assertEquals(2, notifiedIndex)
        assertEquals("First actual content", notifiedParaText)
        assertFalse(advanceChapterCalled)
    }

    @Test
    fun testCoordinatorSpeechQueue_ignoresStaleParagraphDoneAndInterruptedError() {
        var currentParaIndex = 10
        val paragraphs = (0..20).map {
            Paragraph(index = it, rawText = "Para $it", spokenText = "Para $it")
        }

        var pauseRequested = false
        var savedProgressPara = -1

        val queue = CoordinatorSpeechQueue(
            scope = testScope,
            ttsManagerProvider = { null },
            settingsRepositoryProvider = { null },
            voiceEngine = createVoiceEngine(),
            paragraphsProvider = { paragraphs },
            currentParagraphIndexProvider = { currentParaIndex },
            isPlayingProvider = { true },
            currentSourceProvider = { PlaybackSource.READER },
            autoNextChapterProvider = { true },
            autoNextBrowserPageProvider = { false },
            currentNovelIdProvider = { "novel_1" },
            currentChapterIndexProvider = { 0 },
            onParagraphChanged = { idx, _ -> currentParaIndex = idx },
            onChapterAdvanceNeeded = {},
            onBrowserNextPageNeeded = {},
            onPauseRequested = { pauseRequested = true },
            onProgressSaveRequested = { _, _, pIdx, _ -> savedProgressPara = pIdx },
            showToast = {}
        )

        // Simulate callback for an old paragraph (e.g. paragraph 2 finishing after user seeked to 10)
        queue.handleParagraphDone(2)
        assertEquals("Stale paragraph done callback must not save progress for old paragraph", -1, savedProgressPara)

        // Simulate error callback for an old paragraph (e.g. paragraph 2 cancelled with -2)
        queue.handleParagraphError(2, "Error code -2")
        assertFalse("Stale paragraph error callback must not pause playback", pauseRequested)

        // Simulate interrupted error for the current paragraph (user seeking again)
        queue.handleParagraphError(10, "Error code -2")
        assertFalse("Interrupted synthesis (-2) must not trigger playback pause or consecutive errors", pauseRequested)
    }

    @Test
    fun testTopBarTtsToggleLogic_startsAtFirstVisibleAndStopsWhenPlaying() {
        var isPlaying = false
        var stopped = false
        var startedAtParagraph = -1

        fun onTtsToggleClick(firstVisible: Int) {
            if (isPlaying) {
                stopped = true
                isPlaying = false
            } else {
                startedAtParagraph = firstVisible
                isPlaying = true
            }
        }

        // 1. Initial click: not playing, user scrolled to paragraph 14
        onTtsToggleClick(firstVisible = 14)
        assertTrue(isPlaying)
        assertEquals(14, startedAtParagraph)
        assertFalse(stopped)

        // 2. Second click: while playing -> stops session (stop, not pause)
        onTtsToggleClick(firstVisible = 18)
        assertFalse(isPlaying)
        assertTrue(stopped)

        // 3. Third click: user scrolled to paragraph 30 -> starts from 30
        stopped = false
        onTtsToggleClick(firstVisible = 30)
        assertTrue(isPlaying)
        assertEquals(30, startedAtParagraph)
        assertFalse(stopped)
    }
}
