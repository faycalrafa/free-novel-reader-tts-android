package com.novelarrow.reader.data.repository

import androidx.core.util.AtomicFile
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.util.AppLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal object SettingsPersistenceManager {

    private const val TAG = "SettingsPersistenceManager"

    fun writeAtomically(file: File, content: String) {
        val atomicFile = AtomicFile(file)
        var fos: java.io.FileOutputStream? = null
        try {
            fos = atomicFile.startWrite()
            fos.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            atomicFile.failWrite(fos)
            AppLogger.e(TAG, "Atomic write failed for ${file.name}: ${e.message}", e)
            throw e
        }
    }

    fun readAtomically(file: File): String? {
        if (!file.exists()) return null
        return try {
            val atomicFile = AtomicFile(file)
            String(atomicFile.readFully(), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to read atomic file ${file.name}: ${e.message}", e)
            null
        }
    }

    fun loadRawSettings(settingsFile: File, json: Json): ReaderSettings {
        return try {
            val text = readAtomically(settingsFile) ?: return ReaderSettings()
            json.decodeFromString<ReaderSettings>(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to decode reader settings: ${e.message}", e)
            ReaderSettings()
        }
    }

    fun saveSettings(settingsFile: File, settings: ReaderSettings, json: Json) {
        try {
            val text = json.encodeToString(settings)
            writeAtomically(settingsFile, text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save settings: ${e.message}", e)
        }
    }

    fun loadRawBrowserSettings(browserSettingsFile: File, json: Json): WebBrowserSettings {
        return try {
            val text = readAtomically(browserSettingsFile) ?: return WebBrowserSettings()
            json.decodeFromString<WebBrowserSettings>(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to decode browser settings: ${e.message}", e)
            WebBrowserSettings()
        }
    }

    fun saveBrowserSettings(browserSettingsFile: File, settings: WebBrowserSettings, json: Json) {
        try {
            val text = json.encodeToString(settings)
            writeAtomically(browserSettingsFile, text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save browser settings: ${e.message}", e)
        }
    }

    fun loadSharedTts(sharedTtsFile: File, reader: ReaderSettings, browser: WebBrowserSettings, json: Json): SharedTtsSettings {
        try {
            val text = readAtomically(sharedTtsFile)
            if (text != null) {
                val decoded = json.decodeFromString<SharedTtsSettings>(text)
                val sanitizedRules = decoded.pronunciationRules.map {
                    if (it.id.isBlank()) it.copy(id = java.util.UUID.randomUUID().toString()) else it
                }
                val sanitizedSkips = decoded.skipWords.map {
                    if (it.id.isBlank()) it.copy(id = java.util.UUID.randomUUID().toString()) else it
                }
                return decoded.copy(pronunciationRules = sanitizedRules, skipWords = sanitizedSkips)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to decode shared TTS settings: ${e.message}", e)
        }

        // Fallback: merge existing user settings from reader and browser
        val mergedSkips = (reader.skipWords + browser.skipWords).distinctBy { it.text.lowercase() }.map {
            if (it.id.isBlank()) it.copy(id = java.util.UUID.randomUUID().toString()) else it
        }
        val mergedRules = (reader.pronunciationRules + browser.pronunciationRules).distinctBy { it.from.lowercase() }.map {
            if (it.id.isBlank()) it.copy(id = java.util.UUID.randomUUID().toString()) else it
        }
        val rate = if (reader.speechRate != 1.0f) reader.speechRate else browser.ttsRate
        val pitch = if (reader.speechPitch != 1.0f) reader.speechPitch else browser.ttsPitch
        val voice = reader.selectedVoiceName ?: browser.ttsVoice
        val sleepMinutes = if (reader.sleepTimerMinutes > 0) reader.sleepTimerMinutes else browser.sleepTimerMinutes

        val initial = SharedTtsSettings(
            speechRate = rate,
            speechPitch = pitch,
            voiceName = voice,
            sleepTimerMinutes = sleepMinutes,
            skipWords = mergedSkips,
            skipWordsEnabled = reader.skipWordsEnabled,
            skipWordsIgnoreSymbols = reader.skipWordsIgnoreSymbols,
            pronunciationRules = mergedRules,
            pronunciationRulesEnabled = reader.pronunciationRulesEnabled,
            headerSkipRules = reader.headerSkipRules,
            footerSkipRules = reader.footerSkipRules,
            favoriteVoiceEnglish = reader.favoriteVoiceEnglish,
            favoriteVoiceArabic = reader.favoriteVoiceArabic,
            favoriteVoiceFrench = reader.favoriteVoiceFrench,
            selectedEngineType = reader.selectedEngineType,
            selectedTtsEnginePackage = reader.selectedTtsEnginePackage,
            contentSelectors = reader.contentSelectors
        )
        saveSharedTts(sharedTtsFile, initial, json)
        return initial
    }

    fun saveSharedTts(sharedTtsFile: File, shared: SharedTtsSettings, json: Json) {
        try {
            val text = json.encodeToString(shared)
            writeAtomically(sharedTtsFile, text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save shared TTS settings: ${e.message}", e)
        }
    }
}
