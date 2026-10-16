package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PronunciationRulesScreen(
    masterEnabled: Boolean,
    rules: List<PronunciationRule>,
    onToggleMaster: (Boolean) -> Unit,
    onAddRule: (PronunciationRule) -> Unit,
    onUpdateRule: (PronunciationRule) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<PronunciationRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<PronunciationRule?>(null) }

    val filteredRules = remember(rules, searchQuery) {
        if (searchQuery.isBlank()) {
            rules
        } else {
            val q = searchQuery.trim().lowercase()
            rules.filter {
                it.from.lowercase().contains(q) ||
                        it.to.lowercase().contains(q) ||
                        it.scopePattern.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Pronunciation Rules",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    ruleToEdit = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Master Toggle Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Enable Pronunciation Rules",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            if (masterEnabled) "Active phonetic overrides are applied during audio playback"
                            else "All pronunciation overrides are currently suspended",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = onToggleMaster
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by word, replacement or scope...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Rule Count & Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rules (${filteredRules.size}${if (searchQuery.isNotBlank()) " matching" else ""})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                if (rules.isNotEmpty()) {
                    val activeCount = rules.count { it.enabled }
                    Text(
                        text = "$activeCount active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Rules List or Empty State
            if (filteredRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No rules match \"$searchQuery\"" else "No pronunciation rules yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try a different search term" else "Tap '+' below to define your first phonetic correction rule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        PronunciationRuleCard(
                            rule = rule,
                            masterEnabled = masterEnabled,
                            onToggle = { enabled -> onToggleRule(rule.id, enabled) },
                            onEdit = {
                                ruleToEdit = rule
                                showDialog = true
                            },
                            onDelete = {
                                ruleToDelete = rule
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog) {
        PronunciationRuleDialog(
            initialRule = ruleToEdit,
            onDismiss = {
                showDialog = false
                ruleToEdit = null
            },
            onSave = { savedRule ->
                if (ruleToEdit != null) {
                    onUpdateRule(savedRule)
                } else {
                    onAddRule(savedRule)
                }
                showDialog = false
                ruleToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    ruleToDelete?.let { targetRule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Pronunciation Rule") },
            text = {
                Text("Are you sure you want to delete the rule for \"${targetRule.from}\" -> \"${targetRule.to}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRule(targetRule.id)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PronunciationRuleCard(
    rule: PronunciationRule,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled && masterEnabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Rule from -> to & Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rule.from,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rule.to,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    enabled = masterEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges & Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FlowRow for chips to avoid truncation on narrow devices
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Exact Match Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (rule.exactMatch) "Exact Word" else "Substring",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Scope Badge
                    val scopeColor = when (rule.scopeType) {
                        PronunciationScopeType.GLOBAL -> MaterialTheme.colorScheme.primaryContainer
                        PronunciationScopeType.FOLDER -> MaterialTheme.colorScheme.tertiaryContainer
                        PronunciationScopeType.URL_PATTERN -> MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    }
                    val onScopeColor = when (rule.scopeType) {
                        PronunciationScopeType.GLOBAL -> MaterialTheme.colorScheme.onPrimaryContainer
                        PronunciationScopeType.FOLDER -> MaterialTheme.colorScheme.onTertiaryContainer
                        PronunciationScopeType.URL_PATTERN -> MaterialTheme.colorScheme.onSurface
                    }
                    val scopeLabel = when (rule.scopeType) {
                        PronunciationScopeType.GLOBAL -> "Global"
                        PronunciationScopeType.FOLDER -> "Folder: ${rule.scopePattern.ifBlank { "Any" }}"
                        PronunciationScopeType.URL_PATTERN -> "URL: ${rule.scopePattern.ifBlank { "Any" }}"
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = scopeColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = when (rule.scopeType) {
                                    PronunciationScopeType.GLOBAL -> Icons.Default.Public
                                    PronunciationScopeType.FOLDER -> Icons.Default.Folder
                                    PronunciationScopeType.URL_PATTERN -> Icons.Default.Public
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = onScopeColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = scopeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = onScopeColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Edit and Delete Icon Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit rule",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete rule",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

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
