package com.novelarrow.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import com.novelarrow.reader.data.repository.BrowserPlaybackSession
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.ui.browser.BrowserViewModel
import com.novelarrow.reader.ui.common.UnifiedAudioSettingsDialog
import com.novelarrow.reader.ui.reader.ReaderUiState
import com.novelarrow.reader.ui.reader.ReaderViewModel
import com.novelarrow.reader.util.AppErrorBus

@Composable
fun GlobalAppDialogs(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    browserViewModel: BrowserViewModel,
    context: Context,
    prefs: SharedPreferences,
    showCssSelectorDialog: Boolean,
    onDismissCssSelectorDialog: () -> Unit,
    showGlobalAudioSettings: Boolean,
    onDismissGlobalAudioSettings: () -> Unit,
    navigateToSubScreen: (AppScreen) -> Unit,
    onOpenDevLogs: () -> Unit,
    resumeBrowserSession: BrowserPlaybackSession? = null,
    onResumeBrowserSession: ((BrowserPlaybackSession) -> Unit)? = null,
    onDismissResumeBrowserSession: ((BrowserPlaybackSession) -> Unit)? = null
) {
    if (resumeBrowserSession != null && onResumeBrowserSession != null && onDismissResumeBrowserSession != null) {
        ResumeBrowserPlaybackDialog(
            session = resumeBrowserSession,
            onConfirm = { onResumeBrowserSession(resumeBrowserSession) },
            onDismiss = { onDismissResumeBrowserSession(resumeBrowserSession) }
        )
    }

    if (showCssSelectorDialog) {
        NotificationCssSelectorDialog(
            initialSelector = uiState.settings.contentSelector,
            isEnabled = uiState.settings.contentSelectorEnabled,
            onSave = { selector, enabled ->
                viewModel.updateContentSelector(selector)
                viewModel.updateContentSelectorEnabled(enabled)
                browserViewModel.updateContentSelector(selector)
                Toast.makeText(context, "CSS content selector updated", Toast.LENGTH_SHORT).show()
            },
            onDismiss = onDismissCssSelectorDialog
        )
    }

    if (showGlobalAudioSettings) {
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
            onNavigateToPronunciationRules = {
                onDismissGlobalAudioSettings()
                navigateToSubScreen(AppScreen.SETTINGS_PRONUNCIATION)
            },
            onNavigateToSkipWords = {
                onDismissGlobalAudioSettings()
                navigateToSubScreen(AppScreen.SETTINGS_SKIP_WORDS)
            },
            onNavigateToVoiceEngine = {
                onDismissGlobalAudioSettings()
                navigateToSubScreen(AppScreen.VOICE_ENGINE)
            },
            onNavigateToHeaderFooterSkips = {
                onDismissGlobalAudioSettings()
                navigateToSubScreen(AppScreen.SETTINGS_HEADER_FOOTER_SKIPS)
            },
            onNavigateToContentSelector = {
                onDismissGlobalAudioSettings()
                navigateToSubScreen(AppScreen.SETTINGS_CONTENT_SELECTOR)
            },
            onNavigateToIgnoredSelectors = {
                onDismissGlobalAudioSettings()
                navigateToSubScreen(AppScreen.SETTINGS_BROWSER_IGNORED_SELECTORS)
            },
            onDismiss = onDismissGlobalAudioSettings
        )
    }

    if (uiState.showExistingConfigsDialog && uiState.existingConfigsReport != null) {
        val report = uiState.existingConfigsReport
        AlertDialog(
            onDismissRequest = { viewModel.skipExistingConfigs() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Previous Data?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Found saved preferences and reading progress from a previous installation:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            report.summaryText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        "Would you like to restore your preferences and previously loaded novel folders, or start fresh and delete them?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
                            viewModel.restoreExistingConfigs(report)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Restore Data")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
                            viewModel.refuseAndDeleteExistingConfigs(report)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Delete & Start Fresh", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
                            viewModel.skipExistingConfigs()
                        }
                    ) {
                        Text("Skip for Now")
                    }
                }
            },
            dismissButton = null
        )
    }

    val currentAppError by AppErrorBus.currentError.collectAsState()
    currentAppError?.let { err ->
        AlertDialog(
            onDismissRequest = { AppErrorBus.dismiss() },
            shape = RoundedCornerShape(16.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = err.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!err.stackTrace.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            SelectionContainer(modifier = Modifier.padding(8.dp)) {
                                LazyColumn {
                                    item {
                                        Text(
                                            text = err.stackTrace,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(
                                "BookReader Error Log",
                                "ERROR: ${err.title}\nMessage: ${err.message}\n\nStackTrace:\n${err.stackTrace.ifBlank { "N/A" }}"
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Error log copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Error Log")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AppErrorBus.dismiss()
                                onOpenDevLogs()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Open Dev Logs")
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { AppErrorBus.dismiss() }
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
fun NotificationCssSelectorDialog(
    initialSelector: String,
    isEnabled: Boolean,
    onSave: (selector: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectorText by remember { mutableStateOf(initialSelector) }
    var enabled by remember { mutableStateOf(isEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("HTML Content Selector", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Target specific main content container on web pages (e.g., article, #chapter-content, .entry-content). Leave empty for auto-detection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Content Selector", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                OutlinedTextField(
                    value = selectorText,
                    onValueChange = { selectorText = it },
                    label = { Text("CSS Selector") },
                    placeholder = { Text("e.g. #chapter-content, article") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Common Presets:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("#chapter-content", ".chapter-content", "article", ".entry-content").forEach { preset ->
                        FilterChip(
                            selected = selectorText == preset,
                            onClick = { selectorText = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectorText.trim(), enabled)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ResumeBrowserPlaybackDialog(
    session: BrowserPlaybackSession,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Resume Web Reading?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "You were listening to \"${session.chapterTitle.ifBlank { "Web Chapter" }}\" at Paragraph ${session.paragraphIndex + 1}${if (session.totalParagraphs > 0) " of ${session.totalParagraphs}" else ""}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Would you like to continue from where you left off offline?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Stay in Library")
            }
        }
    )
}

