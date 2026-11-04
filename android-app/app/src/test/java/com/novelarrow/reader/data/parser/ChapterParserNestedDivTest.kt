package com.novelarrow.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserNestedDivTest {

    @Test
    fun testNestedDivChapters_doNotDuplicateContent() {
        // Simulates downloaded clean HTML structure with outer container divs wrapping leaf divs
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Test Novel - Chapter 1</title></head>
            <body>
                <div class="reader-container">
                    <header class="reader-header">
                        <div class="reader-novel-title">Test Novel</div>
                        <h1 class="reader-chapter-title">Chapter 1</h1>
                    </header>
                    <div class="reader-content">
                        <div>Chapter 1: The Beginning</div>
                        <div>The morning sun climbed over the distant eastern hills.</div>
                        <div>Lin looked out from the wooden pavilion and took a deep breath.</div>
                        <div>"Another peaceful day in the outer sect," he murmured softly.</div>
                    </div>
                </div>
                <button id="na-floating-next">Next</button>
            </body>
            </html>
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = html,
            fileName = "Test Novel - Chapter 1.html"
        )

        val texts = paragraphs.map { it.rawText }

        // Must not duplicate the entire chapter into giant concatenated blocks
        val titleOccurrences = texts.count { it.contains("Chapter 1: The Beginning") }
        assertEquals("Chapter heading must appear exactly once", 1, titleOccurrences)

        val sunOccurrences = texts.count { it.contains("The morning sun climbed over the distant eastern hills.") }
        assertEquals("Body paragraph must appear exactly once", 1, sunOccurrences)

        assertEquals("Total paragraphs should match the 4 leaf div elements", 4, paragraphs.size)
    }

    @Test
    fun testDeeplyNestedDivStructure_extractsOnlyLeafElements() {
        val html = """
            <div class="main-content">
                <div class="entry-content">
                    <div class="chapter-body">
                        <div>First paragraph of story.</div>
                        <div>Second paragraph of story.</div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = html,
            fileName = "chapter.html"
        )

        assertEquals(2, paragraphs.size)
        assertEquals("First paragraph of story.", paragraphs[0].rawText)
        assertEquals("Second paragraph of story.", paragraphs[1].rawText)
    }
}
