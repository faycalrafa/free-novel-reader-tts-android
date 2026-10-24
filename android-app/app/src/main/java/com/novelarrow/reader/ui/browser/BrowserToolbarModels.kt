package com.novelarrow.reader.ui.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class TopBarActionItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val background: Color = Color.Transparent,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

fun getShortcutIcon(iconName: String): ImageVector {
    return when (iconName) {
        "Next" -> Icons.Default.SkipNext
        "Star" -> Icons.Default.Star
        "Play" -> Icons.Default.PlayArrow
        "Link" -> Icons.Default.Link
        "Bookmark" -> Icons.Default.Bookmark
        "Code" -> Icons.Default.Code
        else -> Icons.Default.Mouse
    }
}
