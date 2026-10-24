package com.novelarrow.reader.ui.common

import org.json.JSONObject

internal object MonacoHtmlBuilder {

    fun buildMonacoHtml(code: String, language: String): String {
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
}
