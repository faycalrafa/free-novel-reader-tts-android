package com.novelarrow.reader.ui.reader.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderChaptersTab(
    currentNovel: Novel?,
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    activeChapterIndex: Int,
    chapterListState: LazyListState,
    activeActionChapterId: String?,
    onSetActiveActionChapterId: (String?) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleChapterReadStatus: (Chapter) -> Unit,
    onShareChapter: (Chapter) -> Unit,
    onDeleteChapterRequest: (Chapter) -> Unit,
    onMoveChapter: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier) {
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
                                        onSetActiveActionChapterId(null)
                                    } else {
                                        onSelectChapter(i)
                                    }
                                },
                                onLongClick = {
                                    onSetActiveActionChapterId(if (activeActionChapterId == chapter.id) null else chapter.id)
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
                                        onSetActiveActionChapterId(null)
                                        onShareChapter(chapter)
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
                                        onDeleteChapterRequest(chapter)
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
