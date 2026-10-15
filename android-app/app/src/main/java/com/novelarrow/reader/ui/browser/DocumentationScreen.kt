package com.novelarrow.reader.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novelarrow.reader.R
import io.noties.markwon.Markwon

@Composable
fun DocumentationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rawMarkdownString = remember {
        try {
            context.resources.openRawResource(R.raw.api_docs).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            "# API Documentation\n\nFailed to load api_docs.md from res/raw."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color(0xFF121212)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Toolbar
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Userscript API Docs",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    "Markdown rendering via Markwon",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("API Docs", rawMarkdownString)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Markdown", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Markdown", fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCCCCC))
                                }
                            }
                        }
                    }

                    // ScrollView with TextView rendered using Markwon
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        factory = { ctx ->
                            val scrollView = ScrollView(ctx).apply {
                                isFillViewport = true
                                clipToPadding = false
                            }
                            val textView = TextView(ctx).apply {
                                setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
                                textSize = 15f
                                setLineSpacing(8f, 1.15f)
                            }
                            scrollView.addView(textView)

                            val markwon = Markwon.create(ctx)
                            markwon.setMarkdown(textView, rawMarkdownString)
                            scrollView
                        }
                    )
                }

                // Floating Action Button at bottom right
                ExtendedFloatingActionButton(
                    text = { Text("Copy Markdown", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("API Docs", rawMarkdownString)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
