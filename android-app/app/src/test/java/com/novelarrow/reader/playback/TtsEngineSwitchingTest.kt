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

    @Test
    fun testEmbeddedSherpaLocalEngineSerialization() {
        val settings = ReaderSettings(
            selectedEngineType = com.novelarrow.reader.data.repository.SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL,
            selectedVoiceName = "piper_en_us_lessac_medium"
        )
        val serialized = json.encodeToString(ReaderSettings.serializer(), settings)
        assertTrue(serialized.contains("SHERPA_LOCAL"))

        val deserialized = json.decodeFromString(ReaderSettings.serializer(), serialized)
        assertEquals("SHERPA_LOCAL", deserialized.selectedEngineType)
        assertEquals("piper_en_us_lessac_medium", deserialized.selectedVoiceName)
    }

    @Test
    fun testLocalEngineActivationMatching() {
        fun isLocalNeuralActive(engineType: String): Boolean {
            return engineType == com.novelarrow.reader.data.repository.SettingsRepository.ENGINE_TYPE_SHERPA_LOCAL ||
                    engineType.equals("PIPER", ignoreCase = true) ||
                    engineType.equals("KOKORO", ignoreCase = true) ||
                    engineType.equals("KITTEN", ignoreCase = true)
        }

        assertTrue(isLocalNeuralActive("SHERPA_LOCAL"))
        assertTrue(isLocalNeuralActive("PIPER"))
        assertTrue(isLocalNeuralActive("piper"))
        assertTrue(isLocalNeuralActive("KOKORO"))
        assertTrue(isLocalNeuralActive("kokoro"))
        assertTrue(isLocalNeuralActive("KITTEN"))
        assertTrue(isLocalNeuralActive("kitten"))
        assertFalse(isLocalNeuralActive("SYSTEM"))
        assertFalse(isLocalNeuralActive("GOOGLE"))
    }

    @Test
    fun testSherpaOnnxPiperModelUrlsIntegrity() {
        val piperVoices = NeuralVoiceCatalog.PIPER_VOICES
        assertTrue("Expected non-empty piper voices", piperVoices.isNotEmpty())

        for (v in piperVoices) {
            assertTrue(
                "Voice ${v.id} should use official csukuangfj sherpa-onnx repository URL",
                v.onnxDownloadUrl.contains("huggingface.co/csukuangfj/")
            )
            assertTrue(
                "Voice ${v.id} should have resolve/main in download URL",
                v.onnxDownloadUrl.contains("/resolve/main/")
            )
            assertTrue(
                "Voice ${v.id} modelFileName must end with .onnx",
                v.modelFileName.endsWith(".onnx")
            )
        }
    }

    @Test
    fun testMultiEngineCatalogResolution() {
        val kokoroVoices = NeuralVoiceCatalog.getVoicesForEngine("KOKORO")
        assertTrue(kokoroVoices.isNotEmpty())
        assertEquals("KOKORO", kokoroVoices.first().engine)

        val kittenVoices = NeuralVoiceCatalog.getVoicesForEngine("KITTEN")
        assertTrue(kittenVoices.isNotEmpty())
        assertEquals("KITTEN", kittenVoices.first().engine)

        val piperVoices = NeuralVoiceCatalog.getVoicesForEngine("PIPER")
        assertTrue(piperVoices.isNotEmpty())
        assertEquals("PIPER", piperVoices.first().engine)
    }

    @Test
    fun testKittenVoicesIntegrity() {
        val kittenVoices = NeuralVoiceCatalog.KITTEN_VOICES
        assertTrue("Expected 8 Kitten voices", kittenVoices.size == 8)

        val speakerIds = mutableSetOf<Int>()
        for (v in kittenVoices) {
            assertEquals("KITTEN", v.engine)
            assertTrue("Model URL should point to csukuangfj sherpa repository", v.onnxDownloadUrl.contains("csukuangfj/kitten-nano-en-v0_1-fp16"))
            assertEquals("voices.bin required for KittenTTS", true, v.voicesDownloadUrl?.contains("voices.bin"))
            assertEquals("tokens.txt required for KittenTTS", true, v.tokensDownloadUrl?.contains("tokens.txt"))
            assertTrue("Speaker ID should be between 0 and 7", v.speakerId in 0..7)
            speakerIds.add(v.speakerId)
        }
        assertEquals("Each Kitten voice should have a unique speaker ID", 8, speakerIds.size)
    }

    @Test
    fun testKokoroVoicesIntegrity() {
        val kokoroVoices = NeuralVoiceCatalog.KOKORO_VOICES
        assertTrue("Expected non-empty Kokoro voices", kokoroVoices.isNotEmpty())

        for (v in kokoroVoices) {
            assertEquals("KOKORO", v.engine)
            assertTrue("Model URL should point to csukuangfj kokoro repository", v.onnxDownloadUrl.contains("csukuangfj/kokoro-int8-multi-lang-v1_0"))
            assertEquals("voices.bin required for Kokoro", true, v.voicesDownloadUrl?.contains("voices.bin"))
            assertEquals("tokens.txt required for Kokoro", true, v.tokensDownloadUrl?.contains("tokens.txt"))
            assertEquals("lexicon required for Kokoro", true, v.lexiconDownloadUrl?.contains("lexicon-us-en.txt"))
            assertTrue("Speaker ID should be >= 0", v.speakerId >= 0)
        }
    }
}

