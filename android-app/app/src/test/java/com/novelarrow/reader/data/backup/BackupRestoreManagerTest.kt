package com.novelarrow.reader.data.backup

import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.data.repository.SharedTtsSettings
import com.novelarrow.reader.data.repository.UserPreferencesBackup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupRestoreManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun testLibraryBackupSerializationAndDeserialization() {
        val originalBackup = LibraryBackup(
            schemaVersion = 1,
            loadedFolders = listOf(
                "/storage/emulated/0/Download/NovelReader/NovelOne",
                "/storage/emulated/0/Books/FantasyNovel"
            ),
            novels = listOf(
                NovelSummaryBackup(
                    id = "novel_1",
                    title = "Novel One",
                    rootTreeUri = "/storage/emulated/0/Download/NovelReader/NovelOne",
                    chapterCount = 120,
                    currentChapterIndex = 45,
                    isWebNovel = false
                ),
                NovelSummaryBackup(
                    id = "novel_2",
                    title = "Fantasy Novel",
                    rootTreeUri = "/storage/emulated/0/Books/FantasyNovel",
                    chapterCount = 50,
                    currentChapterIndex = 10,
                    isWebNovel = true,
                    webUrl = "https://example.com/novel"
                )
            ),
            lastActiveNovelId = "novel_1"
        )

        val jsonString = json.encodeToString(originalBackup)
        assertTrue(jsonString.contains("Novel One"))
        assertTrue(jsonString.contains("/storage/emulated/0/Books/FantasyNovel"))

        val decoded = json.decodeFromString<LibraryBackup>(jsonString)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(2, decoded.loadedFolders.size)
        assertEquals(2, decoded.novels.size)
        assertEquals("novel_1", decoded.lastActiveNovelId)
        assertEquals("Fantasy Novel", decoded.novels[1].title)
        assertEquals(45, decoded.novels[0].currentChapterIndex)
    }

    @Test
    fun testUserPreferencesBackupSerialization() {
        val originalPrefs = UserPreferencesBackup(
            readerSettings = ReaderSettings(
                theme = ThemeMode.DARK,
                fontSizeSp = 18f,
                speechRate = 1.25f
            ),
            sharedTts = SharedTtsSettings(
                speechRate = 1.25f,
                speechPitch = 1.1f,
                voiceName = "en-us-x-sfg#female_1"
            )
        )

        val jsonString = json.encodeToString(originalPrefs)
        val decoded = json.decodeFromString<UserPreferencesBackup>(jsonString)

        assertEquals(ThemeMode.DARK, decoded.readerSettings.theme)
        assertEquals(18f, decoded.readerSettings.fontSizeSp, 0.01f)
        assertEquals(1.25f, decoded.sharedTts.speechRate, 0.01f)
        assertEquals("en-us-x-sfg#female_1", decoded.sharedTts.voiceName)
    }

    @Test
    fun testExistingConfigsReportSummary() {
        val reportWithAll = ExistingConfigsReport(
            hasConfigs = true,
            preferencesBackup = UserPreferencesBackup(),
            detectedNovelsCount = 3,
            progressFiles = listOf(File("prog1.json"), File("prog2.json")),
            loadedFolderPaths = listOf("/path/1", "/path/2", "/path/3")
        )
        val summary = reportWithAll.summaryText
        assertTrue(summary.contains("Custom Settings & Voice Options"))
        assertTrue(summary.contains("3 Novel Playlists"))
        assertTrue(summary.contains("2 Reading Progress Records"))

        val emptyReport = ExistingConfigsReport(hasConfigs = false)
        assertEquals("Saved configuration files", emptyReport.summaryText)
    }

    @Test
    fun testDeleteAllHiddenConfigsSafelyPreservesContent() {
        // Setup root NovelReader directory
        val rootDir = tempFolder.newFolder("NovelReader")
        val prefFile = File(rootDir, ".novelreader_preferences.json").apply { writeText("{}") }
        val libFile = File(rootDir, ".novelreader_library.json").apply { writeText("{}") }

        // Setup novel directory inside root
        val novelDir1 = File(rootDir, "ShadowSlave").apply { mkdirs() }
        val progressFile1 = File(novelDir1, ".progress.json").apply { writeText("{\"novelTitle\":\"ShadowSlave\"}") }
        val chapterFile1 = File(novelDir1, "Chapter 1.txt").apply { writeText("Chapter 1 text content") }
        val chapterFile2 = File(novelDir1, "Chapter 2.txt").apply { writeText("Chapter 2 text content") }

        // Setup external novel directory outside root
        val externalFolder = tempFolder.newFolder("ExternalNovels", "LordOfTheMysteries")
        val progressFile2 = File(externalFolder, ".progress.json").apply { writeText("{\"novelTitle\":\"LOTM\"}") }
        val novelHtmlFile = File(externalFolder, "Chapter 1.html").apply { writeText("<h1>Chapter 1</h1>") }

        val report = ExistingConfigsReport(
            hasConfigs = true,
            preferencesFiles = listOf(prefFile),
            libraryFiles = listOf(libFile),
            progressFiles = listOf(progressFile1, progressFile2),
            loadedFolderPaths = listOf(novelDir1.absolutePath, externalFolder.absolutePath)
        )

        // Verify initial state
        assertTrue(prefFile.exists())
        assertTrue(libFile.exists())
        assertTrue(progressFile1.exists())
        assertTrue(progressFile2.exists())
        assertTrue(chapterFile1.exists())
        assertTrue(chapterFile2.exists())
        assertTrue(novelHtmlFile.exists())

        // Simulate delete operation
        for (f in report.preferencesFiles) { f.delete() }
        for (f in report.libraryFiles) { f.delete() }
        for (f in report.progressFiles) { f.delete() }
        for (folderPath in report.loadedFolderPaths) {
            val dir = File(folderPath)
            dir.listFiles()?.filter {
                it.name.startsWith(".novelreader") || it.name == ".progress.json" || it.name == ".preferences.json"
            }?.forEach { it.delete() }
        }

        // Assert all hidden config files are deleted
        assertFalse(prefFile.exists())
        assertFalse(libFile.exists())
        assertFalse(progressFile1.exists())
        assertFalse(progressFile2.exists())

        // Assert that ALL story content files are strictly preserved!
        assertTrue(chapterFile1.exists())
        assertTrue(chapterFile2.exists())
        assertTrue(novelHtmlFile.exists())
        assertEquals("Chapter 1 text content", chapterFile1.readText())
        assertEquals("<h1>Chapter 1</h1>", novelHtmlFile.readText())
    }
}
