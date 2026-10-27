package com.novelarrow.reader.ui.reader.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.data.model.BrowserTab
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.repository.ChapterSortPreset
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.ui.common.ChapterShareDialog
import com.novelarrow.reader.ui.common.NovelFolderShareDialog
import java.io.File

@Composable
fun LibraryDrawerContent(
    novels: List<Novel>,
    currentNovel: Novel?,
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    browserTabs: List<BrowserTab> = emptyList(),
    currentTabId: String = "",
    installedVersionName: String,
    onSelectNovel: (Novel) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onDeleteNovel: (Novel, deleteFromDisk: Boolean) -> Unit,
    onDeleteChapter: (Chapter, deleteFromDisk: Boolean) -> Unit = { _, _ -> },
    onMoveChapter: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onToggleChapterReadStatus: (Chapter) -> Unit = {},
    onAddFolder: () -> Unit,
    onRefreshFolders: () -> Unit,
    onOpenBrowser: (createNewTab: Boolean) -> Unit,
    onSelectTab: (String) -> Unit = {},
    onNewTab: () -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onOpenDevLogs: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onArchiveNovel: (Novel) -> Unit = {},
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeActionNovelId by remember { mutableStateOf<String?>(null) }
    var activeActionChapterId by remember { mutableStateOf<String?>(null) }
    var sharingNovel by remember { mutableStateOf<Novel?>(null) }
    var sharingChapter by remember { mutableStateOf<Chapter?>(null) }
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }

    val chapterListState = rememberLazyListState()
    val activeChapterIndex = remember(chapters, currentChapter, currentNovel) {
        val idx = chapters.indexOfFirst { it.id == currentChapter?.id }
        if (idx >= 0) idx else (currentNovel?.currentChapterIndex ?: 0)
    }
    LaunchedEffect(selectedTab, activeChapterIndex) {
        activeActionNovelId = null
        activeActionChapterId = null
        if (selectedTab == 1 && activeChapterIndex in chapters.indices) {
            chapterListState.animateScrollToItem((activeChapterIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row {
                IconButton(onClick = onOpenAudioStudio) {
                    Icon(Icons.Default.Audiotrack, contentDescription = "Text to MP3")
                }
                IconButton(onClick = onOpenArchive) {
                    Icon(Icons.Default.Archive, contentDescription = "Archived Novels")
                }
                IconButton(onClick = onAddFolder) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import Folder")
                }
                IconButton(onClick = onRefreshFolders) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh linked folders")
                }
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }

        // Tabs: Library vs Chapters
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Library (${novels.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Chapters (${chapters.size})") }
            )
        }

        HorizontalDivider()

        if (selectedTab == 0) {
            ReaderNovelsTab(
                novels = novels,
                currentNovel = currentNovel,
                browserTabs = browserTabs,
                currentTabId = currentTabId,
                activeActionNovelId = activeActionNovelId,
                onSetActiveActionNovelId = { activeActionNovelId = it },
                onSelectNovel = onSelectNovel,
                onSwitchToChaptersTab = { selectedTab = 1 },
                onShareNovel = { sharingNovel = it },
                onArchiveNovel = onArchiveNovel,
                onDeleteNovelRequest = {
                    novelToDelete = it
                },
                onAddFolder = onAddFolder,
                onOpenHistory = onOpenHistory,
                onOpenDownloads = onOpenDownloads,
                onNewTab = onNewTab,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                modifier = Modifier.weight(1f)
            )
        } else {
            ReaderChaptersTab(
                currentNovel = currentNovel,
                chapters = chapters,
                currentChapter = currentChapter,
                activeChapterIndex = activeChapterIndex,
                chapterListState = chapterListState,
                activeActionChapterId = activeActionChapterId,
                onSetActiveActionChapterId = { activeActionChapterId = it },
                onSelectChapter = onSelectChapter,
                onToggleChapterReadStatus = onToggleChapterReadStatus,
                onShareChapter = { sharingChapter = it },
                onDeleteChapterRequest = {
                    chapterToDelete = it
                },
                onMoveChapter = onMoveChapter,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider()

        // Drawer Bottom Footer: Settings
        Surface(
            onClick = onOpenSettings,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Toolbar, TTS, CSS & Preferences",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        HorizontalDivider()

        // Drawer Bottom Footer: About App
        Surface(
            onClick = onOpenAbout,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "About App",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "About BookReader",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "v$installedVersionName",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onOpenDevLogs,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = "Developer Logs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        novelToDelete?.let { novel ->
            NovelDeleteDialog(
                novel = novel,
                onConfirm = { deleteFromDisk ->
                    onDeleteNovel(novel, deleteFromDisk)
                    novelToDelete = null
                },
                onDismiss = { novelToDelete = null }
            )
        }

        chapterToDelete?.let { chapter ->
            ChapterDeleteDialog(
                chapter = chapter,
                onConfirm = { deleteFromDisk ->
                    onDeleteChapter(chapter, deleteFromDisk)
                    chapterToDelete = null
                },
                onDismiss = { chapterToDelete = null }
            )
        }

        if (sharingNovel != null) {
            val novelToShare = sharingNovel!!
            val novelChapters = if (novelToShare.id == currentNovel?.id) chapters else emptyList()
            NovelFolderShareDialog(
                novel = novelToShare,
                chapters = novelChapters,
                onDismiss = { sharingNovel = null },
                onFuseAndShare = {
                    Toast.makeText(context, "Fusing novel chapters in background...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startFuseNovel(context, novelToShare.id)
                },
                onZipAndShare = {
                    Toast.makeText(context, "Zipping novel folder in background...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startZipNovel(context, novelToShare.id)
                },
                onAudifyAndShare = { start, end, speed, splitMode ->
                    Toast.makeText(context, "Audifying novel in background...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startExportNovel(
                        context = context,
                        novelId = novelToShare.id,
                        startChap = start,
                        endChap = end,
                        splitMode = splitMode,
                        speed = speed
                    )
                    onOpenAudioStudio()
                }
            )
        }

        if (sharingChapter != null) {
            val chapToShare = sharingChapter!!
            ChapterShareDialog(
                chapter = chapToShare,
                onDismiss = { sharingChapter = null },
                onShareDirectly = {
                    val rawFile = File(chapToShare.fileUri)
                    if (rawFile.exists()) {
                        val shareIntent = FileFusionManager.createShareIntent(context, rawFile, "text/*")
                        context.startActivity(Intent.createChooser(shareIntent, "Share chapter"))
                    } else {
                        Toast.makeText(context, "Chapter file not found on disk", Toast.LENGTH_SHORT).show()
                    }
                },
                onAudifyAndShare = { speed ->
                    val novId = currentNovel?.id ?: chapToShare.novelId
                    Toast.makeText(context, "Exporting chapter...", Toast.LENGTH_SHORT).show()
                    AudioExportService.startExportNovel(
                        context = context,
                        novelId = novId,
                        startChap = chapToShare.orderIndex,
                        endChap = chapToShare.orderIndex,
                        splitMode = AudioExportEngine.SplitMode.SINGLE_FILE,
                        speed = speed,
                        audioFormat = AudioExportEngine.AudioExportFormat.WAV,
                        directShare = true
                    )
                }
            )
        }
    }
}
