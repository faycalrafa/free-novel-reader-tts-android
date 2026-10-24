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
    onSortChapters: (ChapterSortPreset) -> Unit = {},
    onToggleChapterReadStatus: (Chapter) -> Unit = {},
    onAddFolder: () -> Unit,
    onRefreshFolders: () -> Unit,
    onOpenBrowser: (createNewTab: Boolean) -> Unit,
    onSelectTab: (String) -> Unit = {},
    onNewTab: () -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onOpenHistory: (() -> Unit)? = null,
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
    var deleteFilesFromDisk by remember { mutableStateOf(false) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var deleteChapterFromDisk by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

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
            Column(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (novels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Book,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No novels in library yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onAddFolder,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Import Folder")
                                    }
                                }
                            }
                        }
                    } else {
                        items(novels.size) { i ->
                            val novel = novels[i]
                            val isCurrent = novel.id == currentNovel?.id

                            val isActionsVisible = activeActionNovelId == novel.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (activeActionNovelId != null) {
                                                activeActionNovelId = null
                                            } else {
                                                onSelectNovel(novel)
                                                selectedTab = 1
                                            }
                                        },
                                        onLongClick = {
                                            activeActionNovelId = if (activeActionNovelId == novel.id) null else novel.id
                                        }
                                    )
                                    .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = novel.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Text(
                                        text = "${novel.chapterCount} chapters • ${novel.currentChapterIndex + 1}/${novel.chapterCount}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                if (isActionsVisible) {
                                    IconButton(
                                        onClick = {
                                            activeActionNovelId = null
                                            sharingNovel = novel
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share novel",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            activeActionNovelId = null
                                            onArchiveNovel(novel)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Archive,
                                            contentDescription = "Archive novel",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            novelToDelete = novel
                                            deleteFilesFromDisk = false
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }

                    // Demarking Line & Browser Tabs Section
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Browser Tabs (${browserTabs.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onOpenHistory != null) {
                                    IconButton(
                                        onClick = onOpenHistory,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = "Browsing History",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onNewTab,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "New Tab",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (browserTabs.isEmpty()) {
                        item {
                            Text(
                                text = "No open tabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(browserTabs.size) { tIdx ->
                            val tab = browserTabs[tIdx]
                            val isSelectedTab = tab.id == currentTabId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTab(tab.id) }
                                    .background(if (isSelectedTab) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isSelectedTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title.ifBlank { "New Tab" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelectedTab) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url.replace("https://", "").replace("http://", ""),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (browserTabs.size > 1) {
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        } else {
            // Chapters list
            Column(modifier = Modifier.weight(1f)) {
                if (currentNovel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentNovel.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
                LazyColumn(
                    state = chapterListState,
                    modifier = Modifier.weight(1f)
                ) {
                    if (chapters.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chapters in current novel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(chapters.size) { i ->
                            val chapter = chapters[i]
                            val isCurrent = if (currentChapter != null) {
                                chapter.id == currentChapter.id
                            } else {
                                i == activeChapterIndex
                            }
                            val isDraggingThis = draggingIndex == i
                            val isTargeted = dragOverIndex == i && !isDraggingThis

                            val isChapterActionsVisible = activeActionChapterId == chapter.id || isDraggingThis

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        if (isDraggingThis) {
                                            translationY = dragOffsetY
                                            shadowElevation = 16f
                                        }
                                    }
                                    .zIndex(if (isDraggingThis) 10f else 0f)
                                    .background(
                                        when {
                                            isDraggingThis -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            isTargeted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (activeActionChapterId != null && activeActionChapterId != chapter.id) {
                                                activeActionChapterId = null
                                            } else {
                                                onSelectChapter(i)
                                            }
                                        },
                                        onLongClick = {
                                            activeActionChapterId = if (activeActionChapterId == chapter.id) null else chapter.id
                                        }
                                    )
                                    .padding(
                                        start = if (isChapterActionsVisible) 6.dp else 16.dp,
                                        end = if (isChapterActionsVisible) 6.dp else 16.dp,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 6 Dots Drag & Drop Reordering Handle on the FAR LEFT (only when long-pressed)
                                if (isChapterActionsVisible) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Hold and drag to reorder",
                                        tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .pointerInput(i) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingIndex = i
                                                        dragOverIndex = i
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val approxItemHeightPx = 135f
                                                        val diff = (dragOffsetY / approxItemHeightPx).toInt()
                                                        val target = (i + diff).coerceIn(0, chapters.size - 1)
                                                        if (target != dragOverIndex) {
                                                            dragOverIndex = target
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val from = draggingIndex
                                                        val to = dragOverIndex
                                                        if (from != null && to != null && from != to) {
                                                            onMoveChapter(from, to)
                                                        }
                                                        draggingIndex = null
                                                        dragOverIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingIndex = null
                                                        dragOverIndex = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                            .padding(end = 4.dp)
                                    )
                                }

                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (chapter.isCompleted) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Chapter read",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val heartbeatTransition = rememberInfiniteTransition(label = "chapterHeartbeat")
                                            val heartbeatScale by heartbeatTransition.animateFloat(
                                                initialValue = 0.8f,
                                                targetValue = 1.35f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(700, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "heartbeatScale"
                                            )
                                            val heartbeatAlpha by heartbeatTransition.animateFloat(
                                                initialValue = 0.5f,
                                                targetValue = 1.0f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(700, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "heartbeatAlpha"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .scale(heartbeatScale)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF9800).copy(alpha = heartbeatAlpha))
                                            )
                                        }
                                    }
                                }

                                // Actions: Read status toggle & Delete Chapter (only when long-pressed)
                                if (isChapterActionsVisible) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                activeActionChapterId = null
                                                sharingChapter = chapter
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share Chapter",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onToggleChapterReadStatus(chapter) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (chapter.isCompleted) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (chapter.isCompleted) "Mark as unread" else "Mark as read",
                                                tint = if (chapter.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                chapterToDelete = chapter
                                                deleteChapterFromDisk = false
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Chapter",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                    }
                }
            }
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

        if (novelToDelete != null) {
            val novel = novelToDelete!!
            AlertDialog(
                onDismissRequest = { novelToDelete = null },
                title = {
                    Text(
                        text = "Remove Folder",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Are you sure you want to remove \"${novel.title}\" from the library?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteFilesFromDisk = !deleteFilesFromDisk }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deleteFilesFromDisk,
                                onCheckedChange = { deleteFilesFromDisk = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete files in memory as well",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deleteFilesFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteNovel(novel, deleteFilesFromDisk)
                            novelToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deleteFilesFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { novelToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (chapterToDelete != null) {
            val chapter = chapterToDelete!!
            AlertDialog(
                onDismissRequest = { chapterToDelete = null },
                title = {
                    Text(
                        text = "Delete Chapter",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Are you sure you want to remove \"${chapter.title}\" from this playlist?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteChapterFromDisk = !deleteChapterFromDisk }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deleteChapterFromDisk,
                                onCheckedChange = { deleteChapterFromDisk = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete file from device storage as well",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deleteChapterFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteChapter(chapter, deleteChapterFromDisk)
                            chapterToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deleteChapterFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chapterToDelete = null }) {
                        Text("Cancel")
                    }
                }
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
