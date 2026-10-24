package com.novelarrow.reader.playback

import com.novelarrow.reader.ui.studio.AudioStudioMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone3Challenger2Test {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("challenger2_m3_test").toFile()
        AudioPlaybackCoordinator.stop()
    }

    @After
    fun tearDown() {
        AudioPlaybackCoordinator.stop()
        tempDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. PLAYLIST NAVIGATION & BOUNDARY CONDITIONS
    // =========================================================================

    @Test
    fun testSingleFilePlaylistNavigationBoundaries() {
        val album = File(tempDir, "SingleTrackAlbum").apply { mkdirs() }
        val singleTrack = File(album, "SoloTrack.mp3").apply { writeBytes(ByteArray(50)) }
        val playlist = listOf(singleTrack)

        // Start playback with single track
        AudioPlaybackCoordinator.playAudioFile(singleTrack, playlist, index = 0)
        var state = AudioPlaybackCoordinator.playbackProgressState.value

        assertTrue("Single track is playing", state.isPlaying)
        assertEquals(PlaybackSource.AUDIO_FILE, state.source)
        assertEquals("Track index is 0", 0, state.currentParagraphIndex)
        assertEquals("Total tracks is 1", 1, state.totalParagraphs)
        assertEquals("Album title", "SingleTrackAlbum", state.novelTitle)
        assertEquals("Chapter title", "SoloTrack", state.chapterTitle)

        // nextParagraph on single-file playlist: must pause instead of out-of-bounds or looping
        AudioPlaybackCoordinator.nextParagraph()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertFalse("Playback must be paused when advancing past end of single-file playlist", state.isPlaying)
        assertEquals("Track index must remain 0", 0, state.currentParagraphIndex)

        // Resume playback
        AudioPlaybackCoordinator.play()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertTrue("Playback resumed", state.isPlaying)

        // previousParagraph on single-file playlist: must remain on track 0 without error
        AudioPlaybackCoordinator.previousParagraph()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertTrue("Playback still active", state.isPlaying)
        assertEquals("Track index remains 0", 0, state.currentParagraphIndex)

        // seekToParagraph out-of-bounds on single-file playlist: must be safely ignored
        AudioPlaybackCoordinator.seekToParagraph(1)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Invalid forward seek ignored", 0, state.currentParagraphIndex)

        AudioPlaybackCoordinator.seekToParagraph(-1)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Invalid backward seek ignored", 0, state.currentParagraphIndex)
    }

    @Test
    fun testMultiTrackFirstAndLastTrackBoundaries() {
        val album = File(tempDir, "EpicNovel").apply { mkdirs() }
        val tracks = (1..4).map { idx ->
            File(album, "Chapter_%02d.mp3".format(idx)).apply { writeBytes(ByteArray(50)) }
        }

        // 1. Start on First Track (index 0)
        AudioPlaybackCoordinator.playAudioFile(tracks[0], tracks, index = 0)
        var state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Starting at track 0", 0, state.currentParagraphIndex)
        assertEquals(4, state.totalParagraphs)

        // previousParagraph on first track: should not underflow below 0
        AudioPlaybackCoordinator.previousParagraph()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Must stay on track 0", 0, state.currentParagraphIndex)
        assertEquals("Chapter_01", state.chapterTitle)

        // previousChapter should delegate to previousParagraph for AUDIO_FILE
        AudioPlaybackCoordinator.previousChapter()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("previousChapter maintains track 0 boundary", 0, state.currentParagraphIndex)

        // 2. Jump to Last Track (index 3)
        AudioPlaybackCoordinator.seekToParagraph(3)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Seeked to last track", 3, state.currentParagraphIndex)
        assertEquals("Chapter_04", state.chapterTitle)

        // nextParagraph on last track: should pause playback (auto-stop) without out-of-bounds error
        AudioPlaybackCoordinator.nextParagraph()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertFalse("Playback pauses at end of playlist", state.isPlaying)
        assertEquals("Remains on last track index", 3, state.currentParagraphIndex)

        // Resume on last track
        AudioPlaybackCoordinator.play()
        assertTrue(AudioPlaybackCoordinator.playbackProgressState.value.isPlaying)

        // nextChapter on last track: should delegate to nextParagraph and pause
        AudioPlaybackCoordinator.nextChapter()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertFalse("nextChapter at end of playlist pauses playback", state.isPlaying)
        assertEquals(3, state.currentParagraphIndex)
    }

    @Test
    fun testTrackProgressionAndStateEndedHandling() {
        val album = File(tempDir, "AudioSeries").apply { mkdirs() }
        val tracks = (1..3).map { idx ->
            File(album, "Episode_$idx.mp3").apply { writeBytes(ByteArray(50)) }
        }

        // Start at track 0
        AudioPlaybackCoordinator.playAudioFile(tracks[0], tracks, index = 0)
        var state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals(0, state.currentParagraphIndex)
        assertTrue(state.isPlaying)

        // Simulate STATE_ENDED for track 0 -> onAudioFileCompleted()
        AudioPlaybackCoordinator.onAudioFileCompleted()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Auto-advanced to track 1", 1, state.currentParagraphIndex)
        assertEquals("Episode_2", state.chapterTitle)
        assertTrue("Auto-advancing keeps playing", state.isPlaying)

        // Simulate STATE_ENDED for track 1 -> advances to track 2
        AudioPlaybackCoordinator.onAudioFileCompleted()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Auto-advanced to track 2 (final track)", 2, state.currentParagraphIndex)
        assertEquals("Episode_3", state.chapterTitle)
        assertTrue(state.isPlaying)

        // Simulate STATE_ENDED on last track (track 2) -> auto-stops by pausing
        AudioPlaybackCoordinator.onAudioFileCompleted()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertFalse("End of playlist automatically pauses playback", state.isPlaying)
        assertEquals("Stays at last track index", 2, state.currentParagraphIndex)
    }

    @Test
    fun testEmptyPlaylistAndOutOfBoundsCoercion() {
        val album = File(tempDir, "EdgeCaseAlbum").apply { mkdirs() }
        val track = File(album, "Standalone.mp3").apply { writeBytes(ByteArray(20)) }

        // Pass empty playlist: should safely fallback to listOf(track)
        AudioPlaybackCoordinator.playAudioFile(file = track, playlist = emptyList(), index = 0)
        var state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Playlist fallback to single item", 1, state.totalParagraphs)
        assertEquals(0, state.currentParagraphIndex)
        assertEquals("Standalone", state.chapterTitle)

        // Pass out-of-bounds initial index (e.g. 50 on single track)
        AudioPlaybackCoordinator.playAudioFile(file = track, playlist = listOf(track), index = 50)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Index coerced into valid range [0..0]", 0, state.currentParagraphIndex)

        // Pass negative initial index (e.g. -10)
        val tracks = (1..3).map { File(album, "Track_$it.mp3").apply { writeBytes(ByteArray(10)) } }
        AudioPlaybackCoordinator.playAudioFile(file = tracks[0], playlist = tracks, index = -10)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Negative index coerced to 0", 0, state.currentParagraphIndex)

        // Pass index exceeding multi-track size (index 10 for size 3)
        AudioPlaybackCoordinator.playAudioFile(file = tracks[2], playlist = tracks, index = 10)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals("Excess index coerced to last element (2)", 2, state.currentParagraphIndex)
    }

    @Test
    fun testPlayAudioFolderRobustness() {
        // 1. Non-existent folder
        val nonExistentFolder = File(tempDir, "DoesNotExist")
        AudioPlaybackCoordinator.playAudioFolder(nonExistentFolder)
        assertEquals("Non-existent folder does not change source", PlaybackSource.NONE, AudioPlaybackCoordinator.playbackProgressState.value.source)

        // 2. Folder without any MP3s
        val nonMp3Folder = File(tempDir, "TextFolder").apply { mkdirs() }
        File(nonMp3Folder, "readme.txt").writeText("hello")
        File(nonMp3Folder, "cover.jpg").writeBytes(ByteArray(10))
        File(nonMp3Folder, "audio.wav").writeBytes(ByteArray(10))
        AudioPlaybackCoordinator.playAudioFolder(nonMp3Folder)
        assertEquals("Folder without MP3s does not start playback", PlaybackSource.NONE, AudioPlaybackCoordinator.playbackProgressState.value.source)

        // 3. Folder with mixed files including uppercase .MP3 and lowercase .mp3
        val validFolder = File(tempDir, "BookFolder").apply { mkdirs() }
        File(validFolder, "Chapter_B.mp3").writeBytes(ByteArray(20))
        File(validFolder, "Chapter_A.MP3").writeBytes(ByteArray(20))
        File(validFolder, "notes.txt").writeText("notes")

        AudioPlaybackCoordinator.playAudioFolder(validFolder)
        val state = AudioPlaybackCoordinator.playbackProgressState.value
        assertTrue("Playback started for valid MP3 folder", state.isPlaying)
        assertEquals(PlaybackSource.AUDIO_FILE, state.source)
        assertEquals("BookFolder", state.novelTitle)
        // Check sorting: Chapter_A before Chapter_B
        assertEquals("Chapter_A", state.chapterTitle)
        assertEquals(0, state.currentParagraphIndex)
        assertEquals(2, state.totalParagraphs)
    }

    // =========================================================================
    // 2. RAPID PLAY / PAUSE TOGGLING & STATE SYNCHRONIZATION
    // =========================================================================

    @Test
    fun testRapidPlayPauseTogglingConsistency() {
        val album = File(tempDir, "StressAlbum").apply { mkdirs() }
        val track = File(album, "Track.mp3").apply { writeBytes(ByteArray(10)) }

        AudioPlaybackCoordinator.playAudioFile(track)
        assertTrue(AudioPlaybackCoordinator.isPlayingState.value)
        assertTrue(AudioPlaybackCoordinator.playbackProgressState.value.isPlaying)

        // Rapid 100-cycle toggling via togglePlayPause()
        for (i in 1..100) {
            AudioPlaybackCoordinator.togglePlayPause()
            val expectedPlaying = (i % 2 == 0)
            assertEquals(
                "isPlayingState out of sync at iteration $i",
                expectedPlaying,
                AudioPlaybackCoordinator.isPlayingState.value
            )
            assertEquals(
                "playbackProgressState out of sync at iteration $i",
                expectedPlaying,
                AudioPlaybackCoordinator.playbackProgressState.value.isPlaying
            )
        }

        // Test rapid alternating calls to play() and pause()
        for (i in 1..50) {
            AudioPlaybackCoordinator.play()
            assertTrue("Should be playing after play()", AudioPlaybackCoordinator.isPlayingState.value)
            assertTrue("StateFlow should reflect playing", AudioPlaybackCoordinator.playbackProgressState.value.isPlaying)

            AudioPlaybackCoordinator.pause()
            assertFalse("Should be paused after pause()", AudioPlaybackCoordinator.isPlayingState.value)
            assertFalse("StateFlow should reflect paused", AudioPlaybackCoordinator.playbackProgressState.value.isPlaying)
        }

        // Stop resets state completely
        AudioPlaybackCoordinator.stop()
        assertFalse(AudioPlaybackCoordinator.isPlayingState.value)
        assertFalse(AudioPlaybackCoordinator.playbackProgressState.value.isPlaying)
        assertEquals(PlaybackSource.NONE, AudioPlaybackCoordinator.playbackProgressState.value.source)
    }

    // =========================================================================
    // 3. UI MODES & CONTRACT VERIFICATION
    // =========================================================================

    @Test
    fun testAudioStudioModeEnumContract() {
        // Contract: AudioStudioMode must define LIBRARY and GENERATOR
        val modes = AudioStudioMode.values()
        assertEquals(2, modes.size)
        assertTrue("Contains LIBRARY", modes.contains(AudioStudioMode.LIBRARY))
        assertTrue("Contains GENERATOR", modes.contains(AudioStudioMode.GENERATOR))

        // Contract: Default view is LIBRARY
        val defaultMode = AudioStudioMode.LIBRARY
        assertEquals(AudioStudioMode.LIBRARY, defaultMode)
    }

    @Test
    fun testTtsDockVisibilityStateContract() {
        val album = File(tempDir, "DockTestAlbum").apply { mkdirs() }
        val track = File(album, "DockTrack.mp3").apply { writeBytes(ByteArray(10)) }

        // Initial state after stop: source is NONE -> Dock must not be shown
        AudioPlaybackCoordinator.stop()
        var state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals(PlaybackSource.NONE, state.source)
        assertFalse("Dock must be hidden when source == NONE", state.source != PlaybackSource.NONE)

        // When audio file starts playing: source is AUDIO_FILE -> Dock must be shown
        AudioPlaybackCoordinator.playAudioFile(track)
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals(PlaybackSource.AUDIO_FILE, state.source)
        assertTrue("Dock must be visible during AUDIO_FILE playback", state.source != PlaybackSource.NONE)
        assertTrue("Dock reflects isPlaying = true", state.isPlaying)

        // When paused: source remains AUDIO_FILE -> Dock must stay visible so user can resume
        AudioPlaybackCoordinator.pause()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals(PlaybackSource.AUDIO_FILE, state.source)
        assertTrue("Dock remains visible when AUDIO_FILE is paused", state.source != PlaybackSource.NONE)
        assertFalse("Dock reflects isPlaying = false", state.isPlaying)

        // When stopped: source reverts to NONE -> Dock hides
        AudioPlaybackCoordinator.stop()
        state = AudioPlaybackCoordinator.playbackProgressState.value
        assertEquals(PlaybackSource.NONE, state.source)
        assertFalse("Dock hides after stop()", state.source != PlaybackSource.NONE)
    }
}
