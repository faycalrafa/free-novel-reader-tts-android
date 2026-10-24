package com.novelarrow.reader.playback

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.round

internal class CoordinatorVoiceEngine(
    private val scope: CoroutineScope,
    private val ttsManagerProvider: () -> TtsManager?,
    private val settingsRepositoryProvider: () -> SettingsRepository?,
    private val onSpeakRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onVoicesUpdated: (List<Voice>) -> Unit
) {

    private val TAG = "CoordinatorVoiceEngine"

    var currentSpeechRate: Float = 1.0f
        private set
    var currentSpeechPitch: Float = 1.0f
        private set
    var currentVoiceName: String? = null
        private set

    private val _currentSpeechRateState = MutableStateFlow(1.0f)
    val currentSpeechRateState: StateFlow<Float> = _currentSpeechRateState.asStateFlow()

    private val _availableVoicesState = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoicesState: StateFlow<List<Voice>> = _availableVoicesState.asStateFlow()

    private val _previewingVoiceName = MutableStateFlow<String?>(null)
    val previewingVoiceName: StateFlow<String?> = _previewingVoiceName.asStateFlow()

    fun initSettings(rate: Float, pitch: Float, voiceName: String?) {
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName
        _currentSpeechRateState.value = rate
    }

    fun setLanguageOverride(locale: Locale?) {
        ttsManagerProvider()?.setLanguageOverride(locale)
    }

    fun cycleSpeechRate() {
        val standardSpeeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = currentSpeechRate
        val next = standardSpeeds.firstOrNull { it > current + 0.05f } ?: standardSpeeds.first()
        updateSpeechRate(next)
    }

    fun updateSpeechRate(rate: Float) {
        currentSpeechRate = rate
        _currentSpeechRateState.value = rate
        applyTtsSettings()
        onSpeakRequested()
        scope.launch(Dispatchers.IO) {
            settingsRepositoryProvider()?.setSpeechRate(rate)
        }
    }

    fun adjustSpeechRate(delta: Float) {
        val next = round((currentSpeechRate + delta) * 10f) / 10f
        updateSpeechRate(next.coerceIn(0.5f, 3.0f))
    }

    fun updateSpeechPitch(pitch: Float) {
        currentSpeechPitch = pitch
        applyTtsSettings()
        scope.launch(Dispatchers.IO) {
            settingsRepositoryProvider()?.setSpeechPitch(pitch)
        }
    }

    fun updateVoice(voiceName: String?) {
        currentVoiceName = voiceName
        applyTtsSettings()
        scope.launch(Dispatchers.IO) {
            settingsRepositoryProvider()?.setVoice(voiceName)
            settingsRepositoryProvider()?.setFavoriteVoice("en", voiceName)
        }
    }

    fun updateTtsSettings(rate: Float, pitch: Float, voiceName: String?) {
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName
        _currentSpeechRateState.value = rate
        applyTtsSettings()
        scope.launch(Dispatchers.IO) {
            settingsRepositoryProvider()?.setSpeechRate(rate)
            settingsRepositoryProvider()?.setSpeechPitch(pitch)
            settingsRepositoryProvider()?.setVoice(voiceName)
            settingsRepositoryProvider()?.setFavoriteVoice("en", voiceName)
        }
    }

    fun setVoice(voiceName: String?) {
        updateVoice(voiceName)
    }

    fun previewVoice(voice: Voice, sampleText: String? = null) {
        if (_previewingVoiceName.value == voice.name) {
            stopVoicePreview()
            return
        }

        onPauseRequested()

        _previewingVoiceName.value = voice.name
        val text = sampleText ?: TtsManager.getSampleTextForLocale(voice.locale)
        AppLogger.d(TAG, "Previewing voice ${voice.name} with sample text: \"$text\"")
        ttsManagerProvider()?.speakPreview(voice, text)
    }

    fun stopVoicePreview() {
        if (_previewingVoiceName.value != null) {
            _previewingVoiceName.value = null
            ttsManagerProvider()?.stopPreview()
            applyTtsSettings()
            AppLogger.d(TAG, "Stopped voice preview and restored TTS settings")
        }
    }

    fun setSelectedEngineType(engineType: String) {
        scope.launch {
            settingsRepositoryProvider()?.setSelectedEngineType(engineType)
        }
    }

    fun getDefaultTtsEngine(): String? = ttsManagerProvider()?.getDefaultEngineName()

    fun getActiveTtsEngine(): String? = ttsManagerProvider()?.getActiveEnginePackage()

    fun getInstalledTtsEngines(): List<TextToSpeech.EngineInfo> = ttsManagerProvider()?.getEngines().orEmpty()

    fun switchTtsEngine(enginePackage: String?, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            settingsRepositoryProvider()?.setSelectedTtsEnginePackage(enginePackage)
            ttsManagerProvider()?.setEngine(enginePackage) { success ->
                if (success) {
                    val voices = ttsManagerProvider()?.getAvailableVoices().orEmpty()
                    _availableVoicesState.value = voices
                    onVoicesUpdated(voices)
                    applyTtsSettings()
                }
                onComplete?.invoke(success)
            }
        }
    }

    fun getAvailableVoices(): List<Voice> = ttsManagerProvider()?.getAvailableVoices().orEmpty()

    fun updateAvailableVoices(voices: List<Voice>) {
        _availableVoicesState.value = voices
        onVoicesUpdated(voices)
    }

    fun applyTtsSettings() {
        val tts = ttsManagerProvider() ?: return
        tts.setRate(currentSpeechRate)
        tts.setPitch(currentSpeechPitch)
        tts.setVoiceByName(currentVoiceName)
    }
}
