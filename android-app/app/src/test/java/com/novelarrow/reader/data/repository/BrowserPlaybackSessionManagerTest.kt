package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BrowserPlaybackSessionManagerTest {

    private lateinit var context: Context
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "browser_last_session.json").delete()
        File(context.filesDir, "browser_offline_session.html").delete()
        val prefs = context.getSharedPreferences("browser_session_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testBrowserPlaybackSessionSerialization() {
        val session = BrowserPlaybackSession(
            url = "https://example.com/novel/ch1",
            chapterTitle = "Chapter 1: The Beginning",
            novelTitle = "Shadow Monarch",
            paragraphIndex = 14,
            isReaderMode = true,
            totalParagraphs = 35,
            timestamp = 1700000000000L
        )

        val encoded = json.encodeToString(session)
        assertTrue(encoded.contains("\"paragraphIndex\": 14"))
        assertTrue(encoded.contains("\"isReaderMode\": true"))

        val decoded = json.decodeFromString<BrowserPlaybackSession>(encoded)
        assertEquals("https://example.com/novel/ch1", decoded.url)
        assertEquals("Chapter 1: The Beginning", decoded.chapterTitle)
        assertEquals(14, decoded.paragraphIndex)
        assertTrue(decoded.isReaderMode)
        assertEquals(35, decoded.totalParagraphs)
    }

    @Test
    fun testLastPlaybackSourceTracking() {
        // Default should be READER
        assertEquals(BrowserPlaybackSessionManager.SOURCE_READER, BrowserPlaybackSessionManager.getLastPlaybackSource(context))

        // Set to BROWSER
        BrowserPlaybackSessionManager.setLastPlaybackSource(context, BrowserPlaybackSessionManager.SOURCE_BROWSER)
        assertEquals(BrowserPlaybackSessionManager.SOURCE_BROWSER, BrowserPlaybackSessionManager.getLastPlaybackSource(context))

        // Set back to READER
        BrowserPlaybackSessionManager.setLastPlaybackSource(context, BrowserPlaybackSessionManager.SOURCE_READER)
        assertEquals(BrowserPlaybackSessionManager.SOURCE_READER, BrowserPlaybackSessionManager.getLastPlaybackSource(context))
    }

    @Test
    fun testSaveAndLoadSessionAndOfflineHtml() = runBlocking {
        val testUrl = "https://novel.test/chapter-5"
        val testTitle = "Chapter 5: Rising Storm"
        val testHtml = "<!DOCTYPE html><html><head><title>Chapter 5</title></head><body><div id=\"chapter-content\"><p>Paragraph 1</p><p>Paragraph 2</p></div></body></html>"

        BrowserPlaybackSessionManager.saveProgressInternal(
            context = context,
            url = testUrl,
            chapterTitle = testTitle,
            novelTitle = "Rising Novel",
            paragraphIndex = 3,
            isReaderMode = false,
            totalParagraphs = 10,
            forceImmediate = true
        )

        BrowserPlaybackSessionManager.saveOfflineHtml(context, testHtml)

        val loadedSession = BrowserPlaybackSessionManager.loadLastSession(context)
        assertNotNull(loadedSession)
        assertEquals(testUrl, loadedSession?.url)
        assertEquals(testTitle, loadedSession?.chapterTitle)
        assertEquals(3, loadedSession?.paragraphIndex)
        assertEquals(10, loadedSession?.totalParagraphs)
        assertEquals(BrowserPlaybackSessionManager.SOURCE_BROWSER, BrowserPlaybackSessionManager.getLastPlaybackSource(context))

        val loadedHtml = BrowserPlaybackSessionManager.getOfflineHtml(context)
        assertNotNull(loadedHtml)
        assertEquals(testHtml, loadedHtml)
        assertTrue(BrowserPlaybackSessionManager.hasOfflineSession(context))
    }

    @Test
    fun testShouldProposeResumeLifecycle() = runBlocking {
        // Initially no session, should not propose
        assertFalse(BrowserPlaybackSessionManager.shouldProposeResume(context))

        // Save progress & offline HTML
        BrowserPlaybackSessionManager.saveProgressInternal(
            context = context,
            url = "https://novel.test/ch1",
            chapterTitle = "Chapter 1",
            novelTitle = "Test Novel",
            paragraphIndex = 2,
            isReaderMode = false,
            totalParagraphs = 10,
            forceImmediate = true
        )
        BrowserPlaybackSessionManager.saveOfflineHtml(context, "<html><body><p>Text</p></body></html>")

        // Source is BROWSER, offline HTML exists, timestamp recent -> should propose
        assertTrue(BrowserPlaybackSessionManager.shouldProposeResume(context))

        // Dismiss prompt
        val session = BrowserPlaybackSessionManager.getLastSession(context)!!
        BrowserPlaybackSessionManager.dismissPrompt(context, session)

        // Dismissed -> should not propose
        assertFalse(BrowserPlaybackSessionManager.shouldProposeResume(context))

        // But if user plays from READER, source is no longer BROWSER -> should not propose
        BrowserPlaybackSessionManager.setLastPlaybackSource(context, BrowserPlaybackSessionManager.SOURCE_READER)
        assertFalse(BrowserPlaybackSessionManager.shouldProposeResume(context))
    }
}
