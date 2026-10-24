package com.novelarrow.reader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.theme.DarkHighlight
import com.novelarrow.reader.theme.LightHighlight
import com.novelarrow.reader.theme.SepiaHighlight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderContentView(
    paragraphs: List<Paragraph>,
    currentIndex: Int,
    isPlaying: Boolean,
    fontSizeSp: Float,
    themeMode: ThemeMode,
    autoScroll: Boolean,
    chapterTitle: String = "Chapter",
    speechRate: Float = 1.0f,
    onOpenAudioStudio: (() -> Unit)? = null,
    onParagraphClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isSelectionMode = selectedIndices.isNotEmpty()

    LaunchedEffect(currentIndex) {
        if (autoScroll && !isSelectionMode && currentIndex in paragraphs.indices) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -150
            )
        }
    }

    val highlightColor = when (themeMode) {
        ThemeMode.DARK -> DarkHighlight
        ThemeMode.SEPIA -> SepiaHighlight
        ThemeMode.LIGHT -> LightHighlight
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
                val isCurrent = index == currentIndex
                val isSelected = selectedIndices.contains(index)
                val backgroundColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        isCurrent -> highlightColor
                        else -> Color.Transparent
                    },
                    label = "highlight_anim"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                } else {
                                    onParagraphClick(index)
                                }
                            },
                            onLongClick = {
                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrent && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        val textToDisplay = paragraph.spokenText.ifBlank { paragraph.rawText }
                        Text(
                            text = textToDisplay,
                            style = if (paragraph.isHeading) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = (fontSizeSp + 4).sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = (fontSizeSp + 10).sp
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.6f).sp
                                )
                            },
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Bottom space so dock doesn't obscure last lines
            item {
                Spacer(modifier = Modifier.height(140.dp))
            }
        }

        // Floating Selection Bar
        if (isSelectionMode) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIndices.size} selected",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    TextButton(
                        onClick = { selectedIndices = paragraphs.indices.toSet() }
                    ) {
                        Text("Select All", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            val text = selectedIndices.sorted()
                                .mapNotNull { paragraphs.getOrNull(it)?.rawText }
                                .joinToString("\n\n")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Paragraphs", text))
                            Toast.makeText(context, "${selectedIndices.size} paragraphs copied", Toast.LENGTH_SHORT).show()
                            selectedIndices = emptySet()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            val text = selectedIndices.sorted()
                                .mapNotNull { paragraphs.getOrNull(it)?.spokenText?.ifBlank { paragraphs[it].rawText } }
                                .joinToString("\n\n")
                            AudioExportService.startExportText(
                                context = context,
                                title = "${chapterTitle}_selection",
                                text = text,
                                speed = speechRate,
                                audioFormat = AudioExportEngine.AudioExportFormat.WAV,
                                directShare = true
                            )
                            Toast.makeText(context, "Exporting ${selectedIndices.size} paragraphs...", Toast.LENGTH_SHORT).show()
                            selectedIndices = emptySet()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = "Share as MP3",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { selectedIndices = emptySet() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
