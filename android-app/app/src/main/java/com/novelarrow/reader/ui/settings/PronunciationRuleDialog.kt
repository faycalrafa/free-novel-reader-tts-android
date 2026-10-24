package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PronunciationRuleDialog(
    initialRule: PronunciationRule? = null,
    onDismiss: () -> Unit,
    onSave: (PronunciationRule) -> Unit
) {
    var fromText by remember { mutableStateOf(initialRule?.from ?: "") }
    var toText by remember { mutableStateOf(initialRule?.to ?: "") }
    var exactMatch by remember { mutableStateOf(initialRule?.exactMatch ?: true) }
    var scopeType by remember { mutableStateOf(initialRule?.scopeType ?: PronunciationScopeType.GLOBAL) }
    var scopePattern by remember { mutableStateOf(initialRule?.scopePattern ?: "") }
    var scopeDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (initialRule == null) "Add Pronunciation Rule" else "Edit Pronunciation Rule",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Original Text (From)
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = { Text("Original text (From)") },
                    placeholder = { Text("e.g. MC or cultivation term") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Replacement Text (To)
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("Pronounce as (To)") },
                    placeholder = { Text("e.g. Master") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Match mode: Exact Word vs Substring
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Exact word boundary", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            if (exactMatch) "Only matches full, distinct words" else "Matches any substring inside words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = exactMatch,
                        onCheckedChange = { exactMatch = it }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scope Selector
                Text("Scope", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = scopeDropdownExpanded,
                    onExpandedChange = { scopeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (scopeType) {
                            PronunciationScopeType.GLOBAL -> "Global (All novels & web pages)"
                            PronunciationScopeType.FOLDER -> "Folder (Specific novel folder)"
                            PronunciationScopeType.URL_PATTERN -> "URL Pattern (Domain or web novel URL)"
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scopeDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = scopeDropdownExpanded,
                        onDismissRequest = { scopeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Global (Applies unconditionally)") },
                            onClick = {
                                scopeType = PronunciationScopeType.GLOBAL
                                scopeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Folder (Match folder path)") },
                            onClick = {
                                scopeType = PronunciationScopeType.FOLDER
                                scopeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("URL Pattern (Match novel web URL)") },
                            onClick = {
                                scopeType = PronunciationScopeType.URL_PATTERN
                                scopeDropdownExpanded = false
                            }
                        )
                    }
                }

                // Scope pattern input field (visible only if FOLDER or URL_PATTERN)
                if (scopeType != PronunciationScopeType.GLOBAL) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = scopePattern,
                        onValueChange = { scopePattern = it },
                        label = {
                            Text(
                                if (scopeType == PronunciationScopeType.FOLDER) "Folder Name or Path Pattern"
                                else "URL or Domain Pattern"
                            )
                        },
                        placeholder = {
                            Text(
                                if (scopeType == PronunciationScopeType.FOLDER) "e.g. Cultivation or /Novels/*"
                                else "e.g. *wuxiaworld.com* or /novel/"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions wrapped in FlowRow to prevent squashing on small screens
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (fromText.isNotBlank() && toText.isNotBlank()) {
                                val newOrUpdated = (initialRule ?: PronunciationRule(
                                    from = fromText.trim(),
                                    to = toText.trim()
                                )).copy(
                                    from = fromText.trim(),
                                    to = toText.trim(),
                                    exactMatch = exactMatch,
                                    scopeType = scopeType,
                                    scopePattern = if (scopeType == PronunciationScopeType.GLOBAL) "" else scopePattern.trim()
                                )
                                onSave(newOrUpdated)
                            }
                        },
                        enabled = fromText.isNotBlank() && toText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Rule")
                    }
                }
            }
        }
    }
}
