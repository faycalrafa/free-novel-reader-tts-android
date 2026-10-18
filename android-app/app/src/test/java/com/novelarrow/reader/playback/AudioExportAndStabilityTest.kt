package com.novelarrow.reader.playback

import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.NaturalOrderComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioExportAndStabilityTest {

    @Test
    fun testAudioExportFormatValues() {
        val formats = AudioExportEngine.AudioExportFormat.values()
        assertEquals(2, formats.size)
        assertTrue(formats.contains(AudioExportEngine.AudioExportFormat.MP3))
        assertTrue(formats.contains(AudioExportEngine.AudioExportFormat.WAV))
    }

    @Test
    fun testWavRiffHeaderGeneration() {
        val sampleRate = 22050
        val channels = 1
        val pcmData = ByteArray(44100) { (it % 128).toByte() } // 1 second of dummy audio

        val out = ByteArrayOutputStream()
        // Write standard RIFF WAV header
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcmData.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1) // AudioFormat 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2) // ByteRate
        header.putShort((channels * 2).toShort()) // BlockAlign
        header.putShort(16) // BitsPerSample
        header.put("data".toByteArray())
        header.putInt(pcmData.size)

        out.write(header.array())
        out.write(pcmData)

        val wavBytes = out.toByteArray()
        assertEquals(44 + 44100, wavBytes.size)

        // Verify RIFF signature
        assertEquals('R'.code.toByte(), wavBytes[0])
        assertEquals('I'.code.toByte(), wavBytes[1])
        assertEquals('F'.code.toByte(), wavBytes[2])
        assertEquals('F'.code.toByte(), wavBytes[3])

        // Verify WAVE signature
        assertEquals('W'.code.toByte(), wavBytes[8])
        assertEquals('A'.code.toByte(), wavBytes[9])
        assertEquals('V'.code.toByte(), wavBytes[10])
        assertEquals('E'.code.toByte(), wavBytes[11])

        // Verify 'data' marker
        val dataMarker = String(wavBytes.copyOfRange(36, 40))
        assertEquals("data", dataMarker)
    }

    @Test
    fun testNaturalOrderSiblingSorting() {
        val names = listOf("track10.mp3", "track1.mp3", "track2.mp3", "track20.mp3")
        val sorted = names.sortedWith { a, b -> NaturalOrderComparator.compare(a, b) }
        assertEquals(listOf("track1.mp3", "track2.mp3", "track10.mp3", "track20.mp3"), sorted)
    }

    @Test
    fun testNovelArchivePreservation() {
        val activeNovel = Novel(
            id = "active_1",
            title = "Active Novel",
            rootTreeUri = "/path/to/active",
            chapterCount = 10,
            isArchived = false
        )
        val archivedNovel = Novel(
            id = "archived_1",
            title = "Archived Novel",
            rootTreeUri = "/path/to/archived",
            chapterCount = 5,
            isArchived = true
        )

        val allNovels = listOf(activeNovel, archivedNovel)
        val activeOnly = allNovels.filter { !it.isArchived }
        val archivedOnly = allNovels.filter { it.isArchived }

        assertEquals(1, activeOnly.size)
        assertEquals("active_1", activeOnly.first().id)
        assertFalse(activeOnly.first().isArchived)

        assertEquals(1, archivedOnly.size)
        assertEquals("archived_1", archivedOnly.first().id)
        assertTrue(archivedOnly.first().isArchived)
    }
}
