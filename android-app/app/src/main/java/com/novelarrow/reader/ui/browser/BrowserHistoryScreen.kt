package com.novelarrow.reader.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.BrowserHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated router screen for web browsing history.
 * Allows users to:
 * 1. Open an entry in the current tab.
 * 2. Open an entry in a new tab.
 * 3. Search and filter browsing history.
 * 4. Delete individual items or clear all history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserHistoryScreen(
    history: List<BrowserHistoryEntry>,
    onSelectUrl: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onClearHistory: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val filteredHistory = remember(history, searchQuery) {
        if (searchQuery.isBlank()) {
            history
        } else {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            history.filter {
                it.title.lowercase(Locale.getDefault()).contains(q) ||
                it.url.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    val timeFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search history...", fontSize = 14.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Browsing History",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            if (history.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${history.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search history")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    }

                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showConfirmClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No browsing history yet" else "No matching history found",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredHistory, key = { it.id }) { entry ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectUrl(entry.url) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = entry.title.ifBlank { entry.url },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = entry.url,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = timeFormat.format(Date(entry.timestamp)),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Open in New Tab action button
                                    IconButton(
                                        onClick = { onOpenInNewTab(entry.url) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open in New Tab",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Remove entry action button
                                    IconButton(
                                        onClick = { onDeleteEntry(entry.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove entry",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            modifier = Modifier.size(18.dp)
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

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all saved browsing history. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
