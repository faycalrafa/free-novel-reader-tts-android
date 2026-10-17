package com.novelarrow.reader.playback.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDurationCalculation() {
        val audioData22k = AudioData(
            samples = FloatArray(22050),
            sampleRate = 22050
        )
        assertEquals(1.0f, audioData22k.durationSeconds, 0.001f)
        assertEquals(1000L, audioData22k.durationMs)

        val audioData16k = AudioData(
            samples = FloatArray(8000),
            sampleRate = 16000
        )
        assertEquals(0.5f, audioData16k.durationSeconds, 0.001f)
        assertEquals(500L, audioData16k.durationMs)

        val emptyAudio = AudioData(
            samples = FloatArray(0),
            sampleRate = 22050
        )
        assertEquals(0.0f, emptyAudio.durationSeconds, 0.001f)
        assertEquals(0L, emptyAudio.durationMs)
    }

    @Test
    fun testToShortPcmClampingAndConversion() {
        val samples = floatArrayOf(
            0.0f,
            1.0f,
            -1.0f,
            0.5f,
            -0.5f,
            2.0f,   // Should be clamped to 1.0f
            -2.5f   // Should be clamped to -1.0f
        )
        val audioData = AudioData(samples, 22050)
        val shorts = audioData.toShortPcm()

        assertEquals(samples.size, shorts.size)
        assertEquals(0.toShort(), shorts[0])
        assertEquals(32767.toShort(), shorts[1])
        assertEquals((-32767).toShort(), shorts[2])
        assertEquals((0.5f * 32767).toInt().toShort(), shorts[3])
        assertEquals((-0.5f * 32767).toInt().toShort(), shorts[4])
        assertEquals(32767.toShort(), shorts[5]) // Clamped
        assertEquals((-32767).toShort(), shorts[6]) // Clamped
    }

    @Test
    fun testToPcm16Bytes() {
        val samples = floatArrayOf(0.0f, 1.0f, -1.0f)
        val audioData = AudioData(samples, 22050)
        val bytes = audioData.toPcm16Bytes()

        assertEquals(samples.size * 2, bytes.size)

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0.toShort(), bb.short)
        assertEquals(32767.toShort(), bb.short)
        assertEquals((-32767).toShort(), bb.short)
    }

    @Test
    fun testSaveAsWav() {
        val sampleRate = 22050
        val sampleCount = 4410 // 0.2s
        val samples = FloatArray(sampleCount) { i ->
            kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate).toFloat()
        }
        val audioData = AudioData(samples, sampleRate)

        val wavFile = File(tempFolder.root, "test_output.wav")
        audioData.saveAsWav(wavFile)

        assertTrue(wavFile.exists())
        assertEquals(44L + sampleCount * 2L, wavFile.length())

        val bytes = wavFile.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Verify RIFF header
        val riff = ByteArray(4)
        bb.get(riff)
        assertEquals("RIFF", String(riff))

        val chunkSize = bb.int
        assertEquals(bytes.size - 8, chunkSize)

        val wave = ByteArray(4)
        bb.get(wave)
        assertEquals("WAVE", String(wave))

        // Verify fmt chunk
        val fmt = ByteArray(4)
        bb.get(fmt)
        assertEquals("fmt ", String(fmt))

        val subchunk1Size = bb.int
        assertEquals(16, subchunk1Size)

        val audioFormat = bb.short
        assertEquals(1.toShort(), audioFormat) // PCM

        val numChannels = bb.short
        assertEquals(1.toShort(), numChannels)

        val wavSampleRate = bb.int
        assertEquals(sampleRate, wavSampleRate)

        val byteRate = bb.int
        assertEquals(sampleRate * 2, byteRate)

        val blockAlign = bb.short
        assertEquals(2.toShort(), blockAlign)

        val bitsPerSample = bb.short
        assertEquals(16.toShort(), bitsPerSample)

        // Verify data chunk
        val dataHeader = ByteArray(4)
        bb.get(dataHeader)
        assertEquals("data", String(dataHeader))

        val dataSize = bb.int
        assertEquals(sampleCount * 2, dataSize)
    }

    @Test
    fun testEqualsAndHashCode() {
        val audio1 = AudioData(floatArrayOf(0.1f, 0.2f), 22050)
        val audio2 = AudioData(floatArrayOf(0.1f, 0.2f), 22050)
        val audio3 = AudioData(floatArrayOf(0.1f, 0.3f), 22050)
        val audio4 = AudioData(floatArrayOf(0.1f, 0.2f), 16000)

        assertEquals(audio1, audio2)
        assertEquals(audio1.hashCode(), audio2.hashCode())
        assertNotEquals(audio1, audio3)
        assertNotEquals(audio1, audio4)
    }
}
