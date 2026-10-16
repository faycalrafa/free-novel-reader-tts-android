package com.novelarrow.reader.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical challenger test suite for Milestone 4:
 * Validates DomContentVerifier under adversarial conditions, edge cases,
 * boundary limits, unicode/CJK text, and multi-chapter transition dynamics.
 */
class Milestone4Challenger1Test {

    // =========================================================================
    // SECTION 1: Empty Paragraph Lists, Null/Empty Strings, Whitespace-Only Content
    // =========================================================================

    @Test
    fun testEmptyParagraphListCreatesEmptySample() {
        val sample = DomContentVerifier.createSample(emptyList())
        assertEquals("", sample)
    }

    @Test
    fun testWhitespaceOnlyParagraphListHandling() {
        // Single whitespace-only paragraph should trim to empty string
        val singleBlank = DomContentVerifier.createSample(listOf("   \t  \n "))
        assertEquals("", singleBlank)

        // Multiple paragraphs with only whitespace
        val multipleBlanks = DomContentVerifier.createSample(listOf("   ", "   ", "   "))
        // Note: joinToString separator is ", ", so joining whitespace items yields commas
        // Verify that verifier handles this without exceptions
        assertTrue(multipleBlanks.isNotBlank())
    }

    @Test
    fun testWhitespaceOnlyHeadingAndSampleHandling() {
        val whitespaceHeading = "   \n\t  "
        val whitespaceSample = "   \r\n  "
        val priorHeading = "Chapter 1"
        val priorSample = "Some novel text"

        // When incoming content is completely whitespace, it must NOT transition
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = whitespaceHeading,
            currentSample = whitespaceSample,
            lastHeading = priorHeading,
            lastSample = priorSample
        )
        assertFalse("Whitespace-only content must not report transition", transitioned)

