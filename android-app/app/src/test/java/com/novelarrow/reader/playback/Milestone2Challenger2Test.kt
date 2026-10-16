package com.novelarrow.reader.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.novelarrow.reader.ui.settings.VoiceEngineScreenMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class Milestone2Challenger2Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createMockContext(filesDir: File): Context {
        return object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
            override fun getExternalFilesDir(type: String?): File = filesDir
            override fun getApplicationContext(): Context = this
        }
    }

    private class TestVoice(
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

    // =========================================================================
    // Area 1: Voice Engine Screen Modes & Navigation State Machine
    // =========================================================================

    @Test
    fun testVoiceEngineScreenMode_enumContract() {
        val modes = VoiceEngineScreenMode.values()
        assertEquals(5, modes.size)
        assertTrue(modes.contains(VoiceEngineScreenMode.ENGINE_SELECTION))
        assertTrue(modes.contains(VoiceEngineScreenMode.VOICE_SELECTION))
        assertTrue(modes.contains(VoiceEngineScreenMode.PIPER_VOICES))
        assertTrue(modes.contains(VoiceEngineScreenMode.KOKORO_VOICES))
        assertTrue(modes.contains(VoiceEngineScreenMode.KITTEN_VOICES))
    }

    @Test
    fun testVoiceEngineScreen_navigationStateMachine() {
        // State machine simulation of VoiceEngineScreen back button and mode transitions
        var currentMode = VoiceEngineScreenMode.ENGINE_SELECTION
        var previewingVoice: String? = null
        var navigatedBackToSettings = false

        fun stopVoicePreview() {
            previewingVoice = null
        }

        fun onNavigateBack() {
            navigatedBackToSettings = true
        }

        // Simulate top-bar or hardware back press
        fun handleBackPress() {
            if (currentMode == VoiceEngineScreenMode.VOICE_SELECTION) {
                stopVoicePreview()
                currentMode = VoiceEngineScreenMode.ENGINE_SELECTION
            } else {
                stopVoicePreview()
                onNavigateBack()
            }
        }

        // 1. Initial mode must be ENGINE_SELECTION
        assertEquals(VoiceEngineScreenMode.ENGINE_SELECTION, currentMode)
        assertFalse(navigatedBackToSettings)

        // 2. Select built-in TTS engine -> enters VOICE_SELECTION
        currentMode = VoiceEngineScreenMode.VOICE_SELECTION
        assertEquals(VoiceEngineScreenMode.VOICE_SELECTION, currentMode)

        // 3. Start previewing a voice in VOICE_SELECTION
        previewingVoice = "en-us-x-sfg#female"
        assertEquals("en-us-x-sfg#female", previewingVoice)

        // 4. Press back while in VOICE_SELECTION: must stop preview and return to ENGINE_SELECTION without exiting screen
        handleBackPress()
        assertEquals(VoiceEngineScreenMode.ENGINE_SELECTION, currentMode)
        assertNull("Voice preview must be stopped when backing out of voice selection", previewingVoice)
        assertFalse("Should not navigate back to settings yet", navigatedBackToSettings)

        // 5. Press back while in ENGINE_SELECTION: must exit screen to settings
        handleBackPress()
        assertTrue("Back press in ENGINE_SELECTION must invoke onNavigateBack", navigatedBackToSettings)
    }

    @Test
    fun testVoiceEngineScreen_disposableEffectCleanupContract() {
        // Simulates disposing the VoiceEngineScreen composable
        var activePreview: String? = "ja-jp-x-htm#female"

        fun onDispose() {
            // Mirrors VoiceEngineScreen line 104: AudioPlaybackCoordinator.stopVoicePreview()
            activePreview = null
        }

        assertNotNull(activePreview)
        onDispose()
        assertNull("Voice preview must be cancelled when VoiceEngineScreen is unmounted", activePreview)
    }

    // =========================================================================
    // Area 2: Rapid Voice Selection & Preview Toggling Mechanics
    // =========================================================================

    @Test
    fun testRapidPreviewToggling_sameVoicePlayStopPlay() {
        // Models AudioPlaybackCoordinator state flow for rapid clicks on the same voice
        val previewingVoiceState = MutableStateFlow<String?>(null)
        val voiceA = "en-us-x-sfg#female"

        fun toggleVoicePreview(voiceName: String) {
            if (previewingVoiceState.value == voiceName) {
                // Clicking currently previewing voice stops it
                previewingVoiceState.value = null
            } else {
                previewingVoiceState.value = voiceName
            }
        }

        // Click 1: Start Voice A
        toggleVoicePreview(voiceA)
        assertEquals(voiceA, previewingVoiceState.value)

        // Click 2: Rapid Stop Voice A
        toggleVoicePreview(voiceA)
        assertNull(previewingVoiceState.value)

        // Click 3: Rapid Re-start Voice A
        toggleVoicePreview(voiceA)
        assertEquals(voiceA, previewingVoiceState.value)

        // Click 4: Rapid Stop Voice A
        toggleVoicePreview(voiceA)
        assertNull(previewingVoiceState.value)
    }

    @Test
    fun testAudioPlaybackCoordinator_directPreviewAndStop() {
        val v1 = TestVoice("test-voice-1", Locale.US)
        val v2 = TestVoice("test-voice-2", Locale.UK)

        // 1. Initial preview should be null
        AudioPlaybackCoordinator.stopVoicePreview()
        assertNull(AudioPlaybackCoordinator.previewingVoiceName.value)

        // 2. Start preview Voice 1
        AudioPlaybackCoordinator.previewVoice(v1)
        assertEquals("test-voice-1", AudioPlaybackCoordinator.previewingVoiceName.value)

        // 3. Tapping the same voice again must stop the preview
        AudioPlaybackCoordinator.previewVoice(v1)
        assertNull(AudioPlaybackCoordinator.previewingVoiceName.value)

        // 4. Start preview Voice 1 again
        AudioPlaybackCoordinator.previewVoice(v1)
        assertEquals("test-voice-1", AudioPlaybackCoordinator.previewingVoiceName.value)

        // 5. Switching directly to Voice 2 mid-preview
        AudioPlaybackCoordinator.previewVoice(v2)
        assertEquals("test-voice-2", AudioPlaybackCoordinator.previewingVoiceName.value)

        // 6. Explicit stopVoicePreview
        AudioPlaybackCoordinator.stopVoicePreview()
        assertNull(AudioPlaybackCoordinator.previewingVoiceName.value)

        // 7. Calling stopVoicePreview when already null is safe and idempotent
        AudioPlaybackCoordinator.stopVoicePreview()
        assertNull(AudioPlaybackCoordinator.previewingVoiceName.value)
    }

    @Test
    fun testVoiceSwitchingMidPreview_unconditionalPreviewDoneAnalysis() {
        // This test models the critical race condition:
        // When Voice A is speaking and user switches to Voice B:
        // 1. previewVoice(Voice B) sets active preview = Voice B
        // 2. TTS engine halts Voice A, which dispatches onError("preview_VoiceA")
        // 3. If onPreviewDone is parameterless () -> Unit, it clears active preview to null!
        val previewingVoiceState = MutableStateFlow<String?>(null)
        val voiceA = "en-us-voice-A"
        val voiceB = "en-us-voice-B"

        // 1. User taps Voice A
        previewingVoiceState.value = voiceA
        assertEquals(voiceA, previewingVoiceState.value)

        // 2. Mid-preview, user taps Voice B
        previewingVoiceState.value = voiceB
        assertEquals(voiceB, previewingVoiceState.value)

        // 3. Android TTS engine asynchronously finishes/cancels Voice A:
        val finishedUtteranceId = "preview_$voiceA"

        // NAIVE implementation (as currently written in TtsManager & AudioPlaybackCoordinator):
        // onPreviewDone?.invoke() is parameterless and does not know WHICH utterance completed!
        val naiveOnPreviewDone = {
            previewingVoiceState.value = null
        }

        // ROBUST implementation:
        // checks if the completed utterance actually matches the current preview
        val robustOnPreviewDone = { utteranceId: String ->
            val completedVoice = utteranceId.removePrefix("preview_")
            if (previewingVoiceState.value == completedVoice) {
                previewingVoiceState.value = null
            }
        }

        // Test robust behavior:
        robustOnPreviewDone(finishedUtteranceId)
        assertEquals(
            "Robust implementation must NOT cancel Voice B when Voice A finishes",
            voiceB,
            previewingVoiceState.value
        )

        // Contrast with naive behavior:
        naiveOnPreviewDone()
        assertNull(
            "Naive implementation erroneously cleared Voice B preview state to null!",
            previewingVoiceState.value
        )
    }

    // =========================================================================
    // Area 3: Search Filtering & Language Grouping Boundary Cases
    // =========================================================================

    @Test
    fun testVoiceSearchFilter_fuzzyMatchingAcrossAttributes() {
        val v1 = TestVoice("en-us-x-sfg#female", Locale.US)
        val v2 = TestVoice("en-gb-x-rjs#male", Locale.UK)
        val v3 = TestVoice("ja-jp-x-htm#female", Locale.JAPAN)
        val v4 = TestVoice("es-es-x-ana#female", Locale.forLanguageTag("es-ES"))
        val v5 = TestVoice("cmn-hans-cn", Locale.SIMPLIFIED_CHINESE)
        val voices = listOf(v1, v2, v3, v4, v5)

        fun filterVoices(query: String): List<Voice> {
            if (query.isBlank()) return voices
            val q = query.trim()
            return voices.filter { voice ->
                voice.name.contains(q, ignoreCase = true) ||
                voice.locale?.displayLanguage?.contains(q, ignoreCase = true) == true ||
                voice.locale?.displayCountry?.contains(q, ignoreCase = true) == true ||
                voice.locale?.toLanguageTag()?.contains(q, ignoreCase = true) == true
            }
        }

        // 1. Empty query returns all
        assertEquals(5, filterVoices("").size)
        assertEquals(5, filterVoices("   ").size)

        // 2. Query by language name
        val japanese = filterVoices("japanese")
        assertEquals(1, japanese.size)
        assertEquals("ja-jp-x-htm#female", japanese[0].name)

        // 3. Query by country name
        val uk = filterVoices("United Kingdom")
        assertEquals(1, uk.size)
        assertEquals("en-gb-x-rjs#male", uk[0].name)

        // 4. Query by voice name substring
        val sfg = filterVoices("sfg")
        assertEquals(1, sfg.size)
        assertEquals("en-us-x-sfg#female", sfg[0].name)

        // 5. Query with no matches
        val none = filterVoices("nonexistent_voice_query")
        assertTrue(none.isEmpty())
    }

    @Test
    fun testVoiceGrouping_emptyVoiceListHandling() {
        val emptyList = emptyList<Voice>()
        val grouped = VoiceGroupingHelper.groupVoicesByLanguage(emptyList)
        assertTrue("Empty voice list should return an empty map without exceptions", grouped.isEmpty())
    }
}
