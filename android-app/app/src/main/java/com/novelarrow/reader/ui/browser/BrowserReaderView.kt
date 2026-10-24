package com.novelarrow.reader.ui.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.ui.common.AppLoadingIndicator

@Composable
fun BrowserReaderView(
    title: String,
    paragraphs: List<Paragraph>,
    currentIndex: Int,
    isLoadingNext: Boolean,
    autoScroll: Boolean,
    autoNextOnScroll: Boolean = false,
    onParagraphClick: (Int) -> Unit,
    onNextChapterClick: () -> Unit,
    onEndReached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (autoScroll && currentIndex in paragraphs.indices) {
            val itemIndex = if (title.isNotBlank()) currentIndex + 1 else currentIndex
            listState.animateScrollToItem(
                index = itemIndex,
                scrollOffset = -150
            )
        }
    }

    LaunchedEffect(listState, paragraphs.size, autoNextOnScroll) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 1 && lastVisible >= totalItems - 1
        }.collect { isAtBottom ->
            if (isAtBottom && !isLoadingNext && paragraphs.isNotEmpty() && autoNextOnScroll) {
                onEndReached()
            }
        }
    }

    if (paragraphs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppLoadingIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = "Extracting clean chapter text...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (title.isNotBlank()) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, top = 6.dp)
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
                val isCurrent = index == currentIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                    label = "highlight_anim"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable { onParagraphClick(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Small paragraph number displayed to the left of each paragraph
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier
                                .width(28.dp)
                                .padding(top = if (paragraph.isHeading) 4.dp else 3.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        val textToDisplay = paragraph.spokenText.ifBlank { paragraph.rawText }
                        Text(
                            text = textToDisplay,
                            style = if (paragraph.isHeading) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp
                                )
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoadingNext) {
                        AppLoadingIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Loading next chapter...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        Button(
                            onClick = onNextChapterClick,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Next Chapter")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }
}
