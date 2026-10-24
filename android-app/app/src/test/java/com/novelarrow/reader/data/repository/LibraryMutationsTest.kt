package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryMutationsTest {

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
    fun testDeleteNovelRemovesFromDatabaseAndCleansProgressFile() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_mutations_1")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelRoot")
        val novelFolder = File(rootDir, "Novel A").apply { mkdir() }
        File(novelFolder, "01.txt").writeText("Chapter 1 text")
        File(novelFolder, "02.txt").writeText("Chapter 2 text")

        val scanned = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals(1, scanned.size)
        val targetNovel = scanned.first()

        // Write a progress file
        val progress = NovelFolderProgress(
            novelTitle = "Novel A",
            currentChapterIndex = 1,
            currentChapterTitle = "02"
        )
        repository.writeFolderProgress(novelFolder.absolutePath, "Novel A", progress)
        val pFile = File(novelFolder, ".progress.json")
        assertTrue("Progress file should exist before delete", pFile.exists())

        // Delete novel with deleteFilesFromDisk = true
        repository.deleteNovel(targetNovel.id, deleteFilesFromDisk = true)

        // Novel directory should be completely removed from disk
        assertFalse("Novel folder should be deleted from disk", novelFolder.exists())

        // Database should no longer contain targetNovel
        val remainingNovels = repository.getNovels(includeArchived = true)
        assertTrue("Library should not contain deleted novel", remainingNovels.none { it.id == targetNovel.id })
        val chapters = repository.getChapters(targetNovel.id)
        assertTrue("Chapters for deleted novel should be empty", chapters.isEmpty())
    }

    @Test
    fun testDeleteChapterAndReindex() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_mutations_2")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelRoot2")
        val novelFolder = File(rootDir, "Novel B").apply { mkdir() }
        val ch1File = File(novelFolder, "01 - Intro.txt").apply { writeText("Chapter 1") }
        val ch2File = File(novelFolder, "02 - Middle.txt").apply { writeText("Chapter 2") }
        val ch3File = File(novelFolder, "03 - End.txt").apply { writeText("Chapter 3") }

        val scanned = repository.scanRootDirectory(rootDir.absolutePath)
        val targetNovel = scanned.first()
        val originalChapters = repository.getChapters(targetNovel.id)
        assertEquals(3, originalChapters.size)

        val chToDelete = originalChapters[1] // "02 - Middle"

        // Delete middle chapter with deleteFromDisk = true
        val (updatedNovel, updatedChapters) = repository.deleteChapter(
            novelId = targetNovel.id,
            chapterId = chToDelete.id,
            deleteFromDisk = true
        )

        assertNotNull(updatedNovel)
        assertEquals(2, updatedChapters.size)
        assertEquals(2, updatedNovel!!.chapterCount)
        assertEquals("01 - Intro", updatedChapters[0].title)
        assertEquals(0, updatedChapters[0].orderIndex)
        assertEquals("03 - End", updatedChapters[1].title)
        assertEquals(1, updatedChapters[1].orderIndex)

        // Verify chapter file is deleted from disk
        assertFalse("Chapter 2 file must be deleted from disk", ch2File.exists())
        assertTrue("Chapter 1 file must remain", ch1File.exists())
        assertTrue("Chapter 3 file must remain", ch3File.exists())
    }

    @Test
    fun testReorderAndSortChapters() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_mutations_3")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelRoot3")
        val novelFolder = File(rootDir, "Novel C").apply { mkdir() }
        File(novelFolder, "A.txt").writeText("A")
        File(novelFolder, "B.txt").writeText("B")
        File(novelFolder, "C.txt").writeText("C")

        val scanned = repository.scanRootDirectory(rootDir.absolutePath)
        val targetNovel = scanned.first()

        // Reorder chapter from index 2 ("C") to index 0
        val (reorderedNovel, reorderedChapters) = repository.reorderChapters(targetNovel.id, fromIndex = 2, toIndex = 0)
        assertNotNull(reorderedNovel)
        assertEquals("C", reorderedChapters[0].title)
        assertEquals("A", reorderedChapters[1].title)
        assertEquals("B", reorderedChapters[2].title)

        // Sort reverse (reverses current list ["C", "A", "B"] -> ["B", "A", "C"])
        val (sortedNovel, sortedChapters) = repository.sortChapters(targetNovel.id, ChapterSortPreset.REVERSE)
        assertNotNull(sortedNovel)
        assertEquals("B", sortedChapters[0].title)
        assertEquals("A", sortedChapters[1].title)
        assertEquals("C", sortedChapters[2].title)

        // Sort NATURAL -> ["A", "B", "C"]
        val (_, naturalChapters) = repository.sortChapters(targetNovel.id, ChapterSortPreset.NATURAL)
        assertEquals("A", naturalChapters[0].title)
        assertEquals("B", naturalChapters[1].title)
        assertEquals("C", naturalChapters[2].title)
    }

    @Test
    fun testToggleChapterReadStatus() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_mutations_4")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelRoot4")
        val novelFolder = File(rootDir, "Novel D").apply { mkdir() }
        File(novelFolder, "01.txt").writeText("Chapter 1")

        val scanned = repository.scanRootDirectory(rootDir.absolutePath)
        val targetNovel = scanned.first()
        val ch = repository.getChapters(targetNovel.id).first()
        assertFalse("Initially should be unread", ch.isCompleted)

        // Toggle to completed
        val (upNovel1, upChaps1) = repository.toggleChapterReadStatus(targetNovel.id, ch.id)
        assertNotNull(upNovel1)
        assertTrue("Should now be marked completed", upChaps1.first().isCompleted)
        assertEquals(1, upNovel1!!.completedCount)

        // Toggle back to uncompleted
        val (upNovel2, upChaps2) = repository.toggleChapterReadStatus(targetNovel.id, ch.id)
        assertNotNull(upNovel2)
        assertFalse("Should be unread again", upChaps2.first().isCompleted)
        assertEquals(0, upNovel2!!.completedCount)
    }
}
