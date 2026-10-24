package com.novelarrow.reader.ui.settings

import android.speech.tts.Voice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.playback.VoiceGroupingHelper

@Composable
internal fun VoiceItemRow(
    voice: Voice,
    isSelected: Boolean,
    isPreviewing: Boolean,
    pulseAlpha: Float,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
    val country = voice.locale?.displayCountry?.ifBlank { voice.locale?.country.orEmpty() }.orEmpty()
    val qualityDesc = VoiceGroupingHelper.formatQuality(voice.quality)
    val latencyDesc = VoiceGroupingHelper.formatLatency(voice.latency)
    val isOffline = !voice.isNetworkConnectionRequired

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPreviewing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = when {
            isPreviewing -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (country.isNotBlank()) {
                        Text(
                            text = country,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = qualityDesc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Text(
                        text = if (isOffline) "Offline" else "Network",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                }

                if (isPreviewing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Speaking preview sample...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Audio Preview Icon Button
            IconButton(
                onClick = onTogglePreview,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                if (isPreviewing) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop Voice Preview",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Play Voice Preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
