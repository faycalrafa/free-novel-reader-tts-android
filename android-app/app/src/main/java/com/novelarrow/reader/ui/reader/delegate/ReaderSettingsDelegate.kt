package com.novelarrow.reader.ui.reader.delegate

import android.app.Application
import com.novelarrow.reader.data.model.*
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.QuickToolbarNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReaderSettingsDelegate(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val getSettings: () -> ReaderSettings,
    private val getCurrentChapter: () -> Chapter?,
    private val reloadCurrentChapter: (Chapter) -> Unit
) {
    fun updateTheme(theme: ThemeMode) {
        scope.launch { settingsRepository.setTheme(theme) }
    }

    fun updateFontSize(sizeSp: Float) {
        scope.launch { settingsRepository.setFontSize(sizeSp) }
    }

    fun updateSpeechRate(rate: Float) {
        scope.launch {
            settingsRepository.setSpeechRate(rate)
            AudioPlaybackCoordinator.updateTtsSettings(
                rate = rate,
                pitch = getSettings().speechPitch,
                voiceName = getSettings().selectedVoiceName
            )
        }
    }

    fun cycleSpeechRate() {
        AudioPlaybackCoordinator.cycleSpeechRate()
    }

    fun updateSpeechPitch(pitch: Float) {
        scope.launch {
            settingsRepository.setSpeechPitch(pitch)
            AudioPlaybackCoordinator.updateTtsSettings(
                rate = getSettings().speechRate,
                pitch = pitch,
                voiceName = getSettings().selectedVoiceName
            )
        }
    }

    fun updateVoice(voiceName: String?) {
        scope.launch {
            settingsRepository.setVoice(voiceName)
            AudioPlaybackCoordinator.updateTtsSettings(
                rate = getSettings().speechRate,
                pitch = getSettings().speechPitch,
                voiceName = voiceName
            )
        }
    }

    fun updateAutoScroll(enabled: Boolean) {
        scope.launch { settingsRepository.setAutoScroll(enabled) }
    }

    fun updateAutoNext(enabled: Boolean) {
        scope.launch { settingsRepository.setAutoNext(enabled) }
    }

    fun updateSoundCuesEnabled(enabled: Boolean) {
        scope.launch { settingsRepository.setSoundCuesEnabled(enabled) }
    }

    fun updateQuickToolbarSettings(toolbarSettings: QuickToolbarSettings) {
        scope.launch {
            settingsRepository.updateQuickToolbarSettings(toolbarSettings)
            QuickToolbarNotificationManager.updateToolbar(application)
        }
    }

    fun updateHardwareButtonSettings(hardwareButtonSettings: HardwareButtonSettings) {
        scope.launch {
            settingsRepository.updateHardwareButtonSettings(hardwareButtonSettings)
        }
    }

    fun updateContentSelector(selector: String) {
        scope.launch {
            settingsRepository.setContentSelector(selector)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateContentSelectorEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setContentSelectorEnabled(enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateHeaderSkipString(headerStr: String) {
        scope.launch {
            settingsRepository.setHeaderSkipString(headerStr)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateHeaderSkipEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setHeaderSkipEnabled(enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateFooterSkipString(footerStr: String) {
        scope.launch {
            settingsRepository.setFooterSkipString(footerStr)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateFooterSkipEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setFooterSkipEnabled(enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateSkipWordsEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setSkipWordsEnabled(enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun setSkipWordsEnabled(enabled: Boolean) {
        updateSkipWordsEnabled(enabled)
    }

    fun updateSkipWordsIgnoreSymbols(enabled: Boolean) {
        scope.launch {
            settingsRepository.setSkipWordsIgnoreSymbols(enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addSkipWord(word: String, exactMatch: Boolean) {
        scope.launch {
            settingsRepository.addSkipWord(SkipWord(text = word, exactMatch = exactMatch))
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addSkipWord(skipWord: SkipWord) {
        scope.launch {
            settingsRepository.addSkipWord(skipWord)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateSkipWord(word: SkipWord) {
        scope.launch {
            settingsRepository.updateSkipWord(word)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun toggleSkipWord(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.toggleSkipWord(id, enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun deleteSkipWord(id: String) {
        scope.launch {
            settingsRepository.deleteSkipWord(id)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun removeSkipWord(index: Int) {
        scope.launch {
            settingsRepository.removeSkipWord(index)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun setPronunciationRulesEnabled(enabled: Boolean) {
        updatePronunciationRulesEnabled(enabled)
    }

    fun updatePronunciationRulesEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setPronunciationRulesEnabled(enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addPronunciationRule(from: String, to: String, exactMatch: Boolean) {
        scope.launch {
            settingsRepository.addPronunciationRule(PronunciationRule(from = from, to = to, exactMatch = exactMatch))
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addPronunciationRule(rule: PronunciationRule) {
        scope.launch {
            settingsRepository.addPronunciationRule(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updatePronunciationRule(rule: PronunciationRule) {
        scope.launch {
            settingsRepository.updatePronunciationRule(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun togglePronunciationRule(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.togglePronunciationRule(id, enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun deletePronunciationRule(id: String) {
        scope.launch {
            settingsRepository.deletePronunciationRule(id)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun removePronunciationRule(index: Int) {
        scope.launch {
            settingsRepository.removePronunciationRule(index)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addHeaderSkipRule(text: String, exact: Boolean) {
        scope.launch {
            settingsRepository.addHeaderSkipRule(SkipRule(text = text, exactMatch = exact))
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun removeHeaderSkipRule(index: Int) {
        scope.launch {
            settingsRepository.removeHeaderSkipRule(index)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addContentSelector(rule: ContentSelectorRule) {
        scope.launch {
            settingsRepository.addContentSelector(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateContentSelector(rule: ContentSelectorRule) {
        scope.launch {
            settingsRepository.updateContentSelector(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun toggleContentSelector(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.toggleContentSelector(id, enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun deleteContentSelector(id: String) {
        scope.launch {
            settingsRepository.removeContentSelector(id)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addHeaderSkipRule(rule: SkipRule) {
        scope.launch {
            settingsRepository.addHeaderSkipRule(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateHeaderSkipRule(rule: SkipRule) {
        scope.launch {
            settingsRepository.updateHeaderSkipRule(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun toggleHeaderSkipRule(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.toggleHeaderSkipRule(id, enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun deleteHeaderSkipRule(id: String) {
        scope.launch {
            settingsRepository.deleteHeaderSkipRule(id)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addFooterSkipRule(rule: SkipRule) {
        scope.launch {
            settingsRepository.addFooterSkipRule(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun updateFooterSkipRule(rule: SkipRule) {
        scope.launch {
            settingsRepository.updateFooterSkipRule(rule)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun toggleFooterSkipRule(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.toggleFooterSkipRule(id, enabled)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun deleteFooterSkipRule(id: String) {
        scope.launch {
            settingsRepository.deleteFooterSkipRule(id)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }

    fun addFooterSkipRule(text: String, exact: Boolean) {
        addFooterSkipRule(SkipRule(text = text, exactMatch = exact))
    }

    fun removeFooterSkipRule(index: Int) {
        scope.launch {
            settingsRepository.removeFooterSkipRule(index)
            getCurrentChapter()?.let { reloadCurrentChapter(it) }
        }
    }
}
