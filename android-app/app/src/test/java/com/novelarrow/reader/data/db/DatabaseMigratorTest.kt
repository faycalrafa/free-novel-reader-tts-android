package com.novelarrow.reader.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
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
@Config(manifest = Config.NONE)
class DatabaseMigratorTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var filesDir: File

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        filesDir = context.filesDir
        // Clean up any test files from prior runs
        File(filesDir, "novel_library_v1.json").delete()
        File(filesDir, "novel_library_v1.json.bak").delete()
        database = AppDatabase.createInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        File(filesDir, "novel_library_v1.json").delete()
        File(filesDir, "novel_library_v1.json.bak").delete()
    }

    @Test
    fun testIngestLegacyJson_successAndPopulatesRoom() = runBlocking {
        val legacyNovel = Novel(
            id = "novel_123",
            title = "Shadow Slave",
            chapterCount = 2,
            completedCount = 1,
            currentChapterIndex = 1,
            isWebNovel = false
        )
        val ch1 = Chapter(
            id = "ch_1",
            novelId = "novel_123",
            title = "Chapter 1",
            fileUri = "file:///storage/ch1.txt",
            orderIndex = 0,
            isCompleted = true,
            lastReadParagraphIndex = 15
        )
        val ch2 = Chapter(
            id = "ch_2",
            novelId = "novel_123",
            title = "Chapter 2",
            fileUri = "file:///storage/ch2.txt",
            orderIndex = 1,
            isCompleted = false,
            lastReadParagraphIndex = 4
        )

        val legacyDb = LegacyLibraryDatabase(
            novels = listOf(legacyNovel),
            chapters = mapOf("novel_123" to listOf(ch1, ch2)),
            lastActiveNovelId = "novel_123"
        )

        val legacyFile = File(filesDir, "novel_library_v1.json")
        legacyFile.writeText(json.encodeToString(legacyDb))

        assertTrue(legacyFile.exists())

        // Execute migration
        val migrated = DatabaseMigrator.ingestLegacyJsonIfNeeded(context, database)
        assertTrue("Migration should report true on first ingestion", migrated)

        // Verify Room contains data
        val novels = database.novelDao().getAllNovels()
        assertEquals(1, novels.size)
        assertEquals("Shadow Slave", novels[0].title)
        assertEquals(1, novels[0].completedCount)

        val chapters = database.chapterDao().getChaptersForNovel("novel_123")
        assertEquals(2, chapters.size)
        assertEquals("Chapter 1", chapters[0].title)
        assertTrue(chapters[0].isCompleted)
        assertEquals(15, chapters[0].lastReadParagraphIndex)
        assertEquals("Chapter 2", chapters[1].title)
        assertEquals(4, chapters[1].lastReadParagraphIndex)

        val lastActive = database.appMetaDao().getMeta("lastActiveNovelId")
        assertEquals("novel_123", lastActive)

        // Verify legacy file was renamed to .bak
        assertFalse(legacyFile.exists())
        val backupFile = File(filesDir, "novel_library_v1.json.bak")
        assertTrue(backupFile.exists())
    }

    @Test
    fun testIngestLegacyJson_alreadyPopulatedDatabase_isNoOp() = runBlocking {
        // Pre-populate Room
        val existingNovel = NovelEntity(
            id = "existing_1",
            title = "Lord of Mysteries"
        )
        database.novelDao().insertNovel(existingNovel)

        // Create legacy file
        val legacyFile = File(filesDir, "novel_library_v1.json")
        legacyFile.writeText("{\"novels\":[{\"id\":\"other\",\"title\":\"Other\"}]}")

        val migrated = DatabaseMigrator.ingestLegacyJsonIfNeeded(context, database)
        assertFalse("Migration should be no-op when DB is already populated", migrated)

        // Database should be unchanged
        val novels = database.novelDao().getAllNovels()
        assertEquals(1, novels.size)
        assertEquals("existing_1", novels[0].id)
    }

    @Test
    fun testIngestLegacyJson_noFile_returnsFalse() = runBlocking {
        val migrated = DatabaseMigrator.ingestLegacyJsonIfNeeded(context, database)
        assertFalse("Migration should be false when no legacy file exists", migrated)
        assertEquals(0, database.novelDao().getNovelCount())
    }
}
