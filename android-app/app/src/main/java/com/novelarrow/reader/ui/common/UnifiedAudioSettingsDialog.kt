package com.novelarrow.reader.ui.common

import android.speech.tts.Voice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.data.model.SkipWord

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAudioSettingsDialog(
    speechRate: Float,
    speechPitch: Float,
    selectedVoiceName: String?,
    availableVoices: List<Voice> = emptyList(),
    sleepTimerMinutes: Int,
    sleepTimerRemainingSeconds: Int = 0,
    pronunciationRulesEnabled: Boolean = true,
    pronunciationRules: List<PronunciationRule> = emptyList(),
    skipWordsEnabled: Boolean = true,
    skipWords: List<SkipWord> = emptyList(),
    headerSkipRulesEnabled: Boolean = true,
    headerSkipRules: List<SkipRule> = emptyList(),
    footerSkipRulesEnabled: Boolean = true,
    footerSkipRules: List<SkipRule> = emptyList(),
    contentSelectorEnabled: Boolean = true,
    contentSelector: String = "",
    onSpeechRateChange: (Float) -> Unit,
    onSpeechPitchChange: (Float) -> Unit,
    onVoiceChange: (String?) -> Unit = {},
    onSleepTimerChange: (Int) -> Unit,
    onPronunciationRulesEnabledChange: ((Boolean) -> Unit)? = null,
    onAddPronunciationRule: ((String, String, Boolean) -> Unit)? = null,
    onRemovePronunciationRule: ((Int) -> Unit)? = null,
    onSkipWordsEnabledChange: ((Boolean) -> Unit)? = null,
    onAddSkipWord: ((String, Boolean) -> Unit)? = null,
    onRemoveSkipWord: ((Int) -> Unit)? = null,
    onHeaderSkipRulesEnabledChange: ((Boolean) -> Unit)? = null,
    onAddHeaderSkipRule: ((String, Boolean) -> Unit)? = null,
    onRemoveHeaderSkipRule: ((Int) -> Unit)? = null,
    onFooterSkipRulesEnabledChange: ((Boolean) -> Unit)? = null,
    onAddFooterSkipRule: ((String, Boolean) -> Unit)? = null,
    onRemoveFooterSkipRule: ((Int) -> Unit)? = null,
    onContentSelectorEnabledChange: ((Boolean) -> Unit)? = null,
    onContentSelectorChange: ((String) -> Unit)? = null,
    onNavigateToPronunciationRules: (() -> Unit)? = null,
    onNavigateToSkipWords: (() -> Unit)? = null,
    onNavigateToVoiceEngine: (() -> Unit)? = null,
    onNavigateToHeaderFooterSkips: (() -> Unit)? = null,
    onNavigateToContentSelector: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Audio Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Speech Rate Slider
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Speech Speed: ${"%.2f".format(speechRate)}x",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (speechRate <= 0.52f) "0.5x" else if (speechRate >= 1.98f) "2.0x" else "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Slider(
                        value = speechRate,
                        onValueChange = { newVal ->
                            val stepped = (kotlin.math.round(newVal * 20f) / 20f).coerceIn(0.5f, 2.5f)
                            onSpeechRateChange(stepped)
                        },
                        valueRange = 0.5f..2.5f,
                        steps = 39
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                            val isSelected = kotlin.math.abs(speechRate - rate) < 0.03f
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { onSpeechRateChange(rate) }
                            ) {
                                Text(
                                    text = "${rate}x",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Speech Pitch Slider
                item {
                    Text(
                        "Voice Pitch: ${"%.2f".format(speechPitch)}x",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Slider(
                        value = speechPitch,
                        onValueChange = { newVal ->
                            val stepped = (kotlin.math.round(newVal * 20f) / 20f).coerceIn(0.5f, 1.5f)
                            onSpeechPitchChange(stepped)
                        },
                        valueRange = 0.5f..1.5f,
                        steps = 19
                    )
                }

                // Sleep Timer
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Sleep Timer",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        if (sleepTimerMinutes > 0 && sleepTimerRemainingSeconds > 0) {
                            val mins = sleepTimerRemainingSeconds / 60
                            val secs = sleepTimerRemainingSeconds % 60
                            Text(
                                text = "${mins}m ${secs}s left",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, label) ->
                            val isSelected = sleepTimerMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { onSleepTimerChange(mins) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Section: 1-Tap Direct Full Routes
                item {
                    Text(
                        text = "ADVANCED SETTINGS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Voice Engine Button
                item {
                    SettingsNavigationRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Voice Engine & Language Selection",
                        subtitle = "Select favorite voices for English, Arabic, and French",
                        onClick = {
                            onDismiss()
                            onNavigateToVoiceEngine?.invoke()
                        }
                    )
                }

                // Pronunciation Rules Button
                item {
                    SettingsNavigationRow(
                        icon = Icons.Default.Translate,
                        title = "Pronunciation Rules",
                        subtitle = "Replace specific names, honorifics, and glossary terms",
                        badge = if (pronunciationRules.isNotEmpty()) "${pronunciationRules.size}" else null,
                        onClick = {
                            onDismiss()
                            onNavigateToPronunciationRules?.invoke()
                        }
                    )
                }

                // Skip Words Button
                item {
                    SettingsNavigationRow(
                        icon = Icons.Default.FilterList,
                        title = "Skip Words",
                        subtitle = "Omit unwanted phrases, emojis, or sound effects from speech",
                        badge = if (skipWords.isNotEmpty()) "${skipWords.size}" else null,
                        onClick = {
                            onDismiss()
                            onNavigateToSkipWords?.invoke()
                        }
                    )
                }

                // Header & Footer Skips Button
                item {
                    SettingsNavigationRow(
                        icon = Icons.Default.FilterList,
                        title = "Header & Footer Skips",
                        subtitle = "Automatically skip author notes, translator messages, and boilerplate",
                        badge = if (headerSkipRules.isNotEmpty() || footerSkipRules.isNotEmpty()) "${headerSkipRules.size + footerSkipRules.size}" else null,
                        onClick = {
                            onDismiss()
                            onNavigateToHeaderFooterSkips?.invoke()
                        }
                    )
                }

                // CSS Content Selectors Button
                item {
                    SettingsNavigationRow(
                        icon = Icons.Default.Code,
                        title = "CSS Content Selectors",
                        subtitle = "Configure prioritized multiple CSS selectors per site or folder",
                        onClick = {
                            onDismiss()
                            onNavigateToContentSelector?.invoke()
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
