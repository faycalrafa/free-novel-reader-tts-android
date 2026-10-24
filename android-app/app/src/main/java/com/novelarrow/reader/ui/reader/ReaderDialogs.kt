package com.novelarrow.reader.ui.reader

import androidx.compose.runtime.Composable
import com.novelarrow.reader.data.model.detectEditorLanguage
import com.novelarrow.reader.ui.common.MonacoCodeEditorDialog
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.components.LibraryScanningOverlay
import com.novelarrow.reader.ui.reader.components.SettingsDialog
import com.novelarrow.reader.ui.reader.components.SleepTimerDialog

@Composable
fun ReaderDialogs(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    showSettingsDialog: Boolean,
    onDismissSettingsDialog: () -> Unit,
    showSleepTimerDialog: Boolean,
    onDismissSleepTimerDialog: () -> Unit,
    showUnifiedAudioSettings: Boolean,
    onDismissUnifiedAudioSettings: () -> Unit,
    onNavigateToVoiceEngine: (() -> Unit)?,
    onNavigateToPronunciationRules: (() -> Unit)?,
    onNavigateToSkipWords: (() -> Unit)?,
    onNavigateToHeaderFooterSkips: (() -> Unit)?,
    onNavigateToContentSelector: (() -> Unit)?
) {
    if (uiState.isLibraryScanning) {
        LibraryScanningOverlay(
            progress = uiState.scanProgress,
            currentIndex = uiState.scanCurrentIndex,
            totalItems = uiState.scanTotalItems,
            currentItem = uiState.scanCurrentItem
        )
    }

    if (uiState.showRawEditor && uiState.rawChapterContent != null) {
        val chapter = uiState.currentChapter
        MonacoCodeEditorDialog(
            title = chapter?.title ?: "Raw Chapter Code",
            initialContent = uiState.rawChapterContent ?: "",
            language = chapter?.detectEditorLanguage() ?: "html",
            onDismiss = { viewModel.dismissRawEditor() },
            onSave = { newContent -> viewModel.saveRawChapterContent(newContent) }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = uiState.settings,
            onDismiss = onDismissSettingsDialog,
            onThemeChange = { viewModel.updateTheme(it) },
            onFontSizeChange = { viewModel.updateFontSize(it) },
            onAutoScrollChange = { viewModel.updateAutoScroll(it) },
            onAutoNextChange = { viewModel.updateAutoNext(it) },
            onClearAllData = {
                viewModel.clearAllData()
                onDismissSettingsDialog()
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutes,
            remainingSeconds = uiState.sleepTimerRemainingSeconds,
            isPlaying = uiState.isPlaying,
            onDismiss = onDismissSleepTimerDialog,
            onSetTimer = { viewModel.setSleepTimer(it) }
        )
    }

    if (showUnifiedAudioSettings) {
        UnifiedAudioSettingsDialog(
            speechRate = uiState.settings.speechRate,
            speechPitch = uiState.settings.speechPitch,
            selectedVoiceName = uiState.settings.selectedVoiceName,
            availableVoices = uiState.availableVoices,
            sleepTimerMinutes = uiState.sleepTimerMinutes,
            sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
            pronunciationRulesEnabled = uiState.settings.pronunciationRulesEnabled,
            pronunciationRules = uiState.settings.pronunciationRules,
            skipWordsEnabled = uiState.settings.skipWordsEnabled,
            skipWords = uiState.settings.skipWords,
            headerSkipRulesEnabled = uiState.settings.headerSkipEnabled,
            headerSkipRules = uiState.settings.headerSkipRules,
            footerSkipRulesEnabled = uiState.settings.footerSkipEnabled,
            footerSkipRules = uiState.settings.footerSkipRules,
            contentSelectorEnabled = uiState.settings.contentSelectorEnabled,
            contentSelector = uiState.settings.contentSelector,
            soundCuesEnabled = uiState.settings.soundCuesEnabled,
            onSpeechRateChange = { viewModel.updateSpeechRate(it) },
            onSpeechPitchChange = { viewModel.updateSpeechPitch(it) },
            onVoiceChange = { viewModel.updateVoice(it) },
            onSleepTimerChange = { viewModel.setSleepTimer(it) },
            onSoundCuesEnabledChange = { viewModel.updateSoundCuesEnabled(it) },
            onPronunciationRulesEnabledChange = { viewModel.updatePronunciationRulesEnabled(it) },
            onAddPronunciationRule = { from, to, exact -> viewModel.addPronunciationRule(from, to, exact) },
            onRemovePronunciationRule = { viewModel.removePronunciationRule(it) },
            onSkipWordsEnabledChange = { viewModel.updateSkipWordsEnabled(it) },
            onAddSkipWord = { word, exact -> viewModel.addSkipWord(word, exact) },
            onRemoveSkipWord = { viewModel.removeSkipWord(it) },
            onHeaderSkipRulesEnabledChange = { viewModel.updateHeaderSkipEnabled(it) },
            onAddHeaderSkipRule = { text, exact -> viewModel.addHeaderSkipRule(text, exact) },
            onRemoveHeaderSkipRule = { viewModel.removeHeaderSkipRule(it) },
            onFooterSkipRulesEnabledChange = { viewModel.updateFooterSkipEnabled(it) },
            onAddFooterSkipRule = { text, exact -> viewModel.addFooterSkipRule(text, exact) },
            onRemoveFooterSkipRule = { viewModel.removeFooterSkipRule(it) },
            onContentSelectorEnabledChange = { viewModel.updateContentSelectorEnabled(it) },
            onContentSelectorChange = { viewModel.updateContentSelector(it) },
            onNavigateToPronunciationRules = onNavigateToPronunciationRules?.let { nav ->
                {
                    onDismissUnifiedAudioSettings()
                    nav()
                }
            },
            onNavigateToSkipWords = onNavigateToSkipWords?.let { nav ->
                {
                    onDismissUnifiedAudioSettings()
                    nav()
                }
            },
            onNavigateToVoiceEngine = onNavigateToVoiceEngine?.let { nav ->
                {
                    onDismissUnifiedAudioSettings()
                    nav()
                }
            },
            onNavigateToHeaderFooterSkips = onNavigateToHeaderFooterSkips?.let { nav ->
                {
                    onDismissUnifiedAudioSettings()
                    nav()
                }
            },
            onNavigateToContentSelector = onNavigateToContentSelector?.let { nav ->
                {
                    onDismissUnifiedAudioSettings()
                    nav()
                }
            },
            onDismiss = onDismissUnifiedAudioSettings
        )
    }
}
