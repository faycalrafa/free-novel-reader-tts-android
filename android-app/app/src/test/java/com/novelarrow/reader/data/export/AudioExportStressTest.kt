package com.novelarrow.reader.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Empirical stress tests for Milestone 3:
 * 1. Monotonicity & extreme bounds of AudioExportEngine.calculateGranularProgress
 * 2. Edge cases, format filtering, zero-byte files, nested directories, special chars in AudioLibraryScanner
 */
class AudioExportStressTest {

    // =========================================================================
    // Part 1: AudioExportEngine.calculateGranularProgress Stress Tests
    // =========================================================================

    @Test
    fun testGranularProgressMonotonicityAcrossWideChapterRange() {
        // Stress test chapter counts from 1 to 200, each with varying paragraph counts
        val chapterCounts = listOf(1, 2, 3, 5, 7, 10, 23, 50, 100, 200)

        for (totalChapters in chapterCounts) {
            val parasPerChapter = 20
            var previousProgress = -1

            for (chap in 1..totalChapters) {
                for (para in 0..parasPerChapter) {
                    val progress = AudioExportEngine.calculateGranularProgress(
                        currentChapter = chap,
                        totalChapters = totalChapters,
                        currentParagraph = para,
                        totalParagraphs = parasPerChapter
                    )

                    assertTrue(
                        "Progress decreased from $previousProgress to $progress at chap $chap/$totalChapters, para $para/$parasPerChapter",
                        progress >= previousProgress
                    )
                    assertTrue("Progress must be >= 0", progress >= 0)
                    assertTrue("Progress must be <= 100", progress <= 100)
                    previousProgress = progress
                }
            }

            assertEquals(
                "Final step of totalChapters=$totalChapters must be exactly 100%",
                100,
                previousProgress
            )
        }
    }

    @Test
    fun testGranularProgressAcrossChapterTransitionsWithVaryingParagraphCounts() {
        // Test chapters with completely uneven paragraph sizes (including 0 paragraphs)
        val chapterParagraphCounts = listOf(
            listOf(10, 20, 30, 40),
            listOf(1, 100, 1, 50, 2),
            listOf(0, 15, 0, 0, 50),
            listOf(500, 1, 0, 200)
        )

        for (parasInChapters in chapterParagraphCounts) {
            val totalChapters = parasInChapters.size
            var previousProgress = 0

            for ((chapIdx, totalParas) in parasInChapters.withIndex()) {
                val chap = chapIdx + 1

                // Initial chapter notification (para 0)
                val startProgress = AudioExportEngine.calculateGranularProgress(
                    chap, totalChapters, 0, totalParas
                )
                assertTrue(
                    "Chapter transition progress decreased: prev $previousProgress > start $startProgress at chap $chap",
                    startProgress >= previousProgress
                )
                previousProgress = startProgress

                // Each paragraph
                for (para in 1..totalParas) {
                    val pProgress = AudioExportEngine.calculateGranularProgress(
                        chap, totalChapters, para, totalParas
                    )
                    assertTrue(
                        "Paragraph progress decreased: prev $previousProgress > cur $pProgress at chap $chap, para $para",
                        pProgress >= previousProgress
                    )
                    previousProgress = pProgress
                }
            }

            assertEquals(
                "Must reach 100% after all chapters complete",
                100,
                previousProgress
            )
        }
    }

    @Test
    fun testGranularProgressExactChapterTransitionsFloatDrift() {
        // Empirically check if floating point drift causes (chap, total, totalParas, totalParas) > (chap+1, total, 0, nextParas)
        // Check for every totalChapters from 1 up to 1000
        val regressions = mutableListOf<String>()
        for (totalChapters in 1..1000) {
            for (chap in 1 until totalChapters) {
                val endOfChap = AudioExportEngine.calculateGranularProgress(
                    currentChapter = chap,
                    totalChapters = totalChapters,
                    currentParagraph = 100,
                    totalParagraphs = 100
                )
                val startOfNextChap = AudioExportEngine.calculateGranularProgress(
                    currentChapter = chap + 1,
                    totalChapters = totalChapters,
                    currentParagraph = 0,
                    totalParagraphs = 50
                )
                if (startOfNextChap < endOfChap) {
                    regressions.add("totalChapters=$totalChapters, chap=$chap: endOfChap=$endOfChap > startOfNextChap=$startOfNextChap")
                }
            }
        }
        println("Found ${regressions.size} backward progress regressions in calculateGranularProgress across totalChapters 1..1000")
        regressions.take(10).forEach { println("Regression sample: $it") }

        assertTrue(
            "Found ${regressions.size} backward progress regressions: ${regressions.take(5)}",
            regressions.isEmpty()
        )
    }

