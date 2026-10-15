package com.novelarrow.reader.ui.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private class CodeEditorBridge(
    private val onReady: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onEditorReady() {
        handler.post {
            onReady.invoke()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeMirrorEditorDialog(
    initialTitle: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (code: String) -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var scriptTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var isEditorReady by remember { mutableStateOf(false) }

    val safeInitialCode = remember(initialContent) {
        JSONObject.quote(initialContent)
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
                .imePadding(),
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Native Toolbar
                Surface(
                    color = Color(0xFF252526),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = scriptTitle.ifBlank { "New Userscript" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "CodeMirror JavaScript Editor",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = Color(0xFFAAAAAA)
                                )
                            )
                        }

                        // Save Button
                        Button(
                            onClick = {
                                webViewRef?.evaluateJavascript("window.getEditorValue();") { value ->
                                    val cleanText = unescapeJsString(value)
                                    onSave(cleanText)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // CodeMirror WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    allowFileAccess = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        evaluateJavascript("initEditor($safeInitialCode);", null)
                                    }
                                }
                                addJavascriptInterface(
                                    CodeEditorBridge {
                                        isEditorReady = true
                                        evaluateJavascript("initEditor($safeInitialCode);", null)
                                    },
                                    "CodeEditorBridge"
                                )
                                loadUrl("file:///android_asset/editor.html")
                                webViewRef = this
                            }
                        },
                        update = { wv ->
                            webViewRef = wv
                        }
                    )
                }

                // Utility Toolbar (Above Keyboard)
                Surface(
                    color = Color(0xFF2D2D30),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Select All
                        TextButton(
                            onClick = {
                                webViewRef?.evaluateJavascript("window.selectAllText();", null)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", modifier = Modifier.size(16.dp), tint = Color(0xFFCCCCCC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select All", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                        }

                        // Cut
                        TextButton(
                            onClick = {
                                webViewRef?.evaluateJavascript("window.getSelectionText();") { value ->
                                    val text = unescapeJsString(value)
                                    if (text.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", text))
                                        webViewRef?.evaluateJavascript("window.replaceSelectionText('');", null)
                                        Toast.makeText(context, "Cut to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(16.dp), tint = Color(0xFFCCCCCC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cut", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                        }

                        // Copy
                        TextButton(
                            onClick = {
                                webViewRef?.evaluateJavascript("window.getSelectionText();") { value ->
                                    val text = unescapeJsString(value)
                                    val toCopy = text.ifBlank {
                                        // If nothing selected, copy all code
                                        null
                                    }
                                    if (toCopy != null) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", toCopy))
                                        Toast.makeText(context, "Copied selection", Toast.LENGTH_SHORT).show()
                                    } else {
                                        webViewRef?.evaluateJavascript("window.getEditorValue();") { allVal ->
                                            val allText = unescapeJsString(allVal)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Code", allText))
                                            Toast.makeText(context, "Copied all code", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp), tint = Color(0xFFCCCCCC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                        }

                        // Paste
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasteText = clip.getItemAt(0).coerceToText(context).toString()
                                    val safePaste = JSONObject.quote(pasteText)
                                    webViewRef?.evaluateJavascript("window.replaceSelectionText($safePaste);", null)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp), tint = Color(0xFFCCCCCC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                        }

                        // Undo
                        IconButton(
                            onClick = { webViewRef?.evaluateJavascript("window.undoAction();", null) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp), tint = Color(0xFFCCCCCC))
                        }

                        // Redo
                        IconButton(
                            onClick = { webViewRef?.evaluateJavascript("window.redoAction();", null) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(16.dp), tint = Color(0xFFCCCCCC))
                        }
                    }
                }
            }
        }
    }
}

private fun unescapeJsString(value: String?): String {
    if (value == null) return ""
    return if (value.startsWith("\"") && value.endsWith("\"")) {
        try {
            org.json.JSONTokener(value).nextValue().toString()
        } catch (_: Exception) {
            value.removeSurrounding("\"")
        }
    } else {
        value
    }
}
