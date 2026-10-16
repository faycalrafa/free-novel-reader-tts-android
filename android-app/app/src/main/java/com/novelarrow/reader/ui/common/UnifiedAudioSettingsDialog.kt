package com.novelarrow.reader.ui.common

import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.SkipRule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAudioSettingsDialog(
    speechRate: Float,
    speechPitch: Float,
    selectedVoiceName: String?,
    availableVoices: List<Voice>,
    sleepTimerMinutes: Int,
    sleepTimerRemainingSeconds: Int,
    pronunciationRulesEnabled: Boolean,
    pronunciationRules: List<PronunciationRule>,
    skipWordsEnabled: Boolean,
    skipWords: List<SkipWord>,
    headerSkipRulesEnabled: Boolean = true,
    headerSkipRules: List<SkipRule> = emptyList(),
    footerSkipRulesEnabled: Boolean = true,
    footerSkipRules: List<SkipRule> = emptyList(),
    contentSelectorEnabled: Boolean = true,
    contentSelector: String = "",
    onSpeechRateChange: (Float) -> Unit,
    onSpeechPitchChange: (Float) -> Unit,
    onVoiceChange: (String?) -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    onPronunciationRulesEnabledChange: (Boolean) -> Unit,
    onAddPronunciationRule: (String, String, Boolean) -> Unit,
    onRemovePronunciationRule: (Int) -> Unit,
    onSkipWordsEnabledChange: (Boolean) -> Unit,
    onAddSkipWord: (String, Boolean) -> Unit,
    onRemoveSkipWord: (Int) -> Unit,
    onHeaderSkipRulesEnabledChange: (Boolean) -> Unit = {},
    onAddHeaderSkipRule: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveHeaderSkipRule: (Int) -> Unit = {},
    onFooterSkipRulesEnabledChange: (Boolean) -> Unit = {},
    onAddFooterSkipRule: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveFooterSkipRule: (Int) -> Unit = {},
    onContentSelectorEnabledChange: ((Boolean) -> Unit)? = null,
    onContentSelectorChange: ((String) -> Unit)? = null,
    onNavigateToPronunciationRules: (() -> Unit)? = null,
    onNavigateToSkipWords: (() -> Unit)? = null,
    onNavigateToVoiceEngine: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var voiceExpanded by remember { mutableStateOf(false) }

    var newRuleFrom by remember { mutableStateOf("") }
    var newRuleTo by remember { mutableStateOf("") }
    var ruleExactMatch by remember { mutableStateOf(true) }

    var newSkipWordText by remember { mutableStateOf("") }
    var skipWordExactMatch by remember { mutableStateOf(true) }

    var newHeaderSkipText by remember { mutableStateOf("") }
    var headerSkipExactMatch by remember { mutableStateOf(false) }

    var newFooterSkipText by remember { mutableStateOf("") }
    var footerSkipExactMatch by remember { mutableStateOf(false) }

    var currentContentSelector by remember(contentSelector) { mutableStateOf(contentSelector) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp),
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
                    text = "TTS & Audio Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Audio & Voice", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Rules (${pronunciationRules.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Word Skips (${skipWords.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Header Skips (${headerSkipRules.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Footer Skips (${footerSkipRules.size})", fontSize = 12.sp) }
                    )
                    if (onContentSelectorChange != null) {
                        Tab(
                            selected = selectedTab == 5,
                            onClick = { selectedTab = 5 },
                            text = { Text("CSS Selector", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Audio & Voice Tab
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Speech Speed: ${"%.2f".format(speechRate)}x",
                                        style = MaterialTheme.typography.titleSmall
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
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

                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Speech Pitch: ${"%.2f".format(speechPitch)}x",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Slider(
                                    value = speechPitch,
                                    onValueChange = onSpeechPitchChange,
                                    valueRange = 0.5f..1.5f,
                                    steps = 9
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Voice Engine & Voices", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))

                                val currentVoiceLabel = selectedVoiceName ?: "System Default"

                                if (onNavigateToVoiceEngine != null) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToVoiceEngine() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = currentVoiceLabel,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Select engine & browse voices with audio preview",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = onNavigateToVoiceEngine,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Change", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                } else {
                                    ExposedDropdownMenuBox(
                                        expanded = voiceExpanded,
                                        onExpandedChange = { voiceExpanded = !voiceExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = currentVoiceLabel,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.RecordVoiceOver,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = voiceExpanded,
                                            onDismissRequest = { voiceExpanded = false },
                                            modifier = Modifier.heightIn(max = 280.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("System Default", fontWeight = FontWeight.Bold) },
                                                onClick = {
                                                    onVoiceChange(null)
                                                    voiceExpanded = false
                                                }
                                            )
                                            availableVoices.forEach { voice ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = voice.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                maxLines = 1
                                                            )
                                                            Text(
                                                                text = "${voice.locale.displayName} • ${voice.quality}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        onVoiceChange(voice.name)
                                                        voiceExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Timer,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Sleep Timer",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                            if (sleepTimerMinutes > 0 && sleepTimerRemainingSeconds > 0) {
                                                val mins = sleepTimerRemainingSeconds / 60
                                                val secs = sleepTimerRemainingSeconds % 60
                                                Text(
                                                    text = "Remaining: $mins:${secs.toString().padStart(2, '0')}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(0, 15, 30, 45, 60, 90).forEach { mins ->
                                                val isSelected = sleepTimerMinutes == mins
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.clickable { onSleepTimerChange(mins) }
                                                ) {
                                                    Text(
                                                        text = if (mins == 0) "Off" else "${mins}m",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Pronunciation Rules Tab
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Pronunciation Replacements",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Custom phonetic rules applied while playing audio",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = pronunciationRulesEnabled,
                                        onCheckedChange = onPronunciationRulesEnabledChange
                                    )
                                }
                            }

                            if (onNavigateToPronunciationRules != null) {
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onNavigateToPronunciationRules()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Full Pronunciation Rules Manager")
                                    }
                                }
                            }

                            if (pronunciationRulesEnabled) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "Add New Rule",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            OutlinedTextField(
                                                value = newRuleFrom,
                                                onValueChange = { newRuleFrom = it },
                                                label = { Text("Original text (From)") },
                                                placeholder = { Text("e.g. MC") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            OutlinedTextField(
                                                value = newRuleTo,
                                                onValueChange = { newRuleTo = it },
                                                label = { Text("Pronounce as (To)") },
                                                placeholder = { Text("e.g. Master") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                ) {
                                                    Switch(
                                                        checked = ruleExactMatch,
                                                        onCheckedChange = { ruleExactMatch = it },
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (ruleExactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        if (newRuleFrom.isNotBlank() && newRuleTo.isNotBlank()) {
                                                            onAddPronunciationRule(newRuleFrom.trim(), newRuleTo.trim(), ruleExactMatch)
                                                            newRuleFrom = ""
                                                            newRuleTo = ""
                                                        }
                                                    },
                                                    enabled = newRuleFrom.isNotBlank() && newRuleTo.isNotBlank(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Rule")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (pronunciationRulesEnabled && pronunciationRules.isNotEmpty()) {
                                item {
                                    Text(
                                        "Active Rules (${pronunciationRules.size}):",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                items(pronunciationRules.size) { idx ->
                                    val rule = pronunciationRules[idx]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${rule.from} ➔ ${rule.to}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        if (rule.exactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemovePronunciationRule(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Word Skips Tab
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Auto Skip Words",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Silences specific words or profanities in speech",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = skipWordsEnabled,
                                        onCheckedChange = onSkipWordsEnabledChange
                                    )
                                }
                            }

                            if (onNavigateToSkipWords != null) {
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onNavigateToSkipWords()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Full Skip Words Manager")
                                    }
                                }
                            }

                            if (skipWordsEnabled) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "Add New Skip Word",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            OutlinedTextField(
                                                value = newSkipWordText,
                                                onValueChange = { newSkipWordText = it },
                                                label = { Text("Word to Silence") },
                                                placeholder = { Text("e.g. damn") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                ) {
                                                    Switch(
                                                        checked = skipWordExactMatch,
                                                        onCheckedChange = { skipWordExactMatch = it },
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (skipWordExactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        if (newSkipWordText.isNotBlank()) {
                                                            onAddSkipWord(newSkipWordText.trim(), skipWordExactMatch)
                                                            newSkipWordText = ""
                                                        }
                                                    },
                                                    enabled = newSkipWordText.isNotBlank(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Word")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (skipWordsEnabled && skipWords.isNotEmpty()) {
                                item {
                                    Text(
                                        "Active Skip Words (${skipWords.size}):",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                items(skipWords.size) { idx ->
                                    val skip = skipWords[idx]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    skip.text,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        if (skip.exactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemoveSkipWord(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Header Skips List Tab
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Auto Skip Header Rules",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Starts reading only from the first matching rule keyword",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = headerSkipRulesEnabled,
                                        onCheckedChange = onHeaderSkipRulesEnabledChange
                                    )
                                }
                            }

                            if (headerSkipRulesEnabled) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "Add Header Skip Rule",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            OutlinedTextField(
                                                value = newHeaderSkipText,
                                                onValueChange = { newHeaderSkipText = it },
                                                label = { Text("Header Keyword to start from") },
                                                placeholder = { Text("e.g. Chapter 1, Prologue, START") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Switch(
                                                        checked = headerSkipExactMatch,
                                                        onCheckedChange = { headerSkipExactMatch = it },
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (headerSkipExactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        if (newHeaderSkipText.isNotBlank()) {
                                                            onAddHeaderSkipRule(newHeaderSkipText.trim(), headerSkipExactMatch)
                                                            newHeaderSkipText = ""
                                                        }
                                                    },
                                                    enabled = newHeaderSkipText.isNotBlank(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Rule")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (headerSkipRulesEnabled && headerSkipRules.isNotEmpty()) {
                                item {
                                    Text(
                                        "Active Header Rules (${headerSkipRules.size}):",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                items(headerSkipRules.size) { idx ->
                                    val rule = headerSkipRules[idx]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    rule.text,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        if (rule.exactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemoveHeaderSkipRule(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            // Footer Skips List Tab
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Auto Skip Footer Rules",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Stops reading before the first matching rule keyword",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = footerSkipRulesEnabled,
                                        onCheckedChange = onFooterSkipRulesEnabledChange
                                    )
                                }
                            }

                            if (footerSkipRulesEnabled) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "Add Footer Skip Rule",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            OutlinedTextField(
                                                value = newFooterSkipText,
                                                onValueChange = { newFooterSkipText = it },
                                                label = { Text("Footer Keyword to stop at") },
                                                placeholder = { Text("e.g. Next Chapter, Comments, Patreon") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Switch(
                                                        checked = footerSkipExactMatch,
                                                        onCheckedChange = { footerSkipExactMatch = it },
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (footerSkipExactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        if (newFooterSkipText.isNotBlank()) {
                                                            onAddFooterSkipRule(newFooterSkipText.trim(), footerSkipExactMatch)
                                                            newFooterSkipText = ""
                                                        }
                                                    },
                                                    enabled = newFooterSkipText.isNotBlank(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Rule")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (footerSkipRulesEnabled && footerSkipRules.isNotEmpty()) {
                                item {
                                    Text(
                                        "Active Footer Rules (${footerSkipRules.size}):",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                items(footerSkipRules.size) { idx ->
                                    val rule = footerSkipRules[idx]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    rule.text,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        if (rule.exactMatch) "Whole word" else "Substring",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemoveFooterSkipRule(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        5 -> {
                            // HTML Content Selector Tab
                            if (onContentSelectorChange != null) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "HTML Content Selector",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        "Target specific main story element in chapters",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                }
                                                if (onContentSelectorEnabledChange != null) {
                                                    Switch(
                                                        checked = contentSelectorEnabled,
                                                        onCheckedChange = onContentSelectorEnabledChange
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            OutlinedTextField(
                                                value = currentContentSelector,
                                                onValueChange = {
                                                    currentContentSelector = it
                                                    onContentSelectorChange(it)
                                                },
                                                label = { Text("CSS Selector") },
                                                placeholder = { Text("e.g. #content, .chapter-text, article") },
                                                supportingText = {
                                                    Text(
                                                        "Leave empty for automatic body detection.",
                                                        fontSize = 11.sp
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
