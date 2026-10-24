package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.util.FileSchemeUtils
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

class AppRouteFolderImportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        LibraryRepository.clearCacheForTesting()
        appFilesDir = tempFolder.newFolder("app_sandbox")
        mockContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = appFilesDir
            override fun getExternalFilesDir(type: String?): File = appFilesDir
            override fun getApplicationContext(): Context = this
        }
    }

    @After
    fun tearDown() {
        LibraryRepository.clearCacheForTesting()
    }

    @Test
    fun testFileSchemeUtilsPathAndExtensionValidation() {
        assertTrue(FileSchemeUtils.isSupportedFile("chapter1.txt"))
        assertTrue(FileSchemeUtils.isSupportedFile("novel.html"))
        assertTrue(FileSchemeUtils.isSupportedFile("book.epub"))
        assertTrue(FileSchemeUtils.isSupportedFile("notes.md"))
        assertFalse(FileSchemeUtils.isSupportedFile(".hidden.txt"))
        assertFalse(FileSchemeUtils.isSupportedFile("image.png"))

        assertTrue(FileSchemeUtils.isLocalFilePath("/storage/emulated/0/book.txt"))
        assertTrue(FileSchemeUtils.isLocalFilePath("file:///storage/emulated/0/book.txt"))
        assertFalse(FileSchemeUtils.isLocalFilePath("content://com.android.externalstorage/tree/123"))

        assertEquals("book.txt", FileSchemeUtils.extractFileName("/path/to/book.txt"))
        assertEquals("book.txt", FileSchemeUtils.extractFileName("file:///path/to/book.txt"))
    }

    @Test
    fun testChapterImportStorageHelperCopiesFileToAppRoute() {
        val novelDir = ChapterImportStorageHelper.getNovelStorageDir(mockContext, "Martial Peak")
        assertTrue(novelDir.exists())
        assertTrue(novelDir.isDirectory)

        // Create a mock source chapter file
        val externalFolder = tempFolder.newFolder("ExternalDownloads")
        val sourceFile = File(externalFolder, "Chapter 001.txt").apply {
            writeText("Chapter 1: The Beginning of the Journey")
        }

        val copiedFile = ChapterImportStorageHelper.copyFileToNovelDir(
            context = mockContext,
            sourceUriOrPath = sourceFile.absolutePath,
            targetDir = novelDir,
            displayName = sourceFile.name,
            sourceSizeBytes = sourceFile.length()
        )

        assertNotNull("Copied file must not be null", copiedFile)
        assertTrue("Copied file must exist", copiedFile!!.exists())
        assertEquals("Chapter 001.txt", copiedFile.name)
        assertEquals("Chapter 1: The Beginning of the Journey", copiedFile.readText())
        assertTrue("Copied file must be located inside app files directory", copiedFile.absolutePath.startsWith(appFilesDir.absolutePath))
    }

    @Test
    fun testNovelDeduplicatorReconcileAndSortChapters() {
        val novelId = "novel_123"
        val scannedFiles = listOf(
            ScannedFileItem("/path/Chapter 10.txt", "Chapter 10.txt", 100L),
            ScannedFileItem("/path/Chapter 2.txt", "Chapter 2.txt", 100L),
            ScannedFileItem("/path/Chapter 1.txt", "Chapter 1.txt", 100L)
        )
        val existingChapters = listOf(
            Chapter(
                id = "${novelId}_chapter_1",
                novelId = novelId,
                title = "Chapter 1",
                fileUri = "/path/Chapter 1.txt",
                orderIndex = 0,
                lastReadParagraphIndex = 5,
                isCompleted = true
            )
        )

        val reconciled = NovelDeduplicator.reconcileAndSortChapters(
            novelId = novelId,
            novelTitle = "My Series",
            scannedFiles = scannedFiles,
            existingChapters = existingChapters,
            folderProgress = null
        )

        assertEquals(3, reconciled.size)
        // Verify natural ordering
        assertEquals("Chapter 1", reconciled[0].title)
        assertEquals("Chapter 2", reconciled[1].title)
        assertEquals("Chapter 10", reconciled[2].title)

        // Verify progress inheritance for Chapter 1
        assertEquals(5, reconciled[0].lastReadParagraphIndex)
        assertTrue(reconciled[0].isCompleted)

        // Verify 0-based sequential ordering
        assertEquals(0, reconciled[0].orderIndex)
        assertEquals(1, reconciled[1].orderIndex)
        assertEquals(2, reconciled[2].orderIndex)
    }

    @Test
    fun testChapterParserParseWithSettingsConvenienceHelper() {
        val content = """
            Chapter 1: The Gathering

            The wind blew cold across the northern ridge.

            The scouts watched from the cliffside silently.
        """.trimIndent()

        val settings = ReaderSettings(
            headerSkipEnabled = false,
            footerSkipEnabled = false,
            contentSelectorEnabled = false
        )

        val paragraphs = ChapterParser.parseWithSettings(
            content = content,
            fileName = "Chapter 1.txt",
            settings = settings
        )

        assertFalse(paragraphs.isEmpty())
        assertTrue(paragraphs.any { it.rawText.contains("The wind blew cold") })
        assertTrue(paragraphs.any { it.rawText.contains("The scouts watched") })
    }
}
