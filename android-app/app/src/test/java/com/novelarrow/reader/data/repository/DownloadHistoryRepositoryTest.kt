package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novelarrow.reader.data.model.DownloadHistoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DownloadHistoryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "download_history_v1.json").delete()
        DownloadHistoryRepository.resetInMemoryCacheForTesting()
        repository = DownloadHistoryRepository(context)
    }

    @Test
    fun testRecordAndRetrieveDownloads() = runBlocking {
        val entry1 = DownloadHistoryEntry(
            fileName = "novel_ch1.html",
            filePath = "/storage/downloads/NovelReader/Novel/ch1.html",
            fileSizeBytes = 1024L,
            downloadUrl = "https://novel.com/ch1",
            novelTitle = "Epic Novel",
            chapterTitle = "Chapter 1",
            chapterNumber = 1,
            isBatchNovelChapter = true,
            mimeType = "text/html"
        )

        val entry2 = DownloadHistoryEntry(
            fileName = "book.pdf",
            filePath = "/storage/downloads/book.pdf",
            fileSizeBytes = 204800L,
            downloadUrl = "https://example.com/book.pdf",
            isBatchNovelChapter = false,
            mimeType = "application/pdf"
        )

        repository.recordDownload(entry1)
        repository.recordDownload(entry2)

        val list = repository.downloadHistoryFlow.first()
        assertEquals(2, list.size)
        // Newest download should be at the top
        assertEquals("book.pdf", list[0].fileName)
        assertFalse(list[0].isBatchNovelChapter)
        assertEquals("novel_ch1.html", list[1].fileName)
        assertTrue(list[1].isBatchNovelChapter)
    }

    @Test
    fun testDeleteDownloadEntry() = runBlocking {
        val entry = DownloadHistoryEntry(
            id = "test-id-1",
            fileName = "sample.html",
            filePath = "/path/sample.html",
            fileSizeBytes = 500L,
            downloadUrl = "https://novel.com/ch2"
        )
        repository.recordDownload(entry)

        val before = repository.downloadHistoryFlow.first()
        assertEquals(1, before.size)

        repository.deleteEntry("test-id-1")

        val after = repository.downloadHistoryFlow.first()
        assertTrue(after.isEmpty())
    }

    @Test
    fun testClearHistory() = runBlocking {
        repository.recordDownload(DownloadHistoryEntry(fileName = "file1.html", filePath = "/path/1"))
        repository.recordDownload(DownloadHistoryEntry(fileName = "file2.html", filePath = "/path/2"))

        assertEquals(2, repository.downloadHistoryFlow.first().size)

        repository.clearHistory()
        assertEquals(0, repository.downloadHistoryFlow.first().size)
    }

    @Test
    fun testPersistenceAcrossRepositoryInstances() = runBlocking {
        val entry = DownloadHistoryEntry(
            fileName = "persisted.html",
            filePath = "/path/persisted.html",
            fileSizeBytes = 12345L,
            novelTitle = "Persistent Novel"
        )
        repository.recordDownload(entry)

        // Create new repository instance pointing to the same storage
        val newRepo = DownloadHistoryRepository(context)
        val loaded = newRepo.downloadHistoryFlow.first()
        assertEquals(1, loaded.size)
        assertEquals("persisted.html", loaded[0].fileName)
        assertEquals("Persistent Novel", loaded[0].novelTitle)
        assertEquals(12345L, loaded[0].fileSizeBytes)
    }
}
