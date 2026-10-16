package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.novelarrow.reader.data.model.SkipRule
import java.util.UUID

enum class SkipTargetSection {
    HEADER,
    FOOTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderFooterSkipsScreen(
    headerSkipEnabled: Boolean,
    footerSkipEnabled: Boolean,
    headerRules: List<SkipRule>,
    footerRules: List<SkipRule>,
    onToggleHeaderMaster: (Boolean) -> Unit,
    onToggleFooterMaster: (Boolean) -> Unit,
    onAddHeaderRule: (SkipRule) -> Unit,
    onUpdateHeaderRule: (SkipRule) -> Unit,
    onToggleHeaderRule: (String, Boolean) -> Unit,
    onDeleteHeaderRule: (String) -> Unit,
    onAddFooterRule: (SkipRule) -> Unit,
    onUpdateFooterRule: (SkipRule) -> Unit,
    onToggleFooterRule: (String, Boolean) -> Unit,
    onDeleteFooterRule: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SkipTargetSection.HEADER) }
    var showDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<SkipRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<Pair<SkipRule, SkipTargetSection>?>(null) }

    val currentRules = if (selectedTab == SkipTargetSection.HEADER) headerRules else footerRules
    val currentMasterEnabled = if (selectedTab == SkipTargetSection.HEADER) headerSkipEnabled else footerSkipEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Header & Footer Skips",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Skip Rule")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs: Header Skips vs Footer Skips
            TabRow(
                selectedTabIndex = if (selectedTab == SkipTargetSection.HEADER) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == SkipTargetSection.HEADER,
                    onClick = { selectedTab = SkipTargetSection.HEADER },
                    text = {
                        Text(
                            "Header Skips (${headerRules.size})",
                            fontWeight = if (selectedTab == SkipTargetSection.HEADER) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == SkipTargetSection.FOOTER,
                    onClick = { selectedTab = SkipTargetSection.FOOTER },
                    text = {
                        Text(
                            "Footer Skips (${footerRules.size})",
                            fontWeight = if (selectedTab == SkipTargetSection.FOOTER) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Master Toggle for Active Section
                item(key = "master_toggle_${selectedTab.name}") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentMasterEnabled) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (currentMasterEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedTab == SkipTargetSection.HEADER) "Enable Header Skips" else "Enable Footer Skips",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (selectedTab == SkipTargetSection.HEADER) {
                                        "Cuts out boilerplate phrases and metadata before novel story text starts"
                                    } else {
                                        "Truncates chapters and ends reading when author notes or donation links appear"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = currentMasterEnabled,
                                onCheckedChange = { checked ->
                                    if (selectedTab == SkipTargetSection.HEADER) {
                                        onToggleHeaderMaster(checked)
                                    } else {
                                        onToggleFooterMaster(checked)
                                    }
                                }
                            )
                        }
                    }
                }

                if (currentRules.isEmpty()) {
                    item(key = "empty_${selectedTab.name}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No ${if (selectedTab == SkipTargetSection.HEADER) "header" else "footer"} skip rules",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap the + button to add a text rule.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    items(currentRules, key = { it.id }) { rule ->
                        SkipRuleItemCard(
                            rule = rule,
                            onToggle = { enabled ->
                                if (selectedTab == SkipTargetSection.HEADER) {
                                    onToggleHeaderRule(rule.id, enabled)
                                } else {
                                    onToggleFooterRule(rule.id, enabled)
                                }
                            },
                            onEdit = {
                                ruleToEdit = rule
                                showDialog = true
                            },
                            onDelete = {
                                ruleToDelete = rule to selectedTab
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog) {
        SkipRuleEditDialog(
            initialRule = ruleToEdit,
            section = selectedTab,
            onDismiss = {
                showDialog = false
                ruleToEdit = null
            },
            onSave = { rule ->
                if (selectedTab == SkipTargetSection.HEADER) {
                    if (ruleToEdit == null) onAddHeaderRule(rule) else onUpdateHeaderRule(rule)
                } else {
                    if (ruleToEdit == null) onAddFooterRule(rule) else onUpdateFooterRule(rule)
                }
                showDialog = false
                ruleToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (ruleToDelete != null) {
        val (rule, targetSection) = ruleToDelete!!
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Skip Rule") },
            text = { Text("Are you sure you want to delete rule '${rule.text}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (targetSection == SkipTargetSection.HEADER) {
                            onDeleteHeaderRule(rule.id)
                        } else {
                            onDeleteFooterRule(rule.id)
                        }
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

@Composable
private fun SkipRuleItemCard(
    rule: SkipRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (rule.enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (rule.exactMatch) "Exact Word Boundary" else "Contains Phrase",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SkipRuleEditDialog(
    initialRule: SkipRule?,
    section: SkipTargetSection,
    onDismiss: () -> Unit,
    onSave: (SkipRule) -> Unit
) {
    var textValue by remember { mutableStateOf(initialRule?.text ?: "") }
    var exactMatch by remember { mutableStateOf(initialRule?.exactMatch ?: false) }

    val isEditing = initialRule != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEditing) {
                        "Edit ${if (section == SkipTargetSection.HEADER) "Header" else "Footer"} Skip Rule"
                    } else {
                        "New ${if (section == SkipTargetSection.HEADER) "Header" else "Footer"} Skip Rule"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Skip Phrase / Marker") },
                    placeholder = {
                        Text(
                            if (section == SkipTargetSection.HEADER) "e.g. Chapter Start, Translator Note:"
                            else "e.g. Author's Thought, End of Chapter, Patreon"
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = exactMatch,
                        onCheckedChange = { exactMatch = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Exact Word Boundary", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "Match whole word boundaries (\\b) instead of substring anywhere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (textValue.isNotBlank()) {
                                onSave(
                                    SkipRule(
                                        id = initialRule?.id ?: UUID.randomUUID().toString(),
                                        text = textValue.trim(),
                                        exactMatch = exactMatch,
                                        enabled = initialRule?.enabled ?: true
                                    )
                                )
                            }
                        },
                        enabled = textValue.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
