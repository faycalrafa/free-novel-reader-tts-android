package com.novelarrow.reader.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.ui.browser.components.BrowserSettingsDialog
import com.novelarrow.reader.ui.common.MonacoCodeEditorDialog
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.components.SleepTimerDialog

@Composable
fun BrowserDialogs(
    uiState: BrowserUiState,
    viewModel: BrowserViewModel,
    showAudioSettingsDialog: Boolean,
    onDismissAudioSettingsDialog: () -> Unit,
    onNavigateToPronunciationRules: (() -> Unit)? = null,
    onNavigateToSkipWords: (() -> Unit)? = null,
    onNavigateToVoiceEngine: (() -> Unit)? = null,
    onNavigateToHeaderFooterSkips: (() -> Unit)? = null,
    onNavigateToContentSelector: (() -> Unit)? = null,
    onNavigateToNextSelector: (() -> Unit)? = null,
    onNavigateToIgnoredSelectors: (() -> Unit)? = null,
    onNavigateToReorderButtons: (() -> Unit)? = null
) {
    // Sleep Timer Dialog
    if (uiState.showSleepTimerDialog) {
        SleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutes,
            remainingSeconds = uiState.sleepTimerRemainingSeconds,
            isPlaying = uiState.isLivePlaying,
            onDismiss = { viewModel.dismissSleepTimerDialog() },
            onSetTimer = { viewModel.setSleepTimer(it) }
        )
    }

    // Raw HTML Source Monaco Editor Dialog
    if (uiState.showRawEditor && uiState.rawHtmlContent != null) {
        MonacoCodeEditorDialog(
            title = "HTML Source: ${uiState.detectedTitle ?: uiState.currentUrl}",
            initialContent = uiState.rawHtmlContent,
            language = "html",
            onDismiss = { viewModel.dismissRawEditor() }
        )
    }

    // Audio / TTS Control Settings Dialog
    if (showAudioSettingsDialog) {
        UnifiedAudioSettingsDialog(
            speechRate = uiState.settings.ttsRate,
            speechPitch = uiState.settings.ttsPitch,
            selectedVoiceName = uiState.settings.ttsVoice,
            availableVoices = uiState.availableVoices,
            sleepTimerMinutes = uiState.sleepTimerMinutes,
            sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
            pronunciationRulesEnabled = uiState.settings.pronunciationRulesEnabled,
            pronunciationRules = uiState.settings.pronunciationRules,
            skipWordsEnabled = uiState.settings.skipWordsEnabled,
            skipWords = uiState.settings.skipWords,
            contentSelector = uiState.settings.contentSelector,
            onSpeechRateChange = { viewModel.updateRate(it) },
            onSpeechPitchChange = { viewModel.updatePitch(it) },
            onVoiceChange = { viewModel.updateVoice(it) },
            onSleepTimerChange = { viewModel.setSleepTimer(it) },
            onPronunciationRulesEnabledChange = { viewModel.togglePronunciationRulesEnabled(it) },
            onAddPronunciationRule = { from, to, exact -> viewModel.addPronunciationRule(from, to, exact) },
            onRemovePronunciationRule = { viewModel.removePronunciationRule(it) },
            onSkipWordsEnabledChange = { viewModel.toggleSkipWordsEnabled(it) },
            onAddSkipWord = { word, exact -> viewModel.addSkipWord(word, exact) },
            onRemoveSkipWord = { viewModel.removeSkipWord(it) },
            onContentSelectorChange = { viewModel.updateContentSelector(it) },
            onNavigateToPronunciationRules = onNavigateToPronunciationRules?.let { nav ->
                {
                    onDismissAudioSettingsDialog()
                    nav()
                }
            },
            onNavigateToSkipWords = onNavigateToSkipWords?.let { nav ->
                {
                    onDismissAudioSettingsDialog()
                    nav()
                }
            },
            onNavigateToVoiceEngine = onNavigateToVoiceEngine?.let { nav ->
                {
                    onDismissAudioSettingsDialog()
                    nav()
                }
            },
            onNavigateToHeaderFooterSkips = onNavigateToHeaderFooterSkips?.let { nav ->
                {
                    onDismissAudioSettingsDialog()
                    nav()
                }
            },
            onNavigateToContentSelector = onNavigateToContentSelector?.let { nav ->
                {
                    onDismissAudioSettingsDialog()
                    nav()
                }
            },
            onDismiss = onDismissAudioSettingsDialog
        )
    }

    // Userscript Automation & Reading Settings Modal
    if (uiState.showSettingsDialog) {
        BrowserSettingsDialog(
            settings = uiState.settings,
            onToggleAutoReadNextChapter = { viewModel.toggleAutoReadNextChapter(it) },
            onToggleTtsAutoScroll = { viewModel.toggleTtsAutoScroll(it) },
            onToggleTtsAutoStart = { viewModel.toggleTtsAutoStart(it) },
            onToggleAutoClickOnScroll = { viewModel.toggleAutoClickOnScroll(it) },
            onToggleAutoNextOnFinishedPage = { viewModel.toggleAutoNextOnFinishedPage(it) },
            onUpdateLoopCount = { viewModel.updateLoopCount(it) },
            onUpdateDelay = { viewModel.updateCountdownDelayMs(it) },
            onNavigateToContentSelector = onNavigateToContentSelector,
            onNavigateToNextSelector = onNavigateToNextSelector,
            onNavigateToIgnoredSelectors = onNavigateToIgnoredSelectors,
            onNavigateToReorderButtons = onNavigateToReorderButtons,
            onResetDefaults = { viewModel.resetSettingsToDefaults() },
            onDismiss = { viewModel.dismissSettingsDialog() }
        )
    }

    // Extensions & Userscripts Bottom Sheet
    if (uiState.showExtensionsSheet) {
        UserscriptBottomSheet(
            currentUrl = uiState.currentUrl,
            activeScripts = uiState.activeUserscripts,
            onToggleScript = { id, enabled -> viewModel.toggleUserscript(id, enabled) },
            onManageScripts = { viewModel.openUserscriptManager() },
            onCreateScript = { viewModel.openCodeMirrorEditor(null) },
            onOpenDocs = { viewModel.openDocumentationDialog() },
            onDismiss = { viewModel.dismissExtensionsSheet() }
        )
    }

    // Userscript Manager Dialog
    if (uiState.showUserscriptManager) {
        UserscriptManagerDialog(
            scripts = uiState.allUserscripts,
            onToggleEnabled = { id, enabled -> viewModel.toggleUserscript(id, enabled) },
            onEditScript = { script -> viewModel.openCodeMirrorEditor(script) },
            onDeleteScript = { id -> viewModel.deleteUserscript(id) },
            onRenameScript = { id, newName -> viewModel.renameUserscript(id, newName) },
            onCreateNewScript = { viewModel.openCodeMirrorEditor(null) },
            onSyncDisk = { viewModel.syncUserscriptsFromDisk() },
            onDismiss = { viewModel.dismissUserscriptManager() }
        )
    }

    // Userscript Monaco Editor Dialog
    if (uiState.showCodeMirrorEditor) {
        MonacoCodeEditorDialog(
            title = uiState.editorPrefilledTitle.ifBlank { "New Userscript" },
            initialContent = uiState.editorPrefilledCode,
            language = "javascript",
            onDismiss = { viewModel.dismissCodeMirrorEditor() },
            onSave = { code -> viewModel.saveUserscriptFromEditor(code) }
        )
    }

    // Userscript Documentation Dialog
    if (uiState.showDocumentationDialog) {
        DocumentationDialog(
            onDismiss = { viewModel.dismissDocumentationDialog() }
        )
    }

    // Browser History Dialog
    if (uiState.showHistoryDialog) {
        BrowserHistoryDialog(
            history = uiState.historyEntries,
            onSelectUrl = { url -> viewModel.navigateTo(url) },
            onDeleteEntry = { id -> viewModel.deleteHistoryEntry(id) },
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { viewModel.dismissHistoryDialog() }
        )
    }

    // Long-Pressed Clickable Element Detection Prompt
    if (uiState.showAddNextSelectorDialog && uiState.longPressedClickable != null) {
        val clickable = uiState.longPressedClickable
        var ruleName by remember(clickable) { mutableStateOf(clickable.text.take(30).ifBlank { "Next Chapter (${clickable.hostname})" }) }
        var selector by remember(clickable) { mutableStateOf(clickable.selector) }
        var domain by remember(clickable) { mutableStateOf(clickable.hostname) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissAddNextSelectorDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mouse, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Next Chapter Selector", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Detected clickable element. Bind it as an Auto Clicker / Next Chapter Selector for ${clickable.hostname}?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Rule Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = selector,
                        onValueChange = { selector = it },
                        label = { Text("CSS Selector") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Target Domain (or blank for global)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmAddNextSelector(ruleName, selector, domain)
                    }
                ) {
                    Text("Add Target")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddNextSelectorDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Screen DOM Clickables Inspector
    if (uiState.showClickablesInspector) {
        BrowserClickablesScreen(
            clickables = uiState.scannedClickables,
            currentUrl = uiState.currentUrl,
            onAddNextRule = { rule -> viewModel.addNextChapterSelectorRule(rule) },
            onAddNavbarShortcut = { shortcut -> viewModel.addNavbarShortcut(shortcut) },
            onOpenShortcutsManager = { viewModel.openShortcutsManager() },
            onNavigateBack = { viewModel.dismissClickablesInspector() }
        )
    }

    // Full Screen Navbar Shortcuts Manager
    if (uiState.showShortcutsManager) {
        BrowserShortcutsManagerScreen(
            shortcuts = uiState.settings.navbarShortcuts,
            currentUrl = uiState.currentUrl,
            onToggleShortcut = { id, enabled -> viewModel.toggleNavbarShortcut(id, enabled) },
            onDeleteShortcut = { id -> viewModel.deleteNavbarShortcut(id) },
            onTriggerShortcut = { selector -> viewModel.clickElementBySelector(selector) },
            onNavigateBack = { viewModel.dismissShortcutsManager() }
        )
    }
}
