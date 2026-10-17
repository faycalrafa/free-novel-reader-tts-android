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

class SubfolderIsolationTest {

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
    fun testSubdirectoryPartitioningIntoDistinctNovels() = runBlocking {
        val appDir = tempFolder.newFolder("app_data")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        // Setup a parent directory with two subfolders and some loose files
        val rootDir = tempFolder.newFolder("ImportedNovels")
        val subA = File(rootDir, "Volume 1 - Awakening").apply { mkdir() }
        val subB = File(rootDir, "Volume 2 - Ascent").apply { mkdir() }

        File(subA, "001 - Chapter 1.txt").writeText("Chapter 1 content")
        File(subA, "002 - Chapter 2.txt").writeText("Chapter 2 content")

        File(subB, "001 - Chapter 1.txt").writeText("Volume 2 Chapter 1 content")
        File(subB, "002 - Chapter 2.txt").writeText("Volume 2 Chapter 2 content")
        File(subB, "003 - Chapter 3.txt").writeText("Volume 2 Chapter 3 content")

        File(rootDir, "Readme.txt").writeText("Introduction to the series")

        val scannedNovels = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals(3, scannedNovels.size)

        // Find each novel
        val novelA = scannedNovels.find { it.title == "Volume 1 - Awakening" }
        val novelB = scannedNovels.find { it.title == "Volume 2 - Ascent" }
        val novelRoot = scannedNovels.find { it.isLooseRootFiles }

        assertNotNull("Subfolder A novel must exist", novelA)
        assertNotNull("Subfolder B novel must exist", novelB)
        assertNotNull("Root loose novel must exist", novelRoot)

        assertFalse(novelA!!.isLooseRootFiles)
        assertEquals(subA.absolutePath, novelA.subfolderPath)

        assertFalse(novelB!!.isLooseRootFiles)
        assertEquals(subB.absolutePath, novelB.subfolderPath)

        assertTrue(novelRoot!!.isLooseRootFiles)
        assertEquals(rootDir.absolutePath, novelRoot.subfolderPath)

        // Verify chapter isolation
        val chapsA = repository.getChapters(novelA.id)
        val chapsB = repository.getChapters(novelB.id)
        val chapsRoot = repository.getChapters(novelRoot.id)

        assertEquals(2, chapsA.size)
        assertEquals(3, chapsB.size)
        assertEquals(1, chapsRoot.size)

        // Ensure subfolder A does NOT contain subfolder B chapters
        assertTrue(chapsA.all { it.fileUri.contains("Volume 1 - Awakening") })
        assertFalse(chapsA.any { it.fileUri.contains("Volume 2 - Ascent") })

        // Ensure subfolder B does NOT contain subfolder A chapters
        assertTrue(chapsB.all { it.fileUri.contains("Volume 2 - Ascent") })
        assertFalse(chapsB.any { it.fileUri.contains("Volume 1 - Awakening") })
    }

    @Test
    fun testSubfolderProgressIsolation() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_progress")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("MultiBookRoot")
        val book1 = File(rootDir, "Book One").apply { mkdir() }
        val book2 = File(rootDir, "Book Two").apply { mkdir() }

        val p1 = NovelFolderProgress(
            novelTitle = "Book One",
            currentChapterIndex = 4,
            currentChapterTitle = "Chapter 5",
            currentParagraphIndex = 12
        )
        val p2 = NovelFolderProgress(
            novelTitle = "Book Two",
            currentChapterIndex = 1,
            currentChapterTitle = "Chapter 2",
            currentParagraphIndex = 30
        )

        // Write progress into distinct subfolders
        repository.writeFolderProgress(book1.absolutePath, "Book One", p1)
        repository.writeFolderProgress(book2.absolutePath, "Book Two", p2)

        // Verify .progress.json exists in each subfolder
        val file1 = File(book1, ".progress.json")
        val file2 = File(book2, ".progress.json")
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        // Read back and ensure they do NOT cross-pollinate
        val read1 = repository.readFolderProgress(book1.absolutePath, "Book One")
        val read2 = repository.readFolderProgress(book2.absolutePath, "Book Two")

        assertNotNull(read1)
        assertNotNull(read2)
        assertEquals(4, read1!!.currentChapterIndex)
        assertEquals("Chapter 5", read1.currentChapterTitle)
        assertEquals(12, read1.currentParagraphIndex)

        assertEquals(1, read2!!.currentChapterIndex)
        assertEquals("Chapter 2", read2.currentChapterTitle)
        assertEquals(30, read2.currentParagraphIndex)
    }

    @Test
    fun testScopedDeletionDoesNotAffectSiblingSubfoldersOrParent() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_delete")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("DeleteRoot")
        val subA = File(rootDir, "SubA").apply { mkdir() }
        val subB = File(rootDir, "SubB").apply { mkdir() }

        File(subA, "ch1.txt").writeText("content A")
        File(subB, "ch1.txt").writeText("content B")
        File(rootDir, "loose.txt").writeText("content root")

        val novels = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals(3, novels.size)

        val novelA = novels.first { it.title == "SubA" }
        val novelLoose = novels.first { it.isLooseRootFiles }

        // 1. Delete SubA from disk
        repository.deleteNovel(novelA.id, deleteFilesFromDisk = true)

        // SubA directory must be deleted
        assertFalse("SubA directory must be deleted", subA.exists())
        // SubB directory and root directory MUST still exist
        assertTrue("SubB directory must remain untouched", subB.exists())
        assertTrue("SubB ch1.txt must remain intact", File(subB, "ch1.txt").exists())
        assertTrue("Root directory must remain untouched", rootDir.exists())
        assertTrue("Root loose file must remain untouched", File(rootDir, "loose.txt").exists())

        // 2. Delete Loose root novel from disk
        repository.deleteNovel(novelLoose.id, deleteFilesFromDisk = true)

        // Parent root directory MUST NOT be deleted even when loose novel files are deleted
        assertTrue("Parent root directory must NOT be deleted when loose novel is removed", rootDir.exists())
        assertTrue("SubB must still exist", subB.exists())
        // The loose chapter file should be deleted
        assertFalse("Loose chapter file must be removed", File(rootDir, "loose.txt").exists())
    }
}
