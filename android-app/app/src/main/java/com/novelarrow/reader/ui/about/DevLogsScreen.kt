package com.novelarrow.reader.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.util.AppLogger
import com.novelarrow.reader.util.LogItem
import com.novelarrow.reader.util.LogLevel
import kotlinx.coroutines.launch

private enum class LogFilterType {
    ALL, ERRORS, WARNINGS, INFO, DEBUG
}

@Composable
fun DevLogsScreen(
    installedVersionName: String,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit = onNavigateBack
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allLogs by AppLogger.logsState.collectAsState()

    var filterType by remember { mutableStateOf(LogFilterType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isDumpingLogcat by remember { mutableStateOf(false) }

    val filteredLogs = remember(allLogs, filterType, searchQuery) {
        allLogs.filter { item ->
            val matchesLevel = when (filterType) {
                LogFilterType.ALL -> true
                LogFilterType.ERRORS -> item.level == LogLevel.ERROR
                LogFilterType.WARNINGS -> item.level == LogLevel.WARN
                LogFilterType.INFO -> item.level == LogLevel.INFO
                LogFilterType.DEBUG -> item.level == LogLevel.DEBUG
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                item.tag.contains(searchQuery, ignoreCase = true) ||
                        item.message.contains(searchQuery, ignoreCase = true) ||
                        (item.stackTrace?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesLevel && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Library Menu")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Developer Logs",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Last 60m • ${filteredLogs.size}/${allLogs.size} entries",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                fontSize = 11.sp
                            )
                        }

                        // Dump System Logcat Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isDumpingLogcat = true
                                    val count = AppLogger.dumpSystemLogcat()
                                    isDumpingLogcat = false
                                    Toast.makeText(context, "Captured $count system logcat lines", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isDumpingLogcat
                        ) {
                            if (isDumpingLogcat) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Dump System Logcat")
                            }
                        }

                        // Copy All Logs
                        IconButton(
                            onClick = {
                                val text = AppLogger.formatForExport(installedVersionName)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("App Logs", text))
                                Toast.makeText(context, "All logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy All Logs")
                        }

                        // Share Logs
                        IconButton(
                            onClick = {
                                val text = AppLogger.formatForExport(installedVersionName)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "NovelArrow Reader Logs")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Debug Logs"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Logs")
                        }

                        // Clear Logs
                        IconButton(
                            onClick = {
                                AppLogger.clear()
                                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Filter by tag, message, or error...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )

                    // Filter chips row
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = filterType == LogFilterType.ALL,
                                onClick = { filterType = LogFilterType.ALL },
                                label = { Text("All (${allLogs.size})", fontSize = 11.sp) }
                            )
                        }
                        item {
                            val errorCount = allLogs.count { it.level == LogLevel.ERROR }
                            FilterChip(
                                selected = filterType == LogFilterType.ERRORS,
                                onClick = { filterType = LogFilterType.ERRORS },
                                label = { Text("Errors ($errorCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                        item {
                            val warnCount = allLogs.count { it.level == LogLevel.WARN }
                            FilterChip(
                                selected = filterType == LogFilterType.WARNINGS,
                                onClick = { filterType = LogFilterType.WARNINGS },
                                label = { Text("Warnings ($warnCount)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            val infoCount = allLogs.count { it.level == LogLevel.INFO }
                            FilterChip(
                                selected = filterType == LogFilterType.INFO,
                                onClick = { filterType = LogFilterType.INFO },
                                label = { Text("Info ($infoCount)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            val debugCount = allLogs.count { it.level == LogLevel.DEBUG }
                            FilterChip(
                                selected = filterType == LogFilterType.DEBUG,
                                onClick = { filterType = LogFilterType.DEBUG },
                                label = { Text("Debug ($debugCount)", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (allLogs.isEmpty()) "No logs captured in the last 60 minutes" else "No logs match the current filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Logs are retained for 1 hour. Tap the Refresh icon above to dump system logcat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLogs, key = { it.id }) { item ->
                    LogItemCard(item = item, context = context)
                }
            }
        }
    }
}

@Composable
private fun LogItemCard(item: LogItem, context: Context) {
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeBg, badgeText, badgeColor) = when (item.level) {
        LogLevel.ERROR -> Triple(Color(0xFFEF4444), "ERR", Color.White)
        LogLevel.WARN -> Triple(Color(0xFFF59E0B), "WARN", Color.White)
        LogLevel.INFO -> Triple(Color(0xFF0EA5E9), "INFO", Color.White)
        LogLevel.DEBUG -> Triple(Color(0xFF64748B), "DBG", Color.White)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.level == LogLevel.ERROR) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF1E293B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Badge, Time, Tag, Copy Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = item.timeFormatted,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.tag,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val textToCopy = "[${item.timeFormatted}] ${item.level}/[${item.tag}]: ${item.message}" +
                                (item.stackTrace?.let { "\n$it" } ?: "")
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Log Entry", textToCopy))
                        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Log",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message text
            SelectionContainer {
                Text(
                    text = item.message,
                    color = if (item.level == LogLevel.ERROR) Color(0xFFFCA5A5) else Color(0xFFF1F5F9),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            // Stack trace if available
            if (!item.stackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "▼ Hide Stack Trace" else "▶ Show Stack Trace",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFF020617), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = item.stackTrace,
                                color = Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
