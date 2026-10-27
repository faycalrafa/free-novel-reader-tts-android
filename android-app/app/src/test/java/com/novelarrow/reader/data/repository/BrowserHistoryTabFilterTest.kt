package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novelarrow.reader.data.model.BrowserHistoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BrowserHistoryTabFilterTest {

    private lateinit var context: Context
    private lateinit var repository: BrowserHistoryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "browser_history_v1.json").delete()
        BrowserHistoryRepository.resetInMemoryCacheForTesting()
        repository = BrowserHistoryRepository(context)
    }

    @Test
    fun testRecordVisitAssociatesTabId() = runBlocking {
        repository.recordVisit("https://novel.com/ch1", "Chapter 1", "tab-1")
        repository.recordVisit("https://novel.com/ch2", "Chapter 2", "tab-2")

        val entries = repository.historyFlow.first()
        assertEquals(2, entries.size)
        assertEquals("tab-2", entries[0].tabId)
        assertEquals("tab-1", entries[1].tabId)
    }

    @Test
    fun testTabFilteredEntriesOnlyShowActiveTab() = runBlocking {
        repository.recordVisit("https://siteA.com", "Site A", "tab-alpha")
        repository.recordVisit("https://siteB.com", "Site B", "tab-beta")
        repository.recordVisit("https://siteA.com/page2", "Site A Page 2", "tab-alpha")

        val allEntries = repository.historyFlow.first()
        assertEquals(3, allEntries.size)

        // Filter for tab-alpha
        val alphaEntries = allEntries.filter { it.tabId == "tab-alpha" }
        assertEquals(2, alphaEntries.size)
        assertTrue(alphaEntries.all { it.tabId == "tab-alpha" })

        // Filter for tab-beta
        val betaEntries = allEntries.filter { it.tabId == "tab-beta" }
        assertEquals(1, betaEntries.size)
        assertEquals("Site B", betaEntries.first().title)
    }

    @Test
    fun testConsecutiveSameUrlDeduplicatesWithinSameTabOnly() = runBlocking {
        // Tab 1 visits Chapter 1 twice
        repository.recordVisit("https://novel.com/ch1", "Chapter 1", "tab-1")
        repository.recordVisit("https://novel.com/ch1", "Chapter 1 Reloaded", "tab-1")

        var entries = repository.historyFlow.first()
        assertEquals(1, entries.size)
        assertEquals("Chapter 1 Reloaded", entries[0].title)

        // Tab 2 visits Chapter 1 (same URL, different tab) -> should NOT deduplicate with Tab 1
        repository.recordVisit("https://novel.com/ch1", "Chapter 1 on Tab 2", "tab-2")

        entries = repository.historyFlow.first()
        assertEquals(2, entries.size)
        assertEquals("tab-2", entries[0].tabId)
        assertEquals("tab-1", entries[1].tabId)
    }

    @Test
    fun testClearHistoryForTabRemovesOnlyTargetTab() = runBlocking {
        repository.recordVisit("https://novel1.com", "Novel 1", "tab-1")
        repository.recordVisit("https://novel2.com", "Novel 2", "tab-2")
        repository.recordVisit("https://novel3.com", "Novel 3", "tab-1")

        assertEquals(3, repository.historyFlow.first().size)

        repository.clearHistoryForTab("tab-1")

        val remaining = repository.historyFlow.first()
        assertEquals(1, remaining.size)
        assertEquals("tab-2", remaining[0].tabId)
        assertEquals("Novel 2", remaining[0].title)
    }

    @Test
    fun testDeleteIndividualEntry() = runBlocking {
        repository.recordVisit("https://site1.com", "Site 1", "tab-1")
        val entry = repository.historyFlow.first().first()

        repository.deleteEntry(entry.id)
        assertTrue(repository.historyFlow.first().isEmpty())
    }
}
