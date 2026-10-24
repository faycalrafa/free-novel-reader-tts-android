package com.novelarrow.reader.ui.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.data.model.WebBrowserSettings

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowserSettingsDialog(
    settings: WebBrowserSettings,
    onToggleAutoReadNextChapter: (Boolean) -> Unit = {},
    onToggleTtsAutoScroll: (Boolean) -> Unit,
    onToggleTtsAutoStart: (Boolean) -> Unit,
    onToggleAutoClickOnScroll: (Boolean) -> Unit,
    onToggleAutoNextOnFinishedPage: (Boolean) -> Unit = {},
    onUpdateLoopCount: (Int) -> Unit,
    onUpdateDelay: (Long) -> Unit,
    onNavigateToContentSelector: (() -> Unit)? = null,
    onNavigateToNextSelector: (() -> Unit)? = null,
    onNavigateToIgnoredSelectors: (() -> Unit)? = null,
    onNavigateToReorderButtons: (() -> Unit)? = null,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 16.dp),
        title = {
            Text(
                "Browser Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SELECTORS & EXTRACTION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Content Container Selector Button
                BrowserSettingsNavCard(
                    icon = Icons.Default.Code,
                    title = "Content Container Selector",
                    subtitle = if (settings.contentSelector.isBlank()) "Autodetect main article container" else settings.contentSelector,
                    onClick = {
                        onDismiss()
                        onNavigateToContentSelector?.invoke()
                    }
                )

                // Next Chapter Selector Button
                BrowserSettingsNavCard(
                    icon = Icons.Default.SkipNext,
                    title = "Next Chapter Selector",
                    subtitle = if (settings.nextSelector.isBlank()) "Default multi-selector active" else settings.nextSelector,
                    onClick = {
                        onDismiss()
                        onNavigateToNextSelector?.invoke()
                    }
                )

                // Ignored Elements (Filter) Button
                BrowserSettingsNavCard(
                    icon = Icons.Default.FilterList,
                    title = "Ignored Elements (Filter)",
                    subtitle = if (settings.ignoredSelectors.isEmpty()) "Filter unwanted elements, ads, and navigation from speech" else "${settings.ignoredSelectors.size} elements filtered from TTS",
                    badge = if (settings.ignoredSelectors.isNotEmpty()) "${settings.ignoredSelectors.size}" else null,
                    onClick = {
                        onDismiss()
                        onNavigateToIgnoredSelectors?.invoke()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "BATCH LOOP & DELAYS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "Auto-Loop Chapters Count: ${settings.loopCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 25, 50, 100, 200).forEach { preset ->
                        FilterChip(
                            selected = settings.loopCount == preset,
                            onClick = { onUpdateLoopCount(preset) },
                            label = { Text("$preset") }
                        )
                    }
                }

                Text(
                    text = "Delay Between Chapters: ${settings.countdownDelayMs / 1000f}s",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500L, 1000L, 1500L, 2000L, 3000L).forEach { ms ->
                        FilterChip(
                            selected = settings.countdownDelayMs == ms,
                            onClick = { onUpdateDelay(ms) },
                            label = { Text("${ms / 1000f}s") }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "READING AUTOMATION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Auto-scroll switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Scroll to Text", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Scrolls page to follow currently spoken sentence", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.ttsAutoScroll,
                        onCheckedChange = onToggleTtsAutoScroll
                    )
                }

                // Auto-read when next chapter loads switch (default Off)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-read when next chapter loads", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Wait for user input before reading new page when Off", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.autoReadNextChapter,
                        onCheckedChange = onToggleAutoReadNextChapter
                    )
                }

                // Auto-start TTS switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start TTS on Page Load", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Immediately begins reading aloud after page finishes loading", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.ttsAutoStart,
                        onCheckedChange = onToggleTtsAutoStart
                    )
                }

                // Auto-next on scroll bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-next on scroll bottom",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Click next when scrolling to bottom of page",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = settings.autoClickOnScroll,
                        onCheckedChange = onToggleAutoClickOnScroll
                    )
                }

                // Auto-next on finishing last paragraph switch (default On)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-next on finished reading",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Auto-advance to next page when reading the last paragraph",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = settings.autoNextOnFinishedPage,
                        onCheckedChange = onToggleAutoNextOnFinishedPage
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "TOOLBAR & NAVIGATION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                BrowserSettingsNavCard(
                    icon = Icons.Default.DragHandle,
                    title = "Reorder Toolbar Buttons",
                    subtitle = "Drag to reorder right-to-left buttons and control overflow",
                    onClick = {
                        onDismiss()
                        onNavigateToReorderButtons?.invoke()
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onResetDefaults) {
                Text("Reset Defaults", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun BrowserSettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
