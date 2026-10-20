package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.BrowserTab
import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.NextChapterSelectorRule
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.data.model.WebBrowserSettings
import android.os.Environment
import android.util.Log
import androidx.core.util.AtomicFile
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val skipWordsIgnoreSymbols: Boolean = true,
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val pronunciationRulesEnabled: Boolean = true,
    val headerSkipRules: List<SkipRule> = emptyList(),
    val footerSkipRules: List<SkipRule> = emptyList(),
    val favoriteVoiceEnglish: String? = null,
    val favoriteVoiceArabic: String? = null,
    val favoriteVoiceFrench: String? = null,
    val selectedEngineType: String = "SYSTEM",
    val selectedTtsEnginePackage: String? = null,
    val contentSelectors: List<ContentSelectorRule> = emptyList(),
    val ignoredSelectors: List<String> = emptyList()
)

@Serializable
data class UserPreferencesBackup(
    val readerSettings: ReaderSettings = ReaderSettings(),
    val browserSettings: WebBrowserSettings = WebBrowserSettings(),
    val sharedTts: SharedTtsSettings = SharedTtsSettings(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class SettingsRepository(private val context: Context) {

    private val mutex = Mutex()
    private val TAG = "SettingsRepository"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val settingsFile: File
        get() = File(context.filesDir, "novel_settings_v1.json")
    private val browserSettingsFile: File
        get() = File(context.filesDir, "novel_browser_settings_v1.json")
    private val sharedTtsFile: File
        get() = File(context.filesDir, "novel_shared_tts_settings_v1.json")

    companion object {
        const val ENGINE_TYPE_SYSTEM = "SYSTEM"

        private val sharedSettingsFlows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<ReaderSettings>>()
        private val sharedBrowserFlows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<WebBrowserSettings>>()

        @androidx.annotation.VisibleForTesting
        fun resetForTesting() {
            sharedSettingsFlows.clear()
            sharedBrowserFlows.clear()
        }
    }

    private val _settingsFlow: MutableStateFlow<ReaderSettings>
    val settingsFlow: StateFlow<ReaderSettings>

    private val _browserSettingsFlow: MutableStateFlow<WebBrowserSettings>
    val browserSettingsFlow: StateFlow<WebBrowserSettings>

    init {
        val cacheKey = try {
            context.filesDir.canonicalPath
        } catch (e: Exception) {
            context.filesDir.absolutePath
        }

        synchronized(SettingsRepository::class.java) {
            if (!sharedSettingsFlows.containsKey(cacheKey) || !sharedBrowserFlows.containsKey(cacheKey)) {
                val backup = restoreBackupFromNovelReaderRoots()
                val isFirstInstall = !settingsFile.exists() && !browserSettingsFile.exists() && !sharedTtsFile.exists()
                val shouldRestoreBackup = isFirstInstall && backup != null

                val loadedReader = if (backup != null && isFirstInstall) {
                    backup.readerSettings
                } else {
                    loadRawSettings()
                }

                val loadedBrowser = if (backup != null && isFirstInstall) {
                    backup.browserSettings
                } else {
                    loadRawBrowserSettings()
                }

                val shared = if (backup != null && isFirstInstall) {
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
                    footerSkipRules = if (shared.footerSkipRules.isNotEmpty()) shared.footerSkipRules else loadedReader.footerSkipRules,
                    favoriteVoiceEnglish = shared.favoriteVoiceEnglish ?: loadedReader.favoriteVoiceEnglish,
                    favoriteVoiceArabic = shared.favoriteVoiceArabic ?: loadedReader.favoriteVoiceArabic,
                    favoriteVoiceFrench = shared.favoriteVoiceFrench ?: loadedReader.favoriteVoiceFrench,
                    selectedEngineType = shared.selectedEngineType,
                    selectedTtsEnginePackage = shared.selectedTtsEnginePackage ?: loadedReader.selectedTtsEnginePackage,
                    contentSelectors = if (shared.contentSelectors.isNotEmpty()) shared.contentSelectors else loadedReader.contentSelectors
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

                sharedSettingsFlows[cacheKey] = MutableStateFlow(mergedReader)
                sharedBrowserFlows[cacheKey] = MutableStateFlow(mergedBrowser)

                if (shouldRestoreBackup) {
                    saveSettings(mergedReader)
                    saveBrowserSettings(mergedBrowser)
                    saveSharedTts(shared)
                }
            }
        }

        _settingsFlow = sharedSettingsFlows[cacheKey]!!
        settingsFlow = _settingsFlow.asStateFlow()

        _browserSettingsFlow = sharedBrowserFlows[cacheKey]!!
        browserSettingsFlow = _browserSettingsFlow.asStateFlow()
    }

    fun getSettings(): ReaderSettings = _settingsFlow.value

    suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_settingsFlow.value)
            _settingsFlow.value = updated
            saveSettings(updated)
        }
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

    suspend fun setSkipWordsIgnoreSymbols(enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(skipWordsIgnoreSymbols = enabled) }
    }

    suspend fun setBrowserPronunciationRulesEnabled(enabled: Boolean) = setPronunciationRulesEnabled(enabled)

    suspend fun setBrowserSkipWordsEnabled(enabled: Boolean) = setSkipWordsEnabled(enabled)

    suspend fun setBrowserSkipWordsIgnoreSymbols(enabled: Boolean) = setSkipWordsIgnoreSymbols(enabled)

    suspend fun setSleepTimerMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(sleepTimerMinutes = minutes) }
    }

    fun getBrowserSettings(): WebBrowserSettings = _browserSettingsFlow.value

    suspend fun updateBrowserSettings(transform: (WebBrowserSettings) -> WebBrowserSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_browserSettingsFlow.value)
            saveBrowserSettings(updated)
            _browserSettingsFlow.value = updated
        }
    }

    suspend fun setAutoReadNextChapter(enabled: Boolean) = updateBrowserSettings { it.copy(autoReadNextChapter = enabled) }

    suspend fun setBrowserAutoScroll(enabled: Boolean) = updateBrowserSettings { it.copy(ttsAutoScroll = enabled) }

    suspend fun setBrowserTtsAutoStart(enabled: Boolean) = updateBrowserSettings { it.copy(ttsAutoStart = enabled) }

    suspend fun setBrowserSynthByParagraph(enabled: Boolean) = updateBrowserSettings { it.copy(ttsSynthByParagraph = enabled) }

    suspend fun setBrowserSleepTimerMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(sleepTimerMinutes = minutes) }
    }

    suspend fun addBrowserIgnoredSelector(selector: String) = withContext(Dispatchers.IO) {
        val trimmed = selector.trim()
        if (trimmed.isNotBlank()) {
            updateSharedTts { shared ->
                if (!shared.ignoredSelectors.contains(trimmed)) {
                    shared.copy(ignoredSelectors = shared.ignoredSelectors + trimmed)
                } else shared
            }
        }
    }

    suspend fun removeBrowserIgnoredSelector(selector: String) = withContext(Dispatchers.IO) {
        updateSharedTts { shared ->
            shared.copy(ignoredSelectors = shared.ignoredSelectors.filter { s -> s != selector })
        }
    }

    suspend fun resetBrowserIgnoredSelectors() = withContext(Dispatchers.IO) {
        val defaults = WebBrowserSettings().ignoredSelectors
        updateSharedTts { shared ->
            shared.copy(ignoredSelectors = defaults)
        }
    }

    suspend fun addIgnoredSelector(selector: String) = addBrowserIgnoredSelector(selector)
    suspend fun removeIgnoredSelector(selector: String) = removeBrowserIgnoredSelector(selector)
    suspend fun resetIgnoredSelectors() = resetBrowserIgnoredSelectors()

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

    suspend fun updateBrowserTabs(tabs: List<BrowserTab>, currentTabId: String) = updateBrowserSettings {
        it.copy(tabs = tabs, currentTabId = currentTabId)
    }

    suspend fun addNextChapterRule(rule: NextChapterSelectorRule) = updateBrowserSettings {
        val filtered = it.nextChapterRules.filterNot { r -> r.id == rule.id }
        it.copy(nextChapterRules = filtered + rule)
    }

    suspend fun updateNextChapterRule(rule: NextChapterSelectorRule) = updateBrowserSettings {
        val updated = it.nextChapterRules.map { r -> if (r.id == rule.id) rule else r }
        it.copy(nextChapterRules = updated)
    }

    suspend fun toggleNextChapterRule(id: String, enabled: Boolean) = updateBrowserSettings {
        val updated = it.nextChapterRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
        it.copy(nextChapterRules = updated)
    }

    suspend fun deleteNextChapterRule(id: String) = updateBrowserSettings {
        val filtered = it.nextChapterRules.filterNot { r -> r.id == id }
        it.copy(nextChapterRules = filtered)
    }

    suspend fun addNextChapterSelectorRule(rule: NextChapterSelectorRule) = addNextChapterRule(rule)
    suspend fun updateNextChapterSelectorRule(rule: NextChapterSelectorRule) = updateNextChapterRule(rule)
    suspend fun toggleNextChapterSelectorRule(id: String, enabled: Boolean) = toggleNextChapterRule(id, enabled)
    suspend fun removeNextChapterSelectorRule(id: String) = deleteNextChapterRule(id)
    suspend fun resetNextChapterSelectorRules() = resetNextChapterRules()
    suspend fun resetNextChapterRules() = updateBrowserSettings {
        it.copy(nextChapterRules = WebBrowserSettings().nextChapterRules)
    }

    suspend fun addNavbarShortcut(shortcut: BrowserNavbarShortcut) = updateBrowserSettings {
        val filtered = it.navbarShortcuts.filterNot { s -> s.id == shortcut.id }
        it.copy(navbarShortcuts = filtered + shortcut)
    }

    suspend fun updateNavbarShortcut(shortcut: BrowserNavbarShortcut) = updateBrowserSettings {
        val updated = it.navbarShortcuts.map { s -> if (s.id == shortcut.id) shortcut else s }
        it.copy(navbarShortcuts = updated)
    }

    suspend fun toggleNavbarShortcut(id: String, enabled: Boolean) = updateBrowserSettings {
        val updated = it.navbarShortcuts.map { s -> if (s.id == id) s.copy(enabled = enabled) else s }
        it.copy(navbarShortcuts = updated)
    }

    suspend fun deleteNavbarShortcut(id: String) = updateBrowserSettings {
        val filtered = it.navbarShortcuts.filterNot { s -> s.id == id }
        it.copy(navbarShortcuts = filtered)
    }

    suspend fun removeNavbarShortcut(id: String) = deleteNavbarShortcut(id)

    suspend fun updateToolbarButtonOrder(order: List<String>) = updateBrowserSettings {
        it.copy(toolbarButtonOrder = order)
    }

    // =========================================================================
    // Consolidated Shared TTS Configuration (Applied Everywhere)
    // =========================================================================

    private suspend fun updateSharedTts(transform: (SharedTtsSettings) -> SharedTtsSettings) {
        mutex.withLock {
            val currentShared = SharedTtsSettings(
                speechRate = _settingsFlow.value.speechRate,
                speechPitch = _settingsFlow.value.speechPitch,
                voiceName = _settingsFlow.value.selectedVoiceName,
                sleepTimerMinutes = _settingsFlow.value.sleepTimerMinutes,
                skipWords = _settingsFlow.value.skipWords,
                skipWordsEnabled = _settingsFlow.value.skipWordsEnabled,
                skipWordsIgnoreSymbols = _settingsFlow.value.skipWordsIgnoreSymbols,
                pronunciationRules = _settingsFlow.value.pronunciationRules,
                pronunciationRulesEnabled = _settingsFlow.value.pronunciationRulesEnabled,
                headerSkipRules = _settingsFlow.value.headerSkipRules,
                footerSkipRules = _settingsFlow.value.footerSkipRules,
                favoriteVoiceEnglish = _settingsFlow.value.favoriteVoiceEnglish,
                favoriteVoiceArabic = _settingsFlow.value.favoriteVoiceArabic,
                favoriteVoiceFrench = _settingsFlow.value.favoriteVoiceFrench,
                selectedEngineType = _settingsFlow.value.selectedEngineType,
                selectedTtsEnginePackage = _settingsFlow.value.selectedTtsEnginePackage,
                contentSelectors = _settingsFlow.value.contentSelectors,
                ignoredSelectors = _settingsFlow.value.ignoredSelectors.ifEmpty { _browserSettingsFlow.value.ignoredSelectors }
            )
            val newShared = transform(currentShared)
            val updatedReader = _settingsFlow.value.copy(
                speechRate = newShared.speechRate,
                speechPitch = newShared.speechPitch,
                selectedVoiceName = newShared.voiceName,
                sleepTimerMinutes = newShared.sleepTimerMinutes,
                skipWords = newShared.skipWords,
                skipWordsEnabled = newShared.skipWordsEnabled,
                skipWordsIgnoreSymbols = newShared.skipWordsIgnoreSymbols,
                pronunciationRules = newShared.pronunciationRules,
                pronunciationRulesEnabled = newShared.pronunciationRulesEnabled,
                headerSkipRules = newShared.headerSkipRules,
                footerSkipRules = newShared.footerSkipRules,
                favoriteVoiceEnglish = newShared.favoriteVoiceEnglish,
                favoriteVoiceArabic = newShared.favoriteVoiceArabic,
                favoriteVoiceFrench = newShared.favoriteVoiceFrench,
                selectedEngineType = newShared.selectedEngineType,
                selectedTtsEnginePackage = newShared.selectedTtsEnginePackage,
                contentSelectors = newShared.contentSelectors,
                ignoredSelectors = newShared.ignoredSelectors
            )
            val updatedBrowser = _browserSettingsFlow.value.copy(
                ttsRate = newShared.speechRate,
                ttsPitch = newShared.speechPitch,
                ttsVoice = newShared.voiceName,
                sleepTimerMinutes = newShared.sleepTimerMinutes,
                skipWords = newShared.skipWords,
                skipWordsEnabled = newShared.skipWordsEnabled,
                skipWordsIgnoreSymbols = newShared.skipWordsIgnoreSymbols,
                pronunciationRules = newShared.pronunciationRules,
                pronunciationRulesEnabled = newShared.pronunciationRulesEnabled,
                contentSelectors = newShared.contentSelectors,
                headerSkipRules = newShared.headerSkipRules,
                ignoredSelectors = newShared.ignoredSelectors
            )

            _settingsFlow.value = updatedReader
            _browserSettingsFlow.value = updatedBrowser

            saveSharedTts(newShared)
            saveSettings(updatedReader)
            saveBrowserSettings(updatedBrowser)
        }
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
            val filtered = it.pronunciationRules.filterNot { r -> r.id == rule.id || r.from.equals(rule.from, ignoreCase = true) }
            it.copy(pronunciationRules = filtered + rule)
        }
    }

    suspend fun addBrowserPronunciationRule(rule: PronunciationRule) = withContext(Dispatchers.IO) {
        addPronunciationRule(rule)
    }

    suspend fun updatePronunciationRule(rule: PronunciationRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.pronunciationRules.map { r ->
                if (r.id == rule.id) rule else r
            }
            it.copy(pronunciationRules = updated)
        }
    }

    suspend fun togglePronunciationRule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.pronunciationRules.map { r ->
                if (r.id == id) r.copy(enabled = enabled) else r
            }
            it.copy(pronunciationRules = updated)
        }
    }

    suspend fun deletePronunciationRule(id: String) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.pronunciationRules.filterNot { r -> r.id == id }
            it.copy(pronunciationRules = filtered)
        }
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
            val filtered = it.skipWords.filterNot { s -> s.id == skip.id || s.text.equals(skip.text, ignoreCase = true) }
            it.copy(skipWords = filtered + skip)
        }
    }

    suspend fun addBrowserSkipWord(skip: SkipWord) = withContext(Dispatchers.IO) {
        addSkipWord(skip)
    }

    suspend fun updateSkipWord(word: SkipWord) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.skipWords.map { w ->
                if (w.id == word.id) word else w
            }
            it.copy(skipWords = updated)
        }
    }

    suspend fun toggleSkipWord(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.skipWords.map { w ->
                if (w.id == id) w.copy(enabled = enabled) else w
            }
            it.copy(skipWords = updated)
        }
    }

    suspend fun deleteSkipWord(id: String) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.skipWords.filterNot { w -> w.id == id }
            it.copy(skipWords = filtered)
        }
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

    suspend fun setFavoriteVoice(language: String, voiceName: String?) = withContext(Dispatchers.IO) {
        updateSharedTts {
            when (language.lowercase().take(2)) {
                "ar" -> it.copy(favoriteVoiceArabic = voiceName)
                "fr" -> it.copy(favoriteVoiceFrench = voiceName)
                else -> it.copy(favoriteVoiceEnglish = voiceName)
            }
        }
    }

    suspend fun setSelectedEngineType(engineType: String) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(selectedEngineType = engineType) }
    }

    suspend fun setSelectedTtsEnginePackage(enginePackage: String?) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(selectedTtsEnginePackage = enginePackage) }
    }

    suspend fun setContentSelectors(selectors: List<ContentSelectorRule>) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(contentSelectors = selectors) }
    }

    suspend fun addContentSelector(rule: ContentSelectorRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.contentSelectors.filterNot { r -> r.id == rule.id }
            it.copy(contentSelectors = filtered + rule)
        }
    }

    suspend fun updateContentSelector(rule: ContentSelectorRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.contentSelectors.map { r -> if (r.id == rule.id) rule else r }
            it.copy(contentSelectors = updated)
        }
    }

    suspend fun toggleContentSelector(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.contentSelectors.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
            it.copy(contentSelectors = updated)
        }
    }

    suspend fun removeContentSelector(id: String) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val filtered = it.contentSelectors.filterNot { r -> r.id == id }
            it.copy(contentSelectors = filtered)
        }
    }

    suspend fun setHeaderSkipRules(rules: List<SkipRule>) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(headerSkipRules = rules) }
    }

    suspend fun setFooterSkipRules(rules: List<SkipRule>) = withContext(Dispatchers.IO) {
        updateSharedTts { it.copy(footerSkipRules = rules) }
    }

    suspend fun updateHeaderSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.headerSkipRules.map { r -> if (r.id == rule.id) rule else r }
            it.copy(headerSkipRules = updated)
        }
    }

    suspend fun deleteHeaderSkipRule(id: String) = withContext(Dispatchers.IO) {
        updateSharedTts {
            it.copy(headerSkipRules = it.headerSkipRules.filterNot { r -> r.id == id })
        }
    }

    suspend fun toggleHeaderSkipRule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.headerSkipRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
            it.copy(headerSkipRules = updated)
        }
    }

    suspend fun updateFooterSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.footerSkipRules.map { r -> if (r.id == rule.id) rule else r }
            it.copy(footerSkipRules = updated)
        }
    }

    suspend fun deleteFooterSkipRule(id: String) = withContext(Dispatchers.IO) {
        updateSharedTts {
            it.copy(footerSkipRules = it.footerSkipRules.filterNot { r -> r.id == id })
        }
    }

    suspend fun toggleFooterSkipRule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateSharedTts {
            val updated = it.footerSkipRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
            it.copy(footerSkipRules = updated)
        }
    }

    suspend fun updateQuickToolbarSettings(toolbarSettings: com.novelarrow.reader.data.model.QuickToolbarSettings) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = current.copy(quickToolbarSettings = toolbarSettings)
        saveSettings(updated)
        _settingsFlow.value = updated
    }

    suspend fun updateHardwareButtonSettings(hardwareButtonSettings: com.novelarrow.reader.data.model.HardwareButtonSettings) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = current.copy(hardwareButtonSettings = hardwareButtonSettings)
        saveSettings(updated)
        _settingsFlow.value = updated
    }

    suspend fun setQuickToolbarEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = current.copy(
            quickToolbarSettings = current.quickToolbarSettings.copy(enabled = enabled)
        )
        saveSettings(updated)
        _settingsFlow.value = updated
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private fun writeAtomically(file: File, content: String) {
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

    private fun readAtomically(file: File): String? {
        if (!file.exists()) return null
        return try {
            val atomicFile = AtomicFile(file)
            String(atomicFile.readFully(), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to read atomic file ${file.name}: ${e.message}", e)
            null
        }
    }

    private fun loadRawSettings(): ReaderSettings {
        return try {
            val text = readAtomically(settingsFile) ?: return ReaderSettings()
            json.decodeFromString<ReaderSettings>(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to decode reader settings: ${e.message}", e)
            ReaderSettings()
        }
    }

    private fun saveSettings(settings: ReaderSettings) {
        try {
            val text = json.encodeToString(settings)
            writeAtomically(settingsFile, text)
            saveBackupToNovelReaderRoots()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save settings: ${e.message}", e)
        }
    }

    private fun loadRawBrowserSettings(): WebBrowserSettings {
        return try {
            val text = readAtomically(browserSettingsFile) ?: return WebBrowserSettings()
            json.decodeFromString<WebBrowserSettings>(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to decode browser settings: ${e.message}", e)
            WebBrowserSettings()
        }
    }

    private fun saveBrowserSettings(settings: WebBrowserSettings) {
        try {
            val text = json.encodeToString(settings)
            writeAtomically(browserSettingsFile, text)
            saveBackupToNovelReaderRoots()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save browser settings: ${e.message}", e)
        }
    }

    private fun loadSharedTts(reader: ReaderSettings, browser: WebBrowserSettings): SharedTtsSettings {
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
        saveSharedTts(initial)
        return initial
    }

    private fun saveSharedTts(shared: SharedTtsSettings) {
        try {
            val text = json.encodeToString(shared)
            writeAtomically(sharedTtsFile, text)
            saveBackupToNovelReaderRoots()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save shared TTS settings: ${e.message}", e)
        }
    }

    suspend fun applyRestoredBackup(backup: UserPreferencesBackup) = withContext(Dispatchers.IO) {
        val loadedReader = backup.readerSettings
        val loadedBrowser = backup.browserSettings
        val shared = backup.sharedTts

        val mergedReader = loadedReader.copy(
            speechRate = shared.speechRate,
            speechPitch = shared.speechPitch,
            selectedVoiceName = shared.voiceName,
            sleepTimerMinutes = shared.sleepTimerMinutes,
            skipWords = shared.skipWords,
            skipWordsEnabled = shared.skipWordsEnabled,
            skipWordsIgnoreSymbols = shared.skipWordsIgnoreSymbols,
            pronunciationRules = shared.pronunciationRules,
            pronunciationRulesEnabled = shared.pronunciationRulesEnabled,
            headerSkipRules = if (shared.headerSkipRules.isNotEmpty()) shared.headerSkipRules else loadedReader.headerSkipRules,
            footerSkipRules = if (shared.footerSkipRules.isNotEmpty()) shared.footerSkipRules else loadedReader.footerSkipRules,
            favoriteVoiceEnglish = shared.favoriteVoiceEnglish ?: loadedReader.favoriteVoiceEnglish,
            favoriteVoiceArabic = shared.favoriteVoiceArabic ?: loadedReader.favoriteVoiceArabic,
            favoriteVoiceFrench = shared.favoriteVoiceFrench ?: loadedReader.favoriteVoiceFrench,
            selectedEngineType = shared.selectedEngineType,
            selectedTtsEnginePackage = shared.selectedTtsEnginePackage ?: loadedReader.selectedTtsEnginePackage,
            contentSelectors = if (shared.contentSelectors.isNotEmpty()) shared.contentSelectors else loadedReader.contentSelectors
        )
        val mergedBrowser = loadedBrowser.copy(
            ttsRate = shared.speechRate,
            ttsPitch = shared.speechPitch,
            ttsVoice = shared.voiceName,
            sleepTimerMinutes = shared.sleepTimerMinutes,
            skipWords = shared.skipWords,
            skipWordsEnabled = shared.skipWordsEnabled,
            skipWordsIgnoreSymbols = shared.skipWordsIgnoreSymbols,
            pronunciationRules = shared.pronunciationRules,
            pronunciationRulesEnabled = shared.pronunciationRulesEnabled
        )

        saveSettings(mergedReader)
        saveBrowserSettings(mergedBrowser)
        saveSharedTts(shared)

        _settingsFlow.value = mergedReader
        _browserSettingsFlow.value = mergedBrowser
    }

    private fun getNovelReaderRoots(): List<File> {
        return com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)
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
                skipWordsIgnoreSymbols = currentReader.skipWordsIgnoreSymbols,
                pronunciationRules = currentReader.pronunciationRules,
                pronunciationRulesEnabled = currentReader.pronunciationRulesEnabled,
                headerSkipRules = currentReader.headerSkipRules,
                footerSkipRules = currentReader.footerSkipRules,
                favoriteVoiceEnglish = currentReader.favoriteVoiceEnglish,
                favoriteVoiceArabic = currentReader.favoriteVoiceArabic,
                favoriteVoiceFrench = currentReader.favoriteVoiceFrench,
                selectedEngineType = currentReader.selectedEngineType,
                selectedTtsEnginePackage = currentReader.selectedTtsEnginePackage,
                contentSelectors = currentReader.contentSelectors
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
