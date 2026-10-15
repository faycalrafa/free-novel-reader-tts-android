package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.data.model.WebBrowserSettings
import android.os.Environment
import android.util.Log
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
    val sleepTimerMinutes: Int = 0,
    val skipWords: List<SkipWord> = emptyList(),
    val skipWordsEnabled: Boolean = true,
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val pronunciationRulesEnabled: Boolean = true,
    val headerSkipRules: List<SkipRule> = emptyList(),
    val footerSkipRules: List<SkipRule> = emptyList()
)

@Serializable
data class UserPreferencesBackup(
    val readerSettings: ReaderSettings = ReaderSettings(),
    val browserSettings: WebBrowserSettings = WebBrowserSettings(),
    val sharedTts: SharedTtsSettings = SharedTtsSettings(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val settingsFile: File
        get() = File(context.filesDir, "novel_settings_v1.json")
    private val browserSettingsFile: File
        get() = File(context.filesDir, "novel_browser_settings_v1.json")
    private val sharedTtsFile: File
        get() = File(context.filesDir, "novel_shared_tts_settings_v1.json")

    companion object {
        private var sharedSettingsFlowInternal: MutableStateFlow<ReaderSettings>? = null
        private var sharedBrowserSettingsFlowInternal: MutableStateFlow<WebBrowserSettings>? = null
    }

    private val _settingsFlow: MutableStateFlow<ReaderSettings>
    val settingsFlow: StateFlow<ReaderSettings>

    private val _browserSettingsFlow: MutableStateFlow<WebBrowserSettings>
    val browserSettingsFlow: StateFlow<WebBrowserSettings>

    init {
        val backup = restoreBackupFromNovelReaderRoots()
        val isFirstInstall = !settingsFile.exists() && !browserSettingsFile.exists() && !sharedTtsFile.exists()
        val shouldRestoreBackup = backup != null && (isFirstInstall || backup.lastUpdated > settingsFile.lastModified())

        val loadedReader = if (shouldRestoreBackup) {
            backup.readerSettings
        } else {
            loadRawSettings()
        }

        val loadedBrowser = if (shouldRestoreBackup) {
            backup.browserSettings
        } else {
            loadRawBrowserSettings()
        }

        val shared = if (shouldRestoreBackup) {
            backup.sharedTts
        } else {
            loadSharedTts(loadedReader, loadedBrowser)
        }

        val mergedReader = loadedReader.copy(
            speechRate = shared.speechRate,
            speechPitch = shared.speechPitch,
            selectedVoiceName = shared.voiceName,
            sleepTimerMinutes = shared.sleepTimerMinutes,
            skipWords = shared.skipWords,
            skipWordsEnabled = shared.skipWordsEnabled,
            pronunciationRules = shared.pronunciationRules,
            pronunciationRulesEnabled = shared.pronunciationRulesEnabled,
            headerSkipRules = if (shared.headerSkipRules.isNotEmpty()) shared.headerSkipRules else loadedReader.headerSkipRules,
            footerSkipRules = if (shared.footerSkipRules.isNotEmpty()) shared.footerSkipRules else loadedReader.footerSkipRules
        )
        val mergedBrowser = loadedBrowser.copy(
            ttsRate = shared.speechRate,
            ttsPitch = shared.speechPitch,
            ttsVoice = shared.voiceName,
            sleepTimerMinutes = shared.sleepTimerMinutes,
            skipWords = shared.skipWords,
            skipWordsEnabled = shared.skipWordsEnabled,
            pronunciationRules = shared.pronunciationRules,
            pronunciationRulesEnabled = shared.pronunciationRulesEnabled
        )

        synchronized(SettingsRepository::class.java) {
            if (sharedSettingsFlowInternal == null) {
                sharedSettingsFlowInternal = MutableStateFlow(mergedReader)
            }
            if (sharedBrowserSettingsFlowInternal == null) {
                sharedBrowserSettingsFlowInternal = MutableStateFlow(mergedBrowser)
            }
        }

        _settingsFlow = sharedSettingsFlowInternal!!
        settingsFlow = _settingsFlow.asStateFlow()

        _browserSettingsFlow = sharedBrowserSettingsFlowInternal!!
        browserSettingsFlow = _browserSettingsFlow.asStateFlow()

        // Sync backup to disk if first install restored
        if (shouldRestoreBackup) {
            saveSettings(mergedReader)
            saveBrowserSettings(mergedBrowser)
            saveSharedTts(shared)
        } else {
            saveBackupToNovelReaderRoots()
        }
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

    suspend fun setPronunciationRulesEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(pronunciationRulesEnabled = enabled) }
    }

    suspend fun setSkipWordsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(skipWordsEnabled = enabled) }
    }

    suspend fun setBrowserPronunciationRulesEnabled(enabled: Boolean) = setPronunciationRulesEnabled(enabled)

    suspend fun setBrowserSkipWordsEnabled(enabled: Boolean) = setSkipWordsEnabled(enabled)

    suspend fun setSleepTimerMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(sleepTimerMinutes = minutes) }
    }

    fun getBrowserSettings(): WebBrowserSettings = _browserSettingsFlow.value

    suspend fun updateBrowserSettings(transform: (WebBrowserSettings) -> WebBrowserSettings) = withContext(Dispatchers.IO) {
        val updated = transform(_browserSettingsFlow.value)
        saveBrowserSettings(updated)
        _browserSettingsFlow.value = updated
    }

    suspend fun setAutoReadNextChapter(enabled: Boolean) = updateBrowserSettings { it.copy(autoReadNextChapter = enabled) }

    suspend fun setBrowserAutoScroll(enabled: Boolean) = updateBrowserSettings { it.copy(ttsAutoScroll = enabled) }

    suspend fun setBrowserTtsAutoStart(enabled: Boolean) = updateBrowserSettings { it.copy(ttsAutoStart = enabled) }

    suspend fun setBrowserSynthByParagraph(enabled: Boolean) = updateBrowserSettings { it.copy(ttsSynthByParagraph = enabled) }

    suspend fun setBrowserSleepTimerMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(sleepTimerMinutes = minutes) }
    }

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
            sleepTimerMinutes = _settingsFlow.value.sleepTimerMinutes,
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
            sleepTimerMinutes = _settingsFlow.value.sleepTimerMinutes,
            skipWords = _settingsFlow.value.skipWords,
            skipWordsEnabled = _settingsFlow.value.skipWordsEnabled,
            pronunciationRules = _settingsFlow.value.pronunciationRules,
            pronunciationRulesEnabled = _settingsFlow.value.pronunciationRulesEnabled
        )
        val newShared = transform(currentShared)
        saveSharedTts(newShared)

        val updatedReader = _settingsFlow.value.copy(
            speechRate = newShared.speechRate,
            speechPitch = newShared.speechPitch,
            selectedVoiceName = newShared.voiceName,
            sleepTimerMinutes = newShared.sleepTimerMinutes,
            skipWords = newShared.skipWords,
            skipWordsEnabled = newShared.skipWordsEnabled,
            pronunciationRules = newShared.pronunciationRules,
            pronunciationRulesEnabled = newShared.pronunciationRulesEnabled
        )
        val updatedBrowser = _browserSettingsFlow.value.copy(
            ttsRate = newShared.speechRate,
            ttsPitch = newShared.speechPitch,
            ttsVoice = newShared.voiceName,
            sleepTimerMinutes = newShared.sleepTimerMinutes,
            skipWords = newShared.skipWords,
            skipWordsEnabled = newShared.skipWordsEnabled,
            pronunciationRules = newShared.pronunciationRules,
            pronunciationRulesEnabled = newShared.pronunciationRulesEnabled
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

    suspend fun addHeaderSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.headerSkipRules.filterNot { r -> r.text.equals(rule.text, ignoreCase = true) }
            it.copy(headerSkipRules = filtered + rule)
        }
    }

    suspend fun removeHeaderSkipRule(index: Int) = withContext(Dispatchers.IO) {
        updateSharedTts {
            it.copy(headerSkipRules = it.headerSkipRules.filterIndexed { i, _ -> i != index })
        }
    }

    suspend fun addFooterSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.footerSkipRules.filterNot { r -> r.text.equals(rule.text, ignoreCase = true) }
            it.copy(footerSkipRules = filtered + rule)
        }
    }

    suspend fun removeFooterSkipRule(index: Int) = withContext(Dispatchers.IO) {
        updateSharedTts {
            it.copy(footerSkipRules = it.footerSkipRules.filterIndexed { i, _ -> i != index })
        }
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
            saveBackupToNovelReaderRoots()
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
            saveBackupToNovelReaderRoots()
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
        val sleepMinutes = if (reader.sleepTimerMinutes > 0) reader.sleepTimerMinutes else browser.sleepTimerMinutes

        val initial = SharedTtsSettings(
            speechRate = rate,
            speechPitch = pitch,
            voiceName = voice,
            sleepTimerMinutes = sleepMinutes,
            skipWords = mergedSkips,
            skipWordsEnabled = reader.skipWordsEnabled,
            pronunciationRules = mergedRules,
            pronunciationRulesEnabled = reader.pronunciationRulesEnabled,
            headerSkipRules = reader.headerSkipRules,
            footerSkipRules = reader.footerSkipRules
        )
        saveSharedTts(initial)
        return initial
    }

    private fun saveSharedTts(shared: SharedTtsSettings) {
        try {
            val text = json.encodeToString(shared)
            sharedTtsFile.writeText(text)
            saveBackupToNovelReaderRoots()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getNovelReaderRoots(): List<File> {
        val roots = mutableListOf<File>()
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            roots.add(File(extDir, "novelreader"))
            roots.add(File(extDir, "NovelReader"))
        }

        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            roots.add(File(publicDownloads, "novelreader"))
            roots.add(File(publicDownloads, "NovelReader"))

            val alt1 = File("/storage/emulated/0/Download")
            if (alt1.exists()) {
                roots.add(File(alt1, "novelreader"))
                roots.add(File(alt1, "NovelReader"))
            }
            val alt2 = File("/storage/emulated/0/Downloads")
            if (alt2.exists()) {
                roots.add(File(alt2, "novelreader"))
                roots.add(File(alt2, "NovelReader"))
            }
        } catch (_: Exception) {}

        roots.add(File(context.filesDir, "novelreader"))
        roots.add(File(context.filesDir, "NovelReader"))
        return roots.distinctBy { it.absolutePath }
    }

    fun saveBackupToNovelReaderRoots() {
        try {
            val currentReader = _settingsFlow.value
            val currentBrowser = _browserSettingsFlow.value
            val currentShared = SharedTtsSettings(
                speechRate = currentReader.speechRate,
                speechPitch = currentReader.speechPitch,
                voiceName = currentReader.selectedVoiceName,
                sleepTimerMinutes = currentReader.sleepTimerMinutes,
                skipWords = currentReader.skipWords,
                skipWordsEnabled = currentReader.skipWordsEnabled,
                pronunciationRules = currentReader.pronunciationRules,
                pronunciationRulesEnabled = currentReader.pronunciationRulesEnabled
            )

            val backup = UserPreferencesBackup(
                readerSettings = currentReader,
                browserSettings = currentBrowser,
                sharedTts = currentShared,
                lastUpdated = System.currentTimeMillis()
            )
            val jsonText = json.encodeToString(backup)

            val roots = getNovelReaderRoots()
            for (root in roots) {
                try {
                    if (!root.exists()) {
                        root.mkdirs()
                    }
                    if (root.exists() && root.isDirectory) {
                        File(root, ".novelreader_preferences.json").writeText(jsonText, Charsets.UTF_8)
                        File(root, ".preferences.json").writeText(jsonText, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    Log.d("SettingsRepo", "Cannot write backup to ${root.absolutePath}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w("SettingsRepo", "Failed to backup preferences: ${e.message}")
        }
    }

    private fun restoreBackupFromNovelReaderRoots(): UserPreferencesBackup? {
        val candidateFileNames = listOf(".novelreader_preferences.json", ".preferences.json")
        for (root in getNovelReaderRoots()) {
            if (!root.exists() || !root.isDirectory) continue
            for (fName in candidateFileNames) {
                val f = File(root, fName)
                if (f.exists() && f.isFile && f.length() > 0L) {
                    try {
                        val text = f.readText(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            val backup = json.decodeFromString<UserPreferencesBackup>(text)
                            Log.i("SettingsRepo", "Loaded preferences from ${f.absolutePath} (theme=${backup.readerSettings.theme}, rate=${backup.sharedTts.speechRate})")
                            return backup
                        }
                    } catch (e: Exception) {
                        Log.w("SettingsRepo", "Failed to parse backup from ${f.absolutePath}: ${e.message}")
                    }
                }
            }
        }
        return null
    }
}
