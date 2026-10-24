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
        MonacoHtmlBuilder.buildMonacoHtml(preprocessedContent, language)
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
                        MonacoEditorToolbar(
                            context = context,
                            webViewRef = webViewRef,
                            isSearchOpen = isSearchOpen,
                            isReplaceOpen = isReplaceOpen,
                            isWordWrapEnabled = isWordWrapEnabled,
                            onToggleSearch = {
                                isSearchOpen = !isSearchOpen
                                if (!isSearchOpen) {
                                    isReplaceOpen = false
                                    searchQuery = ""
                                    searchCurrentIndex = 0
                                    searchTotalCount = 0
                                    webViewRef?.evaluateJavascript("window.performFind('', true, false);", null)
                                }
                            },
                            onToggleReplace = {
                                if (!isSearchOpen) {
                                    isSearchOpen = true
                                }
                                isReplaceOpen = !isReplaceOpen
                            },
                            onToggleWordWrap = {
                                isWordWrapEnabled = !isWordWrapEnabled
                                webViewRef?.evaluateJavascript("window.toggleWordWrap();", null)
                            }
                        )

                        // Tier 3: Native Compose Search & Replace Bar (Zero IME Input Lag)
                        if (isSearchOpen) {
                            HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                            MonacoSearchReplacePanel(
                                webViewRef = webViewRef,
                                isReplaceOpen = isReplaceOpen,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                replaceQuery = replaceQuery,
                                onReplaceQueryChange = { replaceQuery = it },
                                matchCase = matchCase,
                                onMatchCaseChange = { matchCase = it },
                                searchCurrentIndex = searchCurrentIndex,
                                searchTotalCount = searchTotalCount,
                                onClose = {
                                    isSearchOpen = false
                                    isReplaceOpen = false
                                    searchQuery = ""
                                    searchCurrentIndex = 0
                                    searchTotalCount = 0
                                    webViewRef?.evaluateJavascript("window.performFind('', true, false);", null)
                                }
                            )
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
