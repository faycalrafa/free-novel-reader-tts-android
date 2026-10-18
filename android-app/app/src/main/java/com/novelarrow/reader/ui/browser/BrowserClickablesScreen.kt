package com.novelarrow.reader.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.NextChapterSelectorRule
import java.util.UUID

data class ClickableElement(
    val id: String = UUID.randomUUID().toString(),
    val selector: String,
    val text: String,
    val tagName: String,
    val href: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserClickablesScreen(
    clickables: List<ClickableElement>,
    currentUrl: String,
    onAddNextRule: (NextChapterSelectorRule) -> Unit,
    onAddNavbarShortcut: (BrowserNavbarShortcut) -> Unit,
    onOpenShortcutsManager: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedClickable by remember { mutableStateOf<ClickableElement?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf(false) }

    val currentDomain = remember(currentUrl) {
        try {
            java.net.URI(currentUrl).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    val filteredList = remember(clickables, searchQuery) {
        if (searchQuery.isBlank()) clickables
        else {
            val q = searchQuery.trim().lowercase()
            clickables.filter {
                it.text.lowercase().contains(q) ||
                        it.selector.lowercase().contains(q) ||
                        it.tagName.lowercase().contains(q) ||
                        it.href.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "Search clickables (e.g. Next, Chap, >)",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenShortcutsManager) {
                        Icon(Icons.Default.Star, contentDescription = "Manage Shortcuts")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page Clickables (${filteredList.size} found)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap to configure target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching clickable elements found" else "No clickables detected on this page yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedClickable = item
                                showActionSheet = true
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when (item.tagName) {
                                        "a" -> MaterialTheme.colorScheme.primaryContainer
                                        "button" -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                ) {
                                    Text(
                                        text = item.tagName.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.text.ifBlank { "(empty text)" },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = item.selector,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (item.href.isNotBlank()) {
                                Text(
                                    text = item.href,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Modal Sheet: Action options for selected clickable
    if (showActionSheet && selectedClickable != null) {
        val element = selectedClickable!!
        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedClickable = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure Clickable Target",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = element.text.ifBlank { element.selector },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = element.selector,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Option 1: Add as Next Chapter Selector
                OutlinedCard(
                    onClick = {
                        val newRule = NextChapterSelectorRule(
                            name = element.text.take(30).ifBlank { "Next Chapter" },
                            selector = element.selector,
                            domain = currentDomain,
                            enabled = true
                        )
                        onAddNextRule(newRule)
                        showActionSheet = false
                        selectedClickable = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mouse,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Add to Next Chapter Selectors",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Auto-clicks this button during reading & batch scraping (bound to ${currentDomain.ifBlank { "current site" }})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Add as Navbar Shortcut Button
                OutlinedCard(
                    onClick = {
                        showActionSheet = false
                        showShortcutDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Add Shortcut to Browser Navbar",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Places a dedicated one-tap button in top bar when visiting ${currentDomain.ifBlank { "this site" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialog: Configure Navbar Shortcut
    if (showShortcutDialog && selectedClickable != null) {
        val element = selectedClickable!!
        var shortcutName by remember { mutableStateOf(element.text.take(20).ifBlank { "Button" }) }
        var selectedIcon by remember { mutableStateOf("Mouse") }
        var isDomainScoped by remember { mutableStateOf(currentDomain.isNotBlank()) }

        val iconOptions = listOf(
            "Mouse" to Icons.Default.Mouse,
            "Next" to Icons.Default.SkipNext,
            "Star" to Icons.Default.Star,
            "Play" to Icons.Default.PlayArrow,
            "Link" to Icons.Default.Link,
            "Bookmark" to Icons.Default.Bookmark,
            "Code" to Icons.Default.Code
        )

        Dialog(onDismissRequest = {
            showShortcutDialog = false
            selectedClickable = null
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Create Navbar Shortcut",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = shortcutName,
                        onValueChange = { shortcutName = it },
                        label = { Text("Button Label / Tooltip") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Choose Navbar Icon:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        iconOptions.forEach { (name, icon) ->
                            val isSelected = selectedIcon == name
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable { selectedIcon = name }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        icon,
                                        contentDescription = name,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bound to this site only", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isDomainScoped && currentDomain.isNotBlank()) "Visible on $currentDomain" else "Visible globally across all websites",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDomainScoped,
                            onCheckedChange = { isDomainScoped = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showShortcutDialog = false
                            selectedClickable = null
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val shortcut = BrowserNavbarShortcut(
                                name = shortcutName.trim().ifBlank { "Shortcut" },
                                selector = element.selector,
                                iconName = selectedIcon,
                                domain = if (isDomainScoped) currentDomain else "",
                                enabled = true
                            )
                            onAddNavbarShortcut(shortcut)
                            showShortcutDialog = false
                            selectedClickable = null
                            onOpenShortcutsManager()
                        }) {
                            Text("Create & View")
                        }
                    }
                }
            }
        }
    }
}

fun getIconVectorForName(iconName: String): ImageVector {
    return when (iconName) {
        "Next" -> Icons.Default.SkipNext
        "Star" -> Icons.Default.Star
        "Play" -> Icons.Default.PlayArrow
        "Link" -> Icons.Default.Link
        "Bookmark" -> Icons.Default.Bookmark
        "Code" -> Icons.Default.Code
        else -> Icons.Default.Mouse
    }
}