        // Whitespace-only content must NOT be considered duplicate of real content
        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = whitespaceHeading,
            currentSample = whitespaceSample,
            lastHeading = priorHeading,
            lastSample = priorSample
        )
        assertFalse("Whitespace-only content must not match real content as duplicate", isDuplicate)
    }

    @Test
    fun testEmptyDomDoesNotTriggerTransitionWhenPriorSignaturesExist() {
        val priorHeading = "Chapter 4"
        val priorSample = "Paragraph one, Paragraph two, Paragraph three"

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "",
            currentParagraphs = emptyList(),
            lastHeading = priorHeading,
            lastSample = priorSample
        )
        assertFalse("Empty DOM during page load must not trigger transition", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = "",
            currentParagraphs = emptyList(),
            lastHeading = priorHeading,
            lastSample = priorSample
        )
        assertFalse("Empty DOM during page load must not be treated as duplicate content", isDuplicate)
    }

    @Test
    fun testInitialLoadWithEmptyDomReportsNoTransition() {
        // Both last and current signatures are empty
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "",
            currentParagraphs = emptyList(),
            lastHeading = "",
            lastSample = ""
        )
        assertFalse("Initial load with empty DOM must report no transition", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = "",
            currentParagraphs = emptyList(),
            lastHeading = "",
            lastSample = ""
        )
        assertFalse("Initial load with empty DOM must report no duplicate", isDuplicate)
    }

    @Test
    fun testInitialLoadWithValidContentReportsTransition() {
        // First chapter ever extracted (no previous signatures)
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "Chapter 1: The Awakening",
            currentParagraphs = listOf("He woke up in a strange room.", "The walls were cold stone."),
            lastHeading = "",
            lastSample = ""
        )
        assertTrue("First chapter with content must transition from uninitialized state", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = "Chapter 1: The Awakening",
            currentParagraphs = listOf("He woke up in a strange room.", "The walls were cold stone."),
            lastHeading = "",
            lastSample = ""
        )
        assertFalse("First chapter must never be flagged as duplicate", isDuplicate)
    }

    // =========================================================================
    // SECTION 2: Identical Headings with Different Paragraphs (Transition Detected)
    // =========================================================================

    @Test
    fun testIdenticalHeadingWithCompletelyDifferentParagraphs() {
        val genericHeading = "Chapter Title"
        val lastParas = listOf("Alpha paragraph", "Beta paragraph", "Gamma paragraph")
        val newParas = listOf("Delta paragraph", "Epsilon paragraph", "Zeta paragraph")

        val lastSample = DomContentVerifier.createSample(lastParas)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = genericHeading,
            currentParagraphs = newParas,
            lastHeading = genericHeading,
            lastSample = lastSample
        )
        assertTrue("Identical heading with different paragraphs must detect transition", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = genericHeading,
            currentParagraphs = newParas,
            lastHeading = genericHeading,
            lastSample = lastSample
        )
        assertFalse("Identical heading with different paragraphs must not be duplicate", isDuplicate)
    }

    @Test
    fun testIdenticalHeadingWithOnlyOneParagraphDifferentInSample() {
        val heading = "Web Novel Reading Mode"
        val lastParas = listOf("Line 1 identical", "Line 2 identical", "Line 3 OLD")
        val newParas = listOf("Line 1 identical", "Line 2 identical", "Line 3 NEW")

        val lastSample = DomContentVerifier.createSample(lastParas)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = heading,
            currentParagraphs = newParas,
            lastHeading = heading,
            lastSample = lastSample
        )
        assertTrue("Difference in 3rd paragraph of sample must detect transition", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = heading,
            currentParagraphs = newParas,
            lastHeading = heading,
            lastSample = lastSample
        )
        assertFalse("Difference in 3rd paragraph must not be duplicate", isDuplicate)
    }

    @Test
    fun testGenericAggregatorHeadingWithDynamicChapterBody() {
        // Novel aggregator displaying static site title as heading
        val siteHeading = "LightNovelWorld - Free Online Novel"
        val ch1Paras = listOf("Chapter 101 began with a storm.", "Lightning tore across the sky.", "The ship rocked violently.")
        val ch2Paras = listOf("Chapter 102 dawned peaceful.", "The wreckage washed ashore.", "Survivors searched for shelter.")

        val ch1Sample = DomContentVerifier.createSample(ch1Paras)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = siteHeading,
            currentParagraphs = ch2Paras,
            lastHeading = siteHeading,
            lastSample = ch1Sample
        )
        assertTrue("Static aggregator title must still allow transition via body paragraphs", transitioned)
    }

    // =========================================================================
    // SECTION 3: Different Headings with Identical Paragraphs (Transition Detected)
    // =========================================================================

    @Test
    fun testDifferentHeadingWithIdenticalParagraphs() {
        val lastHeading = "Volume 1 Chapter 1"
        val newHeading = "Volume 1 Chapter 2"
        val paras = listOf("Common intro banner text.", "Welcome readers.", "Please support the author.")

        val lastSample = DomContentVerifier.createSample(paras)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = newHeading,
            currentParagraphs = paras,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertTrue("Different heading with identical paragraphs must detect transition", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = newHeading,
            currentParagraphs = paras,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertFalse("Different heading with identical paragraphs must not be duplicate", isDuplicate)
    }

    @Test
    fun testSinglePageAppHeadingUpdatePriorToBodyRender() {
        val oldHeading = "Chapter 25: The Battle"
        val updatedHeading = "Chapter 26: The Aftermath"
        val staleParas = listOf("The swords clashed in the courtyard.", "Sparks flew in all directions.", "The champion stood firm.")

        val oldSample = DomContentVerifier.createSample(staleParas)

        // When SPA updates H1 before fetching chapter body
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = updatedHeading,
            currentSample = oldSample,
            lastHeading = oldHeading,
            lastSample = oldSample
        )
        assertTrue("Transition must be detected as soon as heading changes", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = updatedHeading,
            currentSample = oldSample,
            lastHeading = oldHeading,
            lastSample = oldSample
        )
        assertFalse("Updated heading prevents marking content as duplicate", isDuplicate)
    }

    // =========================================================================
    // SECTION 4: Identical Headings and Identical Paragraphs (Duplicate Detected, No Transition)
    // =========================================================================

    @Test
    fun testIdenticalHeadingAndIdenticalParagraphsDetectsDuplicateAndNoTransition() {
        val heading = "Chapter 50: Golden Core"
        val paras = listOf(
            "He sat cross-legged in the cave.",
            "Spiritual qi swirled around his dantian.",
            "The breakthrough was imminent."
        )
        val sample = DomContentVerifier.createSample(paras)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = heading,
            currentParagraphs = paras,
            lastHeading = heading,
            lastSample = sample
        )
        assertFalse("Identical heading and paragraphs must NOT report transition", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = heading,
            currentParagraphs = paras,
            lastHeading = heading,
            lastSample = sample
        )
        assertTrue("Identical heading and paragraphs MUST report duplicate", isDuplicate)
    }

    @Test
    fun testDuplicateDetectedWhenBodyHasExtraParagraphsBeyondSampleWindow() {
        val heading = "Chapter 7: The Cave"
        val lastParas = listOf("Para 1", "Para 2", "Para 3")
        val lastSample = DomContentVerifier.createSample(lastParas)

        // Stale DOM re-extracted with more paragraphs below fold
        val currentParas = listOf("Para 1", "Para 2", "Para 3", "Para 4", "Para 5", "Para 6")

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = heading,
            currentParagraphs = currentParas,
            lastHeading = heading,
            lastSample = lastSample
        )
        assertTrue("Extra paragraphs beyond first 3 sample paragraphs must still identify duplicate", isDuplicate)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = heading,
            currentParagraphs = currentParas,
            lastHeading = heading,
            lastSample = lastSample
        )
        assertFalse("Stale DOM with extra trailing paragraphs must not transition", transitioned)
    }

    @Test
    fun testDuplicateDetectedWithLeadingAndTrailingWhitespaceVariations() {
        val lastHeading = "Chapter 12: Secrets"
        val lastSample = "A secret was whispered in the shadows."

        val currentHeading = "   Chapter 12: Secrets \t\n"
        val currentSample = " \n A secret was whispered in the shadows.  "

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = currentHeading,
            currentSample = currentSample,
            lastHeading = lastHeading,
            lastSample = lastSample
        )
        assertTrue("Whitespace differences around heading and sample must still match as duplicate", isDuplicate)
    }

    @Test
    fun testDuplicateDetectedWhenOnlyHeadingPresentAndSampleEmpty() {
        val heading = "Chapter 99: Interlude"

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = heading,
            currentSample = "",
            lastHeading = heading,
            lastSample = ""
        )
        assertTrue("Matching heading with empty body samples must be recognized as duplicate", isDuplicate)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = heading,
            currentSample = "",
            lastHeading = heading,
            lastSample = ""
        )
        assertFalse("Matching heading with empty body samples must not report transition", transitioned)
    }

    @Test
    fun testDuplicateDetectedWhenOnlySamplePresentAndHeadingEmpty() {
        val bodySample = "A lonely traveler approached the tavern."

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = "",
            currentSample = bodySample,
            lastHeading = "",
            lastSample = bodySample
        )
        assertTrue("Matching body sample with empty headings must be recognized as duplicate", isDuplicate)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "",
            currentSample = bodySample,
            lastHeading = "",
            lastSample = bodySample
        )
        assertFalse("Matching body sample with empty headings must not report transition", transitioned)
    }

    // =========================================================================
    // SECTION 5: CJK Characters, Quotes, and Unicode Punctuation
    // =========================================================================

    @Test
    fun testCjkChineseSimplifiedAndTraditionalTransitionAndDuplicate() {
        // Simplified Chinese
        val zhHeading1 = "第一章 初始之地"
        val zhParas1 = listOf(
            "夜幕降临，乌云遮蔽了月光。",
            "少年紧握长剑，伫立在城楼之上。",
            "远处传来了野兽低沉的咆哮。"
        )
        val zhSample1 = DomContentVerifier.createSample(zhParas1)

        val zhHeading2 = "第二章 风起云涌"
        val zhParas2 = listOf(
            "清晨的第一缕阳光划破天际。",
            "城门缓缓打开，商队鱼贯而出。",
            "新的征程就此拉开序幕。"
        )

        // 1. Chinese transition
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = zhHeading2,
            currentParagraphs = zhParas2,
            lastHeading = zhHeading1,
            lastSample = zhSample1
        )
        assertTrue("Chinese chapter transition must be detected", transitioned)

        // 2. Chinese duplicate
        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = zhHeading1,
            currentParagraphs = zhParas1,
            lastHeading = zhHeading1,
            lastSample = zhSample1
        )
        assertTrue("Identical Chinese content must be flagged as duplicate", isDuplicate)

        // 3. Traditional Chinese contrast
        val zhTradHeading = "第一章 初始之地"
        val isDuplicateTrad = DomContentVerifier.isDuplicateContent(
            currentHeading = zhTradHeading,
            currentParagraphs = zhParas1,
            lastHeading = zhHeading1,
            lastSample = zhSample1
        )
        assertTrue("Matching Traditional Chinese heading must be recognized as duplicate", isDuplicateTrad)
    }

    @Test
    fun testCjkJapaneseHiraganaKatakanaKanjiTransitionAndDuplicate() {
        val jpHeading1 = "第1話 異世界転移と目覚め"
        val jpParas1 = listOf(
            "目が覚めると、見慣れない高い天井が視界に飛び込んできた。",
            "「ここは……どこだ？」",
            "自分の手を見つめると、記憶よりもずっと小さかった。"
        )
        val jpSample1 = DomContentVerifier.createSample(jpParas1)

        val jpHeading2 = "第2話 ステータスオープン"
        val jpParas2 = listOf(
            "頭の中で『ステータス』と唱えてみる。",
            "目の前に半透明のウィンドウが浮かび上がった。",
            "レベル1。スキル欄には見慣れない文字が並んでいた。"
        )

        // Transition check
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = jpHeading2,
            currentParagraphs = jpParas2,
            lastHeading = jpHeading1,
            lastSample = jpSample1
        )
        assertTrue("Japanese chapter transition must be detected", transitioned)

        // Duplicate check
        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = jpHeading1,
            currentParagraphs = jpParas1,
            lastHeading = jpHeading1,
            lastSample = jpSample1
        )
        assertTrue("Identical Japanese chapter must be recognized as duplicate", isDuplicate)
    }

    @Test
    fun testCjkKoreanHangulTransitionAndDuplicate() {
        val krHeading1 = "제1화 회귀자의 시작"
        val krParas1 = listOf(
            "눈을 떴을 때, 모든 것이 불타고 있었다.",
            "10년 전, 멸망의 날로 돌아온 것이다.",
            "‘이번에는 절대로 실패하지 않는다.’"
        )
        val krSample1 = DomContentVerifier.createSample(krParas1)

        val krHeading2 = "제2화 첫 번째 던전"
        val krParas2 = listOf(
            "강남역 2번 출구 앞.",
            "푸른색 게이트가 일렁이고 있었다.",
            "사람들은 아직 재앙의 시작을 알지 못했다."
        )

        // Transition check
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = krHeading2,
            currentParagraphs = krParas2,
            lastHeading = krHeading1,
            lastSample = krSample1
        )
        assertTrue("Korean chapter transition must be detected", transitioned)

        // Duplicate check
        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = krHeading1,
            currentParagraphs = krParas1,
            lastHeading = krHeading1,
            lastSample = krSample1
        )
        assertTrue("Identical Korean chapter must be recognized as duplicate", isDuplicate)
    }

    @Test
    fun testCurlyQuotesVsStraightQuotesTransition() {
        val straightQuotes = "Chapter 1: \"The Awakening\""
        val curlyQuotes = "Chapter 1: “The Awakening”"
        val sample = "Some body sample."

        // If a website changes quote styles, string comparison detects difference
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = curlyQuotes,
            currentSample = sample,
            lastHeading = straightQuotes,
            lastSample = sample
        )
        assertTrue("Difference between straight and curly quotes constitutes heading change", transitioned)
    }

    @Test
    fun testUnicodePunctuationGuillemetsFullwidthBracketsEmDashEllipsis() {
        val headingWithUnicode = "Chapter 1: 《The Legend》 —— Part I……"
        val parasWithUnicode = listOf(
            "「Hello, world!」 said the traveler.",
            "『Is anyone there?』 came the reply.",
            "【System Alert】: Connection established……"
        )
        val sample = DomContentVerifier.createSample(parasWithUnicode)

        // Check exact match duplicate
        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = headingWithUnicode,
            currentParagraphs = parasWithUnicode,
            lastHeading = headingWithUnicode,
            lastSample = sample
        )
        assertTrue("Unicode punctuation must preserve exact duplicate matching", isDuplicate)

        // Check transition when punctuation changes
        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = "Chapter 1: 《The Legend》 —— Part II……",
            currentParagraphs = parasWithUnicode,
            lastHeading = headingWithUnicode,
            lastSample = sample
        )
        assertTrue("Transition must be detected when unicode numeral changes", transitioned)
    }

    @Test
    fun testEmojiInHeadingsAndBody() {
        val heading1 = "Chapter 1 ⚔️ The Duel"
        val heading2 = "Chapter 2 🛡️ The Defense"
        val paras = listOf("Sword clash! 💥", "Shield block! 🛡️", "Crowd cheers! 🎉")
        val sample = DomContentVerifier.createSample(paras)

        val transitioned = DomContentVerifier.hasContentTransitioned(
            currentHeading = heading2,
            currentParagraphs = paras,
            lastHeading = heading1,
            lastSample = sample
        )
        assertTrue("Emoji heading transition must be detected", transitioned)

        val isDuplicate = DomContentVerifier.isDuplicateContent(
            currentHeading = heading1,
            currentParagraphs = paras,
            lastHeading = heading1,
            lastSample = sample
        )
        assertTrue("Identical emoji content must be recognized as duplicate", isDuplicate)
    }

    // =========================================================================
    // SECTION 6: Stress & Boundary Testing
    // =========================================================================

    @Test
    fun testMassiveParagraphContentUnderHighLoad() {
        // Build 1,000 paragraphs, each 500 characters long (~500 KB)
        val hugeParas = (1..1000).map { i ->
            "Paragraph $i: " + "A".repeat(480)
        }

        val start = System.currentTimeMillis()
        val sample = DomContentVerifier.createSample(hugeParas, count = 3)
        val elapsed = System.currentTimeMillis() - start

        // Must execute in under 100ms because take(3) avoids traversing the entire 1,000 paragraphs
        assertTrue("createSample on 1,000 paragraphs must be fast (took ${elapsed}ms)", elapsed < 100)
        assertTrue("Sample must only contain first 3 paragraphs", sample.startsWith("Paragraph 1:"))
        assertFalse("Sample must not contain paragraph 4", sample.contains("Paragraph 4:"))
    }

    @Test
    fun testRepeatedSequentialTransitions() {
        // Simulate a 50-chapter automated batch download sequence
        var lastHeading = ""
        var lastSample = ""

        for (chapter in 1..50) {
            val currentHeading = "Chapter $chapter: The Journey"
            val currentParas = listOf(
                "Opening line for chapter $chapter.",
                "Middle line for chapter $chapter.",
                "Closing line for chapter $chapter."
            )

            // Step 1: Transition verification
            val transitioned = DomContentVerifier.hasContentTransitioned(
                currentHeading = currentHeading,
                currentParagraphs = currentParas,
                lastHeading = lastHeading,
                lastSample = lastSample
            )
            assertTrue("Chapter $chapter must be detected as transitioned", transitioned)

            // Step 2: Ensure not flagged as duplicate
            val isDuplicate = DomContentVerifier.isDuplicateContent(
                currentHeading = currentHeading,
                currentParagraphs = currentParas,
                lastHeading = lastHeading,
                lastSample = lastSample
            )
            assertFalse("Chapter $chapter must not be flagged as duplicate of Chapter ${chapter - 1}", isDuplicate)

            // Step 3: Advance state
            lastHeading = currentHeading
            lastSample = DomContentVerifier.createSample(currentParas)

            // Step 4: Stale re-check must immediately flag duplicate
            val isStaleDuplicate = DomContentVerifier.isDuplicateContent(
                currentHeading = currentHeading,
                currentParagraphs = currentParas,
                lastHeading = lastHeading,
                lastSample = lastSample
            )
            assertTrue("Immediate re-check of Chapter $chapter must report duplicate", isStaleDuplicate)
        }
    }
}
