package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryRefreshReconciliationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        LibraryRepository.clearCacheForTesting()
    }

    @After
    fun tearDown() {
        LibraryRepository.clearCacheForTesting()
    }

    private fun createMockContext(filesDir: File): Context {
        return object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
            override fun getExternalFilesDir(type: String?): File = filesDir
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun testSortingReconcilesActiveChapterViaCurrentChapterId() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_sort")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelSortTest")
        val f1 = File(rootDir, "10 - Chapter Ten.txt").apply { writeText("10") }
        val f2 = File(rootDir, "01 - Chapter One.txt").apply { writeText("01") }
        val f3 = File(rootDir, "02 - Chapter Two.txt").apply { writeText("02") }

        val novels = repository.scanRootDirectory(rootDir.absolutePath)
        val novel = novels.first()
        val initialChapters = repository.getChapters(novel.id)

        // Chapter One should be at index 0 after Natural sort: ["01 - Chapter One", "02 - Chapter Two", "10 - Chapter Ten"]
        assertEquals("01 - Chapter One", initialChapters[0].title)
        val chapterTwo = initialChapters.first { it.title == "02 - Chapter Two" }

        // Update progress to Chapter Two (index 1)
        val updatedNovel = repository.updateChapterProgress(
            novelId = novel.id,
            chapterIndex = 1,
            paragraphIndex = 15,
            scrollOffset = 0,
            isCompleted = false
        )
        assertNotNull(updatedNovel)
        assertEquals(chapterTwo.id, updatedNovel!!.currentChapterId)
        assertEquals(1, updatedNovel.currentChapterIndex)

        // Sort in REVERSE order: ["10 - Chapter Ten", "02 - Chapter Two", "01 - Chapter One"]
        val (reverseNovel, reverseChapters) = repository.sortChapters(novel.id, ChapterSortPreset.REVERSE)
        assertNotNull(reverseNovel)

        // Chapter Two is now at index 1 in reverse order
        assertEquals("02 - Chapter Two", reverseChapters[reverseNovel!!.currentChapterIndex].title)
        assertEquals(chapterTwo.id, reverseNovel.currentChapterId)

        // Now sort by FILE_NAME or natural again and verify active pointer follows chapterTwo.id
        val (naturalNovel, naturalChapters) = repository.sortChapters(novel.id, ChapterSortPreset.NATURAL)
        assertNotNull(naturalNovel)
        assertEquals("02 - Chapter Two", naturalChapters[naturalNovel!!.currentChapterIndex].title)
        assertEquals(chapterTwo.id, naturalNovel.currentChapterId)
    }

    @Test
    fun testRefreshPreservesExistingReadingProgress() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_refresh")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("RefreshProgressNovel")
        val c1 = File(rootDir, "Chapter 1.txt").apply { writeText("First chapter") }
        val c2 = File(rootDir, "Chapter 2.txt").apply { writeText("Second chapter") }

        val novels = repository.scanRootDirectory(rootDir.absolutePath)
        val novel = novels.first()

        // Read chapter 1 to completion and chapter 2 to paragraph 25
        repository.updateChapterProgress(novel.id, 0, 50, 0, isCompleted = true)
        repository.updateChapterProgress(novel.id, 1, 25, 0, isCompleted = false)

        val chaptersBefore = repository.getChapters(novel.id)
        assertTrue(chaptersBefore[0].isCompleted)
        assertEquals(25, chaptersBefore[1].lastReadParagraphIndex)

        // Add Chapter 3 on disk
        File(rootDir, "Chapter 3.txt").writeText("Third chapter added externally")

        // Trigger refresh
        val refreshedNovels = repository.refreshAllLinkedFolders()
        val refreshedNovel = refreshedNovels.first { it.id == novel.id }
        val chaptersAfter = repository.getChapters(novel.id)

        assertEquals("Should now have 3 chapters", 3, chaptersAfter.size)
        // Chapter 1 progress must be preserved
        assertTrue("Chapter 1 isCompleted must be preserved across refresh", chaptersAfter[0].isCompleted)
        assertEquals(50, chaptersAfter[0].lastReadParagraphIndex)
        // Chapter 2 progress must be preserved
        assertEquals("Chapter 2 paragraph progress must be preserved across refresh", 25, chaptersAfter[1].lastReadParagraphIndex)
        assertFalse(chaptersAfter[1].isCompleted)
        // Active chapter must stay pointing to Chapter 2
        assertEquals(1, refreshedNovel.currentChapterIndex)
        assertEquals(chaptersAfter[1].id, refreshedNovel.currentChapterId)
    }
}
