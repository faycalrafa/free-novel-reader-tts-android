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
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    val progressPercent = if (totalParagraphs > 0) {
        ((displayPara + 1).toFloat() / totalParagraphs.toFloat() * 100).toInt()
    } else 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Slider row with Active Filters, Settings gear & Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = if (isAudioFileMode) {
                        displayAudioMs.toFloat()
                    } else {
                        displayPara.toFloat()
                    },
                    onValueChange = { newVal ->
                        isDragging = true
                        if (isAudioFileMode) {
                            dragAudioMs = newVal
                        } else {
                            dragParaIndex = newVal
                        }
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        if (isAudioFileMode) {
                            onSeekAudioPosition(dragAudioMs.toLong().coerceIn(0L, audioFileDurationMs))
                        } else {
                            val target = dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
                            onSeekParagraph(target)
                        }
                    },
                    valueRange = if (isAudioFileMode) 0f..maxAudioDuration else 0f..(totalParagraphs - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Dedicated Active Filters button immediately right of the progress bar
                IconButton(
                    onClick = {
                        showActiveFiltersDialog = true
                        if (flashHandleRed) onResetFlashHandle()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer {
                            val scale = if (hasActiveRules || flashHandleRed) heartbeatScale else 1.0f
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = "Active Rules & Filters",
                        tint = if (flashHandleRed) redFlashColor else if (hasActiveRules) heartbeatColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Settings gear button
                IconButton(
                    onClick = onOpenAudioSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "TTS and Audio Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Close button to close media bar and stop ongoing playback
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close dock",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata row
            if (isAudioFileMode) {
                val curMs = displayAudioMs
                val curFormatted = formatTimeSeconds(curMs)
                val totalFormatted = formatTimeSeconds(audioFileDurationMs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDragging) "Release to jump to $curFormatted" else if (totalChapters > 0) "Ch. ${currentChapterIndex + 1} / $totalChapters" else "Audio Studio",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$curFormatted / $totalFormatted",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (isBrowserMode) {
                                if (totalParagraphs > 0) "Para ${displayPara + 1} / $totalParagraphs" else "Live Web TTS"
                            } else if (totalChapters > 0) {
                                "Ch. ${currentChapterIndex + 1} / $totalChapters"
                            } else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isDragging) {
                            Text(
                                text = "Release to jump to ¶ ${displayPara + 1} ($progressPercent%)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Text(
                        text = "¶ ${displayPara + 1} / $totalParagraphs ($progressPercent%)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Control buttons row: [Sleep Timer] [Prev] [Play/Pause] [Next] [Speed]
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep Timer button on the left
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSleepTimerClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val timerLabel = if (sleepTimerMinutes <= 0) {
                                "Off"
                            } else if (sleepTimerRemainingSeconds > 0) {
                                val mins = sleepTimerRemainingSeconds / 60
                                val secs = sleepTimerRemainingSeconds % 60
                                "$mins:${secs.toString().padStart(2, '0')}"
                            } else {
                                "${sleepTimerMinutes}m"
                            }
                            Text(
                                text = timerLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    // Prev button directly next to Play/Pause on the left
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

                    // Play/Pause button with gradient background
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (flashHandleRed) Brush.linearGradient(listOf(redFlashColor, redFlashColor))
                                else Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
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

    if (showActiveFiltersDialog) {
        ActiveFiltersDialog(
            rules = activeRuleImpacts,
            onToggleRule = onToggleRule,
            onDismiss = { showActiveFiltersDialog = false }
        )
    }
}
