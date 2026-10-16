package com.novelarrow.reader.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AudioLibraryScannerTest {

    @Test
    fun testFormatFileSizeFormatting() {
        assertEquals("0 B", AudioLibraryScanner.formatFileSize(0))
        assertEquals("0 B", AudioLibraryScanner.formatFileSize(-100))
        assertEquals("500.0 B", AudioLibraryScanner.formatFileSize(500))
        assertEquals("1.0 KB", AudioLibraryScanner.formatFileSize(1024))
        assertEquals("1.5 KB", AudioLibraryScanner.formatFileSize(1536))
        assertEquals("1.0 MB", AudioLibraryScanner.formatFileSize(1024 * 1024))
        assertEquals("2.5 MB", AudioLibraryScanner.formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", AudioLibraryScanner.formatFileSize(1024L * 1024L * 1024L))
    }

    @Test
    fun testScanDirectoriesDiscoveryAndFiltering() {
        val tempRoot = Files.createTempDirectory("scanner_test").toFile()
        try {
            // Standalone MP3
            val standaloneMp3 = File(tempRoot, "Standalone_Chapter.mp3").apply {
                writeBytes(ByteArray(2048))
            }
            // Standalone non-MP3 files (must be ignored)
            File(tempRoot, "playlist.m3u").writeText("#EXTM3U")
            File(tempRoot, "temp_render.wav").writeBytes(ByteArray(100))
            File(tempRoot, "notes.txt").writeText("Notes")

            // Subdirectory 1: Novel 1 with 3 MP3s and 1 non-MP3
            val novelDir1 = File(tempRoot, "Lord of the Mysteries").apply { mkdirs() }
            File(novelDir1, "LotM_Ch001.mp3").writeBytes(ByteArray(1024))
            File(novelDir1, "LotM_Ch002.mp3").writeBytes(ByteArray(2048))
            File(novelDir1, "LotM_Ch003.mp3").writeBytes(ByteArray(3072))
            File(novelDir1, "cover.jpg").writeBytes(ByteArray(500))

            // Subdirectory 2: Novel 2 with 1 MP3
            val novelDir2 = File(tempRoot, "Shadow Slave").apply { mkdirs() }
            File(novelDir2, "SS_Part01.mp3").writeBytes(ByteArray(4096))

            // Subdirectory 3: Empty or no MP3s (must be ignored)
            val emptyDir = File(tempRoot, "EmptyFolder").apply { mkdirs() }
            File(emptyDir, "ignore.wav").writeBytes(ByteArray(50))

            val content = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            assertFalse("Content should not be empty", content.isEmpty)
            assertEquals("Total tracks count", 5, content.totalFiles)

            // Standalone files check
            assertEquals(1, content.standaloneFiles.size)
            val standalone = content.standaloneFiles.first()
            assertEquals("Standalone_Chapter", standalone.name)
            assertEquals(2048L, standalone.sizeBytes)
            assertEquals("2.0 KB", standalone.formattedSize)

            // Folders check
            assertEquals(2, content.folders.size)

            val lotmFolder = content.folders.firstOrNull { it.name == "Lord of the Mysteries" }
            assertTrue("LotM folder exists", lotmFolder != null)
            assertEquals(3, lotmFolder!!.fileCount)
            assertEquals(1024L + 2048L + 3072L, lotmFolder.totalSizeBytes)
            assertEquals("6.0 KB", lotmFolder.formattedSize)
            assertEquals(listOf("LotM_Ch001", "LotM_Ch002", "LotM_Ch003"), lotmFolder.files.map { it.name })

            val ssFolder = content.folders.firstOrNull { it.name == "Shadow Slave" }
            assertTrue("Shadow Slave folder exists", ssFolder != null)
            assertEquals(1, ssFolder!!.fileCount)
            assertEquals(4096L, ssFolder.totalSizeBytes)
            assertEquals("4.0 KB", ssFolder.formattedSize)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testEmptyDirectoryScan() {
        val tempRoot = Files.createTempDirectory("scanner_empty").toFile()
        try {
            val content = AudioLibraryScanner.scanDirectories(listOf(tempRoot))
            assertTrue("Content must be empty", content.isEmpty)
            assertEquals(0, content.totalFiles)
            assertEquals(0, content.folders.size)
            assertEquals(0, content.standaloneFiles.size)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testScanDirectoriesExcludesZeroByteFiles() {
        val tempRoot = Files.createTempDirectory("scanner_zerobyte").toFile()
        try {
            // Standalone 0-byte MP3
            File(tempRoot, "Corrupt_Standalone.mp3").createNewFile()
            // Standalone valid MP3
            File(tempRoot, "Valid_Standalone.mp3").writeBytes(ByteArray(1024))

            // Folder with 0-byte MP3 and valid MP3
            val folder1 = File(tempRoot, "MixedFolder").apply { mkdirs() }
            File(folder1, "Zero.mp3").createNewFile()
            File(folder1, "Valid.mp3").writeBytes(ByteArray(2048))

            // Folder with ONLY 0-byte MP3
            val folder2 = File(tempRoot, "OnlyZeroFolder").apply { mkdirs() }
            File(folder2, "Corrupt1.mp3").createNewFile()
            File(folder2, "Corrupt2.mp3").createNewFile()

            val content = AudioLibraryScanner.scanDirectories(listOf(tempRoot))

            // Only 1 standalone file
            assertEquals(1, content.standaloneFiles.size)
            assertEquals("Valid_Standalone", content.standaloneFiles.first().name)

            // Only MixedFolder should be listed, OnlyZeroFolder should NOT be listed
            assertEquals(1, content.folders.size)
            val mixed = content.folders.first()
            assertEquals("MixedFolder", mixed.name)
            assertEquals(1, mixed.fileCount)
            assertEquals("Valid", mixed.files.first().name)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
