package com.novelarrow.reader.data.repository

import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.WebBrowserSettings

internal object SharedTtsManager {

    fun buildCurrentSharedTts(reader: ReaderSettings, browser: WebBrowserSettings): SharedTtsSettings {
        return SharedTtsSettings(
            speechRate = reader.speechRate,
            speechPitch = reader.speechPitch,
            voiceName = reader.selectedVoiceName,
            sleepTimerMinutes = reader.sleepTimerMinutes,
            skipWords = reader.skipWords,
            skipWordsEnabled = reader.skipWordsEnabled,
            skipWordsIgnoreSymbols = reader.skipWordsIgnoreSymbols,
            pronunciationRules = reader.pronunciationRules,
            pronunciationRulesEnabled = reader.pronunciationRulesEnabled,
            headerSkipRules = reader.headerSkipRules,
            footerSkipRules = reader.footerSkipRules,
            favoriteVoiceEnglish = reader.favoriteVoiceEnglish,
            favoriteVoiceArabic = reader.favoriteVoiceArabic,
            favoriteVoiceFrench = reader.favoriteVoiceFrench,
            selectedEngineType = reader.selectedEngineType,
            selectedTtsEnginePackage = reader.selectedTtsEnginePackage,
            contentSelectors = reader.contentSelectors,
            ignoredSelectors = reader.ignoredSelectors.ifEmpty { browser.ignoredSelectors }
        )
    }

    fun mergeIntoReader(reader: ReaderSettings, shared: SharedTtsSettings): ReaderSettings {
        return reader.copy(
            speechRate = shared.speechRate,
            speechPitch = shared.speechPitch,
            selectedVoiceName = shared.voiceName,
            sleepTimerMinutes = shared.sleepTimerMinutes,
            skipWords = shared.skipWords,
            skipWordsEnabled = shared.skipWordsEnabled,
            skipWordsIgnoreSymbols = shared.skipWordsIgnoreSymbols,
            pronunciationRules = shared.pronunciationRules,
            pronunciationRulesEnabled = shared.pronunciationRulesEnabled,
            headerSkipRules = if (shared.headerSkipRules.isNotEmpty()) shared.headerSkipRules else reader.headerSkipRules,
            footerSkipRules = if (shared.footerSkipRules.isNotEmpty()) shared.footerSkipRules else reader.footerSkipRules,
            favoriteVoiceEnglish = shared.favoriteVoiceEnglish ?: reader.favoriteVoiceEnglish,
            favoriteVoiceArabic = shared.favoriteVoiceArabic ?: reader.favoriteVoiceArabic,
            favoriteVoiceFrench = shared.favoriteVoiceFrench ?: reader.favoriteVoiceFrench,
            selectedEngineType = shared.selectedEngineType,
            selectedTtsEnginePackage = shared.selectedTtsEnginePackage ?: reader.selectedTtsEnginePackage,
            contentSelectors = if (shared.contentSelectors.isNotEmpty()) shared.contentSelectors else reader.contentSelectors,
            ignoredSelectors = shared.ignoredSelectors
        )
    }

    fun mergeIntoBrowser(browser: WebBrowserSettings, shared: SharedTtsSettings): WebBrowserSettings {
        return browser.copy(
            ttsRate = shared.speechRate,
            ttsPitch = shared.speechPitch,
            ttsVoice = shared.voiceName,
            sleepTimerMinutes = shared.sleepTimerMinutes,
            skipWords = shared.skipWords,
            skipWordsEnabled = shared.skipWordsEnabled,
            skipWordsIgnoreSymbols = shared.skipWordsIgnoreSymbols,
            pronunciationRules = shared.pronunciationRules,
            pronunciationRulesEnabled = shared.pronunciationRulesEnabled,
            contentSelectors = shared.contentSelectors,
            headerSkipRules = shared.headerSkipRules,
            ignoredSelectors = shared.ignoredSelectors
        )
    }

    fun addPronunciationRule(shared: SharedTtsSettings, rule: PronunciationRule): SharedTtsSettings {
        val filtered = shared.pronunciationRules.filterNot { r -> r.id == rule.id || r.from.equals(rule.from, ignoreCase = true) }
        return shared.copy(pronunciationRules = filtered + rule)
    }

    fun updatePronunciationRule(shared: SharedTtsSettings, rule: PronunciationRule): SharedTtsSettings {
        val updated = shared.pronunciationRules.map { r -> if (r.id == rule.id) rule else r }
        return shared.copy(pronunciationRules = updated)
    }

    fun togglePronunciationRule(shared: SharedTtsSettings, id: String, enabled: Boolean): SharedTtsSettings {
        val updated = shared.pronunciationRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
        return shared.copy(pronunciationRules = updated)
    }

    fun deletePronunciationRule(shared: SharedTtsSettings, id: String): SharedTtsSettings {
        return shared.copy(pronunciationRules = shared.pronunciationRules.filterNot { r -> r.id == id })
    }

