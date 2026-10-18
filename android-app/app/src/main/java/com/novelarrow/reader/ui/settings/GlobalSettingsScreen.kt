package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToToolbarSettings: () -> Unit,
    onNavigateToHardwareButtons: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onNavigateToVoiceEngine: () -> Unit = {},
    onNavigateToPronunciationRules: () -> Unit = {},
    onNavigateToSkipWords: () -> Unit = {},
    onNavigateToHeaderFooterSkips: () -> Unit = {},
    onNavigateToContentSelector: () -> Unit = {},
    onNavigateToBrowserIgnoredSelectors: () -> Unit = {},
    onNavigateToBrowserNextSelector: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    onNavigateToAbout: () -> Unit
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Library Menu")
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "AUDIO & HARDWARE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsNavigationItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Toolbar (Notification)",
                        subtitle = "Persistent notification toolbar, speed toggle & quick resume",
                        onClick = onNavigateToToolbarSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "Hardware Buttons",
                        subtitle = "Map volume keys, double press, long press & screen on/off actions",
                        onClick = onNavigateToHardwareButtons
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Voice Engine & Voices",
                        subtitle = "Select speech engine, favorite voices for EN, AR, FR & preview samples",
                        onClick = onNavigateToVoiceEngine
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "READING & TEXT FILTERS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsNavigationItem(
                        icon = Icons.Default.Translate,
                        title = "Pronunciation Rules",
                        subtitle = "Phonetic overrides, whole-word/regex replacement & folder/URL scopes",
                        onClick = onNavigateToPronunciationRules
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.Default.FilterAlt,
                        title = "Auto Skip Words",
                        subtitle = "Silence and strip unwanted words, emojis and symbols",
                        onClick = onNavigateToSkipWords
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.Default.FilterList,
                        title = "Header & Footer Skips",
                        subtitle = "Automatically skip author notes, translator messages, and boilerplate",
                        onClick = onNavigateToHeaderFooterSkips
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.Default.Code,
                        title = "CSS Content Selectors",
                        subtitle = "Configure prioritized multiple CSS selectors per site or folder",
                        onClick = onNavigateToContentSelector
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.Default.FilterList,
                        title = "Ignore Elements Selector",
                        subtitle = "Strip ads, comments, and boilerplate from novel extractions (library & web)",
                        onClick = onNavigateToBrowserIgnoredSelectors
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsNavigationItem(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        title = "Browser Next Chapter Selector",
                        subtitle = "Configure CSS selector used to detect and click next chapter links",
                        onClick = onNavigateToBrowserNextSelector
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DATA MANAGEMENT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                SettingsNavigationItem(
                    icon = Icons.Default.Delete,
                    title = "Clear All Library & Progress",
                    subtitle = "Delete all stored novels, chapters, reading history and audio files",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showClearConfirmation = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ABOUT & UPDATES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                SettingsNavigationItem(
                    icon = Icons.Default.Info,
                    title = "About BookReader",
                    subtitle = "App info, license, changelog and software updates",
                    onClick = onNavigateToAbout
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text("Clear Entire Library?")
            },
            text = {
                Text("Are you sure you want to delete all novels, chapters, downloaded files, and reading progress? This action is permanent and cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = tint ?: MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ),
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
