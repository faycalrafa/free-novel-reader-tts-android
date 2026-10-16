package com.novelarrow.reader.playback

import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VoiceGroupingTest {

    /**
     * Simulated Voice object extending Android's Voice class for JVM unit tests,
     * ensuring getters return realistic configured properties.
     */
    private class SimulatedVoice(
        private val customName: String,
        private val customLocale: Locale?,
        private val customQuality: Int = Voice.QUALITY_NORMAL,
        private val customLatency: Int = Voice.LATENCY_NORMAL,
        private val customRequiresNetwork: Boolean = false
    ) : Voice(customName, customLocale ?: Locale.US, customQuality, customLatency, customRequiresNetwork, emptySet()) {
        override fun getName(): String = customName
        override fun getLocale(): Locale? = customLocale
        override fun getQuality(): Int = customQuality
        override fun getLatency(): Int = customLatency
        override fun isNetworkConnectionRequired(): Boolean = customRequiresNetwork
    }

    @Test
    fun testGroupVoicesByLanguage_standardLocales() {
        val voiceUs = SimulatedVoice("en-us-x-sfg#female", Locale.US, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_LOW)
        val voiceUk = SimulatedVoice("en-gb-x-rjs#male", Locale.UK, Voice.QUALITY_HIGH, Voice.LATENCY_LOW)
        val voiceJa = SimulatedVoice("ja-jp-x-htm#female", Locale.JAPAN, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL)
        val voiceFr = SimulatedVoice("fr-fr-x-vlf#female", Locale.FRANCE, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL)
        val voiceEs = SimulatedVoice("es-es-x-ana#female", Locale.forLanguageTag("es-ES"), Voice.QUALITY_HIGH, Voice.LATENCY_LOW)
        val voiceDe = SimulatedVoice("de-de-x-karl#male", Locale.GERMANY, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_LOW)
        val voiceZh = SimulatedVoice("zh-cn-x-cmn#female", Locale.CHINA, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL)

        val voices = listOf(voiceUs, voiceUk, voiceJa, voiceFr, voiceEs, voiceDe, voiceZh)
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(voices)

        // Verify all 6 distinct language groups exist
        assertEquals(6, grouped.size)
        assertTrue(grouped.containsKey("English"))
        assertTrue(grouped.containsKey("Japanese"))
        assertTrue(grouped.containsKey("French"))
        assertTrue(grouped.containsKey("Spanish"))
        assertTrue(grouped.containsKey("German"))
        assertTrue(grouped.containsKey("Chinese"))

        // English group must contain both US and UK voices
        val englishVoices = grouped["English"]
        assertNotNull(englishVoices)
        assertEquals(2, englishVoices!!.size)
        assertTrue(englishVoices.any { it.name == "en-us-x-sfg#female" })
        assertTrue(englishVoices.any { it.name == "en-gb-x-rjs#male" })

        // Check single voices in other groups
        assertEquals(1, grouped["Japanese"]?.size)
        assertEquals("ja-jp-x-htm#female", grouped["Japanese"]?.first()?.name)

        assertEquals(1, grouped["French"]?.size)
        assertEquals("fr-fr-x-vlf#female", grouped["French"]?.first()?.name)

        assertEquals(1, grouped["Spanish"]?.size)
        assertEquals("es-es-x-ana#female", grouped["Spanish"]?.first()?.name)

        assertEquals(1, grouped["German"]?.size)
        assertEquals("de-de-x-karl#male", grouped["German"]?.first()?.name)

        assertEquals(1, grouped["Chinese"]?.size)
        assertEquals("zh-cn-x-cmn#female", grouped["Chinese"]?.first()?.name)
    }

    @Test
    fun testGroupVoicesByLanguage_fallbackWhenBlankOrNullLocale() {
        val voiceValid = SimulatedVoice("en-us-voice", Locale.US, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL)
        val voiceBlankLocale = SimulatedVoice("blank-locale-voice", Locale.forLanguageTag(""), Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL)
        val voiceNullLocale = SimulatedVoice("null-locale-voice", null, Voice.QUALITY_LOW, Voice.LATENCY_HIGH)

        val voices = listOf(voiceValid, voiceBlankLocale, voiceNullLocale)
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(voices)

        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey("English"))
        assertTrue(grouped.containsKey("Other"))

        assertEquals(1, grouped["English"]?.size)
        assertEquals(2, grouped["Other"]?.size)
        assertTrue(grouped["Other"]?.any { it.name == "blank-locale-voice" } == true)
        assertTrue(grouped["Other"]?.any { it.name == "null-locale-voice" } == true)
    }

    @Test
    fun testSamplePreviewPhraseGeneration_allRequiredLocales() {
        // 1. English
        assertEquals("This is a voice sample for BookReader.", TtsManager.getSampleTextForLocale(Locale.US))
        assertEquals("This is a voice sample for BookReader.", TtsManager.getSampleTextForLocale(Locale.UK))
        assertEquals("This is a voice sample for BookReader.", TtsManager.getSampleTextForLocale(Locale.ENGLISH))
        assertEquals("This is a voice sample for BookReader.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("en-AU")))

        // 2. Japanese
        assertEquals("これは音声プレビューです。", TtsManager.getSampleTextForLocale(Locale.JAPAN))
        assertEquals("これは音声プレビューです。", TtsManager.getSampleTextForLocale(Locale.JAPANESE))
        assertEquals("これは音声プレビューです。", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("ja")))

        // 3. Chinese
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale.CHINA))
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale.CHINESE))
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale.SIMPLIFIED_CHINESE))
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("zh-TW")))

        // 4. Spanish
        assertEquals("Esta es una muestra de voz.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("es-ES")))
        assertEquals("Esta es una muestra de voz.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("es-MX")))
        assertEquals("Esta es una muestra de voz.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("es")))

        // 5. French
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale.FRANCE))
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale.FRENCH))
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale.CANADA_FRENCH))
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("fr")))

        // 6. German
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale.GERMANY))
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale.GERMAN))
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("de-AT")))
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("de")))

        // 7. Unknown and null fallbacks
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("xyz")))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(null))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("")))
    }

    @Test
    fun testFormatQualityAndLatencyHelpers() {
        assertEquals("Very High", VoiceGroupingHelper.formatQuality(Voice.QUALITY_VERY_HIGH))
        assertEquals("High", VoiceGroupingHelper.formatQuality(Voice.QUALITY_HIGH))
        assertEquals("Standard", VoiceGroupingHelper.formatQuality(Voice.QUALITY_NORMAL))
        assertEquals("Low", VoiceGroupingHelper.formatQuality(Voice.QUALITY_LOW))
        assertEquals("Very Low", VoiceGroupingHelper.formatQuality(Voice.QUALITY_VERY_LOW))
        assertEquals("Standard", VoiceGroupingHelper.formatQuality(-999))

        assertEquals("Ultra Low Latency", VoiceGroupingHelper.formatLatency(Voice.LATENCY_VERY_LOW))
        assertEquals("Low Latency", VoiceGroupingHelper.formatLatency(Voice.LATENCY_LOW))
        assertEquals("Normal Latency", VoiceGroupingHelper.formatLatency(Voice.LATENCY_NORMAL))
        assertEquals("High Latency", VoiceGroupingHelper.formatLatency(Voice.LATENCY_HIGH))
        assertEquals("Very High Latency", VoiceGroupingHelper.formatLatency(Voice.LATENCY_VERY_HIGH))
        assertEquals("Normal Latency", VoiceGroupingHelper.formatLatency(999))
    }
}
