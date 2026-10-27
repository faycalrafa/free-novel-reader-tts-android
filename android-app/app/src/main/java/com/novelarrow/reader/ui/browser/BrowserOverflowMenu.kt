package com.novelarrow.reader.ui.browser

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrowserOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    overflowedVisibleItems: List<TopBarActionItem>,
    onReorderButtonsClicked: () -> Unit,
    onHistoryClicked: () -> Unit,
    onDownloadsClicked: () -> Unit = {},
    onInspectClickables: () -> Unit,
    onManageShortcuts: () -> Unit,
    onRawCodeClicked: () -> Unit,
    onNextSelectorClicked: (() -> Unit)? = null,
    onIgnoredSelectorsClicked: (() -> Unit)? = null,
    onExtensionsClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onDevLogsClicked: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        // Overflowed primary buttons (positions 9+)
        if (overflowedVisibleItems.isNotEmpty()) {
            overflowedVisibleItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.title) },
                    leadingIcon = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = item.tint ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        item.onClick()
                    }
                )
            }
            HorizontalDivider()
        }

        // Dedicated Reorder Buttons option
        DropdownMenuItem(
            text = { Text("Reorder Buttons") },
            leadingIcon = {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onReorderButtonsClicked()
            }
        )

        // History option
        DropdownMenuItem(
            text = { Text("Browsing History") },
            leadingIcon = {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onHistoryClicked()
            }
        )

        // Downloads option
        DropdownMenuItem(
            text = { Text("Downloads") },
            leadingIcon = {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onDownloadsClicked()
            }
        )

        HorizontalDivider()

        // Secondary browser features
        DropdownMenuItem(
            text = { Text("Inspect Page Clickables") },
            leadingIcon = {
                Icon(
                    Icons.Default.FindInPage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onInspectClickables()
            }
        )

        DropdownMenuItem(
            text = { Text("Manage Navbar Shortcuts") },
            leadingIcon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onManageShortcuts()
            }
        )

        DropdownMenuItem(
            text = { Text("View Raw HTML") },
            leadingIcon = {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
                onDismissRequest()
                onRawCodeClicked()
            }
        )

        if (onNextSelectorClicked != null) {
            DropdownMenuItem(
                text = { Text("Next Chapter Selectors") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                onClick = {
                    onDismissRequest()
                    onNextSelectorClicked()
                }
            )
        }

        if (onIgnoredSelectorsClicked != null) {
            DropdownMenuItem(
                text = { Text("Ignored Elements Filter") },
                leadingIcon = {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                onClick = {
                    onDismissRequest()
                    onIgnoredSelectorsClicked()
                }
            )
        }

        DropdownMenuItem(
            text = { Text("Extensions & Userscripts") },
            leadingIcon = {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                onDismissRequest()
                onExtensionsClicked()
            }
        )

        DropdownMenuItem(
            text = { Text("Browser Settings") },
            leadingIcon = {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
                onDismissRequest()
                onSettingsClicked()
            }
        )

        if (onDevLogsClicked != null) {
            DropdownMenuItem(
                text = { Text("Developer Logs") },
                leadingIcon = {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    onDevLogsClicked()
                }
            )
        }
    }
}