    fun removePronunciationRule(shared: SharedTtsSettings, index: Int): SharedTtsSettings {
        return shared.copy(pronunciationRules = shared.pronunciationRules.filterIndexed { i, _ -> i != index })
    }

    fun addSkipWord(shared: SharedTtsSettings, skip: SkipWord): SharedTtsSettings {
        val filtered = shared.skipWords.filterNot { s -> s.id == skip.id || s.text.equals(skip.text, ignoreCase = true) }
        return shared.copy(skipWords = filtered + skip)
    }

    fun updateSkipWord(shared: SharedTtsSettings, word: SkipWord): SharedTtsSettings {
        val updated = shared.skipWords.map { w -> if (w.id == word.id) word else w }
        return shared.copy(skipWords = updated)
    }

    fun toggleSkipWord(shared: SharedTtsSettings, id: String, enabled: Boolean): SharedTtsSettings {
        val updated = shared.skipWords.map { w -> if (w.id == id) w.copy(enabled = enabled) else w }
        return shared.copy(skipWords = updated)
    }

    fun deleteSkipWord(shared: SharedTtsSettings, id: String): SharedTtsSettings {
        return shared.copy(skipWords = shared.skipWords.filterNot { w -> w.id == id })
    }

    fun removeSkipWord(shared: SharedTtsSettings, index: Int): SharedTtsSettings {
        return shared.copy(skipWords = shared.skipWords.filterIndexed { i, _ -> i != index })
    }

    fun addHeaderSkipRule(shared: SharedTtsSettings, rule: SkipRule): SharedTtsSettings {
        val filtered = shared.headerSkipRules.filterNot { r -> r.text.equals(rule.text, ignoreCase = true) }
        return shared.copy(headerSkipRules = filtered + rule)
    }

    fun removeHeaderSkipRule(shared: SharedTtsSettings, index: Int): SharedTtsSettings {
        return shared.copy(headerSkipRules = shared.headerSkipRules.filterIndexed { i, _ -> i != index })
    }

    fun updateHeaderSkipRule(shared: SharedTtsSettings, rule: SkipRule): SharedTtsSettings {
        val updated = shared.headerSkipRules.map { r -> if (r.id == rule.id) rule else r }
        return shared.copy(headerSkipRules = updated)
    }

    fun deleteHeaderSkipRule(shared: SharedTtsSettings, id: String): SharedTtsSettings {
        return shared.copy(headerSkipRules = shared.headerSkipRules.filterNot { r -> r.id == id })
    }

    fun toggleHeaderSkipRule(shared: SharedTtsSettings, id: String, enabled: Boolean): SharedTtsSettings {
        val updated = shared.headerSkipRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
        return shared.copy(headerSkipRules = updated)
    }

    fun addFooterSkipRule(shared: SharedTtsSettings, rule: SkipRule): SharedTtsSettings {
        val filtered = shared.footerSkipRules.filterNot { r -> r.text.equals(rule.text, ignoreCase = true) }
        return shared.copy(footerSkipRules = filtered + rule)
    }

    fun removeFooterSkipRule(shared: SharedTtsSettings, index: Int): SharedTtsSettings {
        return shared.copy(footerSkipRules = shared.footerSkipRules.filterIndexed { i, _ -> i != index })
    }

    fun updateFooterSkipRule(shared: SharedTtsSettings, rule: SkipRule): SharedTtsSettings {
        val updated = shared.footerSkipRules.map { r -> if (r.id == rule.id) rule else r }
        return shared.copy(footerSkipRules = updated)
    }

    fun deleteFooterSkipRule(shared: SharedTtsSettings, id: String): SharedTtsSettings {
        return shared.copy(footerSkipRules = shared.footerSkipRules.filterNot { r -> r.id == id })
    }

    fun toggleFooterSkipRule(shared: SharedTtsSettings, id: String, enabled: Boolean): SharedTtsSettings {
        val updated = shared.footerSkipRules.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
        return shared.copy(footerSkipRules = updated)
    }

    fun addContentSelector(shared: SharedTtsSettings, rule: ContentSelectorRule): SharedTtsSettings {
        val filtered = shared.contentSelectors.filterNot { r -> r.id == rule.id }
        return shared.copy(contentSelectors = filtered + rule)
    }

    fun updateContentSelector(shared: SharedTtsSettings, rule: ContentSelectorRule): SharedTtsSettings {
        val updated = shared.contentSelectors.map { r -> if (r.id == rule.id) rule else r }
        return shared.copy(contentSelectors = updated)
    }

    fun toggleContentSelector(shared: SharedTtsSettings, id: String, enabled: Boolean): SharedTtsSettings {
        val updated = shared.contentSelectors.map { r -> if (r.id == id) r.copy(enabled = enabled) else r }
        return shared.copy(contentSelectors = updated)
    }

    fun removeContentSelector(shared: SharedTtsSettings, id: String): SharedTtsSettings {
        return shared.copy(contentSelectors = shared.contentSelectors.filterNot { r -> r.id == id })
    }
}
