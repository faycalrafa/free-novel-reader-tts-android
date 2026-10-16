package com.novelarrow.reader.playback

import com.novelarrow.reader.data.export.AudioFileItem
import com.novelarrow.reader.data.export.AudioFolderItem
import com.novelarrow.reader.data.export.AudioLibraryScanner
import com.novelarrow.reader.data.model.NeuralVoiceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class OfflineNeuralVoicesAndAccordionTest {

    @Test
    fun testPiperCatalogLanguagesAndSizes() {
        val piperVoices = NeuralVoiceCatalog.getVoicesForEngine("PIPER")
        assertTrue("Piper catalog must not be empty", piperVoices.isNotEmpty())

        val languages = piperVoices.map { it.language.code }.toSet()
        assertTrue("Piper must support English", languages.contains("en"))
        assertTrue("Piper must support French", languages.contains("fr"))
        assertTrue("Piper must support Arabic", languages.contains("ar"))

        piperVoices.forEach { voice ->
            val sizeMb = voice.sizeBytes / (1024 * 1024)
            assertTrue("Piper voice ${voice.name} ($sizeMb MB) must be under 80MB", sizeMb < 80)
            assertTrue("Piper voice must have valid download url", voice.onnxDownloadUrl.startsWith("https://"))
        }
    }

    @Test
    fun testKokoroCatalogLanguagesAndSizes() {
        val kokoroVoices = NeuralVoiceCatalog.getVoicesForEngine("KOKORO")
        assertTrue("Kokoro catalog must not be empty", kokoroVoices.isNotEmpty())

        val languages = kokoroVoices.map { it.language.code }.toSet()
        assertTrue("Kokoro must support English", languages.contains("en"))
        assertTrue("Kokoro must support French", languages.contains("fr"))

        kokoroVoices.forEach { voice ->
            val sizeMb = voice.sizeBytes / (1024 * 1024)
            assertTrue("Kokoro multi-voice bundle ${voice.name} ($sizeMb MB) must be under 350MB", sizeMb < 350)
            assertTrue("Kokoro voice must have valid download url", voice.onnxDownloadUrl.startsWith("https://"))
        }
    }

    @Test
    fun testKittenTtsCatalogLanguagesAndSizes() {
        val kittenVoices = NeuralVoiceCatalog.getVoicesForEngine("KITTEN")
        assertTrue("KittenTTS catalog must not be empty", kittenVoices.isNotEmpty())

        val languages = kittenVoices.map { it.language.code }.toSet()
        assertTrue("KittenTTS must support English", languages.contains("en"))

        kittenVoices.forEach { voice ->
            val sizeMb = voice.sizeBytes / (1024 * 1024)
            assertTrue("KittenTTS voice ${voice.name} ($sizeMb MB) must be under 80MB", sizeMb < 80)
            assertTrue("KittenTTS voice must have valid download url", voice.onnxDownloadUrl.startsWith("https://"))
        }
    }

    @Test
    fun testArabicSampleTextInTtsManager() {
        val arabicLocale = Locale("ar")
        val sampleText = TtsManager.getSampleTextForLocale(arabicLocale)
        assertTrue("Arabic sample text should contain Arabic characters", sampleText.contains("هذا نموذج"))
    }

    @Test
    fun testAudioFolderItemFileCountAndSize() {
        val dummyFolder = File("/dummy/folder/BookReader/MyNovel")
        val file1 = AudioFileItem(file = File("/dummy/folder/BookReader/MyNovel/Ch001.mp3"), sizeBytes = 1024 * 1024 * 2) // 2MB
        val file2 = AudioFileItem(file = File("/dummy/folder/BookReader/MyNovel/Ch002.mp3"), sizeBytes = 1024 * 1024 * 3) // 3MB

        val folderItem = AudioFolderItem(
            folder = dummyFolder,
            name = "MyNovel",
            files = listOf(file1, file2)
        )

        assertEquals(2, folderItem.fileCount)
        assertEquals(5 * 1024 * 1024L, folderItem.totalSizeBytes)
        assertTrue(folderItem.formattedSize.contains("5.0 MB"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", AudioLibraryScanner.formatFileSize(0))
        assertEquals("1.0 KB", AudioLibraryScanner.formatFileSize(1024))
        assertEquals("50.0 MB", AudioLibraryScanner.formatFileSize(50 * 1024 * 1024))
    }
}
