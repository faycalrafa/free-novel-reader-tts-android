package com.novelarrow.reader.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun MonacoEditorToolbar(
    context: Context,
    webViewRef: WebView?,
    isSearchOpen: Boolean,
    isReplaceOpen: Boolean,
    isWordWrapEnabled: Boolean,
    onToggleSearch: () -> Unit,
    onToggleReplace: () -> Unit,
    onToggleWordWrap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                webViewRef?.evaluateJavascript("window.selectAll();", null)
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.SelectAll,
                contentDescription = "Select All",
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = {
                webViewRef?.evaluateJavascript("window.cutSelection();") { value ->
                    val cleanText = if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                        try {
                            org.json.JSONTokener(value).nextValue().toString()
                        } catch (_: Exception) {
                            value.removeSurrounding("\"")
                        }
                    } else {
                        value ?: ""
                    }
                    if (cleanText.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Cut Code", cleanText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Cut to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.ContentCut,
                contentDescription = "Cut",
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = {
                webViewRef?.evaluateJavascript("window.getSelectedText();") { value ->
                    val cleanText = if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                        try {
                            org.json.JSONTokener(value).nextValue().toString()
                        } catch (_: Exception) {
                            value.removeSurrounding("\"")
                        }
                    } else {
                        value ?: ""
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Code", cleanText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip
                val textToPaste = if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0).coerceToText(context).toString()
                } else ""
                if (textToPaste.isNotEmpty()) {
                    val safeText = JSONObject.quote(textToPaste)
                    webViewRef?.evaluateJavascript("window.pasteText($safeText);", null)
                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.ContentPaste,
                contentDescription = "Paste",
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onToggleReplace,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.FindReplace,
                contentDescription = "Match Replace",
                tint = if (isReplaceOpen) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = {
                webViewRef?.evaluateJavascript("window.triggerFormat();", null)
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = "Beautify / Format",
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onToggleWordWrap,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.WrapText,
                contentDescription = "Toggle Word Wrap",
                tint = if (isWordWrapEnabled) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
