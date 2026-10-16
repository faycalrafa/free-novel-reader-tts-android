package com.novelarrow.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.novelarrow.reader.data.model.isTextBased
import com.novelarrow.reader.data.model.detectEditorLanguage
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import com.novelarrow.reader.data.model.SkipWord

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

    @Test
    fun testMultiKeywordHeaderAndFooterSkip() {
        val text = """
            Ignored preamble line 1.
            Ignored preamble line 2.
            Keyword Two Match: The adventure begins.
            The party set off toward the forest.
            Keyword Footer Match: The end of this chapter.
            Ignored postamble line 1.
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = text,
            fileName = "chap.txt",
            headerSkipString = "Keyword One, Keyword Two Match\nKeyword Three",
            footerSkipString = "Keyword End, Keyword Footer Match\nOther Footer"
        )

        val texts = paragraphs.map { it.rawText }
        assertEquals(2, texts.size)
        assertTrue(texts[0].contains("Keyword Two Match: The adventure begins."))
        assertTrue(texts[1].contains("The party set off toward the forest."))
        assertFalse(texts.any { it.contains("Ignored preamble") })
        assertFalse(texts.any { it.contains("Keyword Footer Match") })
        assertFalse(texts.any { it.contains("Ignored postamble") })
    }

    @Test
    fun testHeaderAndFooterSkipDisabledToggle() {
        val text = """
            Header Line to Skip
            Actual Content
            Footer Line to Skip
        """.trimIndent()

        val paragraphs = ChapterParser.parse(
            content = text,
            fileName = "chap.txt",
            headerSkipString = "Actual Content",
            headerSkipEnabled = false,
            footerSkipString = "Footer Line",
            footerSkipEnabled = false
        )

        val texts = paragraphs.map { it.rawText }
        assertTrue(texts.any { it.contains("Header Line to Skip") })
        assertTrue(texts.any { it.contains("Actual Content") })
        assertTrue(texts.any { it.contains("Footer Line to Skip") })
    }

    @Test
    fun testPronunciationRules_exactMatchVsSubstring() {
        val rules = listOf(
            com.novelarrow.reader.data.model.PronunciationRule(from = "lead", to = "led", exactMatch = true),
            com.novelarrow.reader.data.model.PronunciationRule(from = "Xiao", to = "Shao", exactMatch = false)
        )

        // Exact match should match whole word 'lead', but not 'leader'
        val text1 = "He will lead the leader."
        val spoken1 = ChapterParser.applyRulesAndSkips(text1, rules, emptyList())
        assertEquals("He will led the leader.", spoken1)

        // Substring match should match 'Xiao' everywhere
        val text2 = "Xiaofeng said hello."
        val spoken2 = ChapterParser.applyRulesAndSkips(text2, rules, emptyList())
        assertEquals("Shaofeng said hello.", spoken2)
    }

    @Test
    fun testSkipWords_exactMatchVsSubstring() {
        val skips = listOf(
            com.novelarrow.reader.data.model.SkipWord(text = "damn", exactMatch = true),
            com.novelarrow.reader.data.model.SkipWord(text = "bad", exactMatch = false)
        )

        // Exact match skips 'damn' but keeps words containing it
        val text1 = "That damn fool was goddamned."
        val spoken1 = ChapterParser.applyRulesAndSkips(text1, emptyList(), skips)
        assertEquals("That fool was goddamned.", spoken1)

        // Substring match strips 'bad' everywhere
        val text2 = "The badge is bad."
        val spoken2 = ChapterParser.applyRulesAndSkips(text2, emptyList(), skips)
        assertEquals("The ge is .", spoken2)
    }

    @Test
    fun testSkipWordsAndPronunciationDisabledToggle() {
        val rules = listOf(com.novelarrow.reader.data.model.PronunciationRule("foo", "bar", false))
        val skips = listOf(com.novelarrow.reader.data.model.SkipWord("baz", false))

        val paragraphs = ChapterParser.parse(
            content = "This foo is baz.",
            fileName = "test.txt",
            pronunciationRules = rules,
            pronunciationRulesEnabled = false,
            skipWords = skips,
            skipWordsEnabled = false
        )

        assertEquals("This foo is baz.", paragraphs[0].spokenText)
    }

    @Test
    fun testSplitIntoSentences_quotationMarks() {
        val dialogueText = "\"Stop!\" she said."
        val dialogueSentences = ChapterParser.splitIntoSentences(dialogueText)
        assertEquals(2, dialogueSentences.size)
        assertEquals("\"Stop!\"", dialogueSentences[0])
        assertEquals("she said.", dialogueSentences[1])

        val quotedEndingText = "She said, \"Stop!\" Then she walked away."
        val quotedEndingSentences = ChapterParser.splitIntoSentences(quotedEndingText)
        assertEquals(2, quotedEndingSentences.size)
        assertEquals("She said, \"Stop!\"", quotedEndingSentences[0])
        assertEquals("Then she walked away.", quotedEndingSentences[1])
    }

    @Test
    fun testSplitIntoSentences_cjkPunctuationWithoutSpaces() {
        val text = "这是第一句。这是第二句！第三句？"
        val sentences = ChapterParser.splitIntoSentences(text)
        assertEquals(3, sentences.size)
        assertEquals("这是第一句。", sentences[0])
        assertEquals("这是第二句！", sentences[1])
        assertEquals("第三句？", sentences[2])
    }

    @Test
    fun testDefaultExactMatchIsTrue() {
        val rule = com.novelarrow.reader.data.model.PronunciationRule(from = "apple", to = "orange")
        val skip = com.novelarrow.reader.data.model.SkipWord(text = "banana")
        assertTrue("PronunciationRule exactMatch must default to true", rule.exactMatch)
        assertTrue("SkipWord exactMatch must default to true", skip.exactMatch)
    }

    @Test
    fun testHeaderSkipRules_exactMatchVsSubstring() {
        val text = """
            Unwanted site announcements
            This has chapter preamble
            Chapter 1 Starts Here
            The sun rose over the hills.
            A new day begins.
        """.trimIndent()

        val rulesExact = listOf(
            com.novelarrow.reader.data.model.SkipRule(text = "Chapter", exactMatch = true)
        )
        val paragraphsExact = ChapterParser.parse(
            content = text,
            fileName = "chap.txt",
            headerSkipRules = rulesExact
        )
        val textsExact = paragraphsExact.map { it.rawText }
        assertTrue(textsExact[0].contains("chapter preamble"))

        val rulesPhrase = listOf(
            com.novelarrow.reader.data.model.SkipRule(text = "Chapter 1 Starts Here", exactMatch = false)
        )
        val paragraphsPhrase = ChapterParser.parse(
            content = text,
            fileName = "chap.txt",
            headerSkipRules = rulesPhrase
        )
        val textsPhrase = paragraphsPhrase.map { it.rawText }
        assertEquals(3, textsPhrase.size)
        assertTrue(textsPhrase[0].contains("Chapter 1 Starts Here"))
        assertTrue(textsPhrase[1].contains("The sun rose over the hills."))
    }

    @Test
    fun testFooterSkipRules_cutsOffContent() {
        val text = """
            Chapter 10: The Finale.
            They succeeded against all odds.
            END OF CHAPTER
            Please vote on WebNovel!
            Comments section follows.
        """.trimIndent()

        val footerRules = listOf(
            com.novelarrow.reader.data.model.SkipRule(text = "END OF CHAPTER", exactMatch = false)
        )
        val paragraphs = ChapterParser.parse(
            content = text,
            fileName = "chap10.txt",
            footerSkipRules = footerRules
        )
        val texts = paragraphs.map { it.rawText }
        assertEquals(2, texts.size)
        assertTrue(texts[0].contains("Chapter 10: The Finale."))
        assertTrue(texts[1].contains("They succeeded against all odds."))
        assertFalse(texts.any { it.contains("END OF CHAPTER") })
        assertFalse(texts.any { it.contains("vote on WebNovel") })
    }

    @Test
    fun testChapterIsTextBased_distinguishesTextFromBinary() {
        val htmlChapter = com.novelarrow.reader.data.model.Chapter(
            id = "1", novelId = "n1", title = "Chapter 1", fileUri = "file:///storage/novel/chap1.html", orderIndex = 0
        )
        val txtChapter = com.novelarrow.reader.data.model.Chapter(
            id = "2", novelId = "n1", title = "Chapter 2", fileUri = "file:///storage/novel/chap2.txt", orderIndex = 1
        )
        val mdChapter = com.novelarrow.reader.data.model.Chapter(
            id = "3", novelId = "n1", title = "Chapter 3", fileUri = "file:///storage/novel/chap3.md", orderIndex = 2
        )
        val pdfChapter = com.novelarrow.reader.data.model.Chapter(
            id = "4", novelId = "n1", title = "Book.pdf", fileUri = "file:///storage/novel/Book.pdf", orderIndex = 3
        )
        val epubChapter = com.novelarrow.reader.data.model.Chapter(
            id = "5", novelId = "n1", title = "Novel.epub", fileUri = "file:///storage/novel/Novel.epub", orderIndex = 4
        )

        assertTrue(htmlChapter.isTextBased())
        assertTrue(txtChapter.isTextBased())
        assertTrue(mdChapter.isTextBased())
        assertFalse(pdfChapter.isTextBased())
        assertFalse(epubChapter.isTextBased())

        assertEquals("html", htmlChapter.detectEditorLanguage())
        assertEquals("plaintext", txtChapter.detectEditorLanguage())
        assertEquals("markdown", mdChapter.detectEditorLanguage())
    }

    @Test
    fun testPronunciationRule_activeVsInactiveToggle() {
        val rules = listOf(
            PronunciationRule(
                from = "Qi",
                to = "Chee",
                exactMatch = true,
                enabled = true,
                scopeType = PronunciationScopeType.GLOBAL
            ),
            PronunciationRule(
                from = "Dan",
                to = "Pill",
                exactMatch = true,
                enabled = false,
                scopeType = PronunciationScopeType.GLOBAL
            )
        )

        val text = "Cultivate your Qi and consume the Dan."
        val result = ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = rules,
            skipWords = emptyList(),
            folderPath = null,
            novelUrl = null
        )
        // Qi -> Chee (active), Dan is NOT replaced (disabled)
        assertEquals("Cultivate your Chee and consume the Dan.", result)
    }

    @Test
    fun testPronunciationRule_folderScopeMatching() {
        val rules = listOf(
            PronunciationRule(
                from = "Dao",
                to = "Way",
                exactMatch = true,
                enabled = true,
                scopeType = PronunciationScopeType.FOLDER,
                scopePattern = "xianxia"
            )
        )

        val text = "The Dao is eternal."

        // 1. Matches folder path
        val matchedResult = ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = rules,
            skipWords = emptyList(),
            folderPath = "/storage/emulated/0/Novels/xianxia/chapter1.txt",
            novelUrl = null
        )
        assertEquals("The Way is eternal.", matchedResult)

        // 2. Outside folder path
        val unmatchedResult = ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = rules,
            skipWords = emptyList(),
            folderPath = "/storage/emulated/0/Novels/scifi/chapter1.txt",
            novelUrl = null
        )
        assertEquals("The Dao is eternal.", unmatchedResult)
    }

    @Test
    fun testPronunciationRule_urlPatternScopeMatching() {
        val rules = listOf(
            PronunciationRule(
                from = "Sect",
                to = "Clan",
                exactMatch = true,
                enabled = true,
                scopeType = PronunciationScopeType.URL_PATTERN,
                scopePattern = "*wuxiaworld.com*"
            )
        )

        val text = "Welcome to the Spirit Sect."

        // 1. URL matches pattern
        val matchedResult = ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = rules,
            skipWords = emptyList(),
            folderPath = null,
            novelUrl = "https://www.wuxiaworld.com/novel/spirit-realm/chapter-1"
        )
        assertEquals("Welcome to the Spirit Clan.", matchedResult)

        // 2. URL does not match pattern
        val unmatchedResult = ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = rules,
            skipWords = emptyList(),
            folderPath = null,
            novelUrl = "https://www.royalroad.com/fiction/1234/chapter-1"
        )
        assertEquals("Welcome to the Spirit Sect.", unmatchedResult)
    }

    @Test
    fun testSkipWord_activeVsInactiveToggle() {
        val skips = listOf(
            SkipWord(
                text = "spoiler",
                exactMatch = true,
                enabled = true
            ),
            SkipWord(
                text = "warning",
                exactMatch = true,
                enabled = false
            )
        )

        val text = "This is a spoiler and a warning here."
        val result = ChapterParser.applyRulesAndSkips(
            text = text,
            pronunciationRules = emptyList(),
            skipWords = skips
        )
        // "spoiler" is removed, "warning" remains because enabled = false
        assertEquals("This is a and a warning here.", result)
    }

    @Test
    fun testMultipleContentSelectors_evaluatesInPriorityOrder() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="priority-low">
                    <p>Low priority chapter content that should only be picked if high priority fails.</p>
                    <p>Second paragraph in low priority block with plenty of characters to pass length threshold.</p>
                </div>
                <div class="priority-high">
                    <p>High priority main chapter content parsed correctly from the primary selector.</p>
                    <p>Second paragraph in primary content block with sufficient text length.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val rules = listOf(
            com.novelarrow.reader.data.model.ContentSelectorRule(
                selector = ".priority-high",
                enabled = true
            ),
            com.novelarrow.reader.data.model.ContentSelectorRule(
                selector = ".priority-low",
                enabled = true
            )
        )

        val paragraphs = ChapterParser.parse(
            content = html,
            fileName = "chap1.html",
            contentSelectors = rules
        )

        val texts = paragraphs.map { it.rawText }
        assertTrue(texts.any { it.contains("High priority main chapter content") })
        assertFalse(texts.any { it.contains("Low priority chapter content") })
    }

    @Test
    fun testMultipleContentSelectors_respectsFolderAndSiteScope() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="site-specific">
                    <p>This content belongs specifically to royalroad.com novel chapters.</p>
                    <p>Additional paragraph providing enough content to be valid text block.</p>
                </div>
                <div class="generic-fallback">
                    <p>This is generic fallback text when site specific rule does not match.</p>
                    <p>Second fallback paragraph ensuring sufficient length.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val rules = listOf(
            com.novelarrow.reader.data.model.ContentSelectorRule(
                selector = ".site-specific",
                enabled = true,
                scopeType = PronunciationScopeType.URL_PATTERN,
                scopePattern = "royalroad.com"
            ),
            com.novelarrow.reader.data.model.ContentSelectorRule(
                selector = ".generic-fallback",
                enabled = true,
                scopeType = PronunciationScopeType.GLOBAL
            )
        )

        // Case 1: URL matches royalroad.com -> should pick .site-specific
        val matchedParas = ChapterParser.parse(
            content = html,
            fileName = "chap1.html",
            novelUrl = "https://www.royalroad.com/fiction/123/chapter/1",
            contentSelectors = rules
        )
        assertTrue(matchedParas.map { it.rawText }.any { it.contains("royalroad.com") })

        // Case 2: URL does NOT match royalroad.com -> should fallback to .generic-fallback
        val unmatchedParas = ChapterParser.parse(
            content = html,
            fileName = "chap1.html",
            novelUrl = "https://www.wuxiaworld.com/novel/123/chapter/1",
            contentSelectors = rules
        )
        assertTrue(unmatchedParas.map { it.rawText }.any { it.contains("generic fallback text") })
        assertFalse(unmatchedParas.map { it.rawText }.any { it.contains("royalroad.com") })
    }
}

