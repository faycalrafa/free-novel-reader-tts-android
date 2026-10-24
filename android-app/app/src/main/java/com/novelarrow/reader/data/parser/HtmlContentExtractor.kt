package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object HtmlContentExtractor {

    private const val TAG = "HtmlContentExtractor"

    fun parseHtml(
        html: String,
        contentSelector: String,
        contentSelectors: List<ContentSelectorRule> = emptyList(),
        folderPath: String? = null,
        novelUrl: String? = null,
        ignoredSelectors: List<String> = emptyList(),
        impactsMap: MutableMap<String, RuleImpact>? = null
    ): List<Pair<String, Boolean>> {
        val doc = Jsoup.parse(html)

        // 1. Always start by CSS content selector: identify content container first
        var selectedElement: Element? = null
        var matchedRuleId: String? = null
        var matchedSelectorName: String? = null

        // 1a. Evaluate multiple content selectors in priority order
        val activeRules = contentSelectors.filter { it.enabled }
        for (rule in activeRules) {
            val inScope = when (rule.scopeType) {
                PronunciationScopeType.URL_PATTERN -> novelUrl?.contains(rule.scopePattern, ignoreCase = true) == true
                PronunciationScopeType.FOLDER -> folderPath?.contains(rule.scopePattern, ignoreCase = true) == true
                else -> true
            }
            if (inScope && rule.selector.isNotBlank()) {
                try {
                    val cand = doc.selectFirst(rule.selector)
                    if (cand != null && (cand.text().trim().length >= 50 || cand.select("p").size >= 2)) {
                        selectedElement = cand
                        matchedRuleId = rule.id
                        matchedSelectorName = rule.selector
                        AppLogger.d(TAG, "Content container matched rule '${rule.selector}' (id=${rule.id})")
                        break
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed evaluating content selector '${rule.selector}': ${e.message}", e)
                }
            }
        }

        // 1b. Fallback to legacy single contentSelector if specified
        if (selectedElement == null && contentSelector.isNotBlank()) {
            try {
                selectedElement = doc.selectFirst(contentSelector)
                if (selectedElement != null) {
                    matchedRuleId = "legacy_content_selector"
                    matchedSelectorName = contentSelector
                    AppLogger.d(TAG, "Content container matched legacy selector '$contentSelector'")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed evaluating legacy selector '$contentSelector': ${e.message}", e)
            }
        }

        // 1c. Fallback to well-known novel container candidates if no explicit rule matched
        if (selectedElement == null) {
            val defaultCandidates = listOf(
                "#chapter-content", "#content", ".chapter-content", ".entry-content",
                ".post-content", ".read-content", ".reading-content", ".chapter-c",
                ".text-left", "article", ".content", "#chapter_content", ".novel-content",
                "main", ".chapter-container", "#read-area"
            )
            for (candidate in defaultCandidates) {
                try {
                    val cand = doc.selectFirst(candidate)
                    if (cand != null && cand.select("p").size >= 2) {
                        selectedElement = cand
                        matchedRuleId = "candidate_$candidate"
                        matchedSelectorName = candidate
                        AppLogger.d(TAG, "Content container fallback matched '$candidate'")
                        break
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed candidate selector '$candidate': ${e.message}", e)
                }
            }
        }

        if (selectedElement != null && impactsMap != null && matchedSelectorName != null) {
            val containerChars = selectedElement.text().length
            impactsMap[matchedRuleId ?: matchedSelectorName] = RuleImpact(
                id = matchedRuleId ?: matchedSelectorName,
                name = matchedSelectorName,
                ruleType = RuleType.CONTENT_SELECTOR,
                charsFiltered = containerChars,
                enabled = true,
                details = "Isolated content container ($containerChars characters captured)"
            )
        }

        val rootElement: Element = selectedElement ?: doc.body()

        // 2. Apply filtering and ignored rules strictly WITHIN the selected content container
        rootElement.select("script, style, noscript, iframe").remove()
        rootElement.select("[style*='display: none'], [style*='display:none'], [hidden]").remove()

        // 2a. Custom user-configured ignored selectors
        for (selector in ignoredSelectors) {
            val trimmed = selector.trim()
            if (trimmed.isNotBlank()) {
                try {
                    val matched = rootElement.select(trimmed).filter { it != rootElement }
                    val strippedChars = matched.sumOf { it.text().length }
                    if (strippedChars > 0 && impactsMap != null) {
                        impactsMap["ignored_$trimmed"] = RuleImpact(
                            id = "ignored_$trimmed",
                            name = trimmed,
                            ruleType = RuleType.IGNORED_SELECTOR,
                            charsFiltered = strippedChars,
                            enabled = true,
                            details = "$strippedChars characters removed by selector"
                        )
                    }
                    AppLogger.d(TAG, "Custom ignored selector '$trimmed' removed ${matched.size} elements ($strippedChars chars)")
                    matched.forEach { it.remove() }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed applying custom ignored selector '$trimmed': ${e.message}", e)
                }
            }
        }

        // 2b. Default ignored selectors applied only to descendants inside container
        for (selector in ContentFilterRules.IGNORE_SELECTORS) {
            try {
                val matched = rootElement.select(selector).filter { it != rootElement }
                matched.forEach { it.remove() }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed applying default ignored selector '$selector': ${e.message}", e)
            }
        }

        val elements = rootElement.select("p, h1, h2, h3, h4, h5, h6, li, blockquote, div")
        val pairs = mutableListOf<Pair<String, Boolean>>()

        for (el in elements) {
            // Skip container divs that contain nested block children
            if (el.tagName() in listOf("div", "blockquote", "li") &&
                el.select("p, h1, h2, h3, h4, h5, h6, li, blockquote").isNotEmpty()
            ) {
                continue
            }

            val text = el.text().trim()
            if (text.length >= 2 && !ContentFilterRules.isNonContentText(text)) {
                val isHeading = el.tagName().startsWith("h")
                pairs.add(text to isHeading)
            }
        }

        if (pairs.isEmpty()) {
            val fallbackDoc = Jsoup.parse(html)
            fallbackDoc.select("script, style, noscript").remove()
            val allPs = fallbackDoc.select("p")
            val firstPIndex = allPs.indexOfFirst { it.text().trim().length >= 2 }
            if (firstPIndex != -1) {
                for (i in firstPIndex until allPs.size) {
                    val pText = allPs[i].text().trim()
                    if (pText.length >= 2 && !ContentFilterRules.isNonContentText(pText)) {
                        pairs.add(pText to false)
                    }
                }
            }
        }

        if (pairs.isEmpty() && rootElement.text().isNotBlank()) {
            return parsePlainText(rootElement.text())
        }

        return pairs
    }

    fun parsePlainText(text: String): List<Pair<String, Boolean>> {
        val lines = text.split("\n\n", "\r\n\r\n", "\n", "\r\n")
        val pairs = mutableListOf<Pair<String, Boolean>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length >= 2 && !ContentFilterRules.isNonContentText(trimmed)) {
                val isHeading = trimmed.startsWith("#") ||
                        trimmed.startsWith("Chapter", ignoreCase = true) ||
                        trimmed.length < 50 && !trimmed.endsWith(".")
                pairs.add(trimmed to isHeading)
            }
        }

        return pairs
    }
}
