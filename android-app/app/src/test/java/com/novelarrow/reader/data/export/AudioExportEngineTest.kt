package com.novelarrow.reader.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExportEngineTest {

    @Test
    fun testSplitTextShort() {
        val text = "This is a short chapter for TTS conversion. It should not be split."
        val chunks = AudioExportEngine.splitTextIntoSafeChunks(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun testSplitTextLongPreservesSentenceBoundaries() {
        // Construct text > 3500 chars with sentences
        val sentence = "The knight drew his sword and stepped into the shadowy labyrinth cautiously. "
        val sb = StringBuilder()
        while (sb.length < 5000) {
            sb.append(sentence)
        }
        val fullText = sb.toString().trim()

        val chunks = AudioExportEngine.splitTextIntoSafeChunks(fullText)
        assertTrue(chunks.size >= 2)

        // Ensure no chunk exceeds 3500 chars
        for (chunk in chunks) {
            assertTrue("Chunk length (${chunk.length}) must be <= 3500", chunk.length <= 3500)
            assertTrue("Chunk must end on punctuation", chunk.endsWith(".") || chunk.endsWith("!") || chunk.endsWith("?"))
        }

        // Ensure total content is preserved
        val reassembled = chunks.joinToString(" ")
        assertEquals(fullText.replace(Regex("\\s+"), " "), reassembled.replace(Regex("\\s+"), " "))
    }

    @Test
    fun testSplitTextCjkPunctuation() {
        val cjkSentence = "在幽暗的森林深处，少年握紧了手中的古老秘籍，感受着微弱的灵力波动。"
        val sb = StringBuilder()
        while (sb.length < 4500) {
            sb.append(cjkSentence)
        }
        val fullText = sb.toString().trim()

        val chunks = AudioExportEngine.splitTextIntoSafeChunks(fullText)
        assertTrue(chunks.size >= 2)

        for (chunk in chunks) {
            assertTrue("Chunk length (${chunk.length}) must be <= 3500", chunk.length <= 3500)
            assertTrue("Chunk must end on CJK or standard delimiter", chunk.endsWith("。") || chunk.endsWith("，") || chunk.endsWith("！"))
        }
    }

    @Test
    fun testSanitizeFilename() {
        val safe = AudioExportEngine.sanitizeFilename("Lord of the Mysteries: Chapter 01 [Translated]")
        assertEquals("Lord of the Mysteries_ Chapter 01 _Translated_", safe)

        val fallback = AudioExportEngine.sanitizeFilename("????")
        assertEquals("bookreader_audio", fallback)
    }
}
