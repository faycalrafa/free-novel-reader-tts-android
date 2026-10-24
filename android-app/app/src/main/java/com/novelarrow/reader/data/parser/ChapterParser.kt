package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.ContentSelectorRule
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.data.model.SkipWord

data class ParseResult(
    val paragraphs: List<Paragraph>,
    val ruleImpacts: List<RuleImpact>
)

object ChapterParser {

    fun parseWithSettings(
        content: String,
        fileName: String,
        settings: ReaderSettings,
        folderPath: String? = null,
        novelUrl: String? = null
    ): List<Paragraph> {
        return parse(
            content = content,
            fileName = fileName,
            contentSelector = settings.contentSelector,
            contentSelectorEnabled = settings.contentSelectorEnabled,
            pronunciationRules = settings.pronunciationRules,
            pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
            skipWords = settings.skipWords,
            skipWordsEnabled = settings.skipWordsEnabled,
            skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols,
            headerSkipString = settings.headerSkipString,
            headerSkipEnabled = settings.headerSkipEnabled,
            footerSkipString = settings.footerSkipString,
            footerSkipEnabled = settings.footerSkipEnabled,
            headerSkipRules = settings.headerSkipRules,
            footerSkipRules = settings.footerSkipRules,
            folderPath = folderPath,
            novelUrl = novelUrl,
            contentSelectors = settings.contentSelectors,
            ignoredSelectors = settings.ignoredSelectors
        )
    }

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
            HtmlContentExtractor.parseHtml(content, effectiveSelector, contentSelectors, folderPath, novelUrl, ignoredSelectors, impactsMap)
        } else {
            HtmlContentExtractor.parsePlainText(content)
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

    fun isNonContentText(text: String): Boolean = ContentFilterRules.isNonContentText(text)

    fun containsPunctuation(text: String): Boolean = SentenceSplitter.containsPunctuation(text)

    fun splitIntoSentences(text: String): List<String> = SentenceSplitter.splitIntoSentences(text)

    fun isRuleInScope(
        rule: PronunciationRule,
        folderPath: String? = null,
        novelUrl: String? = null
    ): Boolean = PronunciationFilterEngine.isRuleInScope(rule, folderPath, novelUrl)

    fun matchesScopePattern(target: String?, pattern: String): Boolean =
        PronunciationFilterEngine.matchesScopePattern(target, pattern)

    fun applyRulesAndSkips(
        text: String,
        pronunciationRules: List<PronunciationRule>,
        skipWords: List<SkipWord>,
        folderPath: String? = null,
        novelUrl: String? = null,
        skipWordsIgnoreSymbols: Boolean = true
    ): String = PronunciationFilterEngine.applyRulesAndSkips(
        text, pronunciationRules, skipWords, folderPath, novelUrl, skipWordsIgnoreSymbols
    )

    fun applyRulesAndSkipsWithMetrics(
        text: String,
        pronunciationRules: List<PronunciationRule>,
        skipWords: List<SkipWord>,
        folderPath: String? = null,
        novelUrl: String? = null,
        skipCharImpacts: MutableMap<String, Int>? = null,
        pronunCharImpacts: MutableMap<String, Int>? = null,
        skipWordsIgnoreSymbols: Boolean = true
    ): String = PronunciationFilterEngine.applyRulesAndSkipsWithMetrics(
        text, pronunciationRules, skipWords, folderPath, novelUrl, skipCharImpacts, pronunCharImpacts, skipWordsIgnoreSymbols
    )

    fun applyHeaderAndFooterSkip(
        rawParagraphs: List<Pair<String, Boolean>>,
        headerSkipString: String = "",
        footerSkipString: String = "",
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList()
    ): List<Pair<String, Boolean>> = HeaderFooterTrimmer.applyHeaderAndFooterSkip(
        rawParagraphs, headerSkipString, footerSkipString, headerSkipRules, footerSkipRules
    )

    fun applyHeaderAndFooterSkipWithDiagnostics(
        rawParagraphs: List<Pair<String, Boolean>>,
        headerSkipString: String = "",
        footerSkipString: String = "",
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList()
    ): Triple<List<Pair<String, Boolean>>, RuleImpact?, RuleImpact?> =
        HeaderFooterTrimmer.applyHeaderAndFooterSkipWithDiagnostics(
            rawParagraphs, headerSkipString, footerSkipString, headerSkipRules, footerSkipRules
        )

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
}
