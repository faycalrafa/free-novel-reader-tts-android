package com.novelarrow.reader.browser.engine

import org.json.JSONObject

enum class RunAt {
    DOCUMENT_START,
    DOCUMENT_END,
    DOCUMENT_IDLE
}

interface Userscript {
    val id: String
    val name: String
    val matchPatterns: List<String> // regex / wildcards e.g. *://*.novelbin.*/*
    val runAt: RunAt // DOCUMENT_START, DOCUMENT_END, DOCUMENT_IDLE
    fun getScriptContent(): String
}

object WebScriptInjector {

    private val userscripts = mutableMapOf<String, Userscript>()

    fun registerUserscript(script: Userscript) {
        userscripts[script.id] = script
    }

    fun unregisterUserscript(id: String) {
        userscripts.remove(id)
    }

    fun getAllUserscripts(): List<Userscript> = userscripts.values.toList()

    fun clearUserscripts() {
        userscripts.clear()
    }

    fun matchesPattern(url: String, pattern: String): Boolean {
        if (pattern == "*" || pattern == "*://*/*" || pattern == "<all_urls>") return true
        return try {
            val sb = StringBuilder("^")
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '*' && i + 1 < pattern.length && pattern[i + 1] == '.' -> {
                        sb.append("(?:.*\\.)?")
                        i++
                    }
                    c == '*' -> sb.append(".*")
                    c == '?' -> sb.append(".")
                    c in ".^$[]{}()+|\\" -> sb.append('\\').append(c)
                    else -> sb.append(c)
                }
                i++
            }
            sb.append("$")
            Regex(sb.toString(), RegexOption.IGNORE_CASE).containsMatchIn(url)
        } catch (_: Exception) {
            false
        }
    }

    fun getUserscriptsForUrl(url: String, runAt: RunAt): List<Userscript> {
        return userscripts.values.filter { script ->
            script.runAt == runAt && script.matchPatterns.any { pattern -> matchesPattern(url, pattern) }
        }
    }

    fun buildUserscriptInjectionCode(url: String, runAt: RunAt): String {
        val scripts = getUserscriptsForUrl(url, runAt)
        if (scripts.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("(function() {\n")
        for (script in scripts) {
            sb.append("  try {\n")
            sb.append("    /* Userscript: ").append(script.name.replace("*/", "* /")).append(" */\n")
            sb.append(script.getScriptContent()).append("\n")
            sb.append("  } catch (err) {\n")
            sb.append("    console.error('Error executing userscript [").append(script.id).append("]:', err);\n")
            sb.append("  }\n")
        }
        sb.append("})();")
        return sb.toString()
    }

    fun buildHelperScript(): String {
        return """
            (function() {
                // Polyfill and alias BrowserBridge and AndroidBridge
                window.BrowserBridge = window.BrowserBridge || window.AndroidBridge;
                window.AndroidBridge = window.AndroidBridge || window.BrowserBridge;

                // Polyfill Greasemonkey GM_notification API (Toast)
                if (typeof window.GM_notification === 'undefined') {
                    window.GM_notification = function(details, ondone) {
                        var text = '';
                        if (typeof details === 'string') {
                            text = details;
                        } else if (typeof details === 'object' && details !== null) {
                            text = details.text || details.title || JSON.stringify(details);
                        }
                        if (window.BrowserBridge && window.BrowserBridge.gmNotification) {
                            window.BrowserBridge.gmNotification(String(text));
                        } else if (window.AndroidBridge && window.AndroidBridge.gmNotification) {
                            window.AndroidBridge.gmNotification(String(text));
                        }
                        if (typeof ondone === 'function') {
                            try { ondone(); } catch(e) {}
                        }
                    };
                }

                // 1. Inject highlight and hover styles
                if (!document.getElementById('na-tts-styles')) {
                    var styleEl = document.createElement('style');
                    styleEl.id = 'na-tts-styles';
                    styleEl.textContent = 
                        '.na-tts-highlight { background-color: rgba(255, 235, 59, 0.38) !important; border-radius: 4px !important; box-shadow: 0 0 0 2px rgba(255, 214, 0, 0.4) !important; }' +
                        '[data-novel-para-idx] { cursor: pointer; transition: background-color 0.2s ease; }' +
                        '[data-novel-para-idx]:hover { background-color: rgba(0, 122, 255, 0.08); }';
                    (document.head || document.documentElement).appendChild(styleEl);
                }

                // 2. Interactive paragraph click to seek TTS (do NOT block clicks on links/buttons)
                if (!window.__novelArrowClickBound) {
                    window.__novelArrowClickBound = true;
                    document.addEventListener('click', function(e) {
                        if (e.target && e.target.closest('a, button, input, select, textarea, [role="button"]')) {
                            return; // Let user interact with buttons and links normally
                        }
                        var pEl = e.target.closest('[data-novel-para-idx]');
                        if (pEl) {
                            var idxStr = pEl.getAttribute('data-novel-para-idx');
                            if (idxStr !== null && window.AndroidBridge && window.AndroidBridge.onParagraphClicked) {
                                var idx = parseInt(idxStr, 10);
                                if (!isNaN(idx)) {
                                    window.AndroidBridge.onParagraphClicked(idx);
                                }
                            }
                        }
                    }, true);
                }

                // 3. Scroll to bottom detector for auto-next on scroll
                if (!window.__novelArrowScrollBound) {
                    window.__novelArrowScrollBound = true;
                    window.addEventListener('scroll', function() {
                        if ((window.innerHeight + window.scrollY) >= (document.documentElement.scrollHeight - 16)) {
                            if (window.AndroidBridge && window.AndroidBridge.onScrolledToBottom) {
                                window.AndroidBridge.onScrolledToBottom();
                            }
                        }
                    });
                }
            })();
        """.trimIndent()
    }

    fun buildHighlightScript(index: Int, autoScroll: Boolean): String {
        val scrollSnippet = if (autoScroll) {
            """
                    try {
                        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    } catch(e) {
                        target.scrollIntoView(true);
                    }
            """.trimIndent()
        } else ""

        return """
            (function() {
                if (!document.getElementById('na-tts-styles')) {
                    var styleEl = document.createElement('style');
                    styleEl.id = 'na-tts-styles';
                    styleEl.textContent = 
                        '.na-tts-highlight { background-color: rgba(255, 235, 59, 0.45) !important; outline: 2px solid #fbc02d !important; border-radius: 4px !important; box-shadow: 0 0 10px rgba(255, 214, 0, 0.5) !important; transition: background-color 0.15s ease !important; }' +
                        '[data-novel-para-idx] { cursor: pointer; transition: background-color 0.2s ease; }' +
                        '[data-novel-para-idx]:hover { background-color: rgba(0, 122, 255, 0.08); }';
                    (document.head || document.documentElement).appendChild(styleEl);
                }

                var prev = document.querySelectorAll('.na-tts-highlight');
                for (var i = 0; i < prev.length; i++) {
                    prev[i].classList.remove('na-tts-highlight');
                }
                var target = document.querySelector('[data-novel-para-idx="' + $index + '"]');
                if (!target) {
                    var allTagged = document.querySelectorAll('[data-novel-para-idx]');
                    if (allTagged.length === 0) {
                        var pElements = Array.from(document.querySelectorAll('p')).filter(function(p) {
                            return (p.textContent || '').trim().length >= 2;
                        });
                        for (var k = 0; k < pElements.length; k++) {
                            pElements[k].setAttribute('data-novel-para-idx', String(k));
                        }
                        if ($index < pElements.length) {
                            target = pElements[$index];
                        }
                    }
                }
                if (target) {
                    target.classList.add('na-tts-highlight');
                    $scrollSnippet
                }
            })();
        """.trimIndent()
    }

    fun buildClearHighlightScript(): String {
        return """
            (function() {
                var prev = document.querySelectorAll('.na-tts-highlight');
                for (var i = 0; i < prev.length; i++) {
                    prev[i].classList.remove('na-tts-highlight');
                }
            })();
        """.trimIndent()
    }

    fun buildNextChapterScript(customSelector: String): String {
        val safeSelector = JSONObject.quote(customSelector.trim())
        return """
            (function() {
                if (window.__na_next_clicking) return;
                window.__na_next_clicking = true;
                setTimeout(function() { window.__na_next_clicking = false; }, 2000);

                var customSel = $safeSelector;
                function getNextEl() {
                    if (customSel && customSel.length > 0) {
                        try {
                            var el = document.querySelector(customSel);
                            if (el) return el;
                        } catch(e) {}
                    }
                    var selectors = [
                        'button[title*="Next Chapter"]', 'a[title*="Next Chapter"]',
                        'button[title*="Next chapter"]', 'a[title*="Next chapter"]',
                        'button[title="Next"]', 'a[title="Next"]',
                        '#next_chap', '.next-chap', '.next-chapter', '.btn-next', 'a.next',
                        'a[rel="next"]', '.chapter-next a', '#next-chapter', '.nav-next a'
                    ];
                    for (var s = 0; s < selectors.length; s++) {
                        try {
                            var selEl = document.querySelector(selectors[s]);
                            if (selEl) return selEl;
                        } catch(e) {}
                    }
                    var candidates = Array.from(document.querySelectorAll('a, button, span[role="button"], div[role="button"]'));
                    for (var i = 0; i < candidates.length; i++) {
                        var c = candidates[i];
                        var txt = (c.innerText || c.textContent || c.title || '').trim().toLowerCase();
                        if (txt === 'next chapter' || txt === 'next >' || txt === 'next »' || txt === 'next' || txt.indexOf('next chapter') !== -1) {
                            return c;
                        }
                    }
                    return null;
                }

                function incrementUrl() {
                    try {
                        var urlObj = new URL(window.location.href);
                        var pathname = urlObj.pathname;
                        var regex = /(\d+)(?=\.html?$)/i;
                        if (!regex.test(pathname)) { regex = /(\d+)(?!.*\d)/; }
                        if (regex.test(pathname)) {
                            urlObj.pathname = pathname.replace(regex, function(match) {
                                var len = match.length;
                                var nextNum = parseInt(match, 10) + 1;
                                return nextNum.toString().padStart(len, '0');
                            });
                            window.location.href = urlObj.href;
                            return true;
                        }
                    } catch(e) {}
                    return false;
                }

                var target = getNextEl();
                if (target) {
                    var oldUrl = window.location.href;
                    try {
                        target.scrollIntoView({ block: 'center' });
                        target.focus();
                        target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                        target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                        target.click();
                    } catch(e) {}
                    setTimeout(function() {
                        if (window.location.href === oldUrl) {
                            var aTag = target.tagName && target.tagName.toLowerCase() === 'a' ? target : target.closest('a');
                            if (aTag && aTag.href && !aTag.getAttribute('href').startsWith('javascript:')) {
                                window.location.href = aTag.href;
                            }
                        }
                    }, 2500);
                } else {
                    incrementUrl();
                }
            })();
        """.trimIndent()
    }

    fun buildPrevChapterScript(): String {
        return """
            (function() {
                if (window.__na_prev_clicking) return;
                window.__na_prev_clicking = true;
                setTimeout(function() { window.__na_prev_clicking = false; }, 2000);

                function getPrevEl() {
                    var selectors = [
                        'button[title*="Prev Chapter" i]', 'a[title*="Prev Chapter" i]',
                        'button[title*="Previous Chapter" i]', 'a[title*="Previous Chapter" i]',
                        'button[title*="Prev" i]', 'a[title*="Prev" i]',
                        'button[title*="Previous" i]', 'a[title*="Previous" i]',
                        '#prev_chap', '.prev-chap', '.prev-chapter', '.btn-prev', 'a.prev', 'a.previous',
                        'a[rel="prev"]', '.chapter-prev a', '#prev-chapter', '.nav-prev a', '#prev_chapter'
                    ];
                    for (var s = 0; s < selectors.length; s++) {
                        try {
                            var selEl = document.querySelector(selectors[s]);
                            if (selEl) return selEl;
                        } catch(e) {}
                    }
                    var candidates = Array.from(document.querySelectorAll('a, button, span[role="button"], div[role="button"]'));
                    for (var i = 0; i < candidates.length; i++) {
                        var c = candidates[i];
                        var txt = (c.innerText || c.textContent || c.title || '').trim().toLowerCase();
                        if (txt === 'prev chapter' || txt === 'previous chapter' || txt === '< prev' || txt === '« prev' || txt === 'prev' || txt === 'previous' || txt.indexOf('prev chapter') !== -1 || txt.indexOf('previous chapter') !== -1) {
                            return c;
                        }
                    }
                    return null;
                }

                function decrementUrl() {
                    try {
                        var urlObj = new URL(window.location.href);
                        var pathname = urlObj.pathname;
                        var regex = /(\d+)(?=\.html?$)/i;
                        if (!regex.test(pathname)) { regex = /(\d+)(?!.*\d)/; }
                        if (regex.test(pathname)) {
                            urlObj.pathname = pathname.replace(regex, function(match) {
                                var len = match.length;
                                var prevNum = Math.max(1, parseInt(match, 10) - 1);
                                return prevNum.toString().padStart(len, '0');
                            });
                            window.location.href = urlObj.href;
                            return true;
                        }
                    } catch(e) {}
                    return false;
                }

                var target = getPrevEl();
                if (target) {
                    var oldUrl = window.location.href;
                    try {
                        target.scrollIntoView({ block: 'center' });
                        target.focus();
                        target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                        target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                        target.click();
                    } catch(e) {}
                    setTimeout(function() {
                        if (window.location.href === oldUrl) {
                            var aTag = target.tagName && target.tagName.toLowerCase() === 'a' ? target : target.closest('a');
                            if (aTag && aTag.href && !aTag.getAttribute('href').startsWith('javascript:')) {
                                window.location.href = aTag.href;
                            }
                        }
                    }, 2500);
                } else {
                    if (!decrementUrl()) {
                        window.history.back();
                    }
                }
            })();
        """.trimIndent()
    }
}
