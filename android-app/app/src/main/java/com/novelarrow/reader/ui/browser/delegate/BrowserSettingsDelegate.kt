package com.novelarrow.reader.ui.browser.delegate

import android.app.Application
import com.novelarrow.reader.browser.engine.WebContentExtractor
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.ui.browser.BrowserUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONTokener

class BrowserSettingsDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val settingsRepository: SettingsRepository,
    private val dispatchEvaluateJs: (String, ((String?) -> Unit)?) -> Unit,
    private val reparseActivePage: () -> Unit
) {
    fun playClickSound() {
        // Native view sound effects are handled by Compose UI without allocations
    }

    fun buildContentExtractionScript(contentSelector: String, ignoredSelectors: List<String>): String {
        return WebContentExtractor.buildContentExtractionScript(contentSelector, ignoredSelectors)
    }

    fun openSettingsDialog() {
        uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun dismissSettingsDialog() {
        stopVoiceTest()
        uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun testVoicePreview() {
        if (uiState.value.isVoiceTesting) {
            stopVoiceTest()
            return
        }
        uiState.update { it.copy(isVoiceTesting = true) }
        val sampleText = "Hello, this is a preview of the selected voice for BookReader."
        val settings = uiState.value.settings
        val previewPara = Paragraph(
            index = 0,
            rawText = sampleText,
            spokenText = sampleText,
            isHeading = false
        )
        AudioPlaybackCoordinator.playBrowserParagraphs(
            context = application,
            chapterTitle = "Voice Preview",
            paragraphs = listOf(previewPara),
            startParagraphIndex = 0,
            rate = settings.ttsRate,
            pitch = settings.ttsPitch,
            voiceName = settings.ttsVoice
        )
        scope.launch {
            delay(4000)
            stopVoiceTest()
        }
    }

    private fun stopVoiceTest() {
        if (uiState.value.isVoiceTesting) {
            AudioPlaybackCoordinator.stop()
            uiState.update { it.copy(isVoiceTesting = false) }
        }
    }

    fun updateNextSelector(selector: String) {
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(nextSelector = selector) }
        }
    }

    fun updateContentSelector(selector: String) {
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(contentSelector = selector) }
        }
    }

    fun toggleAutoClickOnScroll(enabled: Boolean) {
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(autoClickOnScroll = enabled) }
        }
    }

    fun toggleAutoNextOnFinishedPage(enabled: Boolean) {
        AudioPlaybackCoordinator.autoNextOnFinishedPage = enabled
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(autoNextOnFinishedPage = enabled) }
        }
        uiState.update { it.copy(settings = it.settings.copy(autoNextOnFinishedPage = enabled)) }
    }

    fun toggleAutoReadNextChapter(enabled: Boolean) {
        scope.launch {
            settingsRepository.setAutoReadNextChapter(enabled)
        }
        uiState.update { it.copy(settings = it.settings.copy(autoReadNextChapter = enabled)) }
    }

    fun toggleTtsAutoStart(enabled: Boolean) {
        scope.launch {
            settingsRepository.setBrowserTtsAutoStart(enabled)
        }
    }

    fun toggleTtsAutoScroll(enabled: Boolean) {
        scope.launch {
            settingsRepository.setBrowserAutoScroll(enabled)
        }
    }

    fun toggleTtsSynthByParagraph(enabled: Boolean) {
        scope.launch {
            settingsRepository.setBrowserSynthByParagraph(enabled)
        }
    }

    fun updateVoice(voiceName: String?) {
        AudioPlaybackCoordinator.updateVoice(voiceName)
    }

    fun updateRate(rate: Float) {
        AudioPlaybackCoordinator.updateSpeechRate(rate)
    }

    fun updatePitch(pitch: Float) {
        AudioPlaybackCoordinator.updateSpeechPitch(pitch)
    }

    fun updateLoopCount(count: Int) {
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(loopCount = count) }
        }
    }

    fun updateCountdownDelayMs(ms: Long) {
        scope.launch {
            settingsRepository.updateBrowserSettings { it.copy(countdownDelayMs = ms) }
        }
    }

    fun addIgnoredSelector(selector: String) {
        scope.launch {
            settingsRepository.addBrowserIgnoredSelector(selector)
        }
    }

    fun removeIgnoredSelector(selector: String) {
        scope.launch {
            settingsRepository.removeBrowserIgnoredSelector(selector)
        }
    }

    fun resetIgnoredSelectors() {
        scope.launch {
            settingsRepository.resetBrowserIgnoredSelectors()
        }
    }

    fun addSkipWord(text: String, exact: Boolean) {
        if (text.isBlank()) return
        scope.launch {
            settingsRepository.addBrowserSkipWord(SkipWord(text.trim(), exact))
        }
    }

    fun removeSkipWord(index: Int) {
        scope.launch {
            settingsRepository.removeBrowserSkipWord(index)
        }
    }

    fun addPronunciationRule(from: String, to: String, exact: Boolean) {
        if (from.isBlank()) return
        scope.launch {
            settingsRepository.addBrowserPronunciationRule(PronunciationRule(from.trim(), to.trim(), exact))
        }
    }

    fun removePronunciationRule(index: Int) {
        scope.launch {
            settingsRepository.removeBrowserPronunciationRule(index)
        }
    }

    fun togglePronunciationRulesEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setBrowserPronunciationRulesEnabled(enabled)
        }
    }

    fun toggleSkipWordsEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setBrowserSkipWordsEnabled(enabled)
        }
    }

    fun resetSettingsToDefaults() {
        scope.launch {
            settingsRepository.resetBrowserSettingsToDefaults()
        }
    }

    fun extractAndShowRawHtml() {
        val settings = uiState.value.settings
        val script = WebContentExtractor.buildRawHtmlExtractionScript(settings.contentSelector)

        dispatchEvaluateJs(script) { result ->
            val rawHtml = if (!result.isNullOrBlank() && result != "null") {
                try {
                    JSONTokener(result).nextValue().toString()
                } catch (_: Exception) {
                    result
                }
            } else {
                "<html><body><p>No content detected</p></body></html>"
            }
            uiState.update {
                it.copy(
                    rawHtmlContent = rawHtml,
                    showRawEditor = true
                )
            }
        }
    }

    fun dismissRawEditor() {
        uiState.update { it.copy(showRawEditor = false, rawHtmlContent = null) }
    }

    fun clearToast() {
        uiState.update { it.copy(toastMessage = null) }
    }

    fun resetFlashHandleRed() {
        uiState.update { it.copy(flashHandleRed = false) }
    }

    fun toggleActiveRule(rule: RuleImpact, enabled: Boolean) {
        scope.launch {
            when (rule.ruleType) {
                RuleType.CONTENT_CONTAINER, RuleType.CONTENT_SELECTOR -> {
                    settingsRepository.toggleContentSelector(rule.id, enabled)
                }
                RuleType.IGNORED_SELECTOR -> {
                    val cleanSel = rule.name
                    if (enabled) {
                        settingsRepository.addBrowserIgnoredSelector(cleanSel)
                    } else {
                        settingsRepository.removeBrowserIgnoredSelector(cleanSel)
                    }
                }
                RuleType.SKIP_WORD -> {
                    settingsRepository.toggleSkipWord(rule.id, enabled)
                }
                RuleType.PRONUNCIATION -> {
                    settingsRepository.togglePronunciationRule(rule.id, enabled)
                }
                RuleType.HEADER_SKIP -> {
                    settingsRepository.setHeaderSkipEnabled(enabled)
                }
                RuleType.FOOTER_SKIP -> {
                    settingsRepository.setFooterSkipEnabled(enabled)
                }
            }
            reparseActivePage()
        }
    }
}
