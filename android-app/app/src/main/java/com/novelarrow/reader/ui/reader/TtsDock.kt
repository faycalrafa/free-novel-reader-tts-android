package com.novelarrow.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.ui.reader.components.ActiveFiltersDialog

@Composable
private fun formatTimeSeconds(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMins = minutes % 60
        "%d:%02d:%02d".format(hours, remMins, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
fun TtsDock(
    currentParagraph: Int,
    totalParagraphs: Int,
    currentChapterIndex: Int = 0,
    totalChapters: Int = 0,
    isPlaying: Boolean,
    speechRate: Float = 1.0f,
    sleepTimerMinutes: Int = 0,
    sleepTimerRemainingSeconds: Int = 0,
    onPlayPauseClick: () -> Unit,
    onPrevChapterClick: () -> Unit,
    onNextChapterClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSeekParagraph: (Int) -> Unit,
    isBrowserMode: Boolean = false,
    onOpenAudioSettings: () -> Unit = {},
    isAudioFileMode: Boolean = false,
    audioFilePositionMs: Long = 0L,
    audioFileDurationMs: Long = 0L,
    onSeekAudioPosition: (Long) -> Unit = {},
    onCloseClick: () -> Unit = {},
    activeRuleImpacts: List<RuleImpact> = emptyList(),
    onToggleRule: (RuleImpact, Boolean) -> Unit = { _, _ -> },
    flashHandleRed: Boolean = false,
    onResetFlashHandle: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragParaIndex by remember(currentParagraph) { mutableFloatStateOf(currentParagraph.toFloat()) }
    var dragAudioMs by remember(audioFilePositionMs) { mutableFloatStateOf(audioFilePositionMs.toFloat()) }
    var showActiveFiltersDialog by remember { mutableStateOf(false) }

    val hasActiveRules = activeRuleImpacts.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "flashRedTransition")
    val redFlashColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFFF1744),
        targetValue = Color(0xFFFF8A80),
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handleFlashColor"
    )

    val heartbeatTransition = rememberInfiniteTransition(label = "filterHeartbeat")
    val heartbeatScale by heartbeatTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatScale"
    )
    val heartbeatColor by heartbeatTransition.animateColor(
        initialValue = Color(0xFFFF9800),
        targetValue = Color(0xFFFF5722),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatColor"
    )

    val maxAudioDuration = audioFileDurationMs.coerceAtLeast(1000L).toFloat()

    val displayPara = if (isDragging) {
        dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
    } else {
        currentParagraph.coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
    }

    val displayAudioMs = if (isDragging) {
        dragAudioMs.toLong().coerceIn(0L, audioFileDurationMs)
    } else {
        audioFilePositionMs.coerceIn(0L, audioFileDurationMs)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top row: Progress info + Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAudioFileMode) {
                            Text(
                                text = "${formatTimeSeconds(displayAudioMs)} / ${formatTimeSeconds(audioFileDurationMs)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        } else {
                            if (totalChapters > 1) {
                                Text(
                                    text = "Ch ${currentChapterIndex + 1}/$totalChapters",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = "Para ${displayPara + 1} / $totalParagraphs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sleepTimerRemainingSeconds > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clickable { onSleepTimerClick() }
                            ) {
                                Text(
                                    text = "${sleepTimerRemainingSeconds / 60}:${"%02d".format(sleepTimerRemainingSeconds % 60)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenAudioSettings,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Audio settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onCloseClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close dock",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Slider row
                if (isAudioFileMode) {
                    Slider(
                        value = displayAudioMs.toFloat(),
                        onValueChange = {
                            isDragging = true
                            dragAudioMs = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeekAudioPosition(dragAudioMs.toLong())
                        },
                        valueRange = 0f..maxAudioDuration,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Slider(
                        value = displayPara.toFloat(),
                        onValueChange = {
                            isDragging = true
                            dragParaIndex = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeekParagraph(dragParaIndex.toInt())
                        },
                        valueRange = 0f..(totalParagraphs - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left buttons: Active Filters indicator + Sleep timer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasActiveRules) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = heartbeatColor.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .scale(heartbeatScale)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showActiveFiltersDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = "Active filters",
                                        tint = heartbeatColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${activeRuleImpacts.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = heartbeatColor
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = onSleepTimerClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Sleep timer",
                                tint = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Center & Right buttons: Prev, Play/Pause, Next, Speed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Previous button directly next to Play/Pause on the left
                        IconButton(
                            onClick = onPrevChapterClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = if (isBrowserMode) "Previous Paragraph" else "Previous Chapter",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause button in center
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (flashHandleRed) redFlashColor else MaterialTheme.colorScheme.primary
                                )
                                .clickable {
                                    if (flashHandleRed) onResetFlashHandle()
                                    onPlayPauseClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Next button directly next to Play/Pause on the right
                        IconButton(
                            onClick = onNextChapterClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = if (isBrowserMode) "Next Paragraph" else "Next Chapter",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Speed button on the right
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSpeedClick() }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${"%.2f".format(speechRate).trimEnd('0').trimEnd('.')}x",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActiveFiltersDialog) {
        ActiveFiltersDialog(
            rules = activeRuleImpacts,
            onToggleRule = onToggleRule,
            onDismiss = { showActiveFiltersDialog = false }
        )
    }
}
