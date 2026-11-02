package com.novelarrow.reader.ui.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.ui.reader.delegate.ReaderContentLoader
import com.novelarrow.reader.ui.reader.delegate.ReaderPlaybackDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderStartupAutoRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var uiState: MutableStateFlow<ReaderUiState>

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        LibraryRepository.clearCacheForTesting()
        uiState = MutableStateFlow(ReaderUiState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        LibraryRepository.clearCacheForTesting()
    }

    private fun createMockContext(filesDir: File): Context {
        return object : android.content.ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir(): File = filesDir
            override fun getExternalFilesDir(type: String?): File = filesDir
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun testSelectNovel_restoresLastReadChapterAndLine_andShowsTtsDock() = testScope.runTest {
        val appDir = tempFolder.newFolder("app_data_restore_1")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelRoot")
        val novelFolder = File(rootDir, "Solo Leveling").apply { mkdir() }
        val f1 = File(novelFolder, "01.txt").apply {
            writeText("This is paragraph zero of chapter one with enough words to satisfy the threshold.\n\nThis is paragraph one of chapter one with enough words to satisfy the threshold.")
        }
        val f2 = File(novelFolder, "02.txt").apply {
            writeText("This is paragraph zero of chapter two with enough words to satisfy the threshold.\n\nThis is paragraph one of chapter two with enough words to satisfy the threshold.\n\nThis is paragraph two of chapter two with enough words to satisfy the threshold.")
        }

        val scanned = repository.scanRootDirectory(rootDir.absolutePath)
        assertTrue(scanned.isNotEmpty())
        val novel = scanned.first()

        // Update progress so chapter 2 is current and paragraph 2 was read
        repository.updateChapterProgress(
            novelId = novel.id,
            chapterIndex = 1,
            paragraphIndex = 2,
            scrollOffset = 0,
            isCompleted = false
        )

        val updatedNovel = repository.getNovel(novel.id) ?: novel

        var dummyPlaybackDelegate: ReaderPlaybackDelegate? = null
        val contentLoader = ReaderContentLoader(
            scope = testScope,
            uiState = uiState,
            libraryRepository = repository,
            playbackDelegateProvider = { dummyPlaybackDelegate!! }
        )

        dummyPlaybackDelegate = ReaderPlaybackDelegate(
            application = ApplicationProvider.getApplicationContext(),
            scope = testScope,
            uiState = uiState,
            settingsRepository = SettingsRepository(context),
            saveCurrentChapterProgress = {},
            syncActiveProgress = { _, _, _, _, _ -> },
            selectChapter = { _, _ -> },
            reloadCurrentChapter = {}
        )

        // When novel is selected
        contentLoader.selectNovel(updatedNovel)

        // Await background Dispatchers.IO and parsing completion
        val startTime = System.currentTimeMillis()
        while ((uiState.value.isLoading || uiState.value.paragraphs.isEmpty()) && System.currentTimeMillis() - startTime < 3000) {
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Then verify:
        // 1. Target chapter is Chapter 2 (index 1)
        assertNotNull(uiState.value.currentChapter)
        assertEquals(1, uiState.value.currentChapter?.orderIndex)

        // 2. Target paragraph index is restored to 2 (last read line)
        assertEquals(2, uiState.value.currentParagraphIndex)

        // 3. TtsDock is visible immediately
        assertTrue(uiState.value.isTtsDockVisible)
    }

    @Test
    fun testLoadChapterContent_restoreProgress_setsLastReadLineAndDockVisibility() = testScope.runTest {
        val appDir = tempFolder.newFolder("app_data_restore_2")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NovelRoot2")
        val novelFolder = File(rootDir, "Omniscient Reader").apply { mkdir() }
        File(novelFolder, "01.txt").apply {
            writeText(
                "This is paragraph zero with enough words to pass the min word threshold safely.\n\n" +
                "This is paragraph one with enough words to pass the min word threshold safely.\n\n" +
                "This is paragraph two with enough words to pass the min word threshold safely.\n\n" +
                "This is paragraph three with enough words to pass the min word threshold safely.\n\n" +
                "This is paragraph four with enough words to pass the min word threshold safely."
            )
        }

        val scanned = repository.scanRootDirectory(rootDir.absolutePath)
        val novel = scanned.first()
        val chapters = repository.getChapters(novel.id)
        val chapter = chapters.first().copy(lastReadParagraphIndex = 3)

        val contentLoader = ReaderContentLoader(
            scope = testScope,
            uiState = uiState,
            libraryRepository = repository,
            playbackDelegateProvider = {
                ReaderPlaybackDelegate(
                    application = ApplicationProvider.getApplicationContext(),
                    scope = testScope,
                    uiState = uiState,
                    settingsRepository = SettingsRepository(context),
                    saveCurrentChapterProgress = {},
                    syncActiveProgress = { _, _, _, _, _ -> },
                    selectChapter = { _, _ -> },
                    reloadCurrentChapter = {}
                )
            }
        )

        contentLoader.loadChapterContent(chapter, restoreProgress = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, uiState.value.currentParagraphIndex)
        assertTrue(uiState.value.isTtsDockVisible)
    }
}
