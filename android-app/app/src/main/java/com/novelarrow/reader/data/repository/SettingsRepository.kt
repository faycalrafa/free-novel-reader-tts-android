package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.data.model.WebBrowserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SharedTtsSettings(
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val voiceName: String? = null,
    val skipWords: List<SkipWord> = emptyList(),
    val pronunciationRules: List<PronunciationRule> = emptyList()
)

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val settingsFile: File
        get() = File(context.filesDir, "novel_settings_v1.json")
    private val browserSettingsFile: File
        get() = File(context.filesDir, "novel_browser_settings_v1.json")
    private val sharedTtsFile: File
        get() = File(context.filesDir, "novel_shared_tts_settings_v1.json")

    private val _settingsFlow: MutableStateFlow<ReaderSettings>
    val settingsFlow: StateFlow<ReaderSettings>

    private val _browserSettingsFlow: MutableStateFlow<WebBrowserSettings>
    val browserSettingsFlow: StateFlow<WebBrowserSettings>

    init {
        val loadedReader = loadRawSettings()
        val loadedBrowser = loadRawBrowserSettings()
        val shared = loadSharedTts(loadedReader, loadedBrowser)

        val mergedReader = loadedReader.copy(
            speechRate = shared.speechRate,
            speechPitch = shared.speechPitch,
            selectedVoiceName = shared.voiceName,
            skipWords = shared.skipWords,
            pronunciationRules = shared.pronunciationRules
        )
        val mergedBrowser = loadedBrowser.copy(
            ttsRate = shared.speechRate,
            ttsPitch = shared.speechPitch,
            ttsVoice = shared.voiceName,
            skipWords = shared.skipWords,
            pronunciationRules = shared.pronunciationRules
        )

        _settingsFlow = MutableStateFlow(mergedReader)
        settingsFlow = _settingsFlow.asStateFlow()

        _browserSettingsFlow = MutableStateFlow(mergedBrowser)
        browserSettingsFlow = _browserSettingsFlow.asStateFlow()
    }

    fun getSettings(): ReaderSettings = _settingsFlow.value

    suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) = withContext(Dispatchers.IO) {
        val updated = transform(_settingsFlow.value)
        saveSettings(updated)
        _settingsFlow.value = updated
    }

    suspend fun setTheme(theme: ThemeMode) = updateSettings { it.copy(theme = theme) }

    suspend fun setFontSize(size: Float) = updateSettings { it.copy(fontSizeSp = size) }

    suspend fun setAutoScroll(enabled: Boolean) = updateSettings { it.copy(autoScroll = enabled) }

    suspend fun setAutoNext(enabled: Boolean) = updateSettings { it.copy(autoNextChapter = enabled) }

    suspend fun setContentSelector(selector: String) = updateSettings { it.copy(contentSelector = selector) }

    suspend fun setContentSelectorEnabled(enabled: Boolean) = updateSettings { it.copy(contentSelectorEnabled = enabled) }

    suspend fun setHeaderSkipString(headerStr: String) = updateSettings { it.copy(headerSkipString = headerStr) }

    suspend fun setHeaderSkipEnabled(enabled: Boolean) = updateSettings { it.copy(headerSkipEnabled = enabled) }

    suspend fun setFooterSkipString(footerStr: String) = updateSettings { it.copy(footerSkipString = footerStr) }

    suspend fun setFooterSkipEnabled(enabled: Boolean) = updateSettings { it.copy(footerSkipEnabled = enabled) }

    suspend fun setPronunciationRulesEnabled(enabled: Boolean) = updateSettings { it.copy(pronunciationRulesEnabled = enabled) }

    suspend fun setSkipWordsEnabled(enabled: Boolean) = updateSettings { it.copy(skipWordsEnabled = enabled) }

    suspend fun setSleepTimerMinutes(minutes: Int) = updateSettings { it.copy(sleepTimerMinutes = minutes) }

    fun getBrowserSettings(): WebBrowserSettings = _browserSettingsFlow.value

    suspend fun updateBrowserSettings(transform: (WebBrowserSettings) -> WebBrowserSettings) = withContext(Dispatchers.IO) {
        val updated = transform(_browserSettingsFlow.value)
        saveBrowserSettings(updated)
        _browserSettingsFlow.value = updated
    }

    suspend fun setBrowserAutoScroll(enabled: Boolean) = updateBrowserSettings { it.copy(ttsAutoScroll = enabled) }

    suspend fun setBrowserTtsAutoStart(enabled: Boolean) = updateBrowserSettings { it.copy(ttsAutoStart = enabled) }

    suspend fun setBrowserSynthByParagraph(enabled: Boolean) = updateBrowserSettings { it.copy(ttsSynthByParagraph = enabled) }

    suspend fun setBrowserSleepTimerMinutes(minutes: Int) = updateBrowserSettings { it.copy(sleepTimerMinutes = minutes) }

    suspend fun addBrowserIgnoredSelector(selector: String) = updateBrowserSettings {
        val trimmed = selector.trim()
        if (trimmed.isNotBlank() && !it.ignoredSelectors.contains(trimmed)) {
            it.copy(ignoredSelectors = it.ignoredSelectors + trimmed)
        } else it
    }

    suspend fun removeBrowserIgnoredSelector(selector: String) = updateBrowserSettings {
        it.copy(ignoredSelectors = it.ignoredSelectors.filter { s -> s != selector })
    }

    suspend fun resetBrowserIgnoredSelectors() = updateBrowserSettings {
        it.copy(ignoredSelectors = WebBrowserSettings().ignoredSelectors)
    }

    suspend fun resetBrowserSettingsToDefaults() = withContext(Dispatchers.IO) {
        val defaults = WebBrowserSettings().copy(
            ttsRate = _settingsFlow.value.speechRate,
            ttsPitch = _settingsFlow.value.speechPitch,
            ttsVoice = _settingsFlow.value.selectedVoiceName,
            skipWords = _settingsFlow.value.skipWords,
            pronunciationRules = _settingsFlow.value.pronunciationRules
        )
        saveBrowserSettings(defaults)
        _browserSettingsFlow.value = defaults
    }

    // =========================================================================
    // Consolidated Shared TTS Configuration (Applied Everywhere)
    // =========================================================================

    private fun updateSharedTts(transform: (SharedTtsSettings) -> SharedTtsSettings) {
        val currentShared = SharedTtsSettings(
            speechRate = _settingsFlow.value.speechRate,
            speechPitch = _settingsFlow.value.speechPitch,
            voiceName = _settingsFlow.value.selectedVoiceName,
            skipWords = _settingsFlow.value.skipWords,
            pronunciationRules = _settingsFlow.value.pronunciationRules
        )
        val newShared = transform(currentShared)
        saveSharedTts(newShared)

        val updatedReader = _settingsFlow.value.copy(
            speechRate = newShared.speechRate,
            speechPitch = newShared.speechPitch,
            selectedVoiceName = newShared.voiceName,
            skipWords = newShared.skipWords,
            pronunciationRules = newShared.pronunciationRules
        )
        val updatedBrowser = _browserSettingsFlow.value.copy(
            ttsRate = newShared.speechRate,
            ttsPitch = newShared.speechPitch,
            ttsVoice = newShared.voiceName,
            skipWords = newShared.skipWords,
            pronunciationRules = newShared.pronunciationRules
        )

        saveSettings(updatedReader)
        saveBrowserSettings(updatedBrowser)

        _settingsFlow.value = updatedReader
        _browserSettingsFlow.value = updatedBrowser
    }

    suspend fun setSpeechRate(rate: Float) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(speechRate = rate) }
    }

    suspend fun setBrowserRate(rate: Float) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(speechRate = rate) }
    }

    suspend fun setSpeechPitch(pitch: Float) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(speechPitch = pitch) }
    }

    suspend fun setBrowserPitch(pitch: Float) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(speechPitch = pitch) }
    }

    suspend fun setVoice(voiceName: String?) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(voiceName = voiceName) }
    }

    suspend fun setBrowserVoice(voice: String?) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(voiceName = voice) }
    }

    suspend fun addPronunciationRule(rule: PronunciationRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.pronunciationRules.filterNot { r -> r.from.equals(rule.from, ignoreCase = true) }
            it.copy(pronunciationRules = filtered + rule)
        }
    }

    suspend fun addBrowserPronunciationRule(rule: PronunciationRule) = withContext(Dispatchers.IO) {
        addPronunciationRule(rule)
    }

    suspend fun removePronunciationRule(index: Int) = withContext(Dispatchers.IO) {
        updateSharedTts {
            it.copy(pronunciationRules = it.pronunciationRules.filterIndexed { i, _ -> i != index })
        }
    }

    suspend fun removeBrowserPronunciationRule(index: Int) = withContext(Dispatchers.IO) {
        removePronunciationRule(index)
    }

    suspend fun addSkipWord(skip: SkipWord) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.skipWords.filterNot { s -> s.text.equals(skip.text, ignoreCase = true) }
            it.copy(skipWords = filtered + skip)
        }
    }

    suspend fun addBrowserSkipWord(skip: SkipWord) = withContext(Dispatchers.IO) {
        addSkipWord(skip)
    }

    suspend fun removeSkipWord(index: Int) = withContext(Dispatchers.IO) {
        updateSharedTts {
            it.copy(skipWords = it.skipWords.filterIndexed { i, _ -> i != index })
        }
    }

    suspend fun removeBrowserSkipWord(index: Int) = withContext(Dispatchers.IO) {
        removeSkipWord(index)
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private fun loadRawSettings(): ReaderSettings {
        return try {
            if (!settingsFile.exists()) return ReaderSettings()
            val text = settingsFile.readText()
            json.decodeFromString<ReaderSettings>(text)
        } catch (_: Exception) {
            ReaderSettings()
        }
    }

    private fun saveSettings(settings: ReaderSettings) {
        try {
            val text = json.encodeToString(settings)
            settingsFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadRawBrowserSettings(): WebBrowserSettings {
        return try {
            if (!browserSettingsFile.exists()) return WebBrowserSettings()
            val text = browserSettingsFile.readText()
            json.decodeFromString<WebBrowserSettings>(text)
        } catch (_: Exception) {
            WebBrowserSettings()
        }
    }

    private fun saveBrowserSettings(settings: WebBrowserSettings) {
        try {
            val text = json.encodeToString(settings)
            browserSettingsFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSharedTts(reader: ReaderSettings, browser: WebBrowserSettings): SharedTtsSettings {
        try {
            if (sharedTtsFile.exists()) {
                val text = sharedTtsFile.readText()
                return json.decodeFromString<SharedTtsSettings>(text)
            }
        } catch (_: Exception) {}

        // Fallback: merge existing user settings from reader and browser
        val mergedSkips = (reader.skipWords + browser.skipWords).distinctBy { it.text.lowercase() }
        val mergedRules = (reader.pronunciationRules + browser.pronunciationRules).distinctBy { it.from.lowercase() }
        val rate = if (reader.speechRate != 1.0f) reader.speechRate else browser.ttsRate
        val pitch = if (reader.speechPitch != 1.0f) reader.speechPitch else browser.ttsPitch
        val voice = reader.selectedVoiceName ?: browser.ttsVoice

        val initial = SharedTtsSettings(
            speechRate = rate,
            speechPitch = pitch,
            voiceName = voice,
            skipWords = mergedSkips,
            pronunciationRules = mergedRules
        )
        saveSharedTts(initial)
        return initial
    }

    private fun saveSharedTts(shared: SharedTtsSettings) {
        try {
            val text = json.encodeToString(shared)
            sharedTtsFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
