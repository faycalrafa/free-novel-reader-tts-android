package com.novelarrow.reader.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomContentVerificationTest {

    @Test
    fun testCreateSampleTakesFirstThreeParagraphs() {
        val paras = listOf(
            "Paragraph one of the chapter.",
            "Paragraph two of the chapter.",
            "Paragraph three of the chapter.",
            "Paragraph four should be ignored.",
            "Paragraph five should be ignored."
        )

        val sample = DomContentVerifier.createSample(paras)
        val expected = listOf(
            "Paragraph one of the chapter.",
            "Paragraph two of the chapter.",
            "Paragraph three of the chapter."
        ).joinToString().trim()

        assertEquals(expected, sample)
    }

    @Test
    fun testDetectIdenticalDomContentVsTransitionedDomContent() {
        val lastHeading = "Chapter 1: The Beginning"
        val lastParas = listOf(
            "The sun rose over the quiet kingdom.",
            "A young knight gripped his blade.",
            "Today would be different."
        )
        val lastSample = DomContentVerifier.createSample(lastParas)

        // 1. Identical DOM content (same heading and same body sample)
        val identicalTransitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = lastHeading,
            currentParagraphs = lastParas,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertFalse("Identical DOM content must NOT be considered transitioned", identicalTransitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = lastHeading,
            currentParagraphs = lastParas,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertTrue("Identical DOM content must be identified as duplicate", isDuplicate)

        // 2. Fully transitioned DOM content (different heading and different body sample)
        val newHeading = "Chapter 2: The Journey Starts"
        val newParas = listOf(
            "Leaving the castle gates behind.",
            "The wind howled across the plains.",
            "Destiny awaited in the dark forest."
        )
        val newTransitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = newHeading,
            currentParagraphs = newParas,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertTrue("Different heading and body must be considered transitioned", newTransitioned)

        val isNewDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = newHeading,
            currentParagraphs = newParas,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertFalse("Different heading and body must not be marked duplicate", isNewDuplicate)
    }

    @Test
    fun testHeadingChangeDetection() {
        val lastHeading = "Chapter 10"
        val bodySample = "Some common aggregator placeholder text."

        // Same body sample, but heading changed to Chapter 11
        val newHeading = "Chapter 11"
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = newHeading,
            currentSample = bodySample,
            lastHeading = lastHeading,
            lastSample = bodySample
        )
        assertTrue("Heading change must trigger transition detection", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = newHeading,
            currentSample = bodySample,
            lastHeading = lastHeading,
            lastSample = bodySample
        )
        assertFalse("Content with changed heading cannot be duplicate", isDuplicate)
    }

    @Test
    fun testBodySampleChangeDetectionWhenHeadingIsIdentical() {
        // Common on some novel aggregator sites where the heading is generic (e.g. "Novel Reader" or blank)
        val genericHeading = "Shadow Slave"
        val lastParas = listOf(
            "Sunny opened his eyes and blinked.",
            "The cold stone floor was unforgiving.",
            "Where had the nightmare transported him?"
        )
        val newParas = listOf(
            "The ancient ruins towered before him.",
            "A dark silhouette stood silently.",
            "He readied his shadow blade."
        )

        val lastSample = DomContentVerifier.createSample(lastParas)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = genericHeading,
            currentParagraphs = newParas,
            lastHeading = genericHeading,
            lastSample = lastSample
        )
        assertTrue("Body sample change must detect transition even when heading is identical", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = genericHeading,
            currentParagraphs = newParas,
            lastHeading = genericHeading,
            lastSample = lastSample
        )
        assertFalse("Content with new body sample must NOT be flagged as duplicate", isDuplicate)
    }

    @Test
    fun testDuplicateCapturePreventionLogic() {
        val lastHeading = "Chapter 42: The Revelation"
        val lastParas = listOf(
            "The secret scroll unraveled.",
            "Ancient runes glowed with golden light.",
            "Everything they believed was a lie."
        )
        val lastSample = DomContentVerifier.createSample(lastParas)

        // Stale DOM re-extracted during fast navigation
        val currentHeading = "Chapter 42: The Revelation"
        val currentParas = listOf(
            "The secret scroll unraveled.",
            "Ancient runes glowed with golden light.",
            "Everything they believed was a lie.",
            "Extra paragraph that doesn't change first 3 sample paragraphs."
        )

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = currentHeading,
            currentParagraphs = currentParas,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertTrue("Duplicate capture prevention must catch stale DOM with matching first 3 paragraphs", isDuplicate)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = currentHeading,
            currentParagraphs = currentParas,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertFalse("Stale DOM must not report content transitioned", transitioned)
    }

    @Test
    fun testInitialChapterAlwaysTransitions() {
        // When there is no previous chapter signature, any extracted content is transitioned
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "Chapter 1",
            currentSample = "Initial chapter text",
            lastHeading = "",
            lastSample = ""
        )
        assertTrue("Initial chapter with no previous signature must be considered transitioned", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = "Chapter 1",
            currentSample = "Initial chapter text",
            lastHeading = "",
            lastSample = ""
        )
        assertFalse("Initial chapter cannot be duplicate", isDuplicate)
    }

    @Test
    fun testEmptyDomDoesNotTransition() {
        val lastHeading = "Chapter 1"
        val lastSample = "Paragraphs from chapter 1"

        // Empty DOM during loading state
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "",
            currentParagraphs = emptyList(),
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertFalse("Empty DOM must NOT report content transitioned", transitioned)
    }

    @Test
    fun testCreateChapterFingerprintDifferentiatesChaptersWithIdenticalHeaders() {
        val chapter1Paras = listOf(
            "Second Life as a Soldier",
            "Author: Studio Red",
            "Translator: WebNovel Team",
            "Dave swung his sword against the training dummy.",
            "He wiped the sweat from his brow and gasped."
        )
        val chapter2Paras = listOf(
            "Second Life as a Soldier",
            "Author: Studio Red",
            "Translator: WebNovel Team",
            "The commander called the platoon together at dawn.",
            "Their first scouting mission had finally arrived."
        )

        // createSample(take 3) would produce identical samples!
        val sample1 = DomContentVerifier.createSample(chapter1Paras)
        val sample2 = DomContentVerifier.createSample(chapter2Paras)
        assertEquals("Standard 3-paragraph sample fails to differentiate pages with shared headers", sample1, sample2)

        // createChapterFingerprint inspects middle and end paragraphs, successfully differentiating them
        val fp1 = DomContentVerifier.createChapterFingerprint(chapter1Paras)
        val fp2 = DomContentVerifier.createChapterFingerprint(chapter2Paras)
        assertTrue("Fingerprints must be distinct for chapters with different body text", fp1 != fp2)
    }

    @Test
    fun testDuplicateContentWhenUrlChangedWithMatchingNovelTitleHeader() {
        val genericHeading = "Second Life as a Soldier"
        val sample1 = "10:Header1::MidParagraphChapter1::EndParagraphChapter1"
        val sample2 = "12:Header1::MidParagraphChapter2::EndParagraphChapter2"

        // When urlChanged is true and body samples differ, identical heading MUST NOT be marked duplicate
        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = genericHeading,
            currentSample = sample2,
            lastHeading = genericHeading,
            lastSample = sample1,
            urlChanged = true
        )
        assertFalse("Different body content with changed URL must not be flagged duplicate even with identical book title heading", isDuplicate)

        // Stale DOM where body sample is identical must still be flagged duplicate
        val isStaleDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = genericHeading,
            currentSample = sample1,
            lastHeading = genericHeading,
            lastSample = sample1,
            urlChanged = true
        )
        assertTrue("Stale DOM with matching body sample must still be flagged duplicate even if URL changed", isStaleDuplicate)
    }
}
