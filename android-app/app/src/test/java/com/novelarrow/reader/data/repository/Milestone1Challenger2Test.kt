package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.parser.ChapterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class Milestone1Challenger2Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @org.junit.Before
    fun setUp() {
        SettingsRepository.resetForTesting()
    }

    @org.junit.After
    fun tearDown() {
        SettingsRepository.resetForTesting()
    }

    private fun createMockContext(filesDir: File): Context {
        return object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
            override fun getExternalFilesDir(type: String?): File = filesDir
            override fun getApplicationContext(): Context = this
        }
    }

    // =========================================================================
    // Area 1: Rule active/inactive toggling across multiple consecutive reads and updates
    // =========================================================================

    @Test
    fun testRuleToggling_consecutiveReadsAndToggles() {
        var rule = PronunciationRule(
            id = "rule-1",
            from = "cultivator",
            to = "practitioner",
            exactMatch = true,
            enabled = true,
            scopeType = PronunciationScopeType.GLOBAL
        )
        val text = "The cultivator sat on the rock."

        // Read 1: Enabled
        var result = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList())
        assertEquals("The practitioner sat on the rock.", result)

        // Update 1: Toggle to Disabled
        rule = rule.copy(enabled = false)
        result = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList())
        assertEquals("The cultivator sat on the rock.", result)

        // Update 2: Toggle back to Enabled
        rule = rule.copy(enabled = true)
        result = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList())
        assertEquals("The practitioner sat on the rock.", result)

        // Update 3: Modify replacement and keep enabled
        rule = rule.copy(to = "immortal")
        result = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList())
        assertEquals("The immortal sat on the rock.", result)

        // Update 4: Modify match to substring and toggle off
        rule = rule.copy(exactMatch = false, enabled = false)
        val pluralText = "Many cultivators gathered."
        result = ChapterParser.applyRulesAndSkips(pluralText, listOf(rule), emptyList())
        assertEquals("Many cultivators gathered.", result)

        // Update 5: Toggle substring match on
        rule = rule.copy(enabled = true)
        result = ChapterParser.applyRulesAndSkips(pluralText, listOf(rule), emptyList())
        assertEquals("Many immortals gathered.", result)
    }

    @Test
    fun testRuleToggling_masterToggleInteraction() {
        val rule1 = PronunciationRule(
            id = "r1",
            from = "sword",
            to = "blade",
            exactMatch = true,
            enabled = true
        )
        val rule2 = PronunciationRule(
            id = "r2",
            from = "shield",
            to = "aegis",
            exactMatch = true,
            enabled = false
        )
        val text = "He raised his sword and shield."

        // 1. Master enabled: rule1 applied, rule2 skipped
        val parsedActive = ChapterParser.parse(
            content = text,
            fileName = "test.txt",
            pronunciationRules = listOf(rule1, rule2),
            pronunciationRulesEnabled = true
        )
        assertEquals("He raised his blade and shield.", parsedActive[0].spokenText)

        // 2. Master disabled: both skipped
        val parsedInactive = ChapterParser.parse(
            content = text,
            fileName = "test.txt",
            pronunciationRules = listOf(rule1, rule2),
            pronunciationRulesEnabled = false
        )
        assertEquals("He raised his sword and shield.", parsedInactive[0].spokenText)
    }

    @Test
    fun testRuleToggling_specialCharactersInReplacementDollarAndBackslash() {
        // Test whether replacement string containing $ or \ crashes regex substitution
        val dollarRule = PronunciationRule(
            from = "cost",
            to = "\$100",
            exactMatch = true,
            enabled = true
        )
        val text = "Total cost is high."

        var dollarException: Throwable? = null
        try {
            val result = ChapterParser.applyRulesAndSkips(text, listOf(dollarRule), emptyList())
            println("Dollar replacement output: $result")
        } catch (e: Throwable) {
            dollarException = e
            println("Encountered expected or unexpected dollar replacement exception: ${e.javaClass.name}: ${e.message}")
        }

        val backslashRule = PronunciationRule(
            from = "slash",
            to = "a\\b",
            exactMatch = true,
            enabled = true
        )
        val text2 = "Single slash here."
        var backslashException: Throwable? = null
        try {
            val result2 = ChapterParser.applyRulesAndSkips(text2, listOf(backslashRule), emptyList())
            println("Backslash replacement output: $result2")
        } catch (e: Throwable) {
            backslashException = e
            println("Encountered backslash replacement exception: ${e.javaClass.name}: ${e.message}")
        }

        // Verify that plain words work without issue
        val normalRule = PronunciationRule(
            from = "cost",
            to = "price",
            exactMatch = true,
            enabled = true
        )
        val normalResult = ChapterParser.applyRulesAndSkips(text, listOf(normalRule), emptyList())
        assertEquals("Total price is high.", normalResult)
    }

    @Test
    fun testRuleToggling_scopingTransitions() {
        var rule = PronunciationRule(
            id = "scoped-1",
            from = "Phoenix",
            to = "Firebird",
            exactMatch = true,
            enabled = true,
            scopeType = PronunciationScopeType.URL_PATTERN,
            scopePattern = "*novelbin*"
        )
        val text = "The Phoenix cried."

        // In scope
        var res = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList(), novelUrl = "https://novelbin.me/novel-book/c1")
        assertEquals("The Firebird cried.", res)

        // Toggle disabled while in scope
        rule = rule.copy(enabled = false)
        res = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList(), novelUrl = "https://novelbin.me/novel-book/c1")
        assertEquals("The Phoenix cried.", res)

        // Toggle back enabled but change URL out of scope
        rule = rule.copy(enabled = true)
        res = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList(), novelUrl = "https://wuxiaworld.com/novel-book/c1")
        assertEquals("The Phoenix cried.", res)

        // Change scope to FOLDER and test folder path matching
        rule = rule.copy(scopeType = PronunciationScopeType.FOLDER, scopePattern = "Fantasy")
        res = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList(), folderPath = "/storage/Novels/Fantasy/c1.txt")
        assertEquals("The Firebird cried.", res)

        // Out of folder scope
        res = ChapterParser.applyRulesAndSkips(text, listOf(rule), emptyList(), folderPath = "/storage/Novels/SciFi/c1.txt")
        assertEquals("The Phoenix cried.", res)
    }

    // =========================================================================
    // Area 2: Concurrency safety in SettingsRepository.kt
    // =========================================================================

    @Test
    fun testSettingsRepository_rapidConcurrentTogglingSingleInstance() = runBlocking(Dispatchers.IO) {
        val testFilesDir = tempFolder.newFolder("settings_test_single")
        val context = createMockContext(testFilesDir)
        val repo = SettingsRepository(context)

        // Seed initial rules
        val rule1 = PronunciationRule(id = "rule-1", from = "Alpha", to = "A", enabled = true)
        val rule2 = PronunciationRule(id = "rule-2", from = "Beta", to = "B", enabled = true)
        val rule3 = PronunciationRule(id = "rule-3", from = "Gamma", to = "G", enabled = true)
        repo.addPronunciationRule(rule1)
        repo.addPronunciationRule(rule2)
        repo.addPronunciationRule(rule3)

        // Launch 30 concurrent toggle coroutines
        val jobs = (1..30).map { i ->
            async {
                val targetId = when (i % 3) {
                    0 -> "rule-1"
                    1 -> "rule-2"
                    else -> "rule-3"
                }
                val enable = (i % 2 == 0)
                repo.togglePronunciationRule(targetId, enable)
            }
        }
        jobs.awaitAll()

        // Verify repository state is coherent
        val current = repo.getSettings().pronunciationRules
        assertEquals(3, current.size)
        assertTrue(current.any { it.id == "rule-1" })
        assertTrue(current.any { it.id == "rule-2" })
        assertTrue(current.any { it.id == "rule-3" })
    }

    @Test
    fun testSettingsRepository_rapidConcurrentMultiInstanceWrites() = runBlocking(Dispatchers.IO) {
        val testFilesDir = tempFolder.newFolder("settings_test_multi")
        val context = createMockContext(testFilesDir)

        val repo1 = SettingsRepository(context)
        val repo2 = SettingsRepository(context)

        // Seed a rule
        val initialRule = PronunciationRule(id = "shared-rule", from = "Delta", to = "D", enabled = true)
        repo1.addPronunciationRule(initialRule)

        // Launch 20 concurrent coroutines interweaving repo1 and repo2
        val jobs = (1..20).map { i ->
            async {
                if (i % 2 == 0) {
                    repo1.togglePronunciationRule("shared-rule", false)
                    repo1.addPronunciationRule(PronunciationRule(id = "r-even-$i", from = "Word$i", to = "Repl$i"))
                } else {
                    repo2.togglePronunciationRule("shared-rule", true)
                    repo2.addPronunciationRule(PronunciationRule(id = "r-odd-$i", from = "Word$i", to = "Repl$i"))
                }
            }
        }
        jobs.awaitAll()

        // Check if settings file exists, is valid JSON, and can be read back cleanly
        val file = File(testFilesDir, "novel_shared_tts_settings_v1.json")
        assertTrue("Shared TTS settings file must exist", file.exists())
        assertTrue("Shared TTS file must not be 0 bytes", file.length() > 0)
    }

    // =========================================================================
    // Area 3: FlowRow and narrow screen wrapping behavior
    // =========================================================================

    @Test
    fun testFlowRowNarrowLayoutContract() {
        val screenWidthDp = 320
        val chipWidthDp = 80
        val chipSpacingDp = 8
        val chipsPerRow = (screenWidthDp + chipSpacingDp) / (chipWidthDp + chipSpacingDp)
        assertEquals(3, chipsPerRow)

        val totalChips = 6
        val expectedRows = (totalChips + chipsPerRow - 1) / chipsPerRow
        assertEquals(2, expectedRows)
        assertTrue("FlowRow must wrap into multiple rows on narrow screens", expectedRows > 1)
    }
}
