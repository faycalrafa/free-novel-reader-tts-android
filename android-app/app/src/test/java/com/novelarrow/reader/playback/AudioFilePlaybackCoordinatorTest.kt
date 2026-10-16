package com.novelarrow.reader.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AudioFilePlaybackCoordinatorTest {

    @Test
    fun testAudioFilePlaybackStateUpdates() {
        val tempDir = Files.createTempDirectory("coordinator_audio_test").toFile()
        try {
            val novelFolder = File(tempDir, "The Lord of the Rings").apply { mkdirs() }
            val track1 = File(novelFolder, "Ch01_Fellowship.mp3").apply { writeBytes(ByteArray(100)) }
            val track2 = File(novelFolder, "Ch02_Shadow_of_the_Past.mp3").apply { writeBytes(ByteArray(100)) }
            val track3 = File(novelFolder, "Ch03_Three_is_Company.mp3").apply { writeBytes(ByteArray(100)) }

            val playlist = listOf(track1, track2, track3)

            // Play track 1
            AudioPlaybackCoordinator.playAudioFile(track1, playlist, index = 0)

            var state = AudioPlaybackCoordinator.playbackProgressState.value
            assertTrue("Is playing", state.isPlaying)
            assertEquals("Source is AUDIO_FILE", PlaybackSource.AUDIO_FILE, state.source)
            assertEquals("Album/Novel name is folder name", "The Lord of the Rings", state.novelTitle)
            assertEquals("Chapter name is track without extension", "Ch01_Fellowship", state.chapterTitle)
            assertEquals("Current paragraph index is track index", 0, state.currentParagraphIndex)
            assertEquals("Total paragraphs is playlist size", 3, state.totalParagraphs)
            assertEquals("Current paragraph text is filename", "Ch01_Fellowship.mp3", state.currentParagraphText)

            // Navigate next track via nextParagraph
            AudioPlaybackCoordinator.nextParagraph()
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertEquals("Advanced to track index 1", 1, state.currentParagraphIndex)
            assertEquals("Track 2 title", "Ch02_Shadow_of_the_Past", state.chapterTitle)

            // Seek to track index 2 via seekToParagraph
            AudioPlaybackCoordinator.seekToParagraph(2)
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertEquals("Seeked to track index 2", 2, state.currentParagraphIndex)
            assertEquals("Track 3 title", "Ch03_Three_is_Company", state.chapterTitle)

            // Navigate previous track via previousParagraph
            AudioPlaybackCoordinator.previousParagraph()
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertEquals("Previous track index 1", 1, state.currentParagraphIndex)
            assertEquals("Track 2 title", "Ch02_Shadow_of_the_Past", state.chapterTitle)

            // Pause
            AudioPlaybackCoordinator.pause()
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertFalse("Playback paused", state.isPlaying)

            // Toggle play/pause
            AudioPlaybackCoordinator.togglePlayPause()
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertTrue("Playback resumed", state.isPlaying)

            // Test playAudioFolder
            AudioPlaybackCoordinator.playAudioFolder(novelFolder)
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertTrue("Folder playback started", state.isPlaying)
            assertEquals(PlaybackSource.AUDIO_FILE, state.source)
            assertEquals("Ch01_Fellowship", state.chapterTitle)
            assertEquals(0, state.currentParagraphIndex)

            // Stop
            AudioPlaybackCoordinator.stop()
            state = AudioPlaybackCoordinator.playbackProgressState.value
            assertFalse("Playback stopped", state.isPlaying)
            assertEquals(PlaybackSource.NONE, state.source)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
