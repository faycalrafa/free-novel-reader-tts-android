package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.data.model.SkipRule
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.util.AppLogger

data class ParseResult(
    val paragraphs: List<Paragraph>,
    val ruleImpacts: List<RuleImpact>
)

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
        skipWordsIgnoreSymbols: Boolean = true,
        headerSkipString: String = "",
        headerSkipEnabled: Boolean = true,
        footerSkipString: String = "",
        footerSkipEnabled: Boolean = true,
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList(),
        folderPath: String? = null,
        novelUrl: String? = null,
        contentSelectors: List<ContentSelectorRule> = emptyList(),
        ignoredSelectors: List<String> = emptyList()
    ): List<Paragraph> {
        return parseWithDiagnostics(
            content = content,
            fileName = fileName,
            contentSelector = contentSelector,
            contentSelectorEnabled = contentSelectorEnabled,
            pronunciationRules = pronunciationRules,
            pronunciationRulesEnabled = pronunciationRulesEnabled,
            skipWords = skipWords,
            skipWordsEnabled = skipWordsEnabled,
            skipWordsIgnoreSymbols = skipWordsIgnoreSymbols,
            headerSkipString = headerSkipString,
            headerSkipEnabled = headerSkipEnabled,
            footerSkipString = footerSkipString,
            footerSkipEnabled = footerSkipEnabled,
            headerSkipRules = headerSkipRules,
            footerSkipRules = footerSkipRules,
            folderPath = folderPath,
            novelUrl = novelUrl,
            contentSelectors = contentSelectors,
            ignoredSelectors = ignoredSelectors
        ).paragraphs
    }

    fun parseWithDiagnostics(
        content: String,
        fileName: String,
        contentSelector: String = "",
        contentSelectorEnabled: Boolean = true,
        pronunciationRules: List<PronunciationRule> = emptyList(),
        pronunciationRulesEnabled: Boolean = true,
        skipWords: List<SkipWord> = emptyList(),
        skipWordsEnabled: Boolean = true,
        skipWordsIgnoreSymbols: Boolean = true,
        headerSkipString: String = "",
        headerSkipEnabled: Boolean = true,
        footerSkipString: String = "",
        footerSkipEnabled: Boolean = true,
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList(),
        folderPath: String? = null,
        novelUrl: String? = null,
        contentSelectors: List<ContentSelectorRule> = emptyList(),
        ignoredSelectors: List<String> = emptyList()
    ): ParseResult {
        val impactsMap = mutableMapOf<String, RuleImpact>()

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
            parseHtml(content, effectiveSelector, contentSelectors, folderPath, novelUrl, ignoredSelectors, impactsMap)
        } else {
            parsePlainText(content)
        }

        val filteredParagraphs = rawParagraphs.filterNot { (text, _) -> isNonContentText(text) }
        val (scopedParagraphs, headerImpact, footerImpact) = applyHeaderAndFooterSkipWithDiagnostics(
            filteredParagraphs,
            effectiveHeader,
            effectiveFooter,
            effectiveHeaderRules,
            effectiveFooterRules
        )

        headerImpact?.let { impactsMap[it.id] = it }
        footerImpact?.let { impactsMap[it.id] = it }

        val skipCharImpacts = mutableMapOf<String, Int>()
        val pronunCharImpacts = mutableMapOf<String, Int>()

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

                val spoken = applyRulesAndSkipsWithMetrics(
                    text = trimmedChunk,
                    pronunciationRules = effectiveRules,
                    skipWords = effectiveSkips,
                    skipWordsIgnoreSymbols = skipWordsIgnoreSymbols,
                    folderPath = folderPath,
                    novelUrl = novelUrl,
                    skipCharImpacts = skipCharImpacts,
                    pronunCharImpacts = pronunCharImpacts
                )
                if (spoken.isBlank()) continue
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

        for (skip in effectiveSkips) {
            val chars = skipCharImpacts[skip.id] ?: 0
            if (chars > 0) {
                impactsMap[skip.id] = RuleImpact(
                    id = skip.id,
                    name = skip.text,
                    ruleType = RuleType.SKIP_WORD,
                    charsFiltered = chars,
                    enabled = true,
                    details = "$chars characters removed"
                )
            }
        }

        for (rule in effectiveRules) {
            val chars = pronunCharImpacts[rule.id] ?: 0
            if (chars > 0) {
                impactsMap[rule.id] = RuleImpact(
                    id = rule.id,
                    name = "${rule.from} ➔ ${rule.to}",
                    ruleType = RuleType.PRONUNCIATION,
                    charsFiltered = chars,
                    enabled = true,
                    details = "$chars characters replaced"
                )
            }
        }

        val sortedImpacts = impactsMap.values.sortedByDescending { it.charsFiltered }
        return ParseResult(paragraphs = result, ruleImpacts = sortedImpacts)
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
        return applyHeaderAndFooterSkipWithDiagnostics(
            rawParagraphs,
            headerSkipString,
            footerSkipString,
            headerSkipRules,
            footerSkipRules
        ).first
    }

    fun applyHeaderAndFooterSkipWithDiagnostics(
        rawParagraphs: List<Pair<String, Boolean>>,
        headerSkipString: String = "",
        footerSkipString: String = "",
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList()
    ): Triple<List<Pair<String, Boolean>>, RuleImpact?, RuleImpact?> {
        var workingList = rawParagraphs
        var headerImpact: RuleImpact? = null
        var footerImpact: RuleImpact? = null

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
            var matchedRule: SkipRule? = null
            for ((idx, pair) in workingList.withIndex()) {
                val (text, _) = pair
                var minSub = -1
                var ruleForMin: SkipRule? = null
                for (rule in activeHeaderRules) {
                    try {
                        val sub = if (rule.exactMatch) {
                            val pattern = Pattern.compile("\\b" + Pattern.quote(rule.text) + "\\b", Pattern.CASE_INSENSITIVE)
                            val matcher = pattern.matcher(text)
                            if (matcher.find()) matcher.start() else -1
                        } else {
                            text.indexOf(rule.text, ignoreCase = true)
                        }
                        if (sub != -1 && (minSub == -1 || sub < minSub)) {
                            minSub = sub
                            ruleForMin = rule
                        }
                    } catch (e: Exception) {
                        AppLogger.w("ChapterParser", "Error evaluating header skip rule '${rule.text}': ${e.message}", e)
                    }
                }
                if (minSub != -1) {
                    matchIndex = idx
                    bestSubIndex = minSub
                    matchedRule = ruleForMin
                    break
                }
            }

            if (matchIndex != -1 && matchedRule != null) {
                var charsCut = 0
                for (i in 0 until matchIndex) {
                    charsCut += workingList[i].first.length
                }
                if (bestSubIndex > 0) {
                    charsCut += bestSubIndex
                }

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

                headerImpact = RuleImpact(
                    id = matchedRule.id,
                    name = matchedRule.text,
                    ruleType = RuleType.HEADER_SKIP,
                    charsFiltered = charsCut,
                    enabled = true,
                    details = "$charsCut header characters skipped"
                )
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
            var matchedRule: SkipRule? = null
            for ((idx, pair) in workingList.withIndex()) {
                val (text, _) = pair
                var minSub = -1
                var ruleForMin: SkipRule? = null
                for (rule in activeFooterRules) {
                    try {
                        val sub = if (rule.exactMatch) {
                            val pattern = Pattern.compile("\\b" + Pattern.quote(rule.text) + "\\b", Pattern.CASE_INSENSITIVE)
                            val matcher = pattern.matcher(text)
                            if (matcher.find()) matcher.start() else -1
                        } else {
                            text.indexOf(rule.text, ignoreCase = true)
                        }
                        if (sub != -1 && (minSub == -1 || sub < minSub)) {
                            minSub = sub
                            ruleForMin = rule
                        }
                    } catch (e: Exception) {
                        AppLogger.w("ChapterParser", "Error evaluating footer skip rule '${rule.text}': ${e.message}", e)
                    }
                }
                if (minSub != -1) {
                    matchIndex = idx
                    bestSubIndex = minSub
                    matchedRule = ruleForMin
                    break
                }
            }

            if (matchIndex != -1 && matchedRule != null) {
                var charsCut = 0
                val (matchText, isHeading) = workingList[matchIndex]
                val trimmedLast = if (bestSubIndex >= 0) {
                    charsCut += (matchText.length - bestSubIndex)
                    matchText.substring(0, bestSubIndex).trim()
                } else {
                    matchText
                }
                for (i in (matchIndex + 1) until workingList.size) {
                    charsCut += workingList[i].first.length
                }

                val prefix = workingList.subList(0, matchIndex)
                workingList = if (trimmedLast.length >= 2) {
                    prefix + listOf(trimmedLast to isHeading)
                } else {
                    prefix
                }

                footerImpact = RuleImpact(
                    id = matchedRule.id,
                    name = matchedRule.text,
                    ruleType = RuleType.FOOTER_SKIP,
                    charsFiltered = charsCut,
                    enabled = true,
                    details = "$charsCut footer characters skipped"
                )
            }
        }

        return Triple(workingList, headerImpact, footerImpact)
    }

    private fun parseHtml(
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
                com.novelarrow.reader.data.model.PronunciationScopeType.URL_PATTERN -> novelUrl?.contains(rule.scopePattern, ignoreCase = true) == true
                com.novelarrow.reader.data.model.PronunciationScopeType.FOLDER -> folderPath?.contains(rule.scopePattern, ignoreCase = true) == true
                else -> true
            }
            if (inScope && rule.selector.isNotBlank()) {
                try {
                    val cand = doc.selectFirst(rule.selector)
                    if (cand != null && (cand.text().trim().length >= 50 || cand.select("p").size >= 2)) {
                        selectedElement = cand
                        matchedRuleId = rule.id
                        matchedSelectorName = rule.selector
                        AppLogger.d("ChapterParser", "Content container matched rule '${rule.selector}' (id=${rule.id})")
                        break
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChapterParser", "Failed evaluating content selector '${rule.selector}': ${e.message}", e)
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
                    AppLogger.d("ChapterParser", "Content container matched legacy selector '$contentSelector'")
                }
            } catch (e: Exception) {
                AppLogger.w("ChapterParser", "Failed evaluating legacy selector '$contentSelector': ${e.message}", e)
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
                        AppLogger.d("ChapterParser", "Content container fallback matched '$candidate'")
                        break
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChapterParser", "Failed candidate selector '$candidate': ${e.message}", e)
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
                    AppLogger.d("ChapterParser", "Custom ignored selector '$trimmed' removed ${matched.size} elements ($strippedChars chars)")
                    matched.forEach { it.remove() }
                } catch (e: Exception) {
                    AppLogger.w("ChapterParser", "Failed applying custom ignored selector '$trimmed': ${e.message}", e)
                }
            }
        }

        // 2b. Default ignored selectors applied only to descendants inside container
        for (selector in IGNORE_SELECTORS) {
            try {
                val matched = rootElement.select(selector).filter { it != rootElement }
                matched.forEach { it.remove() }
            } catch (e: Exception) {
                AppLogger.w("ChapterParser", "Failed applying default ignored selector '$selector': ${e.message}", e)
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
        } catch (e: Exception) {
            AppLogger.w("ChapterParser", "Error compiling scope pattern regex '$pattern' against '$target': ${e.message}", e)
            false
        }
    }

    fun applyRulesAndSkips(
        text: String,
        pronunciationRules: List<PronunciationRule>,
        skipWords: List<SkipWord>,
        folderPath: String? = null,
        novelUrl: String? = null,
        skipWordsIgnoreSymbols: Boolean = true
    ): String {
        return applyRulesAndSkipsWithMetrics(
            text = text,
            pronunciationRules = pronunciationRules,
            skipWords = skipWords,
            folderPath = folderPath,
            novelUrl = novelUrl,
            skipWordsIgnoreSymbols = skipWordsIgnoreSymbols
        )
    }

    fun applyRulesAndSkipsWithMetrics(
        text: String,
        pronunciationRules: List<PronunciationRule>,
        skipWords: List<SkipWord>,
        folderPath: String? = null,
        novelUrl: String? = null,
        skipCharImpacts: MutableMap<String, Int>? = null,
        pronunCharImpacts: MutableMap<String, Int>? = null,
        skipWordsIgnoreSymbols: Boolean = true
    ): String {
        var processed = text

        val activeRules = pronunciationRules.filter { isRuleInScope(it, folderPath, novelUrl) }
        val activeSkips = skipWords.filter { it.enabled }

        for (rule in activeRules) {
            if (rule.from.isBlank()) continue
            try {
                val escaped = Regex.escape(rule.from)
                val beforeLen = processed.length
                processed = if (rule.exactMatch) {
                    val pattern = "(?i)(?<=^|[^\\p{L}\\p{N}])${escaped}(?=[^\\p{L}\\p{N}]|$)"
                    val safeReplacement = java.util.regex.Matcher.quoteReplacement(rule.to)
                    processed.replace(Regex(pattern), safeReplacement)
                } else {
                    processed.replace(rule.from, rule.to, ignoreCase = true)
                }
                if (pronunCharImpacts != null && processed.length != beforeLen) {
                    val diff = kotlin.math.abs(processed.length - beforeLen) + rule.from.length
                    pronunCharImpacts[rule.id] = (pronunCharImpacts[rule.id] ?: 0) + diff
                }
            } catch (e: Exception) {
                AppLogger.w("ChapterParser", "Failed applying pronunciation rule '${rule.from}' ➔ '${rule.to}': ${e.message}", e)
            }
        }

        for (skip in activeSkips) {
            if (skip.text.isBlank()) continue
            try {
                val escaped = Regex.escape(skip.text)
                val beforeLen = processed.length
                processed = if (skipWordsIgnoreSymbols) {
                    if (skip.exactMatch) {
                        val pattern = "(?i)(?<=^|[^\\p{L}\\p{N}])(?:[\\p{P}\\p{S}]*)${escaped}(?:[\\p{P}\\p{S}]*)(?=[^\\p{L}\\p{N}]|$)"
                        processed.replace(Regex(pattern), "")
                    } else {
                        val pattern = "(?i)(?:[\\p{P}\\p{S}]*)${escaped}(?:[\\p{P}\\p{S}]*)"
                        processed.replace(Regex(pattern), "")
                    }
                } else {
                    if (skip.exactMatch) {
                        val pattern = "(?i)(?<=^|[^\\p{L}\\p{N}])${escaped}(?=[^\\p{L}\\p{N}]|$)"
                        processed.replace(Regex(pattern), "")
                    } else {
                        processed.replace(skip.text, "", ignoreCase = true)
                    }
                }
                val removed = (beforeLen - processed.length).coerceAtLeast(0)
                if (skipCharImpacts != null && removed > 0) {
                    skipCharImpacts[skip.id] = (skipCharImpacts[skip.id] ?: 0) + removed
                }
            } catch (e: Exception) {
                AppLogger.w("ChapterParser", "Failed applying skip word '${skip.text}': ${e.message}", e)
            }
        }

        return processed.replace(Regex("\\s+"), " ").trim()
    }

    fun parseWebParagraphsWithDiagnostics(
        rawParagraphs: List<String>,
        currentUrl: String,
        headerSkipRules: List<SkipRule> = emptyList(),
        headerSkipEnabled: Boolean = true,
        footerSkipRules: List<SkipRule> = emptyList(),
        footerSkipEnabled: Boolean = true,
        pronunciationRules: List<PronunciationRule> = emptyList(),
        pronunciationRulesEnabled: Boolean = true,
        skipWords: List<SkipWord> = emptyList(),
        skipWordsEnabled: Boolean = true,
        skipWordsIgnoreSymbols: Boolean = true,
        containerImpact: RuleImpact? = null,
        ignoredSelectorImpacts: List<RuleImpact> = emptyList()
    ): ParseResult {
        val impactsMap = mutableMapOf<String, RuleImpact>()
        containerImpact?.let { impactsMap[it.id] = it }
        for (imp in ignoredSelectorImpacts) {
            impactsMap[imp.id] = imp
        }

        val effectiveHeaderRules = if (headerSkipEnabled) headerSkipRules else emptyList()
        val effectiveFooterRules = if (footerSkipEnabled) footerSkipRules else emptyList()
        val effectiveRules = if (pronunciationRulesEnabled) pronunciationRules else emptyList()
        val effectiveSkips = if (skipWordsEnabled) skipWords else emptyList()

        val parsedPairs = rawParagraphs.map { it to false }
        val (scopedParagraphs, headerImpact, footerImpact) = applyHeaderAndFooterSkipWithDiagnostics(
            rawParagraphs = parsedPairs,
            headerSkipRules = effectiveHeaderRules,
            footerSkipRules = effectiveFooterRules
        )

        headerImpact?.let { impactsMap[it.id] = it }
        footerImpact?.let { impactsMap[it.id] = it }

        val skipCharImpacts = mutableMapOf<String, Int>()
        val pronunCharImpacts = mutableMapOf<String, Int>()

        val result = mutableListOf<Paragraph>()
        var counter = 0

        for ((text, isHeader) in scopedParagraphs) {
            val cleaned = text.trim()
            if (cleaned.length < 2 || isNonContentText(cleaned)) continue

            val spoken = applyRulesAndSkipsWithMetrics(
                text = cleaned,
                pronunciationRules = effectiveRules,
                skipWords = effectiveSkips,
                folderPath = null,
                novelUrl = currentUrl,
                skipCharImpacts = skipCharImpacts,
                pronunCharImpacts = pronunCharImpacts,
                skipWordsIgnoreSymbols = skipWordsIgnoreSymbols
            )

            if (spoken.isBlank()) continue

            result.add(
                Paragraph(
                    index = counter++,
                    rawText = cleaned,
                    spokenText = spoken,
                    isHeading = isHeader
                )
            )
        }

        for (skip in effectiveSkips) {
            val chars = skipCharImpacts[skip.id] ?: 0
            if (chars > 0) {
                impactsMap[skip.id] = RuleImpact(
                    id = skip.id,
                    name = skip.text,
                    ruleType = RuleType.SKIP_WORD,
                    charsFiltered = chars,
                    enabled = true,
                    details = "$chars characters removed"
                )
            }
        }

        for (rule in effectiveRules) {
            val chars = pronunCharImpacts[rule.id] ?: 0
            if (chars > 0) {
                impactsMap[rule.id] = RuleImpact(
                    id = rule.id,
                    name = "${rule.from} ➔ ${rule.to}",
                    ruleType = RuleType.PRONUNCIATION,
                    charsFiltered = chars,
                    enabled = true,
                    details = "$chars characters modified"
                )
            }
        }

        return ParseResult(
            paragraphs = result,
            ruleImpacts = impactsMap.values.toList().sortedByDescending { it.charsFiltered }
        )
    }

    fun countWords(text: String): Int {
        return text.trim().split(Regex("""\s+""")).count { it.isNotBlank() }
    }

    /**
     * Merges short paragraphs (<= [minWordThreshold] words) forward into the next paragraph
     * to prevent isolated, failure-prone, or stuttering synthesis requests.
     * Trailing short paragraphs at the end of a chapter merge into the preceding paragraph.
     */
    fun mergeShortParagraphs(paragraphs: List<Paragraph>, minWordThreshold: Int = 10): List<Paragraph> {
        if (paragraphs.size <= 1) return paragraphs
        val merged = mutableListOf<Paragraph>()
        var accumulator: Paragraph? = null

        for (p in paragraphs) {
            if (accumulator == null) {
                if (countWords(p.spokenText) <= minWordThreshold) {
                    accumulator = p
                } else {
                    merged.add(p)
                }
            } else {
                val combinedRaw = accumulator.rawText + "\n\n" + p.rawText
                val combinedSpoken = accumulator.spokenText + "\n\n" + p.spokenText
                val combinedHeading = accumulator.isHeading || p.isHeading
                val newPara = Paragraph(
                    index = 0,
                    rawText = combinedRaw,
                    spokenText = combinedSpoken,
                    isHeading = combinedHeading,
                    sourceIndices = accumulator.sourceIndices + p.sourceIndices
                )
                if (countWords(combinedSpoken) <= minWordThreshold) {
                    accumulator = newPara
                } else {
                    merged.add(newPara)
                    accumulator = null
                }
            }
        }

        if (accumulator != null) {
            if (merged.isNotEmpty()) {
                val last = merged.removeAt(merged.size - 1)
                merged.add(
                    Paragraph(
                        index = 0,
                        rawText = last.rawText + "\n\n" + accumulator.rawText,
                        spokenText = last.spokenText + "\n\n" + accumulator.spokenText,
                        isHeading = last.isHeading || accumulator.isHeading,
                        sourceIndices = last.sourceIndices + accumulator.sourceIndices
                    )
                )
            } else {
                merged.add(accumulator)
            }
        }

        return merged.mapIndexed { idx, p -> p.copy(index = idx) }
    }
}
