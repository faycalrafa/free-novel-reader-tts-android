package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PronunciationRuleCard(
    rule: PronunciationRule,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled && masterEnabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Rule from -> to & Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rule.from,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rule.to,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    enabled = masterEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges & Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FlowRow for chips to avoid truncation on narrow devices
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Exact Match Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (rule.exactMatch) "Exact Word" else "Substring",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Scope Badge
                    val scopeColor = when (rule.scopeType) {
                        PronunciationScopeType.GLOBAL -> MaterialTheme.colorScheme.primaryContainer
                        PronunciationScopeType.FOLDER -> MaterialTheme.colorScheme.tertiaryContainer
                        PronunciationScopeType.URL_PATTERN -> MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    }
                    val onScopeColor = when (rule.scopeType) {
                        PronunciationScopeType.GLOBAL -> MaterialTheme.colorScheme.onPrimaryContainer
                        PronunciationScopeType.FOLDER -> MaterialTheme.colorScheme.onTertiaryContainer
                        PronunciationScopeType.URL_PATTERN -> MaterialTheme.colorScheme.onSurface
                    }
                    val scopeLabel = when (rule.scopeType) {
                        PronunciationScopeType.GLOBAL -> "Global"
                        PronunciationScopeType.FOLDER -> "Folder: ${rule.scopePattern.ifBlank { "Any" }}"
                        PronunciationScopeType.URL_PATTERN -> "URL: ${rule.scopePattern.ifBlank { "Any" }}"
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = scopeColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = when (rule.scopeType) {
                                    PronunciationScopeType.GLOBAL -> Icons.Default.Public
                                    PronunciationScopeType.FOLDER -> Icons.Default.Folder
                                    PronunciationScopeType.URL_PATTERN -> Icons.Default.Public
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = onScopeColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = scopeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = onScopeColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Edit and Delete Icon Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit rule",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete rule",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
