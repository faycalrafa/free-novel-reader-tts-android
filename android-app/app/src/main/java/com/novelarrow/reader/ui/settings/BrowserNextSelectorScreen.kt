package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.novelarrow.reader.data.model.NextChapterSelectorRule
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserNextSelectorScreen(
    rules: List<NextChapterSelectorRule>,
    onAddRule: (NextChapterSelectorRule) -> Unit,
    onUpdateRule: (NextChapterSelectorRule) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<NextChapterSelectorRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<NextChapterSelectorRule?>(null) }

    val filteredRules = remember(rules, searchQuery) {
        if (searchQuery.isBlank()) {
            rules
        } else {
            val q = searchQuery.trim().lowercase()
            rules.filter {
                it.name.lowercase().contains(q) ||
                        it.selector.lowercase().contains(q) ||
                        it.domain.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search Next Chapter rules...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Next Chapter Selectors (${rules.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onResetDefaults) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset to Defaults")
                        }
                    }
                }
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
                Icon(Icons.Default.Add, contentDescription = "Add Next Selector Rule")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Next Chapter selector rules allow you to target the next button per specific website or globally. Active rules for the loaded domain are prioritized before generic fallbacks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (filteredRules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching next chapter rules found" else "No next chapter rules configured. Tap '+' to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredRules, key = { it.id }) { rule ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (rule.enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Mouse,
                                        contentDescription = null,
                                        tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rule.name.ifBlank { "Next Selector" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { onToggleRule(rule.id, it) },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = rule.selector,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Domain scope pill
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (rule.domain.isNotBlank()) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            if (rule.domain.isNotBlank()) Icons.Default.Language else Icons.Default.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (rule.domain.isNotBlank()) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (rule.domain.isNotBlank()) rule.domain else "Global",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = if (rule.domain.isNotBlank()) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            ruleToEdit = rule
                                            showDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { ruleToDelete = rule },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
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
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Add / Edit Rule Dialog
    if (showDialog) {
        NextSelectorRuleDialog(
            initialRule = ruleToEdit,
            onDismiss = { showDialog = false },
            onSave = { savedRule ->
                if (ruleToEdit != null) {
                    onUpdateRule(savedRule)
                } else {
                    onAddRule(savedRule)
                }
                showDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (ruleToDelete != null) {
        val rule = ruleToDelete!!
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Next Selector Rule?") },
            text = { Text("Are you sure you want to delete '${rule.name.ifBlank { rule.selector }}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRule(rule.id)
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
private fun NextSelectorRuleDialog(
    initialRule: NextChapterSelectorRule?,
    onDismiss: () -> Unit,
    onSave: (NextChapterSelectorRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var selector by remember { mutableStateOf(initialRule?.selector ?: "") }
    var domain by remember { mutableStateOf(initialRule?.domain ?: "") }
    var enabled by remember { mutableStateOf(initialRule?.enabled ?: true) }

    val presets = remember {
        listOf(
            "button[title*=\"Next Chapter\"], a[title*=\"Next Chapter\"]",
            "button[title*=\"Next chapter\"], a[title*=\"Next chapter\"]",
            "#next_chap, .next-chap, .next-chapter",
            ".btn-next, a.next, a[rel=\"next\"]",
            ".chapter-next a, #next-chapter, .nav-next a",
            ".nav-links .nav-next a, .next a"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (initialRule != null) "Edit Next Selector Rule" else "New Next Selector Rule",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name / Label") },
                    placeholder = { Text("e.g. NovelBin Next Button") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = selector,
                    onValueChange = { selector = it },
                    label = { Text("CSS Selector") },
                    placeholder = { Text("e.g. #next_chap, .btn-next") },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Presets (tap to insert):",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selector = preset }
                        ) {
                            Text(
                                text = preset.take(24) + "...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.trim() },
                    label = { Text("Domain / Hostname (Empty = Global)") },
                    placeholder = { Text("e.g. novelbin.me or wuxiaworld.eu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
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
                            if (selector.isNotBlank()) {
                                onSave(
                                    NextChapterSelectorRule(
                                        id = initialRule?.id ?: UUID.randomUUID().toString(),
                                        name = name.trim().ifBlank { selector.trim().take(30) },
                                        selector = selector.trim(),
                                        domain = domain.trim(),
                                        enabled = enabled
                                    )
                                )
                            }
                        },
                        enabled = selector.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
