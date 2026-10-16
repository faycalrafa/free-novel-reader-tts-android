package com.novelarrow.reader.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
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
import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSelectorScreen(
    masterEnabled: Boolean,
    selectors: List<ContentSelectorRule>,
    onToggleMaster: (Boolean) -> Unit,
    onAddSelector: (ContentSelectorRule) -> Unit,
    onUpdateSelector: (ContentSelectorRule) -> Unit,
    onToggleSelector: (String, Boolean) -> Unit,
    onDeleteSelector: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var selectorToEdit by remember { mutableStateOf<ContentSelectorRule?>(null) }
    var selectorToDelete by remember { mutableStateOf<ContentSelectorRule?>(null) }

    val filteredSelectors = remember(selectors, searchQuery) {
        if (searchQuery.isBlank()) {
            selectors
        } else {
            val q = searchQuery.trim().lowercase()
            selectors.filter {
                it.selector.lowercase().contains(q) ||
                        it.scopePattern.lowercase().contains(q)
            }
        }
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search selectors...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "CSS Content Selectors",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search selectors")
                        }
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
                    selectorToEdit = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Content Selector")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Toggle Card
            item(key = "master_toggle") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (masterEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (masterEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
                                text = "Enable Content Selectors",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (masterEnabled) "Active • Custom CSS selectors are evaluated in priority order"
                                else "Disabled • Using default automatic DOM extraction",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = onToggleMaster
                        )
                    }
                }
            }

            // Information Card
            item(key = "info_card") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Selectors are tested sequentially from top to bottom. The first selector that yields chapter text (>50 chars) will be selected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (filteredSelectors.isEmpty()) {
                item(key = "empty_state") {
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
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No selectors match \"$searchQuery\"" else "No custom selectors configured",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "Try a different search query" else "Tap the + button to add a target CSS selector for novel content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredSelectors, key = { _, item -> item.id }) { index, rule ->
                    ContentSelectorItemCard(
                        rule = rule,
                        priorityIndex = index + 1,
                        onToggle = { enabled -> onToggleSelector(rule.id, enabled) },
                        onEdit = {
                            selectorToEdit = rule
                            showDialog = true
                        },
                        onDelete = { selectorToDelete = rule }
                    )
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog) {
        ContentSelectorEditDialog(
            initialRule = selectorToEdit,
            onDismiss = {
                showDialog = false
                selectorToEdit = null
            },
            onSave = { rule ->
                if (selectorToEdit == null) {
                    onAddSelector(rule)
                } else {
                    onUpdateSelector(rule)
                }
                showDialog = false
                selectorToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (selectorToDelete != null) {
        AlertDialog(
            onDismissRequest = { selectorToDelete = null },
            title = { Text("Delete Selector") },
            text = { Text("Are you sure you want to delete selector '${selectorToDelete?.selector}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectorToDelete?.let { onDeleteSelector(it.id) }
                        selectorToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectorToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ContentSelectorItemCard(
    rule: ContentSelectorRule,
    priorityIndex: Int,
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
            // Priority Pill
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (rule.enabled) 0.15f else 0.05f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$priorityIndex",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.selector,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val scopeIcon = when (rule.scopeType) {
                        PronunciationScopeType.URL_PATTERN -> Icons.Default.Public
                        PronunciationScopeType.FOLDER -> Icons.Default.Folder
                        else -> Icons.Default.Public
                    }
                    val scopeLabel = when (rule.scopeType) {
                        PronunciationScopeType.URL_PATTERN -> if (rule.scopePattern.isNotBlank()) "Site: ${rule.scopePattern}" else "All Sites"
                        PronunciationScopeType.FOLDER -> if (rule.scopePattern.isNotBlank()) "Folder: ${rule.scopePattern}" else "All Folders"
                        else -> "All Novels"
                    }

                    Icon(
                        scopeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = scopeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Edit
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Selector",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Selector",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Toggle Switch
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentSelectorEditDialog(
    initialRule: ContentSelectorRule?,
    onDismiss: () -> Unit,
    onSave: (ContentSelectorRule) -> Unit
) {
    var selectorText by remember { mutableStateOf(initialRule?.selector ?: "") }
    var scopeType by remember { mutableStateOf(initialRule?.scopeType ?: PronunciationScopeType.GLOBAL) }
    var scopePattern by remember { mutableStateOf(initialRule?.scopePattern ?: "") }

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
                    text = if (isEditing) "Edit Content Selector" else "New Content Selector",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = selectorText,
                    onValueChange = { selectorText = it },
                    label = { Text("CSS Selector") },
                    placeholder = { Text(".entry-content, #chapter-content, article") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "SCOPE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Scope Radio Group
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scopeType = PronunciationScopeType.GLOBAL }
                    ) {
                        RadioButton(
                            selected = scopeType == PronunciationScopeType.GLOBAL,
                            onClick = { scopeType = PronunciationScopeType.GLOBAL }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Novels / Global", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scopeType = PronunciationScopeType.URL_PATTERN }
                    ) {
                        RadioButton(
                            selected = scopeType == PronunciationScopeType.URL_PATTERN,
                            onClick = { scopeType = PronunciationScopeType.URL_PATTERN }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Specific Website (Domain)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scopeType = PronunciationScopeType.FOLDER }
                    ) {
                        RadioButton(
                            selected = scopeType == PronunciationScopeType.FOLDER,
                            onClick = { scopeType = PronunciationScopeType.FOLDER }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Specific Novel Folder", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (scopeType != PronunciationScopeType.GLOBAL) {
                    OutlinedTextField(
                        value = scopePattern,
                        onValueChange = { scopePattern = it },
                        label = {
                            Text(if (scopeType == PronunciationScopeType.URL_PATTERN) "Website Domain (e.g. wuxiaworld.com)" else "Folder / Novel Name")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            if (selectorText.isNotBlank()) {
                                onSave(
                                    ContentSelectorRule(
                                        id = initialRule?.id ?: UUID.randomUUID().toString(),
                                        selector = selectorText.trim(),
                                        enabled = initialRule?.enabled ?: true,
                                        scopeType = scopeType,
                                        scopePattern = scopePattern.trim()
                                    )
                                )
                            }
                        },
                        enabled = selectorText.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
