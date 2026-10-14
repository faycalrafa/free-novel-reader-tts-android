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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipWord

@OptIn(ExperimentalMaterial3Api::class)
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
    headerSkipEnabled: Boolean = false,
    headerSkipString: String = "",
    footerSkipEnabled: Boolean = false,
    footerSkipString: String = "",
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
    onHeaderSkipEnabledChange: ((Boolean) -> Unit)? = null,
    onHeaderSkipStringChange: ((String) -> Unit)? = null,
    onFooterSkipEnabledChange: ((Boolean) -> Unit)? = null,
    onFooterSkipStringChange: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var voiceExpanded by remember { mutableStateOf(false) }

    var newRuleFrom by remember { mutableStateOf("") }
    var newRuleTo by remember { mutableStateOf("") }
    var ruleExactMatch by remember { mutableStateOf(true) }

    var newSkipWordText by remember { mutableStateOf("") }
    var skipWordExactMatch by remember { mutableStateOf(true) }

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
                TabRow(
                    selectedTabIndex = selectedTab,
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
                        text = { Text("Skips (${skipWords.size})", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Tab 0: Audio, Voice & Sleep Timer
                            item {
                                // Speech Speed
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
                                        text = if (speechRate <= 0.52f) "0.5x (Half speed)" else if (speechRate >= 1.98f) "2.0x (Double)" else "",
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

                                // Preset chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                                        val isSelected = kotlin.math.abs(speechRate - rate) < 0.03f
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSpeechRateChange(rate) }
                                        ) {
                                            Text(
                                                text = "${rate}x",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                // Speech Pitch
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
                                // Voice Picker
                                Text("Voice", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                ExposedDropdownMenuBox(
                                    expanded = voiceExpanded,
                                    onExpandedChange = { voiceExpanded = !voiceExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedVoiceName ?: "Default Device Voice",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = voiceExpanded,
                                        onDismissRequest = { voiceExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Default Device Voice") },
                                            onClick = {
                                                onVoiceChange(null)
                                                voiceExpanded = false
                                            }
                                        )
                                        availableVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text("${voice.name} (${voice.locale.displayName})") },
                                                onClick = {
                                                    onVoiceChange(voice.name)
                                                    voiceExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                // Sleep Timer
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sleep Timer", style = MaterialTheme.typography.titleSmall)
                                    }
                                    if (sleepTimerMinutes > 0 && sleepTimerRemainingSeconds > 0) {
                                        val mins = sleepTimerRemainingSeconds / 60
                                        val secs = sleepTimerRemainingSeconds % 60
                                        Text(
                                            text = "$mins:${secs.toString().padStart(2, '0')} remaining",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        0 to "Off",
                                        15 to "15m",
                                        30 to "30m",
                                        45 to "45m",
                                        60 to "60m",
                                        90 to "90m"
                                    ).forEach { (mins, label) ->
                                        val isSelected = sleepTimerMinutes == mins
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSleepTimerChange(mins) }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Tab 1: Pronunciation Rules
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
                                                    "Pronunciation Rules",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    "Phonetically replaces words during TTS reading",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                            Switch(
                                                checked = pronunciationRulesEnabled,
                                                onCheckedChange = onPronunciationRulesEnabledChange
                                            )
                                        }

                                        if (pronunciationRulesEnabled) {
                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Add new rule form
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
                            // Tab 2: Skip Words & Filters
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
                                                    "Auto Skip Words",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    "Silences specific words or watermarks during reading",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                            Switch(
                                                checked = skipWordsEnabled,
                                                onCheckedChange = onSkipWordsEnabledChange
                                            )
                                        }

                                        if (skipWordsEnabled) {
                                            Spacer(modifier = Modifier.height(10.dp))

                                            OutlinedTextField(
                                                value = newSkipWordText,
                                                onValueChange = { newSkipWordText = it },
                                                label = { Text("Word to skip") },
                                                placeholder = { Text("e.g. watermark") },
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

                            // Optional Header/Footer skips if callbacks provided
                            if (onHeaderSkipEnabledChange != null && onHeaderSkipStringChange != null) {
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
                                                    Text("Auto Skip Header", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                    Text("Skips text before first matching keyword", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                                }
                                                Switch(
                                                    checked = headerSkipEnabled,
                                                    onCheckedChange = onHeaderSkipEnabledChange
                                                )
                                            }
                                            if (headerSkipEnabled) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = headerSkipString,
                                                    onValueChange = onHeaderSkipStringChange,
                                                    label = { Text("Header Keywords (comma or newline)") },
                                                    placeholder = { Text("e.g. Chapter 1, Prologue") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (onFooterSkipEnabledChange != null && onFooterSkipStringChange != null) {
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
                                                    Text("Auto Skip Footer", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                    Text("Stops reading before first matching keyword", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                                }
                                                Switch(
                                                    checked = footerSkipEnabled,
                                                    onCheckedChange = onFooterSkipEnabledChange
                                                )
                                            }
                                            if (footerSkipEnabled) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = footerSkipString,
                                                    onValueChange = onFooterSkipStringChange,
                                                    label = { Text("Footer Keywords (comma or newline)") },
                                                    placeholder = { Text("e.g. Next Chapter, Comments") },
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
