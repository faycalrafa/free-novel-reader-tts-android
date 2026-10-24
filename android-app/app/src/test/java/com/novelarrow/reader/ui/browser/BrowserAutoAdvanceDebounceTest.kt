package com.novelarrow.reader.ui.browser

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BrowserAutoAdvanceDebounceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AudioPlaybackCoordinator.stop()
    }

    @After
    fun tearDown() {
        AudioPlaybackCoordinator.stop()
    }

    @Test
    fun testAudioPlaybackCoordinator_suppressesRapidDuplicateBrowserPlayback() = runTest {
        val paras = listOf(
            Paragraph(index = 0, rawText = "Chapter 14: The Return", spokenText = "Chapter 14: The Return"),
            Paragraph(index = 1, rawText = "Long Chen looked at the sky.", spokenText = "Long Chen looked at the sky.")
        )

        // First call starts browser playback
        AudioPlaybackCoordinator.playBrowserParagraphs(
            context = context,
            chapterTitle = "Chapter 14",
            paragraphs = paras,
            startParagraphIndex = 0,
            isPreMerged = true
        )

        assertEquals(PlaybackSource.BROWSER, AudioPlaybackCoordinator.playbackProgressState.value.source)
        assertEquals("Chapter 14", AudioPlaybackCoordinator.playbackProgressState.value.chapterTitle)
        assertEquals(0, AudioPlaybackCoordinator.playbackProgressState.value.currentParagraphIndex)

        // Rapid duplicate call within < 400ms with same title and index 0
        AudioPlaybackCoordinator.playBrowserParagraphs(
            context = context,
            chapterTitle = "Chapter 14",
            paragraphs = paras,
            startParagraphIndex = 0,
            isPreMerged = true
        )

        // State remains valid and playback is preserved
        assertEquals(PlaybackSource.BROWSER, AudioPlaybackCoordinator.playbackProgressState.value.source)
        assertEquals("Chapter 14", AudioPlaybackCoordinator.playbackProgressState.value.chapterTitle)
    }

    @Test
    fun testBrowserViewModel_onReceivedTitle_updatesTitleWithoutTriggeringPageFinish() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = BrowserViewModel(app)

        // Simulate page started with 30% progress
        viewModel.onPageStarted("https://novelping.com/book/nine-s1/c14")
        viewModel.onPageProgress(30)

        assertEquals("https://novelping.com/book/nine-s1/c14", viewModel.uiState.value.currentUrl)
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(30, viewModel.uiState.value.progress)

        // WebChromeClient receives title while page is still loading
        viewModel.onReceivedTitle("Chapter 14: Nine Heavens")

        // Title must update, but page must NOT be treated as finished!
        assertEquals("Chapter 14: Nine Heavens", viewModel.uiState.value.pageTitle)
        assertTrue("isLoading must remain true when title is received early", viewModel.uiState.value.isLoading)
        assertEquals(30, viewModel.uiState.value.progress)
        assertFalse("Playback must not be triggered prematurely", viewModel.uiState.value.isLivePlaying)
    }
}
