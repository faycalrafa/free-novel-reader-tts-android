package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.QuickToolbarSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarSettingsScreen(
    toolbarSettings: QuickToolbarSettings,
    onUpdateSettings: (QuickToolbarSettings) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Toolbar Settings",
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Master Toggle Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (toolbarSettings.enabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Persistent Notification Toolbar",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keeps a quick-access reader bar pinned in your notification shade even after closing the app, letting you resume reading with one tap.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Toolbar Live Mockup Preview
            Text(
                text = "TOOLBAR PREVIEW",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFEA580C), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("NA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Novel Title Here", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Ch. 45 • ¶ 12/80 • Ready", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (toolbarSettings.showSpeedButton) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEA580C))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text("1.25x", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (toolbarSettings.showSleepTimerButton) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEA580C))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text("💤 15m", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (toolbarSettings.showPrevChapButton) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        if (toolbarSettings.showPlayPauseButton) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(22.dp))
                        }
                        if (toolbarSettings.showNextChapButton) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        if (toolbarSettings.showSiteNextButton) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                        if (toolbarSettings.showCloseButton) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Button Customization Section
            Text(
                text = "CUSTOMIZE BUTTONS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    ButtonToggleItem(
                        title = "Reading Speed Button (1.0x, 1.25x...)",
                        subtitle = "Cycle speech rate directly on the notification",
                        checked = toolbarSettings.showSpeedButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showSpeedButton = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ButtonToggleItem(
                        title = "Sleep Timer Button (Off, 15m, 30m...)",
                        subtitle = "Cycle sleep timer duration directly on the notification",
                        checked = toolbarSettings.showSleepTimerButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showSleepTimerButton = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ButtonToggleItem(
                        title = "Previous Chapter Button",
                        subtitle = "Jump back to previous chapter in reading list",
                        checked = toolbarSettings.showPrevChapButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showPrevChapButton = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ButtonToggleItem(
                        title = "Play / Pause Button",
                        subtitle = "Instantly start or pause reading",
                        checked = toolbarSettings.showPlayPauseButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showPlayPauseButton = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ButtonToggleItem(
                        title = "Next Chapter Button",
                        subtitle = "Advance to the next chapter in your library",
                        checked = toolbarSettings.showNextChapButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showNextChapButton = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ButtonToggleItem(
                        title = "Site Next Chapter Button (Web)",
                        subtitle = "Turn page on live website novels in the background",
                        checked = toolbarSettings.showSiteNextButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showSiteNextButton = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ButtonToggleItem(
                        title = "Close / Dismiss Button",
                        subtitle = "Dismiss the persistent toolbar when you're finished",
                        checked = toolbarSettings.showCloseButton,
                        enabled = toolbarSettings.enabled,
                        onCheckedChange = { onUpdateSettings(toolbarSettings.copy(showCloseButton = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ButtonToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                ),
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
