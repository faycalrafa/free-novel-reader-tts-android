package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.SkipRule
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object ChapterParser {

    private val IGNORE_SELECTORS = listOf(
        // HTML5 non-content tags
        "script", "style", "nav", "footer", "header", "aside",
        "button", "a", "form", "input", "textarea", "select",
        "noscript", "iframe", "svg", "dialog", "menu", "canvas",
        // Footers and bottom metadata
        ".footer", "#footer", ".site-footer", ".chapter-footer", ".page-footer",
        ".entry-footer", ".post-footer", ".bottom-bar", ".page-foot", ".foot",
        // Headers and top navigation
        ".header", "#header", ".site-header", ".chapter-header", ".page-header",
        ".top-header", ".entry-header", ".post-header",
        // Navigation, pagers, pagination
        ".nav", ".navbar", ".navigation", ".chapter-nav", ".chapter-navigation",
        ".nav-links", ".nav-buttons", ".pagination", ".pager", ".paginate",
        ".prev-next", ".next-prev", ".chapter-pager", ".chapter-navs",
        ".breadcrumb", ".breadcrumbs",
        // Buttons, ARIA roles
        ".btn", ".button", ".btn-group", "[role=button]", "[role=navigation]",
        "[role=banner]", "[role=contentinfo]", "[role=complementary]",
        // Comments and social widgets
        ".comments", "#comments", ".comment-section", ".comment-box",
        ".disqus", "#disqus_thread", ".share", ".social-share", ".share-buttons", ".social",
        // Ads and sponsors
        ".ads", ".ad", ".advertisement", ".ad-container", ".ad-wrapper", ".ad-box",
        ".banner-ad", ".adsbygoogle", ".patreon", ".donate", ".ko-fi", ".sponsor",
        // Sidebars & miscellaneous widgets
        ".sidebar", "#sidebar", ".widget", ".widget-area", ".author-info", ".rating", ".vote", ".meta-info"
    )

    private val NON_CONTENT_REGEXES = listOf(
        Regex("""^(<|«|<<)?\s*(prev|previous|back)\s*(chapter|chap|page|part)?\s*(>|»|>>)?$""", RegexOption.IGNORE_CASE),
        Regex("""^(<|«|<<)?\s*(next|forward)\s*(chapter|chap|page|part)?\s*(>|»|>>)?$""", RegexOption.IGNORE_CASE),
        Regex("""^(table of contents|index|contents|all chapters|chapter list|back to index|return to index)$""", RegexOption.IGNORE_CASE),
        Regex("""^(bookmark( this chapter)?|report (chapter|error|broken)|like|share)$""", RegexOption.IGNORE_CASE),
        Regex("""^(join (our )?discord( server)?|support (the author|the translator|us)( on patreon| on ko-fi)?)$""", RegexOption.IGNORE_CASE),
        Regex("""^(leave a comment|comment(s)?\s*(\(\d+\))?|\d+\s+comments?)$""", RegexOption.IGNORE_CASE),
        Regex("""^(next|prev|previous)\s*[|•/»>]\s*(next|prev|previous|index|home)$""", RegexOption.IGNORE_CASE),
        Regex("""^copyright\s*(©|\(c\))?.*all rights reserved.*$""", RegexOption.IGNORE_CASE)
    )

    private val SENTENCE_PATTERN = Pattern.compile(
        "[^.!?。！？]+(?:[.!?]+[\"'”’」』）］｝»)\\]}]*(?=\\s|$)|[。！？]+[\"'”’」』）］｝»)\\]}]*)|[^.!?。！？]+$"
    )

    fun parse(
        content: String,
        fileName: String,
        contentSelector: String = "",
        contentSelectorEnabled: Boolean = true,
        pronunciationRules: List<PronunciationRule> = emptyList(),
        pronunciationRulesEnabled: Boolean = true,
        skipWords: List<SkipWord> = emptyList(),
        skipWordsEnabled: Boolean = true,
        headerSkipString: String = "",
        headerSkipEnabled: Boolean = true,
        footerSkipString: String = "",
        footerSkipEnabled: Boolean = true,
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList(),
        folderPath: String? = null,
        novelUrl: String? = null
    ): List<Paragraph> {
        val effectiveSelector = if (contentSelectorEnabled) contentSelector else ""
        val effectiveRules = if (pronunciationRulesEnabled) {
            pronunciationRules.filter { isRuleInScope(it, folderPath, novelUrl) }
        } else emptyList()
        val effectiveSkips = if (skipWordsEnabled) {
            skipWords.filter { it.enabled }
        } else emptyList()
        val effectiveHeader = if (headerSkipEnabled) headerSkipString else ""
        val effectiveFooter = if (footerSkipEnabled) footerSkipString else ""
        val effectiveHeaderRules = if (headerSkipEnabled) headerSkipRules else emptyList()
        val effectiveFooterRules = if (footerSkipEnabled) footerSkipRules else emptyList()

        val isHtml = fileName.endsWith(".html", ignoreCase = true) ||
                fileName.endsWith(".htm", ignoreCase = true) ||
                fileName.endsWith(".xhtml", ignoreCase = true) ||
                content.contains("<html", ignoreCase = true) ||
                content.contains("<p>", ignoreCase = true)

        val rawParagraphs = if (isHtml) {
            parseHtml(content, effectiveSelector)
        } else {
            parsePlainText(content)
        }

        val filteredParagraphs = rawParagraphs.filterNot { (text, _) -> isNonContentText(text) }
        val scopedParagraphs = applyHeaderAndFooterSkip(
            filteredParagraphs,
            effectiveHeader,
            effectiveFooter,
            effectiveHeaderRules,
            effectiveFooterRules
        )

        val result = mutableListOf<Paragraph>()
        var counter = 0

        for ((text, isHeader) in scopedParagraphs) {
            val cleaned = text.trim()
            if (cleaned.length < 2 || isNonContentText(cleaned)) continue

            // Sentence-level chunking for long paragraphs (over 240 chars)
            val chunks = if (cleaned.length > 240 && containsPunctuation(cleaned)) {
                splitIntoSentences(cleaned)
            } else {
                listOf(cleaned)
            }

            for (chunk in chunks) {
                val trimmedChunk = chunk.trim()
                if (trimmedChunk.length < 2 || isNonContentText(trimmedChunk)) continue

                val spoken = applyRulesAndSkips(trimmedChunk, effectiveRules, effectiveSkips, folderPath, novelUrl)
                result.add(
                    Paragraph(
                        index = counter++,
                        rawText = trimmedChunk,
                        spokenText = spoken,
                        isHeading = isHeader
                    )
                )
            }
        }

        return result
    }

    fun isNonContentText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return true
        return NON_CONTENT_REGEXES.any { it.matches(trimmed) }
    }

    fun applyHeaderAndFooterSkip(
        rawParagraphs: List<Pair<String, Boolean>>,
        headerSkipString: String = "",
        footerSkipString: String = "",
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList()
    ): List<Pair<String, Boolean>> {
        var workingList = rawParagraphs

        val activeHeaderRules: List<SkipRule> = if (headerSkipRules.isNotEmpty()) {
            headerSkipRules
        } else if (headerSkipString.isNotBlank()) {
            headerSkipString.split('\n', ',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { SkipRule(it, exactMatch = false) }
        } else {
            emptyList()
        }

        // Apply header skip: look for any headerSkip rule and start from the earliest match
        if (activeHeaderRules.isNotEmpty()) {
            var matchIndex = -1
            var bestSubIndex = -1
            for ((idx, pair) in workingList.withIndex()) {
                val (text, _) = pair
                var minSub = -1
                for (rule in activeHeaderRules) {
                    val sub = if (rule.exactMatch) {
                        val pattern = Pattern.compile("\\b" + Pattern.quote(rule.text) + "\\b", Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(text)
                        if (matcher.find()) matcher.start() else -1
                    } else {
                        text.indexOf(rule.text, ignoreCase = true)
                    }
                    if (sub != -1 && (minSub == -1 || sub < minSub)) {
                        minSub = sub
                    }
                }
                if (minSub != -1) {
                    matchIndex = idx
                    bestSubIndex = minSub
                    break
                }
            }

            if (matchIndex != -1) {
                val (matchText, isHeading) = workingList[matchIndex]
                val trimmedFirst = if (bestSubIndex > 0) {
                    matchText.substring(bestSubIndex).trim()
                } else {
                    matchText
                }
                val remainder = workingList.subList(matchIndex + 1, workingList.size)
                workingList = if (trimmedFirst.length >= 2) {
                    listOf(trimmedFirst to isHeading) + remainder
                } else {
                    remainder
                }
            }
        }

        val activeFooterRules: List<SkipRule> = if (footerSkipRules.isNotEmpty()) {
            footerSkipRules
        } else if (footerSkipString.isNotBlank()) {
            footerSkipString.split('\n', ',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { SkipRule(it, exactMatch = false) }
        } else {
            emptyList()
        }

        // Apply footer skip: look for any footerSkip rule and end where it is found
        if (activeFooterRules.isNotEmpty()) {
            var matchIndex = -1
            var bestSubIndex = -1
            for ((idx, pair) in workingList.withIndex()) {
                val (text, _) = pair
                var minSub = -1
                for (rule in activeFooterRules) {
                    val sub = if (rule.exactMatch) {
                        val pattern = Pattern.compile("\\b" + Pattern.quote(rule.text) + "\\b", Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(text)
                        if (matcher.find()) matcher.start() else -1
                    } else {
                        text.indexOf(rule.text, ignoreCase = true)
                    }
                    if (sub != -1 && (minSub == -1 || sub < minSub)) {
                        minSub = sub
                    }
                }
                if (minSub != -1) {
                    matchIndex = idx
                    bestSubIndex = minSub
                    break
                }
            }

            if (matchIndex != -1) {
                val (matchText, isHeading) = workingList[matchIndex]
                val trimmedLast = if (bestSubIndex >= 0) {
                    matchText.substring(0, bestSubIndex).trim()
                } else {
                    matchText
                }
                val prefix = workingList.subList(0, matchIndex)
                workingList = if (trimmedLast.length >= 2) {
                    prefix + listOf(trimmedLast to isHeading)
                } else {
                    prefix
                }
            }
        }

        return workingList
    }

    private fun parseHtml(html: String, contentSelector: String): List<Pair<String, Boolean>> {
        val doc = Jsoup.parse(html)
        for (selector in IGNORE_SELECTORS) {
            try {
                doc.select(selector).remove()
            } catch (_: Exception) {}
        }
        doc.select("[style*='display: none'], [style*='display:none'], [hidden]").remove()

        val rootElement: Element = if (contentSelector.isNotBlank()) {
            doc.selectFirst(contentSelector) ?: doc.body()
        } else {
            doc.body()
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
            if (text.length >= 2 && !isNonContentText(text)) {
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
                    if (pText.length >= 2 && !isNonContentText(pText)) {
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

    private fun parsePlainText(text: String): List<Pair<String, Boolean>> {
        val lines = text.split("\n\n", "\r\n\r\n", "\n", "\r\n")
        val pairs = mutableListOf<Pair<String, Boolean>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length >= 2 && !isNonContentText(trimmed)) {
                val isHeading = trimmed.startsWith("#") ||
                        trimmed.startsWith("Chapter", ignoreCase = true) ||
                        trimmed.length < 50 && !trimmed.endsWith(".")
                pairs.add(trimmed to isHeading)
            }
        }

        return pairs
    }

    fun containsPunctuation(text: String): Boolean {
        return text.any { it in ".!?。！？" }
    }

    fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val matcher = SENTENCE_PATTERN.matcher(text)
        while (matcher.find()) {
            val s = matcher.group().trim()
            if (s.length >= 2) {
                sentences.add(s)
            }
        }
        return if (sentences.isNotEmpty()) sentences else listOf(text)
    }

    fun isRuleInScope(
        rule: PronunciationRule,
        folderPath: String? = null,
        novelUrl: String? = null
    ): Boolean {
        if (!rule.enabled) return false
        return when (rule.scopeType) {
            PronunciationScopeType.GLOBAL -> true
            PronunciationScopeType.FOLDER -> {
                if (rule.scopePattern.isBlank()) false
                else matchesScopePattern(folderPath, rule.scopePattern)
            }
            PronunciationScopeType.URL_PATTERN -> {
                if (rule.scopePattern.isBlank()) false
                else matchesScopePattern(novelUrl, rule.scopePattern)
            }
        }
    }

    fun matchesScopePattern(target: String?, pattern: String): Boolean {
        if (target.isNullOrBlank() || pattern.isBlank()) return false
        val trimmedPattern = pattern.trim()

        // 1. Direct case-insensitive substring match
        if (target.contains(trimmedPattern, ignoreCase = true)) return true

        // 2. Direct match with wildcards stripped (e.g. *wuxiaworld.com*)
        val stripped = trimmedPattern.replace("*", "").trim()
        if (stripped.isNotBlank() && target.contains(stripped, ignoreCase = true)) return true

        // 3. Glob / Wildcard Regex match
        return try {
            val regexString = "^" + trimmedPattern.split("*").joinToString(".*") { Regex.escape(it) } + "$"
            Regex(regexString, RegexOption.IGNORE_CASE).matches(target)
        } catch (_: Exception) {
            false
        }
    }

    fun applyRulesAndSkips(
        text: String,
        pronunciationRules: List<PronunciationRule>,
        skipWords: List<SkipWord>,
        folderPath: String? = null,
        novelUrl: String? = null
    ): String {
        var processed = text

        val activeRules = pronunciationRules.filter { isRuleInScope(it, folderPath, novelUrl) }
        val activeSkips = skipWords.filter { it.enabled }

        for (rule in activeRules) {
            if (rule.from.isBlank()) continue
            val escaped = Regex.escape(rule.from)
            processed = if (rule.exactMatch) {
                val pattern = "(?i)(?<=^|[^\\p{L}\\p{N}])${escaped}(?=[^\\p{L}\\p{N}]|$)"
                processed.replace(Regex(pattern), rule.to)
            } else {
                processed.replace(rule.from, rule.to, ignoreCase = true)
            }
        }

        for (skip in activeSkips) {
            if (skip.text.isBlank()) continue
            val escaped = Regex.escape(skip.text)
            processed = if (skip.exactMatch) {
                val pattern = "(?i)(?<=^|[^\\p{L}\\p{N}])${escaped}(?=[^\\p{L}\\p{N}]|$)"
                processed.replace(Regex(pattern), "")
            } else {
                processed.replace(skip.text, "", ignoreCase = true)
            }
        }

        return processed.replace(Regex("\\s+"), " ").trim()
    }
}
