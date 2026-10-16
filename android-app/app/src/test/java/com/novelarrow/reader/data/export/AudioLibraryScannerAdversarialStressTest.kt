package com.novelarrow.reader.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Empirical Adversarial Challenger Suite for AudioLibraryScanner:
 * Stress-testing:
 * 1. 0-byte files (isolated, bulk, hidden, mixed, case-variations)
 * 2. Corrupted files (garbage bytes, truncated ID3, pseudo-mp3 text, directories with .mp3 extension, broken links)
 * 3. Valid MP3 files (synthesized audio frames, multi-root merges, special characters, scale tests up to 1,000 files)
 */
class AudioLibraryScannerAdversarialStressTest {

    // =========================================================================
    // 1. Zero-Byte Files & Empty Directory Edge Cases
    // =========================================================================

    @Test
    fun testZeroByteFilesAllVariantsExcluded() {
        val tempRoot = Files.createTempDirectory("zero_byte_adversarial").toFile()
        try {
            // Standalone zero byte files with different casing
            File(tempRoot, "empty_lower.mp3").createNewFile()
            File(tempRoot, "empty_upper.MP3").createNewFile()
            File(tempRoot, "empty_mixed.Mp3").createNewFile()
            File(tempRoot, "empty_alt.mP3").createNewFile()
            File(tempRoot, ".hidden_zero.mp3").createNewFile()

            // Subdirectories with only zero-byte files
            val deadFolder1 = File(tempRoot, "DeadNovel1").apply { mkdirs() }
            File(deadFolder1, "part01.mp3").createNewFile()
            File(deadFolder1, "part02.MP3").createNewFile()

            val deadFolder2 = File(tempRoot, "DeadNovel2").apply { mkdirs() }
            for (i in 1..20) {
                File(deadFolder2, "chapter_$i.mp3").createNewFile()
            }

            // Mixed folder: 10 zero-byte files + 2 valid non-empty MP3 files
            val mixedFolder = File(tempRoot, "MixedNovel").apply { mkdirs() }
            for (i in 1..10) {
                File(mixedFolder, "corrupt_zero_$i.mp3").createNewFile()
            }
            val valid1 = File(mixedFolder, "valid_chapter_01.mp3").apply { writeBytes(createFakeMp3(512)) }
            val valid2 = File(mixedFolder, "valid_chapter_02.mp3").apply { writeBytes(createFakeMp3(1024)) }

            val result = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            // Standalone: all zero byte files must be excluded
            assertEquals("No standalone files should be discovered", 0, result.standaloneFiles.size)

            // Folders: DeadNovel1 and DeadNovel2 must be excluded; MixedNovel must have exactly 2 files
            assertEquals("Only MixedNovel should be discovered", 1, result.folders.size)
            val mixedItem = result.folders.first()
            assertEquals("MixedNovel", mixedItem.name)
            assertEquals(2, mixedItem.fileCount)
            assertEquals(valid1.length() + valid2.length(), mixedItem.totalSizeBytes)
            assertEquals(listOf("valid_chapter_01", "valid_chapter_02"), mixedItem.files.map { it.name })
            assertEquals(2, result.totalFiles)
            assertFalse(result.isEmpty)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // =========================================================================
    // 2. Corrupt, Pseudo-MP3, and Hostile Filesystem Layouts
    // =========================================================================

    @Test
    fun testCorruptAndNonAudioFiles() {
        val tempRoot = Files.createTempDirectory("corrupt_adversarial").toFile()
        try {
            val novelDir = File(tempRoot, "HostileNovel").apply { mkdirs() }

            // 1 byte file named .mp3
            File(novelDir, "one_byte.mp3").writeBytes(byteArrayOf(0x00))

            // Text/HTML error page pretending to be MP3
            File(novelDir, "error_404.mp3").writeText("<html><head><title>404 Not Found</title></head></html>")

            // Truncated ID3 tag with no frames
            File(novelDir, "truncated_id3.mp3").writeBytes(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

            // Arbitrary binary garbage (e.g. zip file or image renamed to .mp3)
            val garbage = ByteArray(2048) { (it % 256).toByte() }
            File(novelDir, "garbage_archive.mp3").writeBytes(garbage)

            // Valid synthetic MP3
            File(novelDir, "valid_track.mp3").writeBytes(createFakeMp3(4096))

            // Subdirectory named with .mp3 extension (DIRECTORY, not a file!)
            val fakeMp3Dir = File(novelDir, "fake_folder.mp3").apply { mkdirs() }
            File(fakeMp3Dir, "inner_content.txt").writeText("I am inside a directory named .mp3")

            // Files with misleading double extensions
            File(novelDir, "track.mp3.bak").writeBytes(ByteArray(1024))
            File(novelDir, "track.mp3.tmp").writeBytes(ByteArray(1024))
            File(novelDir, "track.mp3.part").writeBytes(ByteArray(1024))
            File(novelDir, "track.wav").writeBytes(ByteArray(1024))
            File(novelDir, "track.m4a").writeBytes(ByteArray(1024))

            val result = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            assertEquals(1, result.folders.size)
            val folder = result.folders.first()
            assertEquals("HostileNovel", folder.name)

            // The scanner considers any non-empty file with extension .mp3 as an MP3 file
            // (decoder errors are handled gracefully downstream by ExoPlayer / AudioPlaybackCoordinator).
            // Directories named .mp3 and non-.mp3 extensions MUST be ignored.
            val fileNames = folder.files.map { it.file.name }
            assertFalse("Directory named fake_folder.mp3 must NOT be included as an MP3 file", fileNames.contains("fake_folder.mp3"))
            assertFalse("Non-mp3 files must not be included", fileNames.contains("track.mp3.bak"))
            assertFalse("Non-mp3 files must not be included", fileNames.contains("track.mp3.tmp"))
            assertFalse("Non-mp3 files must not be included", fileNames.contains("track.wav"))

            assertTrue("Valid track should be included", fileNames.contains("valid_track.mp3"))
            assertTrue("One byte mp3 should be scanned (playback error handled at audio service layer)", fileNames.contains("one_byte.mp3"))
            assertTrue("Error html mp3 should be scanned", fileNames.contains("error_404.mp3"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testDirectoryNamedAsMp3AtRoot() {
        val tempRoot = Files.createTempDirectory("dir_as_mp3_root").toFile()
        try {
            // A root directory containing a subdirectory named "Chapter01.mp3"
            val dirNamedMp3 = File(tempRoot, "Chapter01.mp3").apply { mkdirs() }
            // Inside that directory is a legitimate MP3
            val realTrack = File(dirNamedMp3, "TrackInside.mp3").apply { writeBytes(createFakeMp3(2048)) }

            val result = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            // Chapter01.mp3 should be treated as a FOLDER, not a standalone file
            assertEquals("No standalone files since Chapter01.mp3 is a directory", 0, result.standaloneFiles.size)
            assertEquals("Chapter01.mp3 is recognized as a folder", 1, result.folders.size)
            val folder = result.folders.first()
            assertEquals("Chapter01.mp3", folder.name)
            assertEquals(1, folder.fileCount)
            assertEquals("TrackInside", folder.files.first().name)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // =========================================================================
    // 3. Valid MP3 Files, Multi-Root Merging & Deduplication
    // =========================================================================

    @Test
    fun testValidMp3MultiRootMergingAndDeduplication() {
        val root1 = Files.createTempDirectory("valid_root1").toFile()
        val root2 = Files.createTempDirectory("valid_root2").toFile()
        try {
            val novelA1 = File(root1, "Coiling Dragon").apply { mkdirs() }
            File(novelA1, "CD_Ch01.mp3").writeBytes(createFakeMp3(1000))
            File(novelA1, "CD_Ch02.mp3").writeBytes(createFakeMp3(2000))

            val novelA2 = File(root2, "Coiling Dragon").apply { mkdirs() }
            File(novelA2, "CD_Ch03.mp3").writeBytes(createFakeMp3(3000))
            // Exact same file placed in root2 (simulate shared/linked file or separate file with same name)
            File(novelA2, "CD_Ch01.mp3").writeBytes(createFakeMp3(1000))

            // Standalone files in both roots
            File(root1, "Prologue.mp3").writeBytes(createFakeMp3(500))
            File(root2, "Epilogue.mp3").writeBytes(createFakeMp3(700))

            val result = AudioLibraryScanner.scanDirectories(listOf(root1, root2))

            assertEquals("Standalone files from both roots discovered", 2, result.standaloneFiles.size)
            val standaloneNames = result.standaloneFiles.map { it.name }
            assertTrue(standaloneNames.contains("Prologue"))
            assertTrue(standaloneNames.contains("Epilogue"))

            assertEquals("Coiling Dragon folders merged into one folder item", 1, result.folders.size)
            val folder = result.folders.first()
            assertEquals("Coiling Dragon", folder.name)
            // Note: CD_Ch01 in root1 and root2 have different canonical paths, so distinctBy canonicalPath keeps both
            // CD_Ch01, CD_Ch01, CD_Ch02, CD_Ch03 = 4 files
            assertEquals(4, folder.fileCount)
            assertEquals(7000L, folder.totalSizeBytes)
            assertEquals("6.8 KB", folder.formattedSize)
        } finally {
            root1.deleteRecursively()
            root2.deleteRecursively()
        }
    }

    @Test
    fun testExactCanonicalDuplicateDeduplication() {
        val root = Files.createTempDirectory("dedup_root").toFile()
        try {
            val novel = File(root, "Overlord").apply { mkdirs() }
            File(novel, "Vol01.mp3").writeBytes(createFakeMp3(4096))
            File(root, "Standalone.mp3").writeBytes(createFakeMp3(2048))

            // Pass the same root directory twice to simulate multiple providers pointing to the same folder
            val result = AudioLibraryScanner.scanDirectories(listOf(root, root))

            assertEquals("Standalone deduplicated by canonical path", 1, result.standaloneFiles.size)
            assertEquals(1, result.folders.size)
            assertEquals("Folder tracks deduplicated by canonical path", 1, result.folders.first().fileCount)
            assertEquals(2, result.totalFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    // =========================================================================
    // 4. Scale and Performance Stress Test (1,000 Valid Files)
    // =========================================================================

    @Test
    fun testScaleScanOneThousandFilesPerformance() {
        val root = Files.createTempDirectory("scale_1000_files").toFile()
        try {
            val novel = File(root, "Martial World").apply { mkdirs() }
            val dummyBytes = createFakeMp3(128)

            // Generate 1,000 files
            for (i in 1..1000) {
                val filename = String.format("MW_Chapter_%04d.mp3", i)
                File(novel, filename).writeBytes(dummyBytes)
            }

            val startTime = System.currentTimeMillis()
            val result = AudioLibraryScanner.scanDirectories(listOf(root))
            val duration = System.currentTimeMillis() - startTime

            println("Scanning 1,000 files took: ${duration}ms")
            assertTrue("Scanning 1,000 files should complete within 2000ms, took ${duration}ms", duration < 2000)

            assertEquals(1, result.folders.size)
            val folder = result.folders.first()
            assertEquals("Martial World", folder.name)
            assertEquals(1000, folder.fileCount)
            assertEquals(128L * 1000L, folder.totalSizeBytes)
            assertEquals(1000, result.totalFiles)

            // Verify sorting order of tracks
            assertEquals("MW_Chapter_0001", folder.files.first().name)
            assertEquals("MW_Chapter_1000", folder.files.last().name)
        } finally {
            root.deleteRecursively()
        }
    }

    // =========================================================================
    // 5. Robustness to Missing/Invalid Roots and Post-Scan Deletions
    // =========================================================================

    @Test
    fun testNonExistentAndFileRootsResilience() {
        val nonExistentDir = File("/non/existent/path/for/unit/test/12345")
        val realFile = Files.createTempFile("not_a_dir", ".txt").toFile().apply {
            writeText("I am a file, not a directory")
        }
        try {
            val result = AudioLibraryScanner.scanDirectories(listOf(nonExistentDir, realFile))
            assertTrue("Scan should return empty result without crashing", result.isEmpty)
            assertEquals(0, result.totalFiles)
        } finally {
            realFile.delete()
        }
    }

    @Test
    fun testAudioFileItemResilienceWhenFileDeletedPostScan() {
        val tempRoot = Files.createTempDirectory("deleted_post_scan").toFile()
        try {
            val file = File(tempRoot, "temp_song.mp3").apply { writeBytes(createFakeMp3(1024)) }
            val itemBeforeDelete = AudioFileItem(file)
            assertEquals(1024L, itemBeforeDelete.sizeBytes)

            // Now delete the underlying file
            assertTrue("File deleted successfully", file.delete())
            assertFalse("File should no longer exist", file.exists())

            // Create AudioFileItem pointing to the now deleted file
            val itemAfterDelete = AudioFileItem(file)
            assertEquals("sizeBytes should safely fall back to 0L", 0L, itemAfterDelete.sizeBytes)
            assertEquals("0 B", itemAfterDelete.formattedSize)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // =========================================================================
    // Helper: Generate synthetic valid MPEG-1 Audio Layer 3 frame header bytes
    // =========================================================================
    private fun createFakeMp3(sizeBytes: Int): ByteArray {
        val bytes = ByteArray(maxOf(sizeBytes, 4))
        // MPEG-1 Layer 3 Sync Word: 0xFF, 0xFB (11111111 11111011 = MPEG-1, Layer 3, no CRC)
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xFB.toByte()
        bytes[2] = 0x90.toByte() // 128 kbps, 44.1 kHz, no padding
        bytes[3] = 0x44.toByte() // Channel mode: stereo
        return bytes
    }
}
