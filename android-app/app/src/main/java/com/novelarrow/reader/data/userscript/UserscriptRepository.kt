package com.novelarrow.reader.data.userscript

import android.content.Context
import android.os.Environment
import android.util.Log
import com.novelarrow.reader.browser.engine.WebScriptInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class UserscriptDatabaseState(
    val scripts: List<UserscriptEntity> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class UserscriptRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dbFile: File
        get() = File(context.filesDir, "userscripts_db_v1.json")

    companion object {
        private const val TAG = "UserscriptRepo"
        private const val FOLDER_NAME = "BookReader_Scripts"
        private const val LEGACY_FOLDER_NAME = "NovelArrow_Scripts"
        private val repoMutex = Mutex()
        private var sharedFlowInternal: MutableStateFlow<List<UserscriptEntity>>? = null

        fun sanitizeFileName(name: String): String {
            val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            return clean.ifBlank { "script_" + System.currentTimeMillis() }
        }

        val DEFAULT_DEMO_SCRIPT = """
            // ==UserScript==
            // @name         Clean Novel Reader (Ad & Clutter Remover)
            // @version      1.0.0
            // @description  Removes floating ads, sticky banners, and declutters web novel pages for clean reading and TTS.
            // @match        *://*/*
            // @run-at       document-end
            // @grant        GM_notification
            // ==/UserScript==

            (function() {
                'use strict';
                const adSelectors = [
                    '.ads', '.ad-container', '.ad_box', '#sidebar', 'aside',
                    '.header-advert', '.footer-advert', 'iframe', '.floating-ad',
                    '.social-share', '.share-buttons', '.sticky-banner',
                    '.ad-wrapper', '#floating-banner', '.advertisement',
                    '[id*="ad_"]', '[class*="advert"]'
                ];
                let removed = 0;
                adSelectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        if (el.id === 'chapter-content' || el.classList.contains('chapter-content') || el.tagName === 'BODY') {
                            return;
                        }
                        el.remove();
                        removed++;
                    });
                });
                if (removed > 0 && typeof GM_notification === 'function') {
                    GM_notification('Novel page cleaned (' + removed + ' clutter items removed)');
                }
            })();
        """.trimIndent()

        val DEFAULT_MULTI_DOWNLOAD_SCRIPT = """
            // ==UserScript==
            // @name         Multi-Download
            // @version      2.0.0
            // @description  Batch download loop for web novel chapters with in-page configuration dialog, filtered clean text extraction, and auto-navigation.
            // @match        *://*/*
            // @match        file://*/*
            // @run-at       document-end
            // @grant        GM_registerToolbarButton
            // @grant        GM_notification
            // @grant        GM_setValue
            // @grant        GM_getValue
            // ==/UserScript==

            (function() {
                'use strict';

                var ACTIVE_KEY = 'novel_batch_dl_active';
                var NOVEL_KEY = 'novel_batch_dl_novel';
                var CHAPTER_KEY = 'novel_batch_dl_chapter';
                var REMAINING_KEY = 'novel_batch_dl_remaining';
                var DELAY_KEY = 'novel_batch_dl_delay';
                var LAST_URL_KEY = 'novel_batch_dl_last_url';

                var DEFAULT_IGNORE = [
                    'nav', 'footer', 'header', 'aside', 'button', 'a',
                    '[role="navigation"]', '.ads', '.ad', '.advertisement',
                    'script', 'style', 'noscript', 'iframe',
                    'form', 'input', 'textarea', 'select',
                    '#custom-floating-bar', '#na-root', '#na-floating-next',
                    '#na-player-bar', '#na-modal-backdrop-el',
                    '.na-tts-highlight', '#nd-modal-overlay', '#nd-countdown-pill'
                ];

                function isIgnored(el) {
                    if (!el) return true;
                    try {
                        for (var i = 0; i < DEFAULT_IGNORE.length; i++) {
                            var sel = DEFAULT_IGNORE[i];
                            if (el.matches && (el.matches(sel) || el.closest(sel))) return true;
                        }
                    } catch(e) {}
                    return false;
                }

                function detectNovel() {
                    var title = '';
                    var chNum = 1;
                    var pathname = window.location.pathname || '';
                    var pageTitle = document.title || '';

                    var chMatch = pathname.match(/(?:chapter|chap|ch|c)[-_]?(\d+)/i) || pathname.match(/(\d+)(?:\.html?)?${'$'}/i);
                    if (chMatch) {
                        chNum = parseInt(chMatch[1], 10) || 1;
                    }

                    var pathSegments = pathname.split('/').filter(function(s) {
                        return s && !/^(?:chapter|chap|ch|c|\d+)/i.test(s) && ['novel', 'novels', 'read', 'book', 'books', 'chapter', 'chapters', 'html', 'index'].indexOf(s.toLowerCase()) === -1;
                    });
                    if (pathSegments.length > 0) {
                        var rawSlug = pathSegments[pathSegments.length - 1];
                        title = rawSlug.replace(/\.html?${'$'}/i, '').replace(/[-_]+/g, ' ').replace(/\b\w/g, function(c) { return c.toUpperCase(); }).trim();
                    }

                    if (!title && pageTitle) {
                        var titleMatch = pageTitle.match(/^(.*?)\s*[-–—|:]\s*Chapter\s+(\d+)/i) || pageTitle.match(/Chapter\s+(\d+).*?[-–—|]\s*(.*?)${'$'}/i);
                        if (titleMatch) {
                            title = (titleMatch[1] && !/^\d+${'$'}/.test(titleMatch[1])) ? titleMatch[1].trim() : (titleMatch[2] ? titleMatch[2].trim() : '');
                            if (chNum === 1 && titleMatch[2] && /^\d+${'$'}/.test(titleMatch[2])) {
                                chNum = parseInt(titleMatch[2], 10) || 1;
                            }
                        }
                    }
                    if (!title) {
                        title = pageTitle.replace(/[-–—|].*${'$'}/, '').replace(/Chapter.*${'$'}/i, '').trim() || 'Web Novel';
                    }
                    return {
                        title: title.replace(/[/\\?%*:|"<>]/g, '_').trim(),
                        chapter: chNum
                    };
                }

                function extractFilteredText() {
                    var candidates = ['#chapter-content', '.chapter-content', '#readcontent', '.read-content', '.entry-content', '.text-content', 'article', '.content'];
                    var container = null;
                    for (var i = 0; i < candidates.length; i++) {
                        var el = document.querySelector(candidates[i]);
                        if (el && el.querySelectorAll('p, div').length >= 2) {
                            container = el;
                            break;
                        }
                    }
                    if (!container) container = document.body;

                    var allNodes = Array.from(container.querySelectorAll('p, div, h1, h2, h3, h4, h5, h6, blockquote'));
                    var paras = [];
                    for (var j = 0; j < allNodes.length; j++) {
                        var node = allNodes[j];
                        if (isIgnored(node)) continue;
                        var tag = node.tagName.toLowerCase();
                        if (['div', 'blockquote'].indexOf(tag) !== -1 && node.querySelector('p, div, blockquote')) {
                            continue;
                        }
                        var cs = window.getComputedStyle(node);
                        if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) continue;
                        var rect = node.getBoundingClientRect();
                        if (rect.width === 0 && rect.height === 0 && node.offsetHeight === 0) continue;

                        var text = (node.textContent || '').replace(/\s+/g, ' ').trim();
                        if (text.length > 0 && paras.indexOf(text) === -1) {
                            paras.push(text);
                        }
                    }
                    return paras;
                }

                function buildCleanChapterHtml(novelTitle, chapterTitle, chNum, paragraphs) {
                    function esc(s) {
                        return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
                    }
                    var parasHtml = paragraphs.map(function(p) {
                        return '    <p>' + esc(p) + '</p>';
                    }).join('\n');

                    return '<!DOCTYPE html>\n<html lang="en">\n<head>\n' +
                        '  <meta charset="utf-8">\n' +
                        '  <meta name="viewport" content="width=device-width, initial-scale=1.0">\n' +
                        '  <title>' + esc(novelTitle) + ' - Chapter ' + chNum + '</title>\n' +
                        '  <style>\n' +
                        '    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; line-height: 1.7; padding: 20px; max-width: 800px; margin: 0 auto; color: #1e293b; background: #ffffff; }\n' +
                        '    h1 { font-size: 1.5rem; margin-bottom: 1.2rem; color: #0f172a; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }\n' +
                        '    p { margin-bottom: 1.2rem; text-indent: 1.5rem; font-size: 1.05rem; }\n' +
                        '    @media (prefers-color-scheme: dark) {\n' +
                        '      body { background: #0f172a; color: #cbd5e1; }\n' +
                        '      h1 { color: #f8fafc; border-bottom-color: #334155; }\n' +
                        '    }\n' +
                        '  </style>\n' +
                        '</head>\n<body>\n' +
                        '  <h1>' + esc(chapterTitle) + '</h1>\n' +
                        '  <div id="chapter-content">\n' + parasHtml + '\n  </div>\n' +
                        '</body>\n</html>';
                }

                function saveChapter(novelTitle, chapterTitle, chNum, cleanHtml) {
                    if (window.BrowserBridge && typeof window.BrowserBridge.saveDownloadedChapter === 'function') {
                        window.BrowserBridge.saveDownloadedChapter(novelTitle, chapterTitle, chNum, cleanHtml);
                        return;
                    }
                    if (window.AndroidBridge && typeof window.AndroidBridge.saveDownloadedChapter === 'function') {
                        window.AndroidBridge.saveDownloadedChapter(novelTitle, chapterTitle, chNum, cleanHtml);
                        return;
                    }
                    try {
                        var blob = new Blob([cleanHtml], { type: 'text/html;charset=utf-8' });
                        var url = URL.createObjectURL(blob);
                        var a = document.createElement('a');
                        a.href = url;
                        a.download = novelTitle + ' Chapter ' + ('000' + chNum).slice(-4) + '.html';
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        URL.revokeObjectURL(url);
                    } catch(e) {
                        console.error('Download fallback error:', e);
                    }
                }

                function getNextChapterElement() {
                    var selectors = [
                        'button[title*="Next Chapter" i]',
                        'a[title*="Next Chapter" i]',
                        'button[title="Next chapter"]',
                        'a[title="Next chapter"]',
                        '#next_chap',
                        '.next-chap',
                        '.next-chapter',
                        '.btn-next',
                        'a.next',
                        'a[rel="next"]',
                        '.nav-next a',
                        '.next a'
                    ];
                    for (var i = 0; i < selectors.length; i++) {
                        try {
                            var el = document.querySelector(selectors[i]);
                            if (el && el.offsetParent !== null) return el;
                        } catch(e) {}
                    }
                    var clickables = Array.from(document.querySelectorAll('a, button'));
                    for (var j = 0; j < clickables.length; j++) {
                        var item = clickables[j];
                        var txt = (item.textContent || '').trim().toLowerCase();
                        if (/^(?:next\s*(?:chapter|page)?|next\s*›|›|»)$/i.test(txt) || (txt.indexOf('next chapter') !== -1 && txt.length < 25)) {
                            if (item.offsetParent !== null) return item;
                        }
                    }
                    return null;
                }

                function showCountdownPill(novelTitle, chNum, remaining, secondsLeft) {
                    var pill = document.getElementById('nd-countdown-pill');
                    if (!pill) {
                        pill = document.createElement('div');
                        pill.id = 'nd-countdown-pill';
                        pill.style.cssText = 'position:fixed;bottom:24px;right:24px;z-index:2147483640;background:#1e293b;color:#f8fafc;padding:10px 18px;border-radius:28px;box-shadow:0 8px 24px rgba(0,0,0,0.4);font-family:system-ui,sans-serif;font-size:13px;font-weight:600;display:flex;align-items:center;gap:12px;border:1px solid #334155;';
                        document.body.appendChild(pill);
                    }
                    pill.innerHTML = '<span>📥 <b>' + novelTitle + '</b> Ch. ' + chNum + ' (Left: ' + remaining + ') · Next in <b id="nd-timer-val">' + secondsLeft.toFixed(1) + 's</b></span>' +
                        '<button id="nd-stop-btn" style="background:#ef4444;color:#fff;border:none;padding:5px 12px;border-radius:14px;font-size:12px;font-weight:bold;cursor:pointer;">Stop</button>';
                    var stopBtn = document.getElementById('nd-stop-btn');
                    if (stopBtn) {
                        stopBtn.onclick = function() {
                            stopBatchLoop();
                        };
                    }
                }

                function removeCountdownPill() {
                    var pill = document.getElementById('nd-countdown-pill');
                    if (pill) pill.remove();
                }

                var countdownInterval = null;

                function stopBatchLoop() {
                    sessionStorage.setItem(ACTIVE_KEY, 'false');
                    if (countdownInterval) clearInterval(countdownInterval);
                    countdownInterval = null;
                    removeCountdownPill();
                    if (typeof GM_notification === 'function') {
                        GM_notification('Batch download stopped');
                    }
                }

                function runBatchStep() {
                    if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') return;

                    var novelTitle = sessionStorage.getItem(NOVEL_KEY) || 'Web Novel';
                    var currentCh = parseInt(sessionStorage.getItem(CHAPTER_KEY), 10) || 1;
                    var remaining = parseInt(sessionStorage.getItem(REMAINING_KEY), 10) || 1;
                    var delayMs = Math.max(300, parseInt(sessionStorage.getItem(DELAY_KEY), 10) || 1000);

                    var paras = extractFilteredText();
                    var chapterTitle = novelTitle + ' Chapter ' + currentCh;
                    var cleanHtml = buildCleanChapterHtml(novelTitle, chapterTitle, currentCh, paras);

                    saveChapter(novelTitle, chapterTitle, currentCh, cleanHtml);

                    remaining--;
                    currentCh++;
                    sessionStorage.setItem(REMAINING_KEY, remaining.toString());
                    sessionStorage.setItem(CHAPTER_KEY, currentCh.toString());
                    sessionStorage.setItem(LAST_URL_KEY, window.location.href);

                    if (remaining <= 0) {
                        stopBatchLoop();
                        if (typeof GM_notification === 'function') {
                            GM_notification('Batch download complete for ' + novelTitle + '!');
                        }
                        return;
                    }

                    var timeLeftMs = delayMs;
                    var tickMs = 100;
                    showCountdownPill(novelTitle, currentCh, remaining, timeLeftMs / 1000);

                    countdownInterval = setInterval(function() {
                        if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') {
                            clearInterval(countdownInterval);
                            return;
                        }
                        timeLeftMs -= tickMs;
                        var timerVal = document.getElementById('nd-timer-val');
                        if (timerVal) timerVal.textContent = Math.max(0, timeLeftMs / 1000).toFixed(1) + 's';

                        if (timeLeftMs <= 0) {
                            clearInterval(countdownInterval);
                            var nextEl = getNextChapterElement();
                            if (nextEl) {
                                var oldUrl = window.location.href;
                                try { nextEl.click(); } catch(e) {}
                                setTimeout(function() {
                                    if (window.location.href === oldUrl && nextEl.href) {
                                        window.location.href = nextEl.href;
                                    }
                                }, 1000);
                            } else {
                                stopBatchLoop();
                                alert('Next chapter button not found. Stopped batch download.');
                            }
                        }
                    }, tickMs);
                }

                function showDialog() {
                    if (sessionStorage.getItem(ACTIVE_KEY) === 'true') {
                        if (confirm('Batch download is currently running. Do you want to stop it?')) {
                            stopBatchLoop();
                        }
                        return;
                    }

                    var existing = document.getElementById('nd-modal-overlay');
                    if (existing) existing.remove();

                    var detected = detectNovel();
                    var overlay = document.createElement('div');
                    overlay.id = 'nd-modal-overlay';
                    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.65);display:flex;align-items:center;justify-content:center;z-index:2147483647;backdrop-filter:blur(3px);font-family:system-ui,sans-serif;box-sizing:border-box;';

                    overlay.innerHTML = 
                        '<div style="background:#1e293b;color:#f8fafc;width:92%;max-width:420px;border-radius:18px;padding:24px;box-shadow:0 16px 40px rgba(0,0,0,0.45);border:1px solid #334155;box-sizing:border-box;">' +
                        '  <h2 style="margin:0 0 6px 0;font-size:1.25rem;color:#f8fafc;display:flex;align-items:center;gap:8px;">📥 Batch Download Novel</h2>' +
                        '  <p style="margin:0 0 16px 0;font-size:0.82rem;color:#94a3b8;line-height:1.4;">Extracts clean filtered text into readable HTML and auto-navigates.</p>' +
                        '  <div style="margin-bottom:12px;">' +
                        '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Novel Name</label>' +
                        '    <input id="nd-input-title" type="text" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="' + detected.title.replace(/"/g, '&quot;') + '" />' +
                        '  </div>' +
                        '  <div style="margin-bottom:12px;">' +
                        '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Starting Chapter #</label>' +
                        '    <input id="nd-input-ch" type="number" min="1" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="' + detected.chapter + '" />' +
                        '  </div>' +
                        '  <div style="margin-bottom:12px;">' +
                        '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Chapters Quantity</label>' +
                        '    <input id="nd-input-qty" type="number" min="1" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="20" />' +
                        '  </div>' +
                        '  <div style="margin-bottom:16px;">' +
                        '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Delay Between Chapters (seconds)</label>' +
                        '    <input id="nd-input-delay" type="number" step="0.5" min="0.5" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="1.0" />' +
                        '  </div>' +
                        '  <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:20px;">' +
                        '    <button id="nd-btn-cancel" style="background:transparent;color:#94a3b8;border:none;padding:10px 16px;border-radius:10px;font-size:0.9rem;font-weight:600;cursor:pointer;">Cancel</button>' +
                        '    <button id="nd-btn-start" style="background:#2563eb;color:#fff;border:none;padding:10px 18px;border-radius:10px;font-size:0.9rem;font-weight:600;cursor:pointer;">Start Batch Download</button>' +
                        '  </div>' +
                        '</div>';

                    document.body.appendChild(overlay);

                    document.getElementById('nd-btn-cancel').onclick = function() {
                        overlay.remove();
                    };

                    document.getElementById('nd-btn-start').onclick = function() {
                        var titleInput = document.getElementById('nd-input-title').value.trim() || 'Web Novel';
                        var chInput = parseInt(document.getElementById('nd-input-ch').value, 10) || 1;
                        var qtyInput = parseInt(document.getElementById('nd-input-qty').value, 10) || 20;
                        var delayInput = parseFloat(document.getElementById('nd-input-delay').value) || 1.0;

                        overlay.remove();

                        sessionStorage.setItem(ACTIVE_KEY, 'true');
                        sessionStorage.setItem(NOVEL_KEY, titleInput);
                        sessionStorage.setItem(CHAPTER_KEY, chInput.toString());
                        sessionStorage.setItem(REMAINING_KEY, qtyInput.toString());
                        sessionStorage.setItem(DELAY_KEY, Math.round(delayInput * 1000).toString());
                        sessionStorage.setItem(LAST_URL_KEY, window.location.href);

                        runBatchStep();
                    };
                }

                if (typeof GM_registerToolbarButton === 'function') {
                    GM_registerToolbarButton({
                        id: 'multi_download',
                        title: 'Batch Download Loop',
                        icon: 'CloudDownload',
                        onClick: function() {
                            showDialog();
                        }
                    });
                }

                // Auto-resume across page navigations if loop active
                if (sessionStorage.getItem(ACTIVE_KEY) === 'true') {
                    setTimeout(function() {
                        runBatchStep();
                    }, 800);
                }
            })();
        """.trimIndent()
    }

    private val _scriptsFlow: MutableStateFlow<List<UserscriptEntity>>
    val scriptsFlow: StateFlow<List<UserscriptEntity>>

    init {
        val initialScripts = loadDatabase()
        synchronized(UserscriptRepository::class.java) {
            if (sharedFlowInternal == null) {
                sharedFlowInternal = MutableStateFlow(initialScripts)
            }
        }
        _scriptsFlow = sharedFlowInternal!!
        scriptsFlow = _scriptsFlow.asStateFlow()

        // Sync directory in background to detect files placed in Downloads
        try {
            ensureScriptsDirectory()
            syncFromDiskInternal()
            seedDefaultScripts()
        } catch (e: Exception) {
            Log.w(TAG, "Initial sync failed: ${e.message}")
        }
    }

    private fun seedDefaultScripts() {
        seedScriptFile("Clean_Novel_Reader.user.js", DEFAULT_DEMO_SCRIPT, "Clean Novel Reader (Ad & Clutter Remover)")
        seedScriptFile("Multi-Download.user.js", DEFAULT_MULTI_DOWNLOAD_SCRIPT, "Multi-Download")
    }

    private fun seedScriptFile(fileName: String, scriptCode: String, fallbackName: String) {
        try {
            val scriptsDir = ensureScriptsDirectory()
            val targetFile = File(scriptsDir, fileName)
            val parsed = UserscriptMetadataParser.parse(scriptCode.trimIndent(), fallbackName = fallbackName)
            if (!targetFile.exists() || (fileName == "Multi-Download.user.js" && targetFile.readText().contains("@version      1.0.0"))) {
                targetFile.writeText(scriptCode.trimIndent(), Charsets.UTF_8)
            }

            val current = _scriptsFlow.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.name == parsed.name || it.name == fallbackName }
            if (existingIndex == -1) {
                val entity = UserscriptEntity(
                    id = UUID.randomUUID().toString(),
                    name = parsed.name,
                    version = parsed.version,
                    filePath = targetFile.absolutePath,
                    isEnabled = true,
                    matchPatterns = parsed.matchPatterns,
                    runAt = parsed.runAt,
                    description = parsed.description,
                    author = parsed.author,
                    grantList = parsed.grantList,
                    lastModified = targetFile.lastModified()
                )
                current.add(entity)
                saveDatabase(current)
                _scriptsFlow.value = current
                Log.i(TAG, "Successfully seeded default userscript: ${entity.name}")
            } else if (fileName == "Multi-Download.user.js" && current[existingIndex].version != parsed.version) {
                current[existingIndex] = current[existingIndex].copy(
                    version = parsed.version,
                    description = parsed.description,
                    grantList = parsed.grantList,
                    matchPatterns = parsed.matchPatterns,
                    lastModified = targetFile.lastModified()
                )
                saveDatabase(current)
                _scriptsFlow.value = current
                Log.i(TAG, "Successfully upgraded default userscript: ${parsed.name} to v${parsed.version}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to seed default userscript $fileName: ${e.message}")
        }
    }

    fun getScriptsDirectory(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(publicDir, FOLDER_NAME)
        val legacyTarget = File(publicDir, LEGACY_FOLDER_NAME)
        if (target.exists() && target.isDirectory && target.canWrite()) {
            return target
        }
        // If legacy folder exists, migrate files or rename if possible
        if (legacyTarget.exists() && legacyTarget.isDirectory && !target.exists()) {
            try {
                if (legacyTarget.renameTo(target)) {
                    return target
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to rename legacy scripts folder: ${e.message}", e)
            }
        }
        try {
            if (target.mkdirs() || target.exists()) {
                return target
            }
        } catch (_: Exception) {}

        // Fallback to app-specific external files dir
        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val appTarget = File(appExtDir, FOLDER_NAME)
        if (!appTarget.exists()) appTarget.mkdirs()
        return appTarget
    }

    private fun ensureScriptsDirectory(): File {
        val dir = getScriptsDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun getScripts(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value
        }
    }

    suspend fun getEnabledScripts(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value.filter { it.isEnabled }
        }
    }

    suspend fun getScriptsForUrl(url: String): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value.filter { script ->
                script.isEnabled && script.matchPatterns.any { pattern ->
                    WebScriptInjector.matchesPattern(url, pattern)
                }
            }
        }
    }

    suspend fun getScriptById(id: String): UserscriptEntity? = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            _scriptsFlow.value.firstOrNull { it.id == id }
        }
    }

    suspend fun getScriptCode(script: UserscriptEntity): String = withContext(Dispatchers.IO) {
        try {
            val file = File(script.filePath)
            if (file.exists() && file.isFile) {
                return@withContext file.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read script code from ${script.filePath}: ${e.message}")
        }
        ""
    }

    suspend fun saveScript(
        code: String,
        preferredName: String? = null,
        existingId: String? = null
    ): UserscriptEntity = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val parsed = UserscriptMetadataParser.parse(code, fallbackName = preferredName ?: "Untitled Script")
            val scriptName = if (parsed.name.isNotBlank() && parsed.name != "Untitled Script" && parsed.name != "Userscript") {
                parsed.name
            } else {
                preferredName?.ifBlank { null } ?: parsed.name
            }
            val scriptsDir = ensureScriptsDirectory()

            val existing = if (existingId != null) {
                _scriptsFlow.value.firstOrNull { it.id == existingId }
            } else null

            val targetFile = if (existing != null && File(existing.filePath).exists()) {
                val currentFile = File(existing.filePath)
                val safeName = sanitizeFileName(scriptName)
                val expectedFileName = "$safeName.user.js"
                if (!currentFile.name.equals(expectedFileName, ignoreCase = true)) {
                    val renamedFile = File(scriptsDir, expectedFileName)
                    if (!renamedFile.exists()) {
                        currentFile.renameTo(renamedFile)
                        renamedFile
                    } else {
                        currentFile
                    }
                } else {
                    currentFile
                }
            } else {
                val safeName = sanitizeFileName(scriptName)
                var candidate = File(scriptsDir, "$safeName.user.js")
                var counter = 1
                while (candidate.exists() && existing == null) {
                    candidate = File(scriptsDir, "${safeName}_$counter.user.js")
                    counter++
                }
                candidate
            }

            // Write raw JavaScript to the .user.js file in Downloads/[AppName]_Scripts/
            targetFile.writeText(code, Charsets.UTF_8)

            val scriptEntity = UserscriptEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = scriptName,
                version = parsed.version,
                filePath = targetFile.absolutePath,
                isEnabled = existing?.isEnabled ?: true,
                matchPatterns = parsed.matchPatterns,
                runAt = parsed.runAt,
                description = parsed.description,
                author = parsed.author,
                grantList = parsed.grantList,
                lastModified = System.currentTimeMillis()
            )

            val current = _scriptsFlow.value
            val updated = current.filter { it.id != scriptEntity.id } + scriptEntity
            saveDatabase(updated)
            _scriptsFlow.value = updated
            scriptEntity
        }
    }

    suspend fun toggleScriptEnabled(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val current = _scriptsFlow.value
            val target = current.firstOrNull { it.id == id } ?: return@withLock
            val updated = current.map {
                if (it.id == id) it.copy(isEnabled = isEnabled, lastModified = System.currentTimeMillis()) else it
            }
            saveDatabase(updated)
            _scriptsFlow.value = updated
        }
    }

    suspend fun deleteScript(id: String): Boolean = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            val current = _scriptsFlow.value
            val target = current.firstOrNull { it.id == id } ?: return@withLock false

            // 1. Delete physical file from Downloads folder
            try {
                val file = File(target.filePath)
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete physical file ${target.filePath}: ${e.message}")
            }

            // 2. Remove record from database
            val updated = current.filter { it.id != id }
            saveDatabase(updated)
            _scriptsFlow.value = updated
            true
        }
    }

    suspend fun renameScript(id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return@withContext false
        repoMutex.withLock {
            val list = _scriptsFlow.value.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index == -1) return@withContext false
            val oldEntity = list[index]
            try {
                val file = File(oldEntity.filePath)
                var newFilePath = oldEntity.filePath
                val scriptsDir = ensureScriptsDirectory()
                val newFileName = "${sanitizeFileName(trimmed)}.user.js"
                val newFile = File(scriptsDir, newFileName)
                if (file.exists() && file.isFile) {
                    val code = file.readText(Charsets.UTF_8)
                    val updatedCode = if (code.contains("// @name")) {
                        code.replace(Regex("""//\s*@name\s+.*"""), "// @name         $trimmed")
                    } else {
                        "// @name         $trimmed\n$code"
                    }
                    if (file.absolutePath != newFile.absolutePath) {
                        file.delete()
                    }
                    newFile.writeText(updatedCode, Charsets.UTF_8)
                    newFilePath = newFile.absolutePath
                }
                val updatedEntity = oldEntity.copy(name = trimmed, filePath = newFilePath, lastModified = System.currentTimeMillis())
                list[index] = updatedEntity
                saveDatabase(list)
                _scriptsFlow.value = list
                Log.i(TAG, "Renamed script ${oldEntity.name} to $trimmed (file: $newFilePath)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename script ${oldEntity.name} to $trimmed: ${e.message}", e)
                false
            }
        }
    }

    suspend fun syncFromDisk(): List<UserscriptEntity> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            syncFromDiskInternal()
        }
    }

    private fun syncFromDiskInternal(): List<UserscriptEntity> {
        val scriptsDir = ensureScriptsDirectory()
        val files = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".user.js", ignoreCase = true) } ?: emptyArray()

        val current = _scriptsFlow.value
        val existingByPath = current.associateBy { it.filePath }
        val updatedList = mutableListOf<UserscriptEntity>()

        for (file in files) {
            val existing = existingByPath[file.absolutePath]
            if (existing != null) {
                // If file was modified since last check, refresh metadata
                if (file.lastModified() > existing.lastModified) {
                    val code = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                    val parsed = UserscriptMetadataParser.parse(code, fallbackName = file.nameWithoutExtension.removeSuffix(".user"))
                    updatedList.add(
                        existing.copy(
                            name = parsed.name,
                            version = parsed.version,
                            matchPatterns = parsed.matchPatterns,
                            runAt = parsed.runAt,
                            description = parsed.description,
                            author = parsed.author,
                            grantList = parsed.grantList,
                            lastModified = file.lastModified()
                        )
                    )
                } else {
                    updatedList.add(existing)
                }
            } else {
                // Discovered new file dropped into directory
                val code = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                val parsed = UserscriptMetadataParser.parse(code, fallbackName = file.nameWithoutExtension.removeSuffix(".user"))
                updatedList.add(
                    UserscriptEntity(
                        id = UUID.randomUUID().toString(),
                        name = parsed.name,
                        version = parsed.version,
                        filePath = file.absolutePath,
                        isEnabled = true,
                        matchPatterns = parsed.matchPatterns,
                        runAt = parsed.runAt,
                        description = parsed.description,
                        author = parsed.author,
                        grantList = parsed.grantList,
                        lastModified = file.lastModified()
                    )
                )
            }
        }

        saveDatabase(updatedList)
        _scriptsFlow.value = updatedList
        return updatedList
    }

    private fun loadDatabase(): List<UserscriptEntity> {
        return try {
            if (!dbFile.exists()) return emptyList()
            val text = dbFile.readText(Charsets.UTF_8)
            if (text.isBlank()) return emptyList()
            val state = json.decodeFromString<UserscriptDatabaseState>(text)
            state.scripts
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load database: ${e.message}")
            emptyList()
        }
    }

    private fun saveDatabase(scripts: List<UserscriptEntity>) {
        try {
            val state = UserscriptDatabaseState(scripts = scripts, lastUpdated = System.currentTimeMillis())
            val text = json.encodeToString(state)
            dbFile.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save database: ${e.message}")
        }
    }
}
