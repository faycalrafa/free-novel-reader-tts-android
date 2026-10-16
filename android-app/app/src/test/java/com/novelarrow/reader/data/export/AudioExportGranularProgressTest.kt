package com.novelarrow.reader.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExportGranularProgressTest {

    @Test
    fun testSingleChapterGranularProgression() {
        val totalChapters = 1
        val totalParas = 10

        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, totalChapters, 0, totalParas))
        assertEquals(10, AudioExportEngine.calculateGranularProgress(1, totalChapters, 1, totalParas))
        assertEquals(50, AudioExportEngine.calculateGranularProgress(1, totalChapters, 5, totalParas))
        assertEquals(90, AudioExportEngine.calculateGranularProgress(1, totalChapters, 9, totalParas))
        assertEquals(100, AudioExportEngine.calculateGranularProgress(1, totalChapters, 10, totalParas))
    }

    @Test
    fun testMultiChapterGranularProgression() {
        val totalChapters = 4
        val totalParas = 5

        // Chapter 1
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, totalChapters, 0, totalParas))
        assertEquals(5, AudioExportEngine.calculateGranularProgress(1, totalChapters, 1, totalParas))
        assertEquals(10, AudioExportEngine.calculateGranularProgress(1, totalChapters, 2, totalParas))
        assertEquals(25, AudioExportEngine.calculateGranularProgress(1, totalChapters, 5, totalParas))

        // Chapter 2
        assertEquals(25, AudioExportEngine.calculateGranularProgress(2, totalChapters, 0, totalParas))
        assertEquals(30, AudioExportEngine.calculateGranularProgress(2, totalChapters, 1, totalParas))
        assertEquals(50, AudioExportEngine.calculateGranularProgress(2, totalChapters, 5, totalParas))

        // Chapter 3
        assertEquals(50, AudioExportEngine.calculateGranularProgress(3, totalChapters, 0, totalParas))
        assertEquals(75, AudioExportEngine.calculateGranularProgress(3, totalChapters, 5, totalParas))

        // Chapter 4
        assertEquals(75, AudioExportEngine.calculateGranularProgress(4, totalChapters, 0, totalParas))
        assertEquals(100, AudioExportEngine.calculateGranularProgress(4, totalChapters, 5, totalParas))
    }

    @Test
    fun testStrictMonotonicityAcrossAllCombinations() {
        val testConfigs = listOf(
            Pair(1, 1),
            Pair(1, 50),
            Pair(3, 10),
            Pair(5, 25),
            Pair(10, 5),
            Pair(20, 100)
        )

        for ((totalChapters, totalParagraphs) in testConfigs) {
            var lastPercent = -1
            for (chap in 1..totalChapters) {
                for (para in 0..totalParagraphs) {
                    val percent = AudioExportEngine.calculateGranularProgress(chap, totalChapters, para, totalParagraphs)
                    assertTrue(
                        "Progress must never decrease (prev: $lastPercent, cur: $percent at chap $chap/$totalChapters, para $para/$totalParagraphs)",
                        percent >= lastPercent
                    )
                    assertTrue("Progress must be >= 0", percent >= 0)
                    assertTrue("Progress must be <= 100", percent <= 100)
                    lastPercent = percent
                }
            }
            assertEquals("Final step must reach 100%", 100, lastPercent)
        }
    }

    @Test
    fun testBoundaryAndEdgeCases() {
        // Zero chapters
        assertEquals(0, AudioExportEngine.calculateGranularProgress(0, 0, 0, 0))
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, 0, 5, 10))

        // Negative values
        assertEquals(0, AudioExportEngine.calculateGranularProgress(-1, 5, -2, 10))

        // Out of bounds
        assertEquals(100, AudioExportEngine.calculateGranularProgress(10, 5, 20, 10))

        // Zero paragraphs
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, 2, 0, 0))
        assertEquals(50, AudioExportEngine.calculateGranularProgress(2, 2, 0, 0))
    }
}
