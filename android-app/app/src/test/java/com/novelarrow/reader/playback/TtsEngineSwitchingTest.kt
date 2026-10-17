package com.novelarrow.reader.playback

import com.novelarrow.reader.data.model.NeuralVoiceCatalog
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.repository.SharedTtsSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineSwitchingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testReaderSettingsEnginePackageSerialization() {
        val settings = ReaderSettings(
            selectedTtsEnginePackage = "rhasspy.piper",
            selectedEngineType = "PIPER"
        )
        val serialized = json.encodeToString(ReaderSettings.serializer(), settings)
        assertTrue(serialized.contains("rhasspy.piper"))

        val deserialized = json.decodeFromString(ReaderSettings.serializer(), serialized)
        assertEquals("rhasspy.piper", deserialized.selectedTtsEnginePackage)
        assertEquals("PIPER", deserialized.selectedEngineType)
    }

    @Test
    fun testSharedTtsSettingsEnginePackageSerialization() {
        val shared = SharedTtsSettings(
            selectedTtsEnginePackage = "com.k2fsa.sherpa.onnx.tts",
            selectedEngineType = "KOKORO"
        )
        val serialized = json.encodeToString(SharedTtsSettings.serializer(), shared)
        assertTrue(serialized.contains("com.k2fsa.sherpa.onnx.tts"))

        val deserialized = json.decodeFromString(SharedTtsSettings.serializer(), serialized)
        assertEquals("com.k2fsa.sherpa.onnx.tts", deserialized.selectedTtsEnginePackage)
        assertEquals("KOKORO", deserialized.selectedEngineType)
    }

    @Test
    fun testDefaultSettingsHaveNullEnginePackage() {
        val settings = ReaderSettings()
        assertNull(settings.selectedTtsEnginePackage)

        val shared = SharedTtsSettings()
        assertNull(shared.selectedTtsEnginePackage)
    }

    @Test
    fun testEngineCompatibilityRules() {
        val googleEngine = "com.google.android.tts"
        val piperEngine = "rhasspy.piper"
        val sherpaEngine = "com.k2fsa.sherpa.onnx.tts"
        val samsungEngine = "com.samsung.SMT"

        fun isCompatible(engineType: String, activeEngine: String): Boolean {
            return when (engineType.uppercase()) {
                "PIPER" -> activeEngine.contains("piper", ignoreCase = true) || activeEngine.contains("sherpa", ignoreCase = true)
                "KOKORO", "KITTEN" -> activeEngine.contains("sherpa", ignoreCase = true) || activeEngine.contains("onnx", ignoreCase = true)
                else -> true
            }
        }

        // Google TTS is NOT compatible with offline neural ONNX models
        assertFalse(isCompatible("PIPER", googleEngine))
        assertFalse(isCompatible("KOKORO", googleEngine))
        assertFalse(isCompatible("KITTEN", googleEngine))

        // Samsung TTS is NOT compatible with offline neural ONNX models
        assertFalse(isCompatible("PIPER", samsungEngine))
        assertFalse(isCompatible("KOKORO", samsungEngine))

        // Piper TTS is compatible with Piper
        assertTrue(isCompatible("PIPER", piperEngine))

        // Sherpa ONNX is compatible with all ONNX neural models
        assertTrue(isCompatible("PIPER", sherpaEngine))
        assertTrue(isCompatible("KOKORO", sherpaEngine))
        assertTrue(isCompatible("KITTEN", sherpaEngine))

        // System standard voices are compatible with any engine
        assertTrue(isCompatible("SYSTEM", googleEngine))
        assertTrue(isCompatible("SYSTEM", samsungEngine))
    }

    @Test
    fun testNeuralVoiceModelNameMapping() {
        val danny = NeuralVoiceCatalog.getVoiceById("piper_en_us_danny_low")
        org.junit.Assert.assertNotNull(danny)
        assertEquals("en_US-danny-low.onnx", danny?.modelFileName)
        assertEquals("en_US-danny-low", danny?.modelFileName?.substringBefore("."))

        val alan = NeuralVoiceCatalog.getVoiceById("piper_en_gb_alan_medium")
        org.junit.Assert.assertNotNull(alan)
        assertEquals("en_GB-alan-medium.onnx", alan?.modelFileName)
        assertEquals("en_GB-alan-medium", alan?.modelFileName?.substringBefore("."))
    }
}
