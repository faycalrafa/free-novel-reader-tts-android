package com.novelarrow.reader.playback

import android.content.Context
import android.content.ContextWrapper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Milestone2Challenger1Test {

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

    private fun createMockContext(): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.novelarrow.reader"
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"))
        }
    }

    // =========================================================================
    // SECTION 1: VoiceGroupingHelper Stress Tests
    // =========================================================================

    @Test
    fun testVoiceGrouping_emptyVoiceList() {
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(emptyList())
        assertNotNull(grouped)
        assertTrue(grouped.isEmpty())
        assertEquals(0, grouped.size)
    }

    @Test
    fun testVoiceGrouping_nullLocale() {
        val voiceNull1 = SimulatedVoice("null-loc-1", null)
        val voiceNull2 = SimulatedVoice("null-loc-2", null)
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(listOf(voiceNull1, voiceNull2))

        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey("Other"))
        val otherVoices = grouped["Other"]
        assertNotNull(otherVoices)
        assertEquals(2, otherVoices!!.size)
        assertEquals("null-loc-1", otherVoices[0].name)
        assertEquals("null-loc-2", otherVoices[1].name)
    }

    @Test
    fun testVoiceGrouping_blankAndWhitespaceLocales() {
        val voiceEmpty = SimulatedVoice("empty-loc", Locale.forLanguageTag(""))
        val voiceBlank = SimulatedVoice("blank-loc", Locale("", ""))
        val voiceWhitespace = SimulatedVoice("whitespace-loc", Locale("   ", "   "))

        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(listOf(voiceEmpty, voiceBlank, voiceWhitespace))
        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey("Other"))
        assertEquals(3, grouped["Other"]?.size)
    }

    @Test
    fun testVoiceGrouping_irregularLocaleCodes() {
        val vUnd = SimulatedVoice("v-und", Locale("und"))
        val vPrivate = SimulatedVoice("v-qaa", Locale.forLanguageTag("qaa"))
        val vKlingon = SimulatedVoice("v-klingon", Locale.forLanguageTag("x-klingon"))
        val vFictional = SimulatedVoice("v-xx-YY", Locale("xx", "YY"))
        val vIllFormed = SimulatedVoice("v-12345", Locale.forLanguageTag("12345"))
        val vHant = SimulatedVoice("v-zh-Hant-HK", Locale.forLanguageTag("zh-Hant-HK"))
        val vPosix = SimulatedVoice("v-posix", Locale.forLanguageTag("en-US-u-va-posix"))

        val voices = listOf(vUnd, vPrivate, vKlingon, vFictional, vIllFormed, vHant, vPosix)
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(voices)

        assertNotNull(grouped)
        assertTrue(grouped.isNotEmpty())

        // Ensure total count of voices across all groups matches the input size
        val totalGroupedVoices = grouped.values.sumOf { it.size }
        assertEquals(voices.size, totalGroupedVoices)

        // Case-insensitivity test for keys
        val keys = grouped.keys.toList()
        for (i in 0 until keys.size - 1) {
            assertTrue(
                "Keys should be sorted case-insensitively: ${keys[i]} vs ${keys[i + 1]}",
                String.CASE_INSENSITIVE_ORDER.compare(keys[i], keys[i + 1]) <= 0
            )
        }
    }

    @Test
    fun testVoiceGrouping_multipleVoicesSameLocale() {
        val usVoices = (1..10).map { i ->
            SimulatedVoice("en-us-voice-$i", Locale.US)
        }
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(usVoices)

        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey("English"))
        assertEquals(10, grouped["English"]?.size)
        for (i in 1..10) {
            assertTrue(grouped["English"]!!.any { it.name == "en-us-voice-$i" })
        }
    }

    @Test
    fun testVoiceGrouping_multipleVoicesSameNameSameLocale() {
        // Edge case: Multiple voices returning identical voice.name
        val v1 = SimulatedVoice("duplicate-voice-name", Locale.US, Voice.QUALITY_NORMAL)
        val v2 = SimulatedVoice("duplicate-voice-name", Locale.US, Voice.QUALITY_VERY_HIGH)
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(listOf(v1, v2))

        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey("English"))
        assertEquals(2, grouped["English"]?.size)
        // Both distinct instances must be preserved
        assertEquals(Voice.QUALITY_NORMAL, grouped["English"]!![0].quality)
        assertEquals(Voice.QUALITY_VERY_HIGH, grouped["English"]!![1].quality)
    }

    @Test
    fun testVoiceGrouping_multipleVoicesSameNameDifferentLocales() {
        // Edge case: Identical voice names across different locales
        val vEn = SimulatedVoice("standard-voice", Locale.US)
        val vFr = SimulatedVoice("standard-voice", Locale.FRANCE)
        val vDe = SimulatedVoice("standard-voice", Locale.GERMANY)
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(listOf(vEn, vFr, vDe))

        assertEquals(3, grouped.size)
        assertTrue(grouped.containsKey("English"))
        assertTrue(grouped.containsKey("French"))
        assertTrue(grouped.containsKey("German"))
        assertEquals(1, grouped["English"]?.size)
        assertEquals(1, grouped["French"]?.size)
        assertEquals(1, grouped["German"]?.size)
    }

    @Test
    fun testVoiceGrouping_largeScaleStress() {
        val locales = listOf(
            Locale.US, Locale.UK, Locale.JAPAN, Locale.CHINA, Locale.FRANCE,
            Locale.GERMANY, Locale.ITALY, Locale.KOREA, Locale.forLanguageTag("es-ES"),
            Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("ru-RU"),
            Locale.forLanguageTag("ar-SA"), Locale.forLanguageTag("hi-IN"),
            null
        )
        val largeList = mutableListOf<Voice>()
        for (i in 1..1000) {
            val loc = locales[i % locales.size]
            largeList.add(SimulatedVoice("voice-stress-$i", loc, i % 5, i % 5))
        }

        val startTime = System.currentTimeMillis()
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(largeList)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(1000, grouped.values.sumOf { it.size })
        assertTrue("Large grouping must finish rapidly (< 100ms)", elapsed < 100)
    }

    // =========================================================================
    // SECTION 2: Sample Preview Phrase Generation Tests
    // =========================================================================

    @Test
    fun testSampleTextForLocale_edgeCasesAndFallbacks() {
        // Null locale
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(null))

        // Empty and blank
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale("")))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale("   ")))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale.forLanguageTag("")))

        // 3-letter ISO language codes
        assertEquals("This is a voice sample for BookReader.", TtsManager.getSampleTextForLocale(Locale("eng")))
        assertEquals("これは音声プレビューです。", TtsManager.getSampleTextForLocale(Locale("jpn")))
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale("zho")))
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale("chi")))
        assertEquals("Esta es una muestra de voz.", TtsManager.getSampleTextForLocale(Locale("spa")))
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale("fra")))
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale("fre")))
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale("deu")))
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale("ger")))

        // Uppercase and mixed case language tags
        assertEquals("This is a voice sample for BookReader.", TtsManager.getSampleTextForLocale(Locale("EN", "US")))
        assertEquals("これは音声プレビューです。", TtsManager.getSampleTextForLocale(Locale("JA", "JP")))
        assertEquals("这是语音预览示例。", TtsManager.getSampleTextForLocale(Locale("ZH", "CN")))
        assertEquals("Esta es una muestra de voz.", TtsManager.getSampleTextForLocale(Locale("ES", "ES")))
        assertEquals("Ceci est un échantillon vocal.", TtsManager.getSampleTextForLocale(Locale("FR", "FR")))
        assertEquals("Dies ist ein Sprachbeispiel.", TtsManager.getSampleTextForLocale(Locale("DE", "DE")))

        // Arabic sample phrases
        assertEquals("هذا نموذج صوتي لتطبيق قارئ الروايات.", TtsManager.getSampleTextForLocale(Locale("ar")))
        assertEquals("هذا نموذج صوتي لتطبيق قارئ الروايات.", TtsManager.getSampleTextForLocale(Locale("ara")))

        // Unmatched/unsupported languages fallback cleanly to default sentence
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale.ITALIAN))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale.KOREAN))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale("ru")))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale("pt")))
        assertEquals("Testing Text to Speech voice preview.", TtsManager.getSampleTextForLocale(Locale("hi")))
    }

    // =========================================================================
    // SECTION 3: Utterance Listener Filtering & Isolation Tests
    // =========================================================================

    @Test
    fun testUtteranceListener_previewIsolationFromReaderCallbacks() {
        val context = createMockContext()

        var onInitInvoked = false
        val manager = TtsManager(context) { success ->
            onInitInvoked = true
        }

        val paragraphStartCalls = mutableListOf<Int>()
        val paragraphDoneCalls = mutableListOf<Int>()
        val errorCalls = mutableListOf<Pair<Int, String>>()
        var previewDoneInvoked = false

        manager.onParagraphStart = { index -> paragraphStartCalls.add(index) }
        manager.onParagraphDone = { index -> paragraphDoneCalls.add(index) }
        manager.onError = { index, err -> errorCalls.add(index to err) }
        manager.onPreviewDone = { previewDoneInvoked = true }

        // Use reflection to invoke setupProgressListener
        val setupMethod = TtsManager::class.java.getDeclaredMethod("setupProgressListener")
        setupMethod.isAccessible = true

        // Capture the UtteranceProgressListener passed to TextToSpeech
        // In JVM tests with isReturnDefaultValues = true, we can install a mock or inspect tts
        var capturedListener: UtteranceProgressListener? = null
        val ttsField = TtsManager::class.java.getDeclaredField("tts")
        ttsField.isAccessible = true

        // Create a fake TextToSpeech proxy or wrapper that intercepts setOnUtteranceProgressListener
        val mockTts = object : TextToSpeech(context, null) {
            override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?): Int {
                capturedListener = listener
                return TextToSpeech.SUCCESS
            }
        }
        ttsField.set(manager, mockTts)
        setupMethod.invoke(manager)

        assertNotNull("Listener should be registered on TTS", capturedListener)
        val listener = capturedListener!!

        // --- SUBTEST A: Preview utterance start ---
        listener.onStart("preview_en-us-x-sfg#female")
        assertTrue("Preview start must NOT trigger paragraphStart", paragraphStartCalls.isEmpty())
        assertTrue("Preview start must NOT trigger paragraphDone", paragraphDoneCalls.isEmpty())
        assertFalse("Preview start must NOT trigger previewDone", previewDoneInvoked)

        // --- SUBTEST B: Preview utterance done ---
        previewDoneInvoked = false
        listener.onDone("preview_en-us-x-sfg#female")
        assertTrue("Preview done must NOT trigger paragraphDone", paragraphDoneCalls.isEmpty())
        assertTrue("Preview done must trigger onPreviewDone", previewDoneInvoked)

        // --- SUBTEST C: Preview utterance error (deprecated overload) ---
        previewDoneInvoked = false
        listener.onError("preview_en-us-x-sfg#female")
        assertTrue("Preview error must NOT trigger reader onError", errorCalls.isEmpty())
        assertTrue("Preview error must trigger onPreviewDone to reset UI state", previewDoneInvoked)

        // --- SUBTEST D: Preview utterance error with error code ---
        previewDoneInvoked = false
        listener.onError("preview_en-us-x-sfg#female", -1)
        assertTrue("Preview error with code must NOT trigger reader onError", errorCalls.isEmpty())
        assertTrue("Preview error with code must trigger onPreviewDone", previewDoneInvoked)

        // --- SUBTEST E: Reader utterance isolation ---
        previewDoneInvoked = false
        // Single paragraph reading
        listener.onStart("5")
        assertEquals(listOf(5), paragraphStartCalls)
        assertFalse(previewDoneInvoked)

        listener.onDone("5")
        assertEquals(listOf(5), paragraphDoneCalls)
        assertFalse(previewDoneInvoked)

        // Reader error
        listener.onError("5", -2)
        assertEquals(listOf(5 to "Error code -2"), errorCalls)
        assertFalse(previewDoneInvoked)

        // --- SUBTEST F: Chunked utterance reading isolation ---
        paragraphStartCalls.clear()
        paragraphDoneCalls.clear()
        errorCalls.clear()
        previewDoneInvoked = false

        // Chunk 0 of 3 starts -> should trigger onParagraphStart(10)
        listener.onStart("chunk_10_0_3")
        assertEquals(listOf(10), paragraphStartCalls)

        // Chunk 1 of 3 starts -> should NOT trigger onParagraphStart
        listener.onStart("chunk_10_1_3")
        assertEquals(listOf(10), paragraphStartCalls)

        // Chunk 0 of 3 done -> should NOT trigger onParagraphDone
        listener.onDone("chunk_10_0_3")
        assertTrue(paragraphDoneCalls.isEmpty())

        // Chunk 1 of 3 done -> should NOT trigger onParagraphDone
        listener.onDone("chunk_10_1_3")
        assertTrue(paragraphDoneCalls.isEmpty())

        // Chunk 2 of 3 done -> final chunk, should trigger onParagraphDone(10)
        listener.onDone("chunk_10_2_3")
        assertEquals(listOf(10), paragraphDoneCalls)
        assertFalse("Reader chunks must never invoke previewDone", previewDoneInvoked)

        // --- SUBTEST G: Null and malformed utterance IDs ---
        listener.onStart(null)
        listener.onDone(null)
        listener.onStart("malformed_utterance")
        listener.onDone("malformed_utterance")
        // Verify no crashes occurred and reader state didn't get corrupted
        assertEquals(listOf(10), paragraphDoneCalls)
    }
}
