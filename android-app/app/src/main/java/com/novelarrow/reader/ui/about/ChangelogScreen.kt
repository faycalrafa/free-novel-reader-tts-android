package com.novelarrow.reader.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ReleaseItem(
    val version: String,
    val date: String,
    val title: String,
    val changes: List<String>
)

val FullReleaseHistory = listOf(
    ReleaseItem(
        version = "v3.5.4",
        date = "Latest Release",
        title = "BookReader Rebranding & Adaptive Launcher Icon Fix",
        changes = listOf(
            "Unified app-wide branding under BookReader across UI headers, settings, notifications, toasts, and logs",
            "Fixed adaptive icon on Android 8.0 through Android 16 with rich terracotta lighting gradient background",
            "Bound roundIcon to dedicated circular terracotta assets, resolving the double-border rounded-inside-square issue",
            "Upgraded userscripts directory to Downloads/BookReader_Scripts with backward-compatible migration",
            "Renamed accessibility service to BookReaderAccessibilityService with full backward compatibility"
        )
    ),
    ReleaseItem(
        version = "v3.3.0",
        date = "2026-10-17",
        title = "Loading Animation Overhaul & Squircle Launcher App Icon",
        changes = listOf(
            "Integrated high-definition animated WebP loading indicator (turning pages & pulsating sound wave) replacing circular progress indicators app-wide",
            "Set 3D terracotta squircle cushion icon as default launcher app icon across all Android launchers and stores",
            "Preserved dedicated circular 3D icon for in-app screens (About, Reader Home empty state, Onboarding)",
            "Implemented responsive AppLoadingIndicator composable with hardware-accelerated AnimatedImageDrawable and safe fallback"
        )
    ),
    ReleaseItem(
        version = "v3.2.2",
        date = "2026-10-16",
        title = "Starting Home Screen & Onboarding 3D Icon Suite",
        changes = listOf(
            "Integrated circular 3D app icon (ic_app_icon_round.webp) on starting home screen empty state",
            "Integrated circular 3D app icon (ic_app_icon_round.webp) on onboarding welcome slide",
            "Harmonized visual branding and claymorphic aesthetic throughout entire app launch workflow"
        )
    ),
    ReleaseItem(
        version = "v3.2.1",
        date = "2026-10-16",
        title = "About Screen Icon Crash Fix & Terracotta 3D Squircle Update",
        changes = listOf(
            "Fixed Compose painterResource crash in AboutScreen caused by adaptive-icon XML",
            "Rendered dedicated circular 3D icon (ic_app_icon_round.webp) in About screen hero card",
            "Upgraded standard squircle launcher icon (ic_launcher.webp) with rich terracotta 3D claymorphic cushion",
            "Multi-density WebP mipmap suite compiled across mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi"
        )
    ),
    ReleaseItem(
        version = "v3.0.1",
        date = "2026-10-16",
        title = "3D Claymorphic Launcher & Adaptive Icon Asset Suite",
        changes = listOf(
            "Upgraded application launcher and adaptive icons with high-fidelity 3D claymorphic artwork",
            "Added full density WebP mipmap coverage (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)",
            "Configured multi-launcher adaptive viewport masking for Google Pixel, Samsung One UI, and Xiaomi",
            "Added high-resolution 512x512 Play Store master asset"
        )
    ),
    ReleaseItem(
        version = "v3.0.0",
        date = "2026-10-16",
        title = "BookReader Evolution, Text-to-MP3 Studio & Quick Settings Tile",
        changes = listOf(
            "Renamed application to BookReader and introduced the Sepia 'Resonant Codex' adaptive icon",
            "Added Quick Settings Tile drawer button to resume playback where last left off",
            "Dedicated Text-to-MP3 Studio page next to Archive button with range picker and minute/chapter splits",
            "Paragraph multi-selection in Reader mode to copy text or share as MP3 using current voice settings",
            "Folder sharing in Library drawer: fuse into single HTML/TXT/MD, zip folder, or audify to MP3",
            "Chapter sharing: send raw file directly or audify to MP3",
            "System Share target: share web links (open in browser, save frozen HTML, or save as MP3) and raw text",
            "Background conversion and zipping service (AudioExportService) with live progress notification and hide capability",
            "Automatic saving of export files > 1MB into the Downloads/BookReader folder",
            "Full chronological version history changelog screen"
        )
    ),
    ReleaseItem(
        version = "v2.9.0",
        date = "2026-10-16",
        title = "Screen-Off & System-Wide Hardware Volume Shortcuts",
        changes = listOf(
            "Added system-wide and screen-off hardware volume button mapping via BookReaderAccessibilityService",
            "Integrated MediaSession key interception for locked device controls",
            "Configurable hardware button actions for single and double clicks"
        )
    ),
    ReleaseItem(
        version = "v2.8.0",
        date = "2026-10-16",
        title = "Library Archive Screen & Action Reveals",
        changes = listOf(
            "Added long-press action reveal buttons for novels in the Library drawer",
            "Introduced dedicated ArchiveScreen to manage archived novels away from the main library",
            "One-tap archive and restore workflows for completed books"
        )
    ),
    ReleaseItem(
        version = "v2.7.0",
        date = "2026-10-16",
        title = "Hardware Volume Key Mapping Hub",
        changes = listOf(
            "Configurable hardware volume button mapping for playback, chapter skip, and speech speed",
            "Added screen-on and screen-off condition filters for button actions"
        )
    ),
    ReleaseItem(
        version = "v2.6.0",
        date = "2026-10-16",
        title = "Monaco Editor & Navigation Harmonization",
        changes = listOf(
            "Bundled fully offline Monaco Editor for Userscript creation and editing",
            "Streamlined notification toolbar layout displaying chapter fractions",
            "Unified burger drawer navigation consistently across Reader, Browser, and Settings"
        )
    ),
    ReleaseItem(
        version = "v2.5.0",
        date = "2026-10-15",
        title = "Notification Quick Toolbar Redesign & Live Sleep Timer",
        changes = listOf(
            "Redesigned notification toolbar UI with dark slate pills and Material 3 vector icons",
            "Live real-time sleep timer countdown (mm:ss) synchronized via coroutines",
            "Fixed speech rate display formatting bug (1.75xx -> 1.75x)",
            "Harmonized ToolbarSettingsScreen preview mockup"
        )
    ),
    ReleaseItem(
        version = "v2.4.4",
        date = "2026-10-15",
        title = "Notification Channel Hardening & Layout Optimization",
        changes = listOf(
            "Migrated to novel_quick_toolbar_channel_v4 with IMPORTANCE_DEFAULT and no sound/vibration",
            "Pruned obsolete notification channels from system settings",
            "Resolved notification visibility issues on OnePlus OxygenOS and Android 16"
        )
    ),
    ReleaseItem(
        version = "v2.4.3",
        date = "2026-10-15",
        title = "RemoteViews Crash Hardening & Layout Validation",
        changes = listOf(
            "Replaced unsupported View tags with @RemoteView-annotated FrameLayout",
            "Added defensive in-process pre-inflation test and safe fallback in QuickToolbarForegroundService",
            "Introduced automated RemoteViewsValidationTest suite"
        )
    ),
    ReleaseItem(
        version = "v2.4.2",
        date = "2026-10-14",
        title = "Foreground Service Toolbar & Dev Diagnostics",
        changes = listOf(
            "Migrated quick toolbar to QuickToolbarForegroundService (specialUse) with dismiss rebound",
            "Added AppLogger 60-minute rolling ring buffer and PID logcat dumper",
            "Created DevLogsScreen in About section with search, filtering, and export",
            "Integrated AppErrorBus and crash notification dialogs"
        )
    ),
    ReleaseItem(
        version = "v2.4.1",
        date = "2026-10-14",
        title = "Toolbar Visibility, Sleep Timer & Dialog Polish",
        changes = listOf(
            "Resolved quick toolbar visibility bugs across OEM Android skins",
            "Enhanced sleep timer controls with 15, 30, 45, 60-minute quick presets",
            "Redesigned dialog styling with edge-to-edge support"
        )
    ),
    ReleaseItem(
        version = "v2.4.0",
        date = "2026-10-14",
        title = "Global Settings Hub & In-Browser Reading Mode",
        changes = listOf(
            "Introduced unified GlobalSettingsScreen for TTS voice, speech rate, and pitch",
            "Added in-browser reading mode with customized font size and background themes",
            "Implemented persistent notification toolbar toggle"
        )
    ),
    ReleaseItem(
        version = "v2.3.0",
        date = "2026-10-14",
        title = "Userscript Manager & Documentation Center",
        changes = listOf(
            "Comprehensive Userscript manager supporting Tampermonkey and Violentmonkey headers",
            "Local script storage and execution inside sandboxed IIFEs",
            "Integrated Markwon documentation center for Userscript APIs and tutorials"
        )
    ),
    ReleaseItem(
        version = "v2.2.0",
        date = "2026-10-14",
        title = "Reinstall Backup Restore & Setup Guide",
        changes = listOf(
            "Added automated backup and restore for library state and novel progress",
            "Interactive 3-view onboarding slideshow explaining storage and battery permissions",
            "Cleaned up legacy configuration files"
        )
    ),
    ReleaseItem(
        version = "v2.1.0",
        date = "2026-10-14",
        title = "Drag-and-Drop Reordering & Reactive Notifications",
        changes = listOf(
            "Drag-and-drop chapter reordering with animated feedback",
            "Unified media control bar with sleep timer and speed adjustments",
            "Reactive lockscreen and notification updates via Media3 session"
        )
    ),
    ReleaseItem(
        version = "v2.0.1",
        date = "2026-10-13",
        title = "Library Indexing & Chapter Management",
        changes = listOf(
            "Chapter deletion and reordering safeguards",
            "Duplicate chapter prevention during downloads",
            "Fast directory indexing with natural order sorting"
        )
    ),
    ReleaseItem(
        version = "v2.0.0",
        date = "2026-10-13",
        title = "Embedded Web Browser & Full Page Captures",
        changes = listOf(
            "Built-in multi-tab web browser accessible from the library drawer",
            "Offline snapshot capture with floating next-chapter button",
            "Automated batch downloading for full-page novel scraping",
            "Interactive live reading directly inside the browser"
        )
    ),
    ReleaseItem(
        version = "v1.0.0",
        date = "2026-10-13",
        title = "Initial Release",
        changes = listOf(
            "Native Android Text-To-Speech playback with sentence chunking",
            "Offline novel reading with custom CSS content selectors",
            "System media notification controls and lockscreen seekbar sync",
            "GitHub in-app auto-updater and Quantum Mechanics sample novel"
        )
    )
)

@Composable
fun ChangelogScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Version History & Changes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Release Log (Newest to Oldest)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Full history of architectural updates, new features, and fixes since v1.0.0.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            items(FullReleaseHistory, key = { it.version }) { release ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (release.version == "v3.5.4") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = release.version,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (release.version == "v3.5.4") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = release.date,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (release.version == "v3.5.4") {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.NewReleases,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Latest",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            release.changes.forEach { change ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = change,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
