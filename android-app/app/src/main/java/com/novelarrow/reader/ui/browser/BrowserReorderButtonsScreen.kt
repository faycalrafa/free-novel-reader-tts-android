package com.novelarrow.reader.ui.browser

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.CustomToolbarButton
import com.novelarrow.reader.data.model.BrowserNavbarShortcut

data class ReorderableToolbarItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val isCustom: Boolean = false
)

val DEFAULT_TOOLBAR_BUTTON_ORDER = listOf(
    "tts",
    "reader_mode",
    "auto_clicker",
    "multi_download",
    "reload",
    "forward",
    "back",
    "save",
    "extensions",
    "inspect_clickables"
)

fun resolveToolbarIcon(name: String): ImageVector {
    return when (name.lowercase().trim()) {
        "clouddownload", "cloud_download", "download", "multi_download" -> Icons.Default.CloudDownload
        "headphones", "tts", "listen" -> Icons.Default.Headphones
        "menubook", "menu_book", "reader_mode", "read" -> Icons.AutoMirrored.Filled.MenuBook
        "mouse", "auto_clicker", "clicker" -> Icons.Default.Mouse
        "refresh", "reload" -> Icons.Default.Refresh
        "chevronleft", "chevron_left", "back" -> Icons.Default.ChevronLeft
        "chevronright", "chevron_right", "forward" -> Icons.Default.ChevronRight
        "save", "save_chapter" -> Icons.Default.Save
        "extension", "extensions", "userscripts" -> Icons.Default.Extension
        "findinpage", "find_in_page", "inspect_clickables" -> Icons.Default.FindInPage
        else -> Icons.Default.Extension
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserReorderButtonsScreen(
    currentOrder: List<String>,
    customButtons: List<CustomToolbarButton> = emptyList(),
    shortcuts: List<BrowserNavbarShortcut> = emptyList(),
    onOrderChanged: (List<String>) -> Unit,
    onNavigateBack: () -> Unit
) {
    // Resolve full list of all available buttons
    val allStandardItems = remember {
        listOf(
            ReorderableToolbarItem("tts", "TTS Read Aloud", Icons.Default.Headphones),
            ReorderableToolbarItem("reader_mode", "Reading Mode", Icons.AutoMirrored.Filled.MenuBook),
            ReorderableToolbarItem("auto_clicker", "Next Chapter (In Page)", Icons.Default.Mouse),
            ReorderableToolbarItem("multi_download", "Batch Download Loop", Icons.Default.CloudDownload),
            ReorderableToolbarItem("reload", "Reload Page", Icons.Default.Refresh),
            ReorderableToolbarItem("forward", "Navigate Forward", Icons.Default.ChevronRight),
            ReorderableToolbarItem("back", "Navigate Back", Icons.Default.ChevronLeft),
            ReorderableToolbarItem("save", "Save Chapter HTML", Icons.Default.Save),
            ReorderableToolbarItem("extensions", "Extensions / Scripts", Icons.Default.Extension),
            ReorderableToolbarItem("inspect_clickables", "Inspect Page Clickables", Icons.Default.FindInPage),
            ReorderableToolbarItem("downloads", "Downloads History", Icons.Default.CloudDownload)
        )
    }

    val dynamicCustomItems = remember(customButtons) {
        customButtons.map {
            ReorderableToolbarItem(
                id = it.id,
                title = it.title,
                icon = resolveToolbarIcon(it.icon),
                isCustom = true
            )
        }
    }

    val shortcutItems = remember(shortcuts) {
        shortcuts.map {
            ReorderableToolbarItem(
                id = "shortcut_${it.id}",
                title = it.name,
                icon = resolveToolbarIcon(it.iconName),
                isCustom = true
            )
        }
    }

    val allKnownItems = remember(allStandardItems, dynamicCustomItems, shortcutItems) {
        val map = (allStandardItems + dynamicCustomItems + shortcutItems).associateBy { it.id }
        map
    }

    // Build ordered list according to user's saved preferences
    var orderedList by remember(currentOrder, allKnownItems) {
        val existing = mutableListOf<ReorderableToolbarItem>()
        val seen = mutableSetOf<String>()

        // 1. Add items matching user order
        val effectiveOrder = if (currentOrder.isEmpty()) DEFAULT_TOOLBAR_BUTTON_ORDER else currentOrder
        effectiveOrder.forEach { id ->
            allKnownItems[id]?.let {
                if (seen.add(it.id)) existing.add(it)
            }
        }

        // 2. Add any remaining items not yet in order
        allKnownItems.values.forEach { item ->
            if (seen.add(item.id)) existing.add(item)
        }

        mutableStateOf(existing.toList())
    }

    val saveOrder: (List<ReorderableToolbarItem>) -> Unit = { updated ->
        orderedList = updated
        onOrderChanged(updated.map { it.id })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Reorder Buttons",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val defaultOrder = DEFAULT_TOOLBAR_BUTTON_ORDER
                            val reset = mutableListOf<ReorderableToolbarItem>()
                            val seen = mutableSetOf<String>()
                            defaultOrder.forEach { id ->
                                allKnownItems[id]?.let {
                                    if (seen.add(it.id)) reset.add(it)
                                }
                            }
                            allKnownItems.values.forEach { item ->
                                if (seen.add(item.id)) reset.add(item)
                            }
                            saveOrder(reset)
                        }
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Right-to-Left Display Order",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The first 8 buttons are visible in the navigation bar, displayed from right to left (#1 is rightmost, immediately next to the 3-dot overflow menu). Buttons #9 and beyond are placed inside the 3-dot menu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = "VISIBLE IN NAV BAR (UP TO 8, RIGHT-TO-LEFT)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val navBarItems = orderedList.take(8)
            val overflowItems = orderedList.drop(8)

            itemsIndexed(navBarItems) { index, item ->
                val position = index + 1
                val positionLabel = when (position) {
                    1 -> "#1 (Rightmost • Next to 3-dot)"
                    8 -> "#8 (Leftmost in Nav Bar)"
                    else -> "#$position in Nav Bar"
                }

                ToolbarReorderRow(
                    item = item,
                    positionLabel = positionLabel,
                    isFirst = index == 0,
                    isLast = index == orderedList.size - 1,
                    isOverflow = false,
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = orderedList.toMutableList()
                            val tmp = mutable[index]
                            mutable[index] = mutable[index - 1]
                            mutable[index - 1] = tmp
                            saveOrder(mutable)
                        }
                    },
                    onMoveDown = {
                        if (index < orderedList.size - 1) {
                            val mutable = orderedList.toMutableList()
                            val tmp = mutable[index]
                            mutable[index] = mutable[index + 1]
                            mutable[index + 1] = tmp
                            saveOrder(mutable)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (overflowItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "OVERFLOW MENU (POSITIONS 9+)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                itemsIndexed(overflowItems) { subIndex, item ->
                    val actualIndex = 8 + subIndex
                    val position = actualIndex + 1
                    ToolbarReorderRow(
                        item = item,
                        positionLabel = "#$position • In 3-dot Menu",
                        isFirst = actualIndex == 0,
                        isLast = actualIndex == orderedList.size - 1,
                        isOverflow = true,
                        onMoveUp = {
                            if (actualIndex > 0) {
                                val mutable = orderedList.toMutableList()
                                val tmp = mutable[actualIndex]
                                mutable[actualIndex] = mutable[actualIndex - 1]
                                mutable[actualIndex - 1] = tmp
                                saveOrder(mutable)
                            }
                        },
                        onMoveDown = {
                            if (actualIndex < orderedList.size - 1) {
                                val mutable = orderedList.toMutableList()
                                val tmp = mutable[actualIndex]
                                mutable[actualIndex] = mutable[actualIndex + 1]
                                mutable[actualIndex + 1] = tmp
                                saveOrder(mutable)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToolbarReorderRow(
    item: ReorderableToolbarItem,
    positionLabel: String,
    isFirst: Boolean,
    isLast: Boolean,
    isOverflow: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOverflow) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        tonalElevation = if (isOverflow) 1.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOverflow) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (isOverflow) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = positionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (isOverflow) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
