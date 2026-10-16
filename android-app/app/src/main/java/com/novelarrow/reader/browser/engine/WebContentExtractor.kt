package com.novelarrow.reader.browser.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ExtractedContent(
    val title: String,
    val paragraphs: List<String>
)

object WebContentExtractor {

    private const val TAG = "WebContentExtractor"

    fun buildContentExtractionScript(
        contentSelector: String,
        ignoredSelectors: List<String>,
        contentSelectorRules: List<com.novelarrow.reader.data.model.ContentSelectorRule> = emptyList(),
        currentUrl: String = ""
    ): String {
        val activeSelectors = mutableListOf<String>()
        if (contentSelector.isNotBlank()) {
            activeSelectors.add(contentSelector.trim())
        }
        for (rule in contentSelectorRules) {
            if (rule.enabled && rule.selector.isNotBlank()) {
                val inScope = when (rule.scopeType) {
                    com.novelarrow.reader.data.model.PronunciationScopeType.URL_PATTERN -> currentUrl.contains(rule.scopePattern, ignoreCase = true)
                    else -> true
                }
                if (inScope && !activeSelectors.contains(rule.selector.trim())) {
                    activeSelectors.add(rule.selector.trim())
                }
            }
        }
        val safeSelectorsArray = JSONArray(activeSelectors).toString()
        val safeIgnoredArray = JSONArray(ignoredSelectors).toString()
        return """
            (function() {
                try {
                    var candidateSelectors = $safeSelectorsArray;
                    var userIgnored = $safeIgnoredArray;
                    var defaultIgnore = [
                        'a', 'button', 'input', 'select', 'textarea', 'form',
                        'header', 'footer', 'aside', 'nav',
                        'script', 'style', 'noscript', 'iframe',
                        '.sidebar', '#sidebar', '.header', '#header', '.footer', '#footer',
                        '.ad', '.ads', '.advertisement', '#comments', '.comments',
                        '#custom-floating-bar', '#na-root', '#na-player-bar', '#na-floating-next',
                        '.na-tts-highlight'
                    ];
                    var ignored = defaultIgnore.slice();
                    for (var d = 0; d < userIgnored.length; d++) {
                        if (userIgnored[d] && ignored.indexOf(userIgnored[d]) === -1) {
                            ignored.push(userIgnored[d]);
                        }
                    }

                    function isIgnored(el) {
                        if (!el) return true;
                        try {
                            for (var i = 0; i < ignored.length; i++) {
                                var sel = ignored[i];
                                if (sel && (el.matches(sel) || el.closest(sel))) return true;
                            }
                        } catch(e) {}
                        return false;
                    }

                    function extractFrom(container, useFilters) {
                        var allNodes = Array.from(container.querySelectorAll('p, div, li, blockquote, h1, h2, h3, h4, h5, h6'));
                        var paras = [];
                        var elements = [];

                        for (var i = 0; i < allNodes.length; i++) {
                            var el = allNodes[i];
                            if (useFilters && isIgnored(el)) continue;
                            if (!useFilters && (el.matches('script, style, noscript, iframe') || el.closest('script, style, noscript, iframe'))) continue;

                            var tag = el.tagName.toLowerCase();
                            if (['div', 'li', 'blockquote'].indexOf(tag) !== -1) {
                                if (el.querySelector('p, section, article, li, blockquote')) continue;
                            }

                            var cs = window.getComputedStyle(el);
                            if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) continue;
                            var rect = el.getBoundingClientRect();
                            if (rect.width === 0 && rect.height === 0 && el.offsetHeight === 0) continue;

                            var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
                            if (text.length >= 2) {
                                paras.push(text);
                                elements.push(el);
                            }
                        }
                        return { paras: paras, elements: elements };
                    }

                    // 1. Container selection from multiple prioritized selectors
                    var container = null;
                    for (var s = 0; s < candidateSelectors.length; s++) {
                        var sel = candidateSelectors[s];
                        if (sel && sel.length > 0) {
                            try {
                                var cand = document.querySelector(sel);
                                if (cand) {
                                    var testRes = extractFrom(cand, true);
                                    if (testRes.paras.length >= 2 || (testRes.paras.length >= 1 && testRes.paras[0].length >= 50)) {
                                        container = cand;
                                        break;
                                    }
                                }
                            } catch(e) {}
                        }
                    }
                    if (!container) {
                        var candidates = [
                            '#chapter-content', '#content', '.chapter-content', '.entry-content',
                            '.post-content', '.read-content', '.reading-content', '.chapter-c',
                            '.text-left', 'article', '.content', '#chapter_content', '.novel-content',
                            'main', '.chapter-container', '#read-area'
                        ];
                        for (var c = 0; c < candidates.length; c++) {
                            var cand = document.querySelector(candidates[c]);
                            if (cand && cand.querySelectorAll('p').length >= 2) {
                                container = cand;
                                break;
                            }
                        }
                    }
                    if (!container) container = document.body;

                    // 2. Heading
                    var heading = '';
                    var h1 = document.querySelector('h1, .chapter-title, .title-chapter, .entry-title');
                    if (h1 && !isIgnored(h1)) {
                        heading = (h1.textContent || '').replace(/\s+/g, ' ').trim();
                    }
                    if (!heading) heading = (document.title || '').trim();

                    // 3. Elements extraction with filters
                    var res = extractFrom(container, true);
                    var paras = res.paras;
                    var elements = res.elements;

                    // 4. FALLBACK: If caught nothing using filters, IGNORE them and start from the first <p> in the page
                    if (paras.length === 0) {
                        var allPs = Array.from(document.body.querySelectorAll('p'));
                        var firstPIndex = -1;
                        for (var p = 0; p < allPs.length; p++) {
                            var pEl = allPs[p];
                            if (pEl.matches('script, style, noscript, iframe') || pEl.closest('script, style, noscript, iframe')) continue;
                            var pText = (pEl.textContent || '').replace(/\s+/g, ' ').trim();
                            if (pText.length >= 2) {
                                firstPIndex = p;
                                break;
                            }
                        }

                        if (firstPIndex !== -1) {
                            paras = [];
                            elements = [];
                            for (var q = firstPIndex; q < allPs.length; q++) {
                                var elQ = allPs[q];
                                if (elQ.matches('script, style, noscript, iframe') || elQ.closest('script, style, noscript, iframe')) continue;
                                var tQ = (elQ.textContent || '').replace(/\s+/g, ' ').trim();
                                if (tQ.length >= 2) {
                                    paras.push(tQ);
                                    elements.push(elQ);
                                }
                            }
                        } else {
                            var fallbackRes = extractFrom(document.body, false);
                            paras = fallbackRes.paras;
                            elements = fallbackRes.elements;
                        }
                    }

                    // 5. Final fallback if still empty: split document.body.innerText by newlines
                    if (paras.length === 0) {
                        var rawLines = (document.body.innerText || '').split('\n');
                        for (var l = 0; l < rawLines.length; l++) {
                            var line = rawLines[l].replace(/\s+/g, ' ').trim();
                            if (line.length >= 2) {
                                paras.push(line);
                            }
                        }
                    }

                    // Tag matched elements for click-to-seek and auto-scroll highlighting
                    for (var idx = 0; idx < elements.length; idx++) {
                        elements[idx].setAttribute('data-novel-para-idx', String(idx));
                    }

                    return JSON.stringify({
                        title: heading,
                        paragraphs: paras
                    });
                } catch(err) {
                    return JSON.stringify({
                        title: document.title || '',
                        paragraphs: [],
                        error: err.toString()
                    });
                }
            })();
        """.trimIndent()
    }

    fun parseJavascriptResultToJson(result: String?): JSONObject? {
        if (result == null || result == "null" || result.isBlank()) return null
        return try {
            val tokener = JSONTokener(result)
            val value = tokener.nextValue()
            when (value) {
                is JSONObject -> value
                is String -> {
                    val secondTokener = JSONTokener(value)
                    val secondValue = secondTokener.nextValue()
                    if (secondValue is JSONObject) secondValue else JSONObject(value)
                }
                else -> JSONObject(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding JS result: $e, raw: $result")
            null
        }
    }

    fun extractChapterContent(result: String?, fallbackTitle: String): ExtractedContent {
        val json = parseJavascriptResultToJson(result)
        val title = json?.optString("title", fallbackTitle)?.ifBlank { fallbackTitle } ?: fallbackTitle
        val paragraphsArray = json?.optJSONArray("paragraphs") ?: JSONArray()
        val paragraphs = mutableListOf<String>()
        for (i in 0 until paragraphsArray.length()) {
            val p = paragraphsArray.optString(i, "").trim()
            if (p.isNotBlank()) paragraphs.add(p)
        }
        return ExtractedContent(title = title, paragraphs = paragraphs)
    }
}
