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

    suspend fun addPronunciationRule(rule: PronunciationRule) = updateSettings {
        it.copy(pronunciationRules = it.pronunciationRules + rule)
    }

    suspend fun removePronunciationRule(index: Int) = updateSettings {
        it.copy(pronunciationRules = it.pronunciationRules.filterIndexed { i, _ -> i != index })
    }

    suspend fun addSkipWord(skip: SkipWord) = updateSettings {
        it.copy(skipWords = it.skipWords + skip)
    }

    suspend fun removeSkipWord(index: Int) = updateSettings {
        it.copy(skipWords = it.skipWords.filterIndexed { i, _ -> i != index })
    }

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
