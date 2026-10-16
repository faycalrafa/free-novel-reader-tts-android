package com.novelarrow.reader.data.repository

import com.novelarrow.reader.data.model.Novel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArchiveTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test
    fun testNovelDefaultIsArchivedFalse() {
        val novel = Novel(id = "n1", title = "Shadow Slave")
        assertFalse("New novel should not be archived by default", novel.isArchived)
    }

    @Test
    fun testNovelSerializationWithIsArchived() {
        val archivedNovel = Novel(
            id = "n1",
            title = "Lord of Mysteries",
            chapterCount = 1400,
            currentChapterIndex = 250,
            isArchived = true
        )
        val serialized = json.encodeToString(archivedNovel)
        assertTrue(serialized.contains("\"isArchived\":true"))

        val deserialized = json.decodeFromString<Novel>(serialized)
        assertEquals(archivedNovel.id, deserialized.id)
        assertEquals(archivedNovel.title, deserialized.title)
        assertTrue(deserialized.isArchived)
    }

    @Test
    fun testNovelBackwardCompatibilityWithoutIsArchivedField() {
        // Old JSON schema without isArchived field
        val legacyJson = """
            {
                "id": "legacy_1",
                "title": "Legacy Novel",
                "chapterCount": 50,
                "currentChapterIndex": 10,
                "lastUpdated": 123456789,
                "isWebNovel": false
            }
        """.trimIndent()

        val parsed = json.decodeFromString<Novel>(legacyJson)
        assertEquals("legacy_1", parsed.id)
        assertFalse("Legacy JSON without isArchived should default to false", parsed.isArchived)
    }

    @Test
    fun testFilteringActiveAndArchivedNovels() {
        val novels = listOf(
            Novel(id = "1", title = "Novel A", isArchived = false),
            Novel(id = "2", title = "Novel B", isArchived = true),
            Novel(id = "3", title = "Novel C", isArchived = false),
            Novel(id = "4", title = "Novel D", isArchived = true)
        )

        val active = novels.filter { !it.isArchived }
        val archived = novels.filter { it.isArchived }

        assertEquals(2, active.size)
        assertEquals(listOf("Novel A", "Novel C"), active.map { it.title })

        assertEquals(2, archived.size)
        assertEquals(listOf("Novel B", "Novel D"), archived.map { it.title })
    }

    @Test
    fun testArchiveAndRestoreLifecycle() {
        var library = listOf(
            Novel(id = "1", title = "Novel 1", isArchived = false),
            Novel(id = "2", title = "Novel 2", isArchived = false)
        )

        // Archive Novel 1
        library = library.map { if (it.id == "1") it.copy(isArchived = true) else it }
        assertEquals(1, library.filter { !it.isArchived }.size)
        assertEquals(1, library.filter { it.isArchived }.size)
        assertEquals("Novel 2", library.first { !it.isArchived }.title)
        assertEquals("Novel 1", library.first { it.isArchived }.title)

        // Restore Novel 1
        library = library.map { if (it.id == "1") it.copy(isArchived = false) else it }
        assertEquals(2, library.filter { !it.isArchived }.size)
        assertEquals(0, library.filter { it.isArchived }.size)
    }

    @Test
    fun testRenameNovelPreservesArchiveStateAndProgress() {
        val novel = Novel(
            id = "n_100",
            title = "Old Name",
            chapterCount = 100,
            currentChapterIndex = 42,
            isArchived = true
        )

        val renamed = novel.copy(title = "New Clean Name")
        assertEquals("New Clean Name", renamed.title)
        assertEquals(42, renamed.currentChapterIndex)
        assertEquals(100, renamed.chapterCount)
        assertTrue(renamed.isArchived)
    }
}