    @Test
    fun testGranularProgressExtremeInputs() {
        // totalChapters = 1
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, 1, 0, 500))
        assertEquals(50, AudioExportEngine.calculateGranularProgress(1, 1, 250, 500))
        assertEquals(100, AudioExportEngine.calculateGranularProgress(1, 1, 500, 500))

        // totalChapters = 1000
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, 1000, 0, 10))
        assertEquals(50, AudioExportEngine.calculateGranularProgress(501, 1000, 0, 10))
        assertEquals(100, AudioExportEngine.calculateGranularProgress(1000, 1000, 10, 10))

        // 0 paragraphs
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, 5, 0, 0))
        assertEquals(20, AudioExportEngine.calculateGranularProgress(2, 5, 0, 0))
        assertEquals(80, AudioExportEngine.calculateGranularProgress(5, 5, 0, 0))

        // Large paragraph counts (e.g. 100,000 paragraphs in a massive chapter)
        val massiveParas = 100_000
        assertEquals(0, AudioExportEngine.calculateGranularProgress(1, 1, 0, massiveParas))
        assertEquals(25, AudioExportEngine.calculateGranularProgress(1, 1, 25_000, massiveParas))
        assertEquals(50, AudioExportEngine.calculateGranularProgress(1, 1, 50_000, massiveParas))
        assertEquals(75, AudioExportEngine.calculateGranularProgress(1, 1, 75_000, massiveParas))
        assertEquals(100, AudioExportEngine.calculateGranularProgress(1, 1, 100_000, massiveParas))

        // Zero and negative chapters (boundary guards)
        assertEquals(0, AudioExportEngine.calculateGranularProgress(0, 0, 0, 0))
        assertEquals(0, AudioExportEngine.calculateGranularProgress(-1, -10, -5, -5))
        assertEquals(0, AudioExportEngine.calculateGranularProgress(5, 0, 10, 10))

        // Negative currentChapter / currentParagraph
        assertEquals(0, AudioExportEngine.calculateGranularProgress(-5, 10, -3, 20))

        // Overflow / out of bounds inputs
        assertEquals(100, AudioExportEngine.calculateGranularProgress(2000, 1000, 500, 10))
        assertEquals(100, AudioExportEngine.calculateGranularProgress(10, 10, 9999, 10))
    }

    // =========================================================================
    // Part 2: AudioLibraryScanner Stress Tests
    // =========================================================================

    @Test
    fun testScannerDeepNestedDirectoriesAndNonAudioExclusion() {
        val tempRoot = Files.createTempDirectory("scanner_stress_nested").toFile()
        try {
            // Level 1 Novel directory
            val novelDir = File(tempRoot, "Supreme Magus").apply { mkdirs() }
            File(novelDir, "Ch01.mp3").writeBytes(ByteArray(1024))
            File(novelDir, "Ch02.MP3").writeBytes(ByteArray(2048)) // Test uppercase extension

            // Level 2 Sub-directory inside novelDir (nested)
            val subVol = File(novelDir, "Volume 2").apply { mkdirs() }
            File(subVol, "Ch03.mp3").writeBytes(ByteArray(1500))

            // Non-audio files inside novelDir
            File(novelDir, "playlist.m3u").writeText("#EXTM3U")
            File(novelDir, "audio_backup.wav").writeBytes(ByteArray(5000))
            File(novelDir, "chapter_notes.txt").writeText("Notes...")
            File(novelDir, "metadata.json").writeText("{}")
            File(novelDir, "cover.png").writeBytes(ByteArray(300))
            File(novelDir, "corrupted_archive.zip").writeBytes(ByteArray(400))

            // Deeply nested subdirectories outside novel
            var deepDir = tempRoot
            for (i in 1..10) {
                deepDir = File(deepDir, "level_$i").apply { mkdirs() }
            }
            File(deepDir, "deep_track.mp3").writeBytes(ByteArray(800))

            // Scan root
            val result = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            // Only novelDir and level_1 are immediate subdirectories of tempRoot
            val novelItem = result.folders.firstOrNull { it.name == "Supreme Magus" }
            assertNotNull("Novel folder must be discovered", novelItem)
            assertEquals("Should contain only direct MP3s (Ch01 and Ch02)", 2, novelItem!!.fileCount)
            assertEquals(1024L + 2048L, novelItem.totalSizeBytes)
            // Verify uppercase .MP3 is matched
            val fileNames = novelItem.files.map { it.file.name }
            assertTrue(fileNames.contains("Ch01.mp3"))
            assertTrue(fileNames.contains("Ch02.MP3"))

            // Verify non-audio files are strictly ignored
            assertFalse(fileNames.contains("playlist.m3u"))
            assertFalse(fileNames.contains("audio_backup.wav"))
            assertFalse(fileNames.contains("chapter_notes.txt"))
            assertFalse(fileNames.contains("metadata.json"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testScannerZeroByteFiles() {
        val tempRoot = Files.createTempDirectory("scanner_stress_zerobyte").toFile()
        try {
            // Standalone 0-byte MP3 (must be excluded)
            val zeroStandalone = File(tempRoot, "ZeroTrack.mp3").apply { createNewFile() }
            assertEquals(0L, zeroStandalone.length())

            // Folder with 0-byte MP3 (must be excluded)
            val novelDir = File(tempRoot, "ZeroNovel").apply { mkdirs() }
            File(novelDir, "ZeroChapter.mp3").apply { createNewFile() }

            val result = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            // 0-byte empty or corrupt files must be excluded
            assertEquals(0, result.standaloneFiles.size)
            assertEquals(0, result.folders.size)
            assertTrue(result.isEmpty)
            assertEquals(0, result.totalFiles)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testScannerSpecialCharactersAndUnicode() {
        val tempRoot = Files.createTempDirectory("scanner_stress_special_chars").toFile()
        try {
            // Folder with CJK, Emoji, brackets, quotes, spaces
            val complexFolderName = "🎧 诡秘之主 [Lord of Mysteries] (Vol. 1) - 'Special & Rare' #42"
            val folder = File(tempRoot, complexFolderName).apply { mkdirs() }

            val trackName1 = "Ch 01 - 序章「红月亮」.mp3"
            val trackName2 = "Ch 02 - What's Next? [Edit] (v1.2).mp3"
            val trackName3 = "  Trimmed Track Name  .mp3"

            File(folder, trackName1).writeBytes(ByteArray(1024))
            File(folder, trackName2).writeBytes(ByteArray(2048))
            File(folder, trackName3).writeBytes(ByteArray(4096))

            // Standalone with dots and spaces
            val complexStandalone = "My.Novel.Audio.Book.2026.Part 01.mp3"
            File(tempRoot, complexStandalone).writeBytes(ByteArray(8192))

            val result = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            // Verify standalone
            assertEquals(1, result.standaloneFiles.size)
            assertEquals("My.Novel.Audio.Book.2026.Part 01", result.standaloneFiles[0].name)
            assertEquals(8192L, result.standaloneFiles[0].sizeBytes)

            // Verify folder
            assertEquals(1, result.folders.size)
            val discoveredFolder = result.folders[0]
            assertEquals(complexFolderName, discoveredFolder.name)
            assertEquals(3, discoveredFolder.fileCount)
            assertEquals(1024L + 2048L + 4096L, discoveredFolder.totalSizeBytes)

            val trackNames = discoveredFolder.files.map { it.name }
            assertTrue(trackNames.contains("Ch 01 - 序章「红月亮」"))
            assertTrue(trackNames.contains("Ch 02 - What's Next? [Edit] (v1.2)"))
            assertTrue(trackNames.contains("  Trimmed Track Name  "))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testScannerDuplicatePathsAcrossMultipleScanRoots() {
        // When public storage and app external both point to or include the same file
        val root1 = Files.createTempDirectory("scanner_root1").toFile()
        val root2 = Files.createTempDirectory("scanner_root2").toFile()
        try {
            // Folder with same name in both roots
            val folder1 = File(root1, "SharedNovel").apply { mkdirs() }
            val folder2 = File(root2, "SharedNovel").apply { mkdirs() }

            val track1 = File(folder1, "Chapter1.mp3").apply { writeBytes(ByteArray(1000)) }
            val track2 = File(folder2, "Chapter2.mp3").apply { writeBytes(ByteArray(2000)) }

            val result = AudioLibraryScanner.scanDirectories(listOf(root1, root2))

            // Map groups by folder name "SharedNovel"
            assertEquals("Folders with identical names are merged", 1, result.folders.size)
            val mergedFolder = result.folders[0]
            assertEquals("SharedNovel", mergedFolder.name)
            assertEquals(2, mergedFolder.fileCount)
            assertEquals(3000L, mergedFolder.totalSizeBytes)
            assertEquals(listOf("Chapter1", "Chapter2"), mergedFolder.files.map { it.name })
        } finally {
            root1.deleteRecursively()
            root2.deleteRecursively()
        }
    }

    @Test
    fun testFormatFileSizeExtremeBounds() {
        assertEquals("0 B", AudioLibraryScanner.formatFileSize(Long.MIN_VALUE))
        assertEquals("0 B", AudioLibraryScanner.formatFileSize(-1))
        assertEquals("0 B", AudioLibraryScanner.formatFileSize(0))
        assertEquals("1.0 B", AudioLibraryScanner.formatFileSize(1))
        assertEquals("1023.0 B", AudioLibraryScanner.formatFileSize(1023))
        assertEquals("1.0 KB", AudioLibraryScanner.formatFileSize(1024))
        assertEquals("1024.0 KB", AudioLibraryScanner.formatFileSize(1024 * 1024 - 1))
        assertEquals("1.0 MB", AudioLibraryScanner.formatFileSize(1024 * 1024))
        assertEquals("1.0 GB", AudioLibraryScanner.formatFileSize(1024L * 1024L * 1024L))
        // Multiple Terabytes (coerced to GB unit)
        val terabyte = 1024L * 1024L * 1024L * 1024L
        val formattedTb = AudioLibraryScanner.formatFileSize(terabyte)
        assertTrue("TB formats under GB units gracefully", formattedTb.endsWith("GB"))
        assertEquals("1024.0 GB", formattedTb)

        val maxLong = AudioLibraryScanner.formatFileSize(Long.MAX_VALUE)
        assertTrue("Long.MAX_VALUE does not throw", maxLong.endsWith("GB"))
    }
}
