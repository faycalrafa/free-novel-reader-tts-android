package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val settingsFile: File
        get() = File(context.filesDir, "novel_settings_v1.json")

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ReaderSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): ReaderSettings = _settingsFlow.value

    suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) = withContext(Dispatchers.IO) {
        val updated = transform(_settingsFlow.value)
        saveSettings(updated)
        _settingsFlow.value = updated
    }

    suspend fun setTheme(theme: ThemeMode) = updateSettings { it.copy(theme = theme) }

    suspend fun setFontSize(size: Float) = updateSettings { it.copy(fontSizeSp = size) }

    suspend fun setSpeechRate(rate: Float) = updateSettings { it.copy(speechRate = rate) }

    suspend fun setSpeechPitch(pitch: Float) = updateSettings { it.copy(speechPitch = pitch) }

    suspend fun setVoice(voiceName: String?) = updateSettings { it.copy(selectedVoiceName = voiceName) }

    suspend fun setAutoScroll(enabled: Boolean) = updateSettings { it.copy(autoScroll = enabled) }

    suspend fun setAutoNext(enabled: Boolean) = updateSettings { it.copy(autoNextChapter = enabled) }

    suspend fun setContentSelector(selector: String) = updateSettings { it.copy(contentSelector = selector) }

    suspend fun setContentSelectorEnabled(enabled: Boolean) = updateSettings { it.copy(contentSelectorEnabled = enabled) }

    suspend fun setHeaderSkipString(headerStr: String) = updateSettings { it.copy(headerSkipString = headerStr) }

    suspend fun setHeaderSkipEnabled(enabled: Boolean) = updateSettings { it.copy(headerSkipEnabled = enabled) }

    suspend fun setFooterSkipString(footerStr: String) = updateSettings { it.copy(footerSkipString = footerStr) }

    suspend fun setFooterSkipEnabled(enabled: Boolean) = updateSettings { it.copy(footerSkipEnabled = enabled) }

    suspend fun setPronunciationRulesEnabled(enabled: Boolean) = updateSettings { it.copy(pronunciationRulesEnabled = enabled) }

    suspend fun addPronunciationRule(rule: PronunciationRule) = updateSettings {
        it.copy(pronunciationRules = it.pronunciationRules + rule)
    }

    suspend fun removePronunciationRule(index: Int) = updateSettings {
        it.copy(pronunciationRules = it.pronunciationRules.filterIndexed { i, _ -> i != index })
    }

    suspend fun setSkipWordsEnabled(enabled: Boolean) = updateSettings { it.copy(skipWordsEnabled = enabled) }

    suspend fun addSkipWord(skip: SkipWord) = updateSettings {
        it.copy(skipWords = it.skipWords + skip)
    }

    suspend fun removeSkipWord(index: Int) = updateSettings {
        it.copy(skipWords = it.skipWords.filterIndexed { i, _ -> i != index })
    }

    suspend fun setSleepTimerMinutes(minutes: Int) = updateSettings { it.copy(sleepTimerMinutes = minutes) }

    private fun loadSettings(): ReaderSettings {
        return try {
            if (!settingsFile.exists()) return ReaderSettings()
            val text = settingsFile.readText()
            json.decodeFromString<ReaderSettings>(text)
        } catch (e: Exception) {
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
}
