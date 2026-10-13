package com.novelarrow.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    @Test
    fun testHtmlCleanup_removesFootersButtonsAndNav() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Chapter 1</title></head>
            <body>
                <header class="site-header">Novel Site Header</header>
                <nav class="breadcrumb">Home > Novel > Chapter 1</nav>
                <div class="nav-buttons">
                    <button class="btn">Previous Chapter</button>
                    <button class="btn">Next Chapter</button>
                </div>
                <div id="content">
                    <h1>Chapter 1: The Beginning</h1>
                    <p>It was a quiet morning in the valley.</p>
                    <p>The wind blew gently through the pine trees.</p>
                </div>
                <div class="prev-next">
                    <a href="#">Next Chapter</a>
                </div>
                <div class="comments">
                    <h3>Comments</h3>
                    <p>Great chapter!</p>
                </div>
                <footer class="site-footer">
                    <p>Copyright 2026. All rights reserved.</p>
                </footer>
            </body>
            </html>
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = html,
            fileName = "chapter1.html"
        )

        val texts = paragraphs.map { it.rawText }
        assertTrue(texts.any { it.contains("Chapter 1: The Beginning") })
        assertTrue(texts.any { it.contains("It was a quiet morning in the valley.") })
        assertTrue(texts.any { it.contains("The wind blew gently through the pine trees.") })

        // Check that footer, button texts, navs, and comments are removed
        assertFalse(texts.any { it.contains("Novel Site Header", ignoreCase = true) })
        assertFalse(texts.any { it.contains("Previous Chapter", ignoreCase = true) })
        assertFalse(texts.any { it.contains("Next Chapter", ignoreCase = true) })
        assertFalse(texts.any { it.contains("Great chapter!", ignoreCase = true) })
        assertFalse(texts.any { it.contains("Copyright 2026", ignoreCase = true) })
    }

    @Test
    fun testHeaderSkipString_startsFromSpecifiedText() {
        val text = """
            Random Site Preamble and Announcements.
            Please support our Patreon.
            START_READING_HERE
            Chapter 5: The Mountain Peak.
            They reached the summit at dusk.
            The view was breathtaking.
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = text,
            fileName = "chapter5.txt",
            headerSkipString = "START_READING_HERE"
        )

        val texts = paragraphs.map { it.rawText }
        assertEquals(4, texts.size)
        assertTrue(texts[0].contains("START_READING_HERE"))
        assertTrue(texts[1].contains("Chapter 5: The Mountain Peak."))
        assertTrue(texts[2].contains("They reached the summit at dusk."))
        assertTrue(texts[3].contains("The view was breathtaking."))
        assertFalse(texts.any { it.contains("Random Site Preamble") })
        assertFalse(texts.any { it.contains("support our Patreon") })
    }

    @Test
    fun testFooterSkipString_endsAtSpecifiedText() {
        val text = """
            Chapter 6: Into the Cavern.
            The cavern was illuminated by glowing moss.
            They pressed forward cautiously.
            STOP_READING_HERE
            Author's note: I will be taking next week off.
            Leave a comment below!
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = text,
            fileName = "chapter6.txt",
            footerSkipString = "STOP_READING_HERE"
        )

        val texts = paragraphs.map { it.rawText }
        assertEquals(3, texts.size)
        assertTrue(texts[0].contains("Chapter 6: Into the Cavern."))
        assertTrue(texts[1].contains("The cavern was illuminated by glowing moss."))
        assertTrue(texts[2].contains("They pressed forward cautiously."))
        assertFalse(texts.any { it.contains("STOP_READING_HERE") })
        assertFalse(texts.any { it.contains("Author's note") })
        assertFalse(texts.any { it.contains("Leave a comment") })
    }

    @Test
    fun testHeaderAndFooterSkipTogether() {
        val text = """
            [Notice: Translated by Team XYZ]
            === CHAPTER START ===
            Line one of the real content.
            Line two of the real content.
            === CHAPTER END ===
            [Donation links and translators note]
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = text,
            fileName = "chap.txt",
            headerSkipString = "=== CHAPTER START ===",
            footerSkipString = "=== CHAPTER END ==="
        )

        val texts = paragraphs.map { it.rawText }
        assertTrue(texts.any { it.contains("Line one of the real content.") })
        assertTrue(texts.any { it.contains("Line two of the real content.") })
        assertFalse(texts.any { it.contains("Notice: Translated") })
        assertFalse(texts.any { it.contains("Donation links") })
    }

    @Test
    fun testNonContentTextFiltering() {
        assertTrue(ChapterParser.isNonContentText("Next Chapter"))
        assertTrue(ChapterParser.isNonContentText("< Previous Chapter"))
        assertTrue(ChapterParser.isNonContentText("Table of Contents"))
        assertTrue(ChapterParser.isNonContentText("Index"))
        assertTrue(ChapterParser.isNonContentText("Join our Discord"))
        assertTrue(ChapterParser.isNonContentText("Leave a comment"))
        assertFalse(ChapterParser.isNonContentText("The hero stepped into the ancient ruins."))
    }
}
