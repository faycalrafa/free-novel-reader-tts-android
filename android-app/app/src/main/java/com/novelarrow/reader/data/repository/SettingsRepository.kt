package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.BrowserTab
import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.NextChapterSelectorRule
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.data.model.WebBrowserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val settingsFile: File get() = File(context.filesDir, "novel_settings_v1.json")
    private val browserSettingsFile: File get() = File(context.filesDir, "novel_browser_settings_v1.json")
    private val sharedTtsFile: File get() = File(context.filesDir, "novel_shared_tts_settings_v1.json")

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
        } catch (_: Exception) {
            context.filesDir.absolutePath
        }

        synchronized(SettingsRepository::class.java) {
            if (!sharedSettingsFlows.containsKey(cacheKey) || !sharedBrowserFlows.containsKey(cacheKey)) {
                val backup = SettingsBackupDelegate.restoreBackupFromNovelReaderRoots(context, json)
                val isFirstInstall = !settingsFile.exists() && !browserSettingsFile.exists() && !sharedTtsFile.exists()
                val shouldRestoreBackup = isFirstInstall && backup != null

                val loadedReader = if (backup != null && isFirstInstall) {
                    backup.readerSettings
                } else {
                    SettingsPersistenceManager.loadRawSettings(settingsFile, json)
                }

                val loadedBrowser = if (backup != null && isFirstInstall) {
                    backup.browserSettings
                } else {
                    SettingsPersistenceManager.loadRawBrowserSettings(browserSettingsFile, json)
                }

                val shared = if (backup != null && isFirstInstall) {
                    backup.sharedTts
                } else {
                    SettingsPersistenceManager.loadSharedTts(sharedTtsFile, loadedReader, loadedBrowser, json)
                }

                val mergedReader = SharedTtsManager.mergeIntoReader(loadedReader, shared)
                val mergedBrowser = SharedTtsManager.mergeIntoBrowser(loadedBrowser, shared)

                sharedSettingsFlows[cacheKey] = MutableStateFlow(mergedReader)
                sharedBrowserFlows[cacheKey] = MutableStateFlow(mergedBrowser)

                if (shouldRestoreBackup) {
                    SettingsPersistenceManager.saveSettings(settingsFile, mergedReader, json)
                    SettingsPersistenceManager.saveBrowserSettings(browserSettingsFile, mergedBrowser, json)
                    SettingsPersistenceManager.saveSharedTts(sharedTtsFile, shared, json)
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
            SettingsPersistenceManager.saveSettings(settingsFile, updated, json)
            saveBackupToNovelReaderRoots()
        }
    }

    suspend fun setTheme(theme: ThemeMode) = updateSettings { it.copy(theme = theme) }
    suspend fun setFontSize(size: Float) = updateSettings { it.copy(fontSizeSp = size) }
    suspend fun setAutoScroll(enabled: Boolean) = updateSettings { it.copy(autoScroll = enabled) }
    suspend fun setAutoNext(enabled: Boolean) = updateSettings { it.copy(autoNextChapter = enabled) }
    suspend fun setSoundCuesEnabled(enabled: Boolean) = updateSettings { it.copy(soundCuesEnabled = enabled) }
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
            SettingsPersistenceManager.saveBrowserSettings(browserSettingsFile, updated, json)
            _browserSettingsFlow.value = updated
            saveBackupToNovelReaderRoots()
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
        updateSharedTts { it.copy(ignoredSelectors = defaults) }
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
        SettingsPersistenceManager.saveBrowserSettings(browserSettingsFile, defaults, json)
        _browserSettingsFlow.value = defaults
    }

    suspend fun updateBrowserTabs(tabs: List<BrowserTab>, currentTabId: String) = updateBrowserSettings {
        it.copy(tabs = tabs, currentTabId = currentTabId)
    }

    suspend fun addNextChapterRule(rule: NextChapterSelectorRule) = updateBrowserSettings {
        it.copy(nextChapterRules = it.nextChapterRules.filterNot { r -> r.id == rule.id } + rule)
    }

    suspend fun updateNextChapterRule(rule: NextChapterSelectorRule) = updateBrowserSettings {
        it.copy(nextChapterRules = it.nextChapterRules.map { r -> if (r.id == rule.id) rule else r })
    }

    suspend fun toggleNextChapterRule(id: String, enabled: Boolean) = updateBrowserSettings {
        it.copy(nextChapterRules = it.nextChapterRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r })
    }

    suspend fun deleteNextChapterRule(id: String) = updateBrowserSettings {
        it.copy(nextChapterRules = it.nextChapterRules.filterNot { r -> r.id == id })
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
        it.copy(navbarShortcuts = it.navbarShortcuts.filterNot { s -> s.id == shortcut.id } + shortcut)
    }

    suspend fun updateNavbarShortcut(shortcut: BrowserNavbarShortcut) = updateBrowserSettings {
        it.copy(navbarShortcuts = it.navbarShortcuts.map { s -> if (s.id == shortcut.id) shortcut else s })
    }

    suspend fun toggleNavbarShortcut(id: String, enabled: Boolean) = updateBrowserSettings {
        it.copy(navbarShortcuts = it.navbarShortcuts.map { s -> if (s.id == id) s.copy(enabled = enabled) else s })
    }

    suspend fun deleteNavbarShortcut(id: String) = updateBrowserSettings {
        it.copy(navbarShortcuts = it.navbarShortcuts.filterNot { s -> s.id == id })
    }

    suspend fun removeNavbarShortcut(id: String) = deleteNavbarShortcut(id)

    suspend fun updateToolbarButtonOrder(order: List<String>) = updateBrowserSettings {
        it.copy(toolbarButtonOrder = order)
    }

    private suspend fun updateSharedTts(transform: (SharedTtsSettings) -> SharedTtsSettings) {
        mutex.withLock {
            val currentShared = SharedTtsManager.buildCurrentSharedTts(_settingsFlow.value, _browserSettingsFlow.value)
            val newShared = transform(currentShared)
            val updatedReader = SharedTtsManager.mergeIntoReader(_settingsFlow.value, newShared)
            val updatedBrowser = SharedTtsManager.mergeIntoBrowser(_browserSettingsFlow.value, newShared)

            _settingsFlow.value = updatedReader
            _browserSettingsFlow.value = updatedBrowser

            SettingsPersistenceManager.saveSharedTts(sharedTtsFile, newShared, json)
            SettingsPersistenceManager.saveSettings(settingsFile, updatedReader, json)
            SettingsPersistenceManager.saveBrowserSettings(browserSettingsFile, updatedBrowser, json)
            saveBackupToNovelReaderRoots()
        }
    }

    suspend fun setSpeechRate(rate: Float) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(speechRate = rate) } }
    suspend fun setBrowserRate(rate: Float) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(speechRate = rate) } }
    suspend fun setSpeechPitch(pitch: Float) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(speechPitch = pitch) } }
    suspend fun setBrowserPitch(pitch: Float) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(speechPitch = pitch) } }
    suspend fun setVoice(voiceName: String?) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(voiceName = voiceName) } }
    suspend fun setBrowserVoice(voice: String?) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(voiceName = voice) } }

    suspend fun addPronunciationRule(rule: PronunciationRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.addPronunciationRule(it, rule) } }
    suspend fun addBrowserPronunciationRule(rule: PronunciationRule) = addPronunciationRule(rule)
    suspend fun updatePronunciationRule(rule: PronunciationRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.updatePronunciationRule(it, rule) } }
    suspend fun togglePronunciationRule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.togglePronunciationRule(it, id, enabled) } }
    suspend fun deletePronunciationRule(id: String) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.deletePronunciationRule(it, id) } }
    suspend fun removePronunciationRule(index: Int) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.removePronunciationRule(it, index) } }
    suspend fun removeBrowserPronunciationRule(index: Int) = removePronunciationRule(index)

    suspend fun addSkipWord(skip: SkipWord) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.addSkipWord(it, skip) } }
    suspend fun addBrowserSkipWord(skip: SkipWord) = addSkipWord(skip)
    suspend fun updateSkipWord(word: SkipWord) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.updateSkipWord(it, word) } }
    suspend fun toggleSkipWord(id: String, enabled: Boolean) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.toggleSkipWord(it, id, enabled) } }
    suspend fun deleteSkipWord(id: String) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.deleteSkipWord(it, id) } }
    suspend fun removeSkipWord(index: Int) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.removeSkipWord(it, index) } }
    suspend fun removeBrowserSkipWord(index: Int) = removeSkipWord(index)

    suspend fun addHeaderSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.addHeaderSkipRule(it, rule) } }
    suspend fun removeHeaderSkipRule(index: Int) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.removeHeaderSkipRule(it, index) } }
    suspend fun addFooterSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.addFooterSkipRule(it, rule) } }
    suspend fun removeFooterSkipRule(index: Int) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.removeFooterSkipRule(it, index) } }

    suspend fun setFavoriteVoice(language: String, voiceName: String?) = withContext(Dispatchers.IO) {
        updateSharedTts {
            when (language.lowercase().take(2)) {
                "ar" -> it.copy(favoriteVoiceArabic = voiceName)
                "fr" -> it.copy(favoriteVoiceFrench = voiceName)
                else -> it.copy(favoriteVoiceEnglish = voiceName)
            }
        }
    }

    suspend fun setSelectedEngineType(engineType: String) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(selectedEngineType = engineType) } }
    suspend fun setSelectedTtsEnginePackage(enginePackage: String?) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(selectedTtsEnginePackage = enginePackage) } }
    suspend fun setContentSelectors(selectors: List<ContentSelectorRule>) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(contentSelectors = selectors) } }
    suspend fun addContentSelector(rule: ContentSelectorRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.addContentSelector(it, rule) } }
    suspend fun updateContentSelector(rule: ContentSelectorRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.updateContentSelector(it, rule) } }
    suspend fun toggleContentSelector(id: String, enabled: Boolean) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.toggleContentSelector(it, id, enabled) } }
    suspend fun removeContentSelector(id: String) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.removeContentSelector(it, id) } }

    suspend fun setHeaderSkipRules(rules: List<SkipRule>) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(headerSkipRules = rules) } }
    suspend fun setFooterSkipRules(rules: List<SkipRule>) = withContext(Dispatchers.IO) { updateSharedTts { it.copy(footerSkipRules = rules) } }
    suspend fun updateHeaderSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.updateHeaderSkipRule(it, rule) } }
    suspend fun deleteHeaderSkipRule(id: String) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.deleteHeaderSkipRule(it, id) } }
    suspend fun toggleHeaderSkipRule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.toggleHeaderSkipRule(it, id, enabled) } }
    suspend fun updateFooterSkipRule(rule: SkipRule) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.updateFooterSkipRule(it, rule) } }
    suspend fun deleteFooterSkipRule(id: String) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.deleteFooterSkipRule(it, id) } }
    suspend fun toggleFooterSkipRule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) { updateSharedTts { SharedTtsManager.toggleFooterSkipRule(it, id, enabled) } }

    suspend fun updateQuickToolbarSettings(toolbarSettings: com.novelarrow.reader.data.model.QuickToolbarSettings) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = current.copy(quickToolbarSettings = toolbarSettings)
        SettingsPersistenceManager.saveSettings(settingsFile, updated, json)
        _settingsFlow.value = updated
        saveBackupToNovelReaderRoots()
    }

    suspend fun updateHardwareButtonSettings(hardwareButtonSettings: com.novelarrow.reader.data.model.HardwareButtonSettings) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = current.copy(hardwareButtonSettings = hardwareButtonSettings)
        SettingsPersistenceManager.saveSettings(settingsFile, updated, json)
        _settingsFlow.value = updated
        saveBackupToNovelReaderRoots()
    }

    suspend fun setQuickToolbarEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = current.copy(
            quickToolbarSettings = current.quickToolbarSettings.copy(enabled = enabled)
        )
        SettingsPersistenceManager.saveSettings(settingsFile, updated, json)
        _settingsFlow.value = updated
        saveBackupToNovelReaderRoots()
    }

    suspend fun applyRestoredBackup(backup: UserPreferencesBackup) = withContext(Dispatchers.IO) {
        val mergedReader = SharedTtsManager.mergeIntoReader(backup.readerSettings, backup.sharedTts)
        val mergedBrowser = SharedTtsManager.mergeIntoBrowser(backup.browserSettings, backup.sharedTts)

        SettingsPersistenceManager.saveSettings(settingsFile, mergedReader, json)
        SettingsPersistenceManager.saveBrowserSettings(browserSettingsFile, mergedBrowser, json)
        SettingsPersistenceManager.saveSharedTts(sharedTtsFile, backup.sharedTts, json)

        _settingsFlow.value = mergedReader
        _browserSettingsFlow.value = mergedBrowser
        saveBackupToNovelReaderRoots()
    }

    fun saveBackupToNovelReaderRoots() {
        val currentReader = _settingsFlow.value
        val currentBrowser = _browserSettingsFlow.value
        val currentShared = SharedTtsManager.buildCurrentSharedTts(currentReader, currentBrowser)
        val backup = UserPreferencesBackup(
            readerSettings = currentReader,
            browserSettings = currentBrowser,
            sharedTts = currentShared,
            lastUpdated = System.currentTimeMillis()
        )
        SettingsBackupDelegate.saveBackupToNovelReaderRoots(context, backup, json)
    }
}
