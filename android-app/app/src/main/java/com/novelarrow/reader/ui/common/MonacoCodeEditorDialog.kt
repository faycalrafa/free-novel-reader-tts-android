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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
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
    private val onSaveRequested: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun saveContent(content: String) {
        handler.post {
            onSaveRequested.invoke(content)
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
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                    webViewRef?.evaluateJavascript("window.triggerSearch();", null)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFFCCCCCC),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.triggerReplace();", null)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.FindReplace,
                                    contentDescription = "Match Replace",
                                    tint = Color(0xFFCCCCCC),
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
                                addJavascriptInterface(
                                    MonacoBridge { savedText ->
                                        onSave?.invoke(savedText)
                                    },
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
                                fontSize: 13,
                                lineNumbers: 'on',
                                minimap: { enabled: false },
                                scrollBeyondLastLine: false,
                                wordWrap: 'on',
                                folding: true,
                                contextmenu: false,
                                quickSuggestions: { other: true, comments: false, strings: true },
                                acceptSuggestionOnEnter: "on",
                                tabCompletion: "on",
                                formatOnPaste: true,
                                find: {
                                    addExtraSpaceOnTop: false,
                                    autoFindInSelection: 'never',
                                    seedSearchStringFromSelection: 'always'
                                }
                            });

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

                window.triggerSearch = function() {
                    if (window.editor && monacoLoaded) {
                        window.editor.getAction('actions.find').run();
                    }
                };

                window.triggerReplace = function() {
                    if (window.editor && monacoLoaded) {
                        window.editor.getAction('editor.action.startFindReplaceAction').run();
                    }
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
