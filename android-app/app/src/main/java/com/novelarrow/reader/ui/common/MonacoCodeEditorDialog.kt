package com.novelarrow.reader.ui.common

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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import org.jsoup.Jsoup

private class MonacoBridge(
    private val onSaveRequested: (String) -> Unit,
    private val onSearchResultsReported: (Int, Int) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun saveContent(content: String) {
        handler.post {
            onSaveRequested.invoke(content)
        }
    }

    @JavascriptInterface
    fun reportSearchResults(current: Int, total: Int) {
        handler.post {
            onSearchResultsReported.invoke(current, total)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoCodeEditorDialog(
    title: String,
    initialContent: String,
    language: String = "html",
    onDismiss: () -> Unit,
    onSave: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWordWrapEnabled by remember { mutableStateOf(true) }
    var currentTitle by remember(title) { mutableStateOf(title) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }

    // Native Search & Replace Toolbar State
    var isSearchOpen by remember { mutableStateOf(false) }
    var isReplaceOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var searchCurrentIndex by remember { mutableStateOf(0) }
    var searchTotalCount by remember { mutableStateOf(0) }

    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val prevSoftInputMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            prevSoftInputMode?.let { window?.setSoftInputMode(it) }
        }
    }

    // Beautify HTML initially using Jsoup if language is html
    val preprocessedContent = remember(initialContent, language) {
        if (language.equals("html", ignoreCase = true) ||
            language.equals("htm", ignoreCase = true) ||
            language.equals("xhtml", ignoreCase = true)
        ) {
            try {
                val doc = Jsoup.parse(initialContent)
                doc.outputSettings().indentAmount(2).prettyPrint(true)
                doc.html()
            } catch (_: Exception) {
                initialContent
            }
        } else {
            initialContent
        }
    }

    val monacoHtml = remember(preprocessedContent, language) {
        buildMonacoHtml(preprocessedContent, language)
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
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Toolbar
                Surface(
                    color = Color(0xFF252526),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tier 1: Title and Window Actions (Save & Close)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = currentTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (language.equals("javascript", ignoreCase = true)) {
                                        IconButton(
                                            onClick = {
                                                renameInputText = currentTitle
                                                showRenameDialog = true
                                            },
                                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename Script",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = language.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Monaco Code Editor",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = Color(0xFFAAAAAA)
                                        )
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onSave != null) {
                                    IconButton(
                                        onClick = {
                                            webViewRef?.evaluateJavascript(
                                                "if (window.MonacoBridge) { window.MonacoBridge.saveContent(window.getEditorValue()); }",
                                                null
                                            )
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = "Save Changes",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

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

                        HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)

                        // Tier 2: Horizontally Scrollable Action Toolbar
                        Row(
                            modifier = Modifier
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
                                onClick = {
                                    isSearchOpen = !isSearchOpen
                                    if (!isSearchOpen) {
                                        isReplaceOpen = false
                                        searchQuery = ""
                                        searchCurrentIndex = 0
                                        searchTotalCount = 0
                                        webViewRef?.evaluateJavascript("window.performFind('', true, false);", null)
                                    }
                                },
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
                                onClick = {
                                    if (!isSearchOpen) {
                                        isSearchOpen = true
                                    }
                                    isReplaceOpen = !isReplaceOpen
                                },
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
                                onClick = {
                                    isWordWrapEnabled = !isWordWrapEnabled
                                    webViewRef?.evaluateJavascript("window.toggleWordWrap();", null)
                                },
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

                        // Tier 3: Native Compose Search & Replace Bar (Zero IME Input Lag)
                        if (isSearchOpen) {
                            HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2A2D2E))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                // Search Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { newQuery ->
                                            searchQuery = newQuery
                                            val safeQ = JSONObject.quote(newQuery)
                                            webViewRef?.evaluateJavascript("window.performFind($safeQ, true, $matchCase);", null)
                                        },
                                        placeholder = {
                                            Text("Find in code...", fontSize = 13.sp, color = Color(0xFF888888))
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color(0xFF444444),
                                            focusedContainerColor = Color(0xFF1E1E1E),
                                            unfocusedContainerColor = Color(0xFF1E1E1E)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Match Count Badge
                                    Text(
                                        text = if (searchQuery.isBlank()) "0/0" else "$searchCurrentIndex/$searchTotalCount",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFFCCCCCC)),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    // Match Case Toggle
                                    FilledIconToggleButton(
                                        checked = matchCase,
                                        onCheckedChange = {
                                            matchCase = it
                                            val safeQ = JSONObject.quote(searchQuery)
                                            webViewRef?.evaluateJavascript("window.performFind($safeQ, true, $matchCase);", null)
                                        },
                                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                                            containerColor = Color.Transparent,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            contentColor = Color(0xFFCCCCCC),
                                            checkedContentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("Aa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Prev Match Button
                                    IconButton(
                                        onClick = {
                                            val safeQ = JSONObject.quote(searchQuery)
                                            webViewRef?.evaluateJavascript("window.performFind($safeQ, false, $matchCase);", null)
                                        },
                                        enabled = searchQuery.isNotBlank(),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ChevronLeft,
                                            contentDescription = "Previous Match",
                                            tint = if (searchQuery.isNotBlank()) Color.White else Color(0xFF666666),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Next Match Button
                                    IconButton(
                                        onClick = {
                                            val safeQ = JSONObject.quote(searchQuery)
                                            webViewRef?.evaluateJavascript("window.performFind($safeQ, true, $matchCase);", null)
                                        },
                                        enabled = searchQuery.isNotBlank(),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "Next Match",
                                            tint = if (searchQuery.isNotBlank()) Color.White else Color(0xFF666666),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Close Search Bar
                                    IconButton(
                                        onClick = {
                                            isSearchOpen = false
                                            isReplaceOpen = false
                                            searchQuery = ""
                                            searchCurrentIndex = 0
                                            searchTotalCount = 0
                                            webViewRef?.evaluateJavascript("window.performFind('', true, false);", null)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close Search",
                                            tint = Color(0xFF888888),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Replace Row (shown when isReplaceOpen is true)
                                if (isReplaceOpen) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = replaceQuery,
                                            onValueChange = { replaceQuery = it },
                                            placeholder = {
                                                Text("Replace with...", fontSize = 13.sp, color = Color(0xFF888888))
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color(0xFF444444),
                                                focusedContainerColor = Color(0xFF1E1E1E),
                                                unfocusedContainerColor = Color(0xFF1E1E1E)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Replace Single
                                        Button(
                                            onClick = {
                                                val safeR = JSONObject.quote(replaceQuery)
                                                val safeQ = JSONObject.quote(searchQuery)
                                                webViewRef?.evaluateJavascript("window.performReplace($safeR); window.performFind($safeQ, true, $matchCase);", null)
                                            },
                                            enabled = searchQuery.isNotBlank(),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                disabledContainerColor = Color(0xFF3A3D3E)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Replace", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Replace All
                                        FilledTonalButton(
                                            onClick = {
                                                val safeQ = JSONObject.quote(searchQuery)
                                                val safeR = JSONObject.quote(replaceQuery)
                                                webViewRef?.evaluateJavascript("window.performReplaceAll($safeQ, $safeR, $matchCase);", null)
                                            },
                                            enabled = searchQuery.isNotBlank(),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Monaco WebView Editor Container
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                isFocusable = true
                                isFocusableInTouchMode = true
                                requestFocus(android.view.View.FOCUS_DOWN)
                                setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    allowFileAccessFromFileURLs = true
                                    allowUniversalAccessFromFileURLs = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                    }
                                }
                                setOnTouchListener { v, event ->
                                    if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_UP) {
                                        if (!v.hasFocus()) {
                                            v.requestFocus(android.view.View.FOCUS_DOWN)
                                        }
                                    }
                                    false
                                }
                                addJavascriptInterface(
                                    MonacoBridge(
                                        onSaveRequested = { savedText -> onSave?.invoke(savedText) },
                                        onSearchResultsReported = { cur, tot ->
                                            searchCurrentIndex = cur
                                            searchTotalCount = tot
                                        }
                                    ),
                                    "MonacoBridge"
                                )
                                loadDataWithBaseURL(
                                    "file:///android_asset/",
                                    monacoHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                                webViewRef = this
                            }
                        },
                        update = { wv ->
                            webViewRef = wv
                        }
                    )
                }
            }
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                shape = RoundedCornerShape(16.dp),
                title = { Text("Rename Userscript", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter new script name:", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Script Name") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = renameInputText.trim()
                            if (trimmed.isNotBlank()) {
                                currentTitle = trimmed
                                val safeTitleJson = JSONObject.quote(trimmed)
                                webViewRef?.evaluateJavascript(
                                    """
                                    (function() {
                                        var newName = $safeTitleJson;
                                        var code = window.getEditorValue();
                                        if (code.indexOf('// @name') !== -1) {
                                            code = code.replace(/\/\/\s*@name\s+.*/, '// @name         ' + newName);
                                        } else {
                                            code = '// @name         ' + newName + '\n' + code;
                                        }
                                        if (window.editor && monacoLoaded) {
                                            window.editor.setValue(code);
                                        } else {
                                            var ta = document.getElementById('fallback-editor');
                                            if (ta) ta.value = code;
                                        }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                            showRenameDialog = false
                        },
                        enabled = renameInputText.isNotBlank()
                    ) {
                        Text("Update Name")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun buildMonacoHtml(code: String, language: String): String {
    val safeCode = JSONObject.quote(code)
    val safeLang = JSONObject.quote(language.lowercase())

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background-color: #1e1e1e;
                    color: #d4d4d4;
                    font-family: Consolas, 'Courier New', monospace;
                }
                #editor-container {
                    width: 100%;
                    height: 100%;
                }
                #editor-container textarea.inputarea {
                    pointer-events: auto !important;
                }
                #fallback-container {
                    display: none;
                    width: 100%;
                    height: 100%;
                    box-sizing: border-box;
                    padding: 8px;
                }
                #fallback-editor {
                    width: 100%;
                    height: 100%;
                    box-sizing: border-box;
                    padding: 10px;
                    background-color: #1e1e1e;
                    color: #d4d4d4;
                    font-family: Consolas, 'Courier New', monospace;
                    font-size: 13px;
                    border: 1px solid #333;
                    border-radius: 4px;
                    resize: none;
                    outline: none;
                    white-space: pre;
                    word-wrap: normal;
                }
                #loading-banner {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    font-family: sans-serif;
                    font-size: 14px;
                    color: #9cdcfe;
                    z-index: 10;
                }
            </style>
            <script src="file:///android_asset/monaco/vs/loader.js"></script>
        </head>
        <body>
            <div id="loading-banner">Loading Monaco Editor...</div>
            <div id="editor-container"></div>
            <div id="fallback-container">
                <textarea id="fallback-editor" spellcheck="false"></textarea>
            </div>

            <script>
                var monacoLoaded = false;
                var initialCode = $safeCode;
                var docLanguage = $safeLang;

                window.MonacoEnvironment = {
                    getWorkerUrl: function(workerId, label) {
                        return 'data:text/javascript;charset=utf-8,' + encodeURIComponent(
                            "self.MonacoEnvironment = { baseUrl: 'file:///android_asset/monaco/vs/' };" +
                            "importScripts('file:///android_asset/monaco/vs/base/worker/workerMain.js');"
                        );
                    }
                };

                function showFallback() {
                    if (monacoLoaded) return;
                    var banner = document.getElementById('loading-banner');
                    if (banner) banner.style.display = 'none';
                    document.getElementById('editor-container').style.display = 'none';
                    var fb = document.getElementById('fallback-container');
                    fb.style.display = 'block';
                    var ta = document.getElementById('fallback-editor');
                    ta.value = initialCode;
                }

                function initMonaco() {
                    try {
                        if (typeof require === 'undefined') {
                            showFallback();
                            return;
                        }
                        require.config({
                            paths: { 'vs': 'file:///android_asset/monaco/vs' }
                        });

                        var fallbackTimer = setTimeout(function() {
                            if (!monacoLoaded) showFallback();
                        }, 4000);

                        require(['vs/editor/editor.main'], function() {
                            clearTimeout(fallbackTimer);
                            monacoLoaded = true;
                            var banner = document.getElementById('loading-banner');
                            if (banner) banner.style.display = 'none';

                            window.editor = monaco.editor.create(document.getElementById('editor-container'), {
                                value: initialCode,
                                language: docLanguage,
                                theme: 'vs-dark',
                                automaticLayout: true,
                                fontSize: 14,
                                lineNumbers: 'on',
                                minimap: { enabled: false },
                                scrollBeyondLastLine: false,
                                wordWrap: 'on',
                                folding: true,
                                contextmenu: false,
                                accessibilitySupport: 'on',
                                readOnly: false,
                                domReadOnly: false,
                                quickSuggestions: { other: true, comments: false, strings: true },
                                acceptSuggestionOnEnter: "on",
                                tabCompletion: "on",
                                formatOnPaste: true,
                                find: {
                                    addExtraSpaceOnTop: false,
                                    autoFindInSelection: 'never',
                                    seedSearchStringFromSelection: 'never'
                                }
                            });

                            function focusEditor() {
                                if (window.editor && monacoLoaded) {
                                    window.editor.focus();
                                    var inputArea = document.querySelector('#editor-container textarea.inputarea');
                                    if (inputArea) {
                                        inputArea.focus();
                                    }
                                } else {
                                    var ta = document.getElementById('fallback-editor');
                                    if (ta) ta.focus();
                                }
                            }

                            document.getElementById('editor-container').addEventListener('click', focusEditor);
                            document.getElementById('editor-container').addEventListener('touchend', function() {
                                focusEditor();
                            }, { passive: true });

                            if (docLanguage === 'html' || docLanguage === 'javascript') {
                                setTimeout(function() {
                                    try {
                                        window.editor.getAction('editor.action.formatDocument').run();
                                    } catch(e) {}
                                }, 300);
                            }
                        }, function(err) {
                            showFallback();
                        });
                    } catch(e) {
                        showFallback();
                    }
                }

                window.getEditorValue = function() {
                    if (window.editor && monacoLoaded) {
                        return window.editor.getValue();
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        return ta ? ta.value : initialCode;
                    }
                };

                window.selectAll = function() {
                    if (window.editor && monacoLoaded) {
                        var fullRange = window.editor.getModel().getFullModelRange();
                        window.editor.setSelection(fullRange);
                        window.editor.focus();
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (ta) { ta.focus(); ta.select(); }
                    }
                };

                window.getSelectedText = function() {
                    if (window.editor && monacoLoaded) {
                        var sel = window.editor.getSelection();
                        if (sel && !sel.isEmpty()) {
                            return window.editor.getModel().getValueInRange(sel);
                        }
                        return window.editor.getValue();
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (ta) {
                            var start = ta.selectionStart;
                            var end = ta.selectionEnd;
                            if (start !== end) {
                                return ta.value.substring(start, end);
                            }
                            return ta.value;
                        }
                        return initialCode;
                    }
                };

                window.cutSelection = function() {
                    var cutText = "";
                    if (window.editor && monacoLoaded) {
                        var sel = window.editor.getSelection();
                        if (sel && !sel.isEmpty()) {
                            cutText = window.editor.getModel().getValueInRange(sel);
                            window.editor.executeEdits('cut', [{
                                range: sel,
                                text: '',
                                forceMoveMarkers: true
                            }]);
                        } else {
                            cutText = window.editor.getValue();
                            var fullRange = window.editor.getModel().getFullModelRange();
                            window.editor.executeEdits('cut', [{
                                range: fullRange,
                                text: '',
                                forceMoveMarkers: true
                            }]);
                        }
                        window.editor.focus();
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (ta) {
                            var start = ta.selectionStart;
                            var end = ta.selectionEnd;
                            if (start !== end) {
                                cutText = ta.value.substring(start, end);
                                ta.value = ta.value.substring(0, start) + ta.value.substring(end);
                                ta.selectionStart = start;
                                ta.selectionEnd = start;
                            } else {
                                cutText = ta.value;
                                ta.value = "";
                            }
                        }
                    }
                    return cutText;
                };

                window.pasteText = function(text) {
                    if (window.editor && monacoLoaded) {
                        var sel = window.editor.getSelection();
                        if (!sel) {
                            var pos = window.editor.getPosition() || new monaco.Position(1, 1);
                            sel = new monaco.Selection(pos.lineNumber, pos.column, pos.lineNumber, pos.column);
                        }
                        window.editor.executeEdits('paste', [{
                            range: sel,
                            text: text,
                            forceMoveMarkers: true
                        }]);
                        window.editor.focus();
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (ta) {
                            var start = ta.selectionStart || 0;
                            var end = ta.selectionEnd || 0;
                            ta.value = ta.value.substring(0, start) + text + ta.value.substring(end);
                            ta.selectionStart = start + text.length;
                            ta.selectionEnd = start + text.length;
                            ta.focus();
                        }
                    }
                };

                window.performFind = function(query, forward, matchCase) {
                    if (!query) {
                        if (window.MonacoBridge && window.MonacoBridge.reportSearchResults) {
                            window.MonacoBridge.reportSearchResults(0, 0);
                        }
                        return;
                    }
                    if (window.editor && monacoLoaded) {
                        var model = window.editor.getModel();
                        if (!model) return;
                        var matches = model.findMatches(query, false, false, matchCase, null, true);
                        var total = matches ? matches.length : 0;
                        if (total === 0) {
                            if (window.MonacoBridge && window.MonacoBridge.reportSearchResults) {
                                window.MonacoBridge.reportSearchResults(0, 0);
                            }
                            return;
                        }

                        var currentPos = window.editor.getPosition() || new monaco.Position(1, 1);
                        var targetIndex = -1;

                        if (forward) {
                            for (var i = 0; i < matches.length; i++) {
                                var range = matches[i].range;
                                if (range.startLineNumber > currentPos.lineNumber ||
                                   (range.startLineNumber === currentPos.lineNumber && range.startColumn > currentPos.column)) {
                                    targetIndex = i;
                                    break;
                                }
                            }
                            if (targetIndex === -1) targetIndex = 0;
                        } else {
                            for (var j = matches.length - 1; j >= 0; j--) {
                                var r = matches[j].range;
                                if (r.endLineNumber < currentPos.lineNumber ||
                                   (r.endLineNumber === currentPos.lineNumber && r.endColumn < currentPos.column)) {
                                    targetIndex = j;
                                    break;
                                }
                            }
                            if (targetIndex === -1) targetIndex = matches.length - 1;
                        }

                        var targetMatch = matches[targetIndex];
                        window.editor.setSelection(targetMatch.range);
                        window.editor.revealRangeInCenter(targetMatch.range);
                        if (window.MonacoBridge && window.MonacoBridge.reportSearchResults) {
                            window.MonacoBridge.reportSearchResults(targetIndex + 1, total);
                        }
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (!ta) return;
                        var content = ta.value;
                        var searchStr = matchCase ? query : query.toLowerCase();
                        var src = matchCase ? content : content.toLowerCase();
                        var idx = forward ? src.indexOf(searchStr, ta.selectionEnd) : src.lastIndexOf(searchStr, Math.max(0, ta.selectionStart - 1));
                        if (idx === -1) {
                            idx = forward ? src.indexOf(searchStr, 0) : src.lastIndexOf(searchStr);
                        }
                        if (idx !== -1) {
                            ta.focus();
                            ta.setSelectionRange(idx, idx + query.length);
                        }
                    }
                };

                window.performReplace = function(replaceText) {
                    if (window.editor && monacoLoaded) {
                        var sel = window.editor.getSelection();
                        if (sel && !sel.isEmpty()) {
                            window.editor.executeEdits('replace', [{
                                range: sel,
                                text: replaceText || '',
                                forceMoveMarkers: true
                            }]);
                        }
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (ta) {
                            var start = ta.selectionStart;
                            var end = ta.selectionEnd;
                            if (start !== end) {
                                ta.value = ta.value.substring(0, start) + (replaceText || '') + ta.value.substring(end);
                                ta.selectionStart = start;
                                ta.selectionEnd = start + (replaceText || '').length;
                            }
                        }
                    }
                };

                window.performReplaceAll = function(query, replaceText, matchCase) {
                    if (!query) return;
                    if (window.editor && monacoLoaded) {
                        var model = window.editor.getModel();
                        if (!model) return;
                        var matches = model.findMatches(query, false, false, matchCase, null, true);
                        if (!matches || matches.length === 0) return;
                        var edits = matches.map(function(m) {
                            return {
                                range: m.range,
                                text: replaceText || '',
                                forceMoveMarkers: true
                            };
                        });
                        window.editor.executeEdits('replaceAll', edits);
                        if (window.MonacoBridge && window.MonacoBridge.reportSearchResults) {
                            window.MonacoBridge.reportSearchResults(0, 0);
                        }
                    } else {
                        var ta = document.getElementById('fallback-editor');
                        if (ta) {
                            var flags = matchCase ? 'g' : 'gi';
                            var regex = new RegExp(query.replace(/[.*+?^${'$'}{}()|[\\]\\]/g, '\\${'$'}&'), flags);
                            ta.value = ta.value.replace(regex, replaceText || '');
                        }
                    }
                };

                window.triggerSearch = function() {
                    // Handled natively by Compose toolbar
                };

                window.triggerReplace = function() {
                    // Handled natively by Compose toolbar
                };

                window.triggerFormat = function() {
                    if (window.editor && monacoLoaded) {
                        window.editor.getAction('editor.action.formatDocument').run();
                    }
                };

                window.toggleWordWrap = function() {
                    if (window.editor && monacoLoaded) {
                        var current = window.editor.getRawOptions().wordWrap;
                        var next = current === 'on' ? 'off' : 'on';
                        window.editor.updateOptions({ wordWrap: next });
                    }
                };

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initMonaco);
                } else {
                    initMonaco();
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
