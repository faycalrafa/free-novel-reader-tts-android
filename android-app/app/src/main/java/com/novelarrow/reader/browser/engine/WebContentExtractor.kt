package com.novelarrow.reader.browser.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ExtractedContent(
    val title: String,
    val paragraphs: List<String>,
    val matchedContainerSelector: String? = null,
    val ignoredSelectorStats: Map<String, Int> = emptyMap(),
    val containerFilteredChars: Int = 0
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

                    var ignoredStats = {};
                    function isIgnored(el) {
                        if (!el) return true;
                        try {
                            for (var i = 0; i < ignored.length; i++) {
                                var sel = ignored[i];
                                if (sel && (el.matches(sel) || el.closest(sel))) {
                                    var len = (el.textContent || '').length;
                                    ignoredStats[sel] = (ignoredStats[sel] || 0) + len;
                                    return true;
                                }
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
                    var matchedSelector = null;
                    for (var s = 0; s < candidateSelectors.length; s++) {
                        var sel = candidateSelectors[s];
                        if (sel && sel.length > 0) {
                            try {
                                var cand = document.querySelector(sel);
                                if (cand) {
                                    container = cand;
                                    matchedSelector = sel;
                                    break;
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
                                matchedSelector = candidates[c];
                                break;
                            }
                        }
                    }
                    if (!container) container = document.body;

                    var containerFilteredChars = 0;
                    if (container !== document.body) {
                        var totalBodyChars = (document.body.textContent || '').length;
                        var containerChars = (container.textContent || '').length;
                        containerFilteredChars = containerChars;
                    }

                    // 2. Heading
                    var heading = '';
                    var h1 = document.querySelector('h1, .chapter-title, .title-chapter, .entry-title');
                    if (h1 && !isIgnored(h1)) {
                        heading = (h1.textContent || '').replace(/\s+/g, ' ').trim();
                    }
                    if (!heading) heading = (document.title || '').trim();

                    // 3. Elements extraction with filters inside container
                    var res = extractFrom(container, true);
                    var paras = res.paras;
                    var elements = res.elements;

                    // Tag matched elements for click-to-seek and auto-scroll highlighting
                    for (var idx = 0; idx < elements.length; idx++) {
                        elements[idx].setAttribute('data-novel-para-idx', String(idx));
                    }

                    return JSON.stringify({
                        title: heading,
                        paragraphs: paras,
                        matchedContainerSelector: matchedSelector || '',
                        ignoredSelectorStats: ignoredStats,
                        containerFilteredChars: containerFilteredChars
                    });
                } catch(err) {
                    return JSON.stringify({
                        title: document.title || '',
                        paragraphs: [],
                        matchedContainerSelector: '',
                        ignoredSelectorStats: {},
                        containerFilteredChars: 0,
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
        val matchedContainer = json?.optString("matchedContainerSelector")?.takeIf { it.isNotBlank() }
        val ignoredStats = mutableMapOf<String, Int>()
        val ignoredObj = json?.optJSONObject("ignoredSelectorStats")
        if (ignoredObj != null) {
            val keys = ignoredObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                ignoredStats[k] = ignoredObj.optInt(k, 0)
            }
        }
        val containerFilteredChars = json?.optInt("containerFilteredChars", 0) ?: 0
        return ExtractedContent(
            title = title,
            paragraphs = paragraphs,
            matchedContainerSelector = matchedContainer,
            ignoredSelectorStats = ignoredStats,
            containerFilteredChars = containerFilteredChars
        )
    }

    fun buildRawHtmlExtractionScript(customSelector: String): String {
        val safeSelector = JSONObject.quote(customSelector.trim())
        return """
            (function() {
                try {
                    var sel = $safeSelector;
                    var el = sel ? document.querySelector(sel) : null;
                    if (!el) {
                        var candidates = ['article', '.chapter-content', '.entry-content', '#chapter-content', '#content', '.content', 'main', '.text-left', '#chapter-c'];
                        for (var i = 0; i < candidates.length; i++) {
                            var found = document.querySelector(candidates[i]);
                            if (found && (found.innerText || '').length > 200) {
                                el = found;
                                break;
                            }
                        }
                    }
                    if (el) {
                        return el.outerHTML;
                    }
                    return document.documentElement.outerHTML;
                } catch(e) {
                    return document.documentElement.outerHTML;
                }
            })()
        """.trimIndent()
    }
}
