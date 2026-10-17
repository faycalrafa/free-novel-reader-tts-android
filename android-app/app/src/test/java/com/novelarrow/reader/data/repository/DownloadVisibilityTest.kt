package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadVisibilityTest {

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
    fun testReactiveDownloadVisibilityAndEventStream() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_download")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val novelTitle = "Lord of the Mysteries"
        val downloadFolder = com.novelarrow.reader.data.browser.WebNovelDownloader.getNovelDownloadDirectory(context, novelTitle).apply { mkdirs() }

        val events = mutableListOf<LibraryUpdateEvent>()
        val collectorJob = launch {
            repository.libraryUpdateEvents.collect {
                events.add(it)
            }
        }

        // 1. Download Chapter 1
        val c1File = File(downloadFolder, "0001 - Crimson.txt").apply { writeText("The crimson moon...") }
        val novel = repository.addDownloadedChapter(
            novelTitle = novelTitle,
            chapterFile = c1File,
            chapterTitle = "0001 - Crimson",
            chapterNumber = 1,
            sourceUrl = "https://example.com/lotm/1",
            commitNow = true
        )

        assertNotNull(novel)
        assertEquals(1, novel.chapterCount)
        assertEquals(downloadFolder.absolutePath, novel.subfolderPath)

        // Chapter 1 must be immediately visible in repository
        val chaptersAfterOne = repository.getChapters(novel.id)
        assertEquals(1, chaptersAfterOne.size)
        assertEquals("0001 - Crimson", chaptersAfterOne[0].title)

        // 2. Commit a batch of chapters (Chapters 2 to 5)
        for (i in 2..5) {
            val f = File(downloadFolder, String.format("%04d - Chapter.txt", i)).apply { writeText("Content $i") }
            repository.addDownloadedChapter(
                novelTitle = novelTitle,
                chapterFile = f,
                chapterTitle = String.format("%04d - Chapter", i),
                chapterNumber = i,
                sourceUrl = "https://example.com/lotm/$i",
                commitNow = false
            )
        }

        repository.commitBatch(novelTitle)

        // All 5 chapters must be immediately visible and deduplicated
        val chaptersAfterBatch = repository.getChapters(novel.id)
        assertEquals("All 5 chapters must be visible after batch commit", 5, chaptersAfterBatch.size)

        collectorJob.cancel()

        // Verify that events were emitted
        assertTrue("Must receive ChapterDownloaded event", events.any { it is LibraryUpdateEvent.ChapterDownloaded })
        assertTrue("Must receive BatchCommitted event", events.any { it is LibraryUpdateEvent.BatchCommitted })
    }
}
