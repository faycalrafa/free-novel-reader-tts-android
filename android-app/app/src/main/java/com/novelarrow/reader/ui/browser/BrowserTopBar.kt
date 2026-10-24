package com.novelarrow.reader.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.CustomToolbarButton
import com.novelarrow.reader.util.AppLogger
import java.net.URI

private const val TAG = "BrowserTopBar"

/**
 * Top Toolbar cleanly divided into 2 well-spaced rows:
 * Row 1: App Back button, Full-width URL bar with Clear & Go
 * Row 2: Clean action row: Back, Forward, Reload, Reading Mode, Multi Download, Single Download, Auto Clicker, Start TTS + Domain Shortcuts + 3-Dot Overflow Menu
 */
@Composable
fun BrowserTopBar(
    inputUrl: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLiveReading: Boolean,
    isDownloading: Boolean,
    isReaderMode: Boolean,
    totalParagraphs: Int,
    currentParagraphIndex: Int,
    currentUrl: String,
    navbarShortcuts: List<BrowserNavbarShortcut>,
    onBackClicked: () -> Unit,
    onForwardClicked: () -> Unit,
    onReloadClicked: () -> Unit,
    onReadingModeClicked: () -> Unit,
    onLoopClicked: () -> Unit,
    onSaveFrozenHtmlClicked: () -> Unit,
    onNextChapterClicked: () -> Unit,
    onLiveReadClicked: () -> Unit,
    onSeekParagraph: (Int) -> Unit,
    onTriggerShortcut: (String) -> Unit,
    onInspectClickables: () -> Unit,
    onManageShortcuts: () -> Unit,
    onRawCodeClicked: () -> Unit,
    onNextSelectorClicked: (() -> Unit)? = null,
    onIgnoredSelectorsClicked: (() -> Unit)? = null,
    onExtensionsClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onDevLogsClicked: (() -> Unit)? = null,
    onUrlChanged: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    toolbarButtonOrder: List<String> = emptyList(),
    customButtons: List<CustomToolbarButton> = emptyList(),
    onCustomButtonClicked: (String) -> Unit = {},
    onReorderButtonsClicked: () -> Unit = {},
    onHistoryClicked: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // ROW 1: Full-width URL bar with Drawer Menu button and Clear/Go buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Library Menu",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    TextField(
                        value = inputUrl,
                        onValueChange = onUrlChanged,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Search Google or enter URL",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onUrlSubmitted(inputUrl) }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    )

                    if (inputUrl.isNotBlank()) {
                        IconButton(
                            onClick = { onUrlChanged("") },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }

                    IconButton(
                        onClick = { onUrlSubmitted(inputUrl) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ROW 2: Clean action toolbar with 3-dot overflow menu for small screens & extra options
            val currentDomain = remember(currentUrl) {
                try {
                    URI(currentUrl).host.orEmpty().lowercase()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to parse URI host for $currentUrl: ${e.message}", e)
                    ""
                }
            }

            val activeShortcuts = remember(navbarShortcuts, currentDomain) {
                navbarShortcuts.filter { sc ->
                    sc.enabled && (sc.domain.isBlank() || (currentDomain.isNotBlank() && currentDomain.contains(sc.domain.trim().lowercase())))
                }
            }

            val primaryColor = MaterialTheme.colorScheme.primary
            val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
            val secondaryColor = MaterialTheme.colorScheme.secondary

            val allAvailableMap = remember(
                canGoBack,
                canGoForward,
                isReaderMode,
                isDownloading,
                isLiveReading,
                customButtons,
                activeShortcuts,
                primaryColor,
                primaryContainerColor,
                secondaryColor
            ) {
                val map = mutableMapOf<String, TopBarActionItem>()
                map["tts"] = TopBarActionItem(
                    id = "tts",
                    title = "TTS Read Aloud",
                    icon = Icons.Default.Headphones,
                    tint = if (isLiveReading) primaryColor else null,
                    background = if (isLiveReading) primaryContainerColor else Color.Transparent,
                    onClick = onLiveReadClicked
                )
                map["reader_mode"] = TopBarActionItem(
                    id = "reader_mode",
                    title = if (isReaderMode) "Exit Reading Mode" else "Reading Mode",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    tint = if (isReaderMode) primaryColor else null,
                    background = if (isReaderMode) primaryContainerColor else Color.Transparent,
                    onClick = onReadingModeClicked
                )
                map["auto_clicker"] = TopBarActionItem(
                    id = "auto_clicker",
                    title = "Next Chapter (Click in Page)",
                    icon = Icons.Default.Mouse,
                    onClick = onNextChapterClicked
                )
                map["multi_download"] = TopBarActionItem(
                    id = "multi_download",
                    title = "Batch Download Loop",
                    icon = Icons.Default.CloudDownload,
                    tint = if (isDownloading) primaryColor else null,
                    background = if (isDownloading) primaryContainerColor else Color.Transparent,
                    onClick = onLoopClicked
                )
                map["loop"] = map["multi_download"]!!
                map["reload"] = TopBarActionItem(
                    id = "reload",
                    title = "Reload",
                    icon = Icons.Default.Refresh,
                    onClick = onReloadClicked
                )
                map["forward"] = TopBarActionItem(
                    id = "forward",
                    title = "Forward",
                    icon = Icons.Default.ChevronRight,
                    enabled = canGoForward,
                    onClick = onForwardClicked
                )
                map["back"] = TopBarActionItem(
                    id = "back",
                    title = "Back",
                    icon = Icons.Default.ChevronLeft,
                    enabled = canGoBack,
                    onClick = onBackClicked
                )
                map["save"] = TopBarActionItem(
                    id = "save",
                    title = "Save Chapter HTML",
                    icon = Icons.Default.Save,
                    onClick = onSaveFrozenHtmlClicked
                )
                map["extensions"] = TopBarActionItem(
                    id = "extensions",
                    title = "Extensions & Userscripts",
                    icon = Icons.Default.Extension,
                    tint = primaryColor,
                    onClick = onExtensionsClicked
                )
                map["inspect_clickables"] = TopBarActionItem(
                    id = "inspect_clickables",
                    title = "Inspect Page Clickables",
                    icon = Icons.Default.FindInPage,
                    tint = primaryColor,
                    onClick = onInspectClickables
                )

                customButtons.forEach { btn ->
                    map[btn.id] = TopBarActionItem(
                        id = btn.id,
                        title = btn.title,
                        icon = resolveToolbarIcon(btn.icon),
                        tint = primaryColor,
                        onClick = { onCustomButtonClicked(btn.id) }
                    )
                }

                activeShortcuts.forEach { sc ->
                    val scId = "shortcut_${sc.id}"
                    map[scId] = TopBarActionItem(
                        id = scId,
                        title = sc.name,
                        icon = getShortcutIcon(sc.iconName),
                        tint = secondaryColor,
                        onClick = { onTriggerShortcut(sc.selector) }
                    )
                }
                map
            }

            val effectiveOrder = remember(toolbarButtonOrder) {
                if (toolbarButtonOrder.isEmpty()) DEFAULT_TOOLBAR_BUTTON_ORDER else toolbarButtonOrder
            }

            val orderedButtons = remember(allAvailableMap, effectiveOrder) {
                val list = mutableListOf<TopBarActionItem>()
                val seen = mutableSetOf<String>()
                effectiveOrder.forEach { id ->
                    val actualId = if (id == "loop") "multi_download" else id
                    allAvailableMap[actualId]?.let {
                        if (seen.add(it.id)) list.add(it)
                    }
                }
                allAvailableMap.values.forEach { item ->
                    if (item.id != "loop" && seen.add(item.id)) list.add(item)
                }
                list
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                val buttonWidth = 36.dp
                val overflowMenuWidth = 38.dp
                val screenFit = ((maxWidth - overflowMenuWidth) / buttonWidth).toInt().coerceAtLeast(1)
                val maxDirectFit = minOf(8, screenFit)

                val directlyShownItems = if (orderedButtons.size <= maxDirectFit) {
                    orderedButtons
                } else {
                    orderedButtons.take(maxDirectFit)
                }

                val overflowedVisibleItems = if (orderedButtons.size <= maxDirectFit) {
                    emptyList()
                } else {
                    orderedButtons.drop(maxDirectFit)
                }

                var showMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        directlyShownItems.reversed().forEach { item ->
                            IconButton(
                                onClick = item.onClick,
                                enabled = item.enabled,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(item.background, shape = RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.tint ?: (if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        BrowserOverflowMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            overflowedVisibleItems = overflowedVisibleItems,
                            onReorderButtonsClicked = onReorderButtonsClicked,
                            onHistoryClicked = onHistoryClicked,
                            onInspectClickables = onInspectClickables,
                            onManageShortcuts = onManageShortcuts,
                            onRawCodeClicked = onRawCodeClicked,
                            onNextSelectorClicked = onNextSelectorClicked,
                            onIgnoredSelectorsClicked = onIgnoredSelectorsClicked,
                            onExtensionsClicked = onExtensionsClicked,
                            onSettingsClicked = onSettingsClicked,
                            onDevLogsClicked = onDevLogsClicked
                        )
                    }
                }
            }
        }
    }
}
