package com.novelarrow.reader.data.browser

import com.novelarrow.reader.data.util.NaturalOrderComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNovelDownloaderTest {

    @Test
    fun testDetectNovelInfoFromUrlPattern1() {
        val url = "https://novelbin.me/novel-book/shadow-slave/chapter-123"
        val title = "Shadow Slave - Chapter 123"
        val detected = WebNovelDownloader.detectNovelInfo(url, title)

        assertEquals("Shadow Slave", detected.novelTitle)
        assertEquals(123, detected.chapterNumber)
    }

    @Test
    fun testDetectNovelInfoFromUrlPattern2() {
        val url = "https://example.com/novels/lord-of-the-mysteries/c456.html"
        val title = "Lord Of The Mysteries Chapter 456 Online Free"
        val detected = WebNovelDownloader.detectNovelInfo(url, title)

        assertEquals("Lord Of The Mysteries", detected.novelTitle)
        assertEquals(456, detected.chapterNumber)
    }

    @Test
    fun testDetectNovelInfoFromUrlWithSlugFallback() {
        val url = "https://readnovelfull.com/the-beginning-after-the-end/chapter-5.html"
        val detected = WebNovelDownloader.detectNovelInfo(url, "")

        assertEquals("The Beginning After The End", detected.novelTitle)
        assertEquals(5, detected.chapterNumber)
    }

    @Test
    fun testSanitizeFileName() {
        val unsafeName = "Shadow Slave: Volume 1 / Part 2? *Special*"
        val safe = WebNovelDownloader.sanitizeFileName(unsafeName)

        assertEquals("Shadow Slave_ Volume 1 _ Part 2_ _Special_", safe)
    }

    @Test
    fun testFormatChapterFileNameChronological() {
        val ch1 = WebNovelDownloader.formatChapterFileName("Shadow Slave", 1)
        val ch9 = WebNovelDownloader.formatChapterFileName("Shadow Slave", 9)
        val ch10 = WebNovelDownloader.formatChapterFileName("Shadow Slave", 10)
        val ch100 = WebNovelDownloader.formatChapterFileName("Shadow Slave", 100)

        assertEquals("Shadow Slave Chapter 0001.html", ch1)
        assertEquals("Shadow Slave Chapter 0009.html", ch9)
        assertEquals("Shadow Slave Chapter 0010.html", ch10)
        assertEquals("Shadow Slave Chapter 0100.html", ch100)

        val list = listOf(ch100, ch1, ch10, ch9)
        val sorted = list.sortedWith { a, b -> NaturalOrderComparator.compare(a, b) }

        assertEquals(listOf(ch1, ch9, ch10, ch100), sorted)
    }

    @Test
    fun testGenerateCleanChapterHtml() {
        val html = WebNovelDownloader.generateCleanChapterHtml(
            novelTitle = "Shadow Slave",
            chapterTitle = "Chapter 1: Nightmare",
            chapterNumber = 1,
            paragraphs = listOf(
                "First paragraph of the chapter.",
                "Second paragraph describing the scene."
            ),
            sourceUrl = "https://example.com/chapter-1"
        )

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<h1>Chapter 1: Nightmare</h1>"))
        assertTrue(html.contains("<div id=\"chapter-content\">"))
        assertTrue(html.contains("<p>First paragraph of the chapter.</p>"))
        assertTrue(html.contains("<p>Second paragraph describing the scene.</p>"))
        // Clean downloaded HTML must not contain links or next buttons
        assertFalse(html.contains("<a "))
        assertFalse(html.contains("#na-floating-next"))
    }
}
