package com.novelarrow.reader.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Milestone4Challenger2Test {

    // =========================================================================
    // 1. DomContentVerifier: Sample Extraction Stress Tests
    // =========================================================================

    @Test
    fun testCreateSampleWithEmptyAndSingletonLists() {
        // Empty list
        assertEquals("", DomContentVerifier.createSample(emptyList()))

        // Singleton list
        assertEquals("First solo paragraph", DomContentVerifier.createSample(listOf("First solo paragraph")))

        // Exactly two paragraphs
        assertEquals("P1, P2", DomContentVerifier.createSample(listOf("P1", "P2")))

        // Exactly three paragraphs
        assertEquals("P1, P2, P3", DomContentVerifier.createSample(listOf("P1", "P2", "P3")))

        // More than three paragraphs (must cap at 3)
        assertEquals("P1, P2, P3", DomContentVerifier.createSample(listOf("P1", "P2", "P3", "P4", "P5", "P6")))
    }

    @Test
    fun testCreateSampleWithCustomCount() {
        val paras = (1..10).map { "Paragraph $it" }
        assertEquals("Paragraph 1", DomContentVerifier.createSample(paras, count = 1))
        assertEquals("Paragraph 1, Paragraph 2, Paragraph 3, Paragraph 4, Paragraph 5", DomContentVerifier.createSample(paras, count = 5))
        assertEquals("", DomContentVerifier.createSample(paras, count = 0))
    }

    @Test
    fun testCreateSampleWhitespaceAndTrimming() {
        val parasWithWhitespace = listOf(
            "   Paragraph with leading and trailing spaces   ",
            "\tParagraph with tabs\t",
            "\nParagraph with newlines\n"
        )
        val sample = DomContentVerifier.createSample(parasWithWhitespace)
        // joinToString() joins the exact strings with ", " and then trim() is called on the whole string
        assertTrue(sample.contains("Paragraph with leading and trailing spaces"))
        assertFalse(sample.startsWith(" "))
        assertFalse(sample.endsWith("\n"))
    }

    @Test
    fun testCreateSampleUnicodeAndCJK() {
        val cjkParas = listOf(
            "第一章：林凡的觉醒。天地玄黄，宇宙洪荒。",
            "第二段：宗门大比开始，众长老神色肃穆。",
            "第三段：突然天边一道惊雷劈落，万物震颤。"
        )
        val sample = DomContentVerifier.createSample(cjkParas)
        assertEquals(
            "第一章：林凡的觉醒。天地玄黄，宇宙洪荒。, 第二段：宗门大比开始，众长老神色肃穆。, 第三段：突然天边一道惊雷劈落，万物震颤。",
            sample
        )
    }

    // =========================================================================
    // 2. hasContentTransitioned: Combinatorial Stress Testing
    // =========================================================================

    @Test
    fun testTransitionFromInitialEmptySignature() {
        // When lastHeading and lastSample are both blank (initial state of download loop)
        assertTrue(
            "Any extracted content is transitioned from empty initial signature",
            DomContentVerifier.hasContentTransitioned("Chapter 1", "Body text", "", "")
        )
        assertTrue(
            "Heading-only is transitioned from empty initial signature",
            DomContentVerifier.hasContentTransitioned("Chapter 1", "", "", "")
        )
        assertTrue(
            "Sample-only is transitioned from empty initial signature",
            DomContentVerifier.hasContentTransitioned("", "Body text", "", "")
        )
        assertFalse(
            "Blank content from empty initial signature must not be transitioned",
            DomContentVerifier.hasContentTransitioned("", "", "", "")
        )
    }

    @Test
    fun testTransitionWithIdenticalContent() {
        val heading = "Chapter 100: The Battle"
        val sample = "Clash of swords, shield wall held, archers fired."

        assertFalse(
            "Strictly identical heading and sample must not be considered transitioned",
            DomContentVerifier.hasContentTransitioned(heading, sample, heading, sample)
        )
    }

    @Test
    fun testTransitionHeadingOnlyChange() {
        val oldHeading = "Chapter 100: The Battle"
        val newHeading = "Chapter 101: The Aftermath"
        val sameSample = "Clash of swords, shield wall held, archers fired."

        assertTrue(
            "Changed heading with same sample must be considered transitioned",
            DomContentVerifier.hasContentTransitioned(newHeading, sameSample, oldHeading, sameSample)
        )
    }

    @Test
    fun testTransitionBodyOnlyChange() {
        // E.g. Generic title "Novel Chapter" or aggregator title that doesn't change
        val genericHeading = "Reincarnated as a Slime"
        val oldSample = "Rimuru woke up in a dark cave."
        val newSample = "Veldora roared with laughter."

        assertTrue(
            "Changed body sample with same generic heading must be considered transitioned",
            DomContentVerifier.hasContentTransitioned(genericHeading, newSample, genericHeading, oldSample)
        )
    }

    @Test
    fun testTransitionBothChanged() {
        val oldHeading = "Chapter 1"
        val oldSample = "In the quiet village of Oakhaven."
        val newHeading = "Chapter 2"
        val newSample = "Traveling down the King's highway."

        assertTrue(
            "Both heading and sample changed must be considered transitioned",
            DomContentVerifier.hasContentTransitioned(newHeading, newSample, oldHeading, oldSample)
        )
    }

    @Test
    fun testTransitionWhenCurrentContentIsBlank() {
        val lastHeading = "Chapter 1"
        val lastSample = "Sample 1"

        assertFalse(
            "Empty current content during page load must not be considered transitioned",
            DomContentVerifier.hasContentTransitioned("", "", lastHeading, lastSample)
        )
        assertFalse(
            "Whitespace-only current content must not be considered transitioned",
            DomContentVerifier.hasContentTransitioned("   ", "\t\n  ", lastHeading, lastSample)
        )
    }

    // =========================================================================
    // 3. isDuplicateContent: Duplicate Capture Prevention Matrix
    // =========================================================================

    @Test
    fun testDuplicateWhenBothMatch() {
        val heading = "Chapter 5"
        val sample = "First sentence. Second sentence. Third sentence."

        assertTrue(
            "Exact match on both heading and sample must be duplicate",
            DomContentVerifier.isDuplicateContent(heading, sample, heading, sample)
        )

        // With whitespace differences
        assertTrue(
            "Match with surrounding whitespace must still be duplicate",
            DomContentVerifier.isDuplicateContent("  $heading  ", "\n$sample\t", heading, sample)
        )
    }

    @Test
    fun testDuplicateWhenOnlyHeadingAvailableInLastSignature() {
        // If last signature only has heading
        val heading = "Chapter 5"
        assertTrue(
            "Matching heading when last sample is blank must be duplicate",
            DomContentVerifier.isDuplicateContent(heading, "Some dynamic text", heading, "")
        )
        assertFalse(
            "Different heading when last sample is blank must not be duplicate",
            DomContentVerifier.isDuplicateContent("Chapter 6", "Some dynamic text", heading, "")
        )
    }

    @Test
    fun testDuplicateWhenOnlySampleAvailableInLastSignature() {
        // If last signature has blank heading but valid sample
        val sample = "The dragon descended from the clouds."
        assertTrue(
            "Matching sample when last heading is blank must be duplicate",
            DomContentVerifier.isDuplicateContent("Any heading", sample, "", sample)
        )
        assertFalse(
            "Different sample when last heading is blank must not be duplicate",
            DomContentVerifier.isDuplicateContent("Any heading", "Different body text", "", sample)
        )
    }

    @Test
    fun testDuplicateWhenCurrentContentIsBlank() {
        // Blank current content must never be treated as duplicate (it's loading/empty, not duplicate)
        assertFalse(
            "Blank current content must not be marked duplicate",
            DomContentVerifier.isDuplicateContent("", "", "Chapter 1", "Sample text")
        )
        assertFalse(
            "Whitespace current content must not be marked duplicate",
            DomContentVerifier.isDuplicateContent("   ", "  \n\t ", "Chapter 1", "Sample text")
        )
    }

    @Test
    fun testDuplicateWhenInitialSignaturesAreBlank() {
        assertFalse(
            "First chapter with blank previous signatures cannot be duplicate",
            DomContentVerifier.isDuplicateContent("Chapter 1", "Sample text", "", "")
        )
    }

    @Test
    fun testDuplicateWhenHeadingMatchesButSampleDiffers() {
        // When heading is identical (e.g. site title "NovelReader") but sample changed,
        // it is NOT duplicate (it is a valid new chapter on a site with poor headings)
        val genericHeading = "My Novel Title"
        val oldSample = "Chapter 1 text here..."
        val newSample = "Chapter 2 text here..."

        assertFalse(
            "Identical generic heading with different body samples must NOT be duplicate",
            DomContentVerifier.isDuplicateContent(genericHeading, newSample, genericHeading, oldSample)
        )
    }

    @Test
    fun testDuplicateWhenSampleMatchesButHeadingDiffers() {
        // Heading changed (e.g. Chapter 2) but body has not rendered yet (stale DOM)
        // Since heading changed, is it duplicate?
        // Let's verify DomContentVerifier contract:
        // when: lastHeading.isNotBlank() && lastSample.isNotBlank() -> headingMatches && sampleMatches
        // If heading differs, headingMatches is false -> isDuplicate returns false!
        // AND hasContentTransitioned evaluates: headingChanged || bodyChanged -> headingChanged is true!
        // This is correct: if heading transitioned to Chapter 2, it indicates a new chapter.
        val heading1 = "Chapter 1"
        val heading2 = "Chapter 2"
        val staleSample = "Stale body text from chapter 1"

        assertFalse(
            "Different heading with stale sample does not return duplicate",
            DomContentVerifier.isDuplicateContent(heading2, staleSample, heading1, staleSample)
        )
        assertTrue(
            "Different heading with stale sample is considered transitioned by heading",
            DomContentVerifier.hasContentTransitioned(heading2, staleSample, heading1, staleSample)
        )
    }

    // =========================================================================
    // 4. Overload Consistency: List<String> vs String Overloads
    // =========================================================================

    @Test
    fun testListOverloadConsistency() {
        val paras = listOf("Para 1", "Para 2", "Para 3", "Para 4")
        val sample = DomContentVerifier.createSample(paras)

        val transitionedList = DomContentVerifier.hasContentTransitioned("Ch 2", paras, "Ch 1", "Old Sample")
        val transitionedString = DomContentVerifier.hasContentTransitioned("Ch 2", sample, "Ch 1", "Old Sample")
        assertEquals("List and String overloads for hasContentTransitioned must agree", transitionedString, transitionedList)

        val duplicateList = DomContentVerifier.isDuplicateContent("Ch 1", paras, "Ch 1", sample)
        val duplicateString = DomContentVerifier.isDuplicateContent("Ch 1", sample, "Ch 1", sample)
        assertEquals("List and String overloads for isDuplicateContent must agree", duplicateString, duplicateList)
    }

    // =========================================================================
    // 5. Polling and Timeout Mathematical Model
    // =========================================================================

    @Test
    fun testPollingIntervalAndTimeoutCalculations() {
        val timeoutMs = 15000L
        val pollIntervalMs = 600L

        val maxPollAttempts = (timeoutMs / pollIntervalMs).toInt() // 25 attempts
        assertEquals(25, maxPollAttempts)

        // Verify elapsed seconds formatting logic
        val startTime = 1000000L
        for (elapsedMs in listOf(0L, 600L, 1200L, 5999L, 6000L, 14999L, 15000L)) {
            val now = startTime + elapsedMs
            val elapsedSec = ((now - startTime) / 1000).toInt()
            val status = "Waiting for DOM re-render (${elapsedSec}s)..."
            assertTrue(status.startsWith("Waiting for DOM re-render (") && status.endsWith("s)..."))
        }
    }
}
