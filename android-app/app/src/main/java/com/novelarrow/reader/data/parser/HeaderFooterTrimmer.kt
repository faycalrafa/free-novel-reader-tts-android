package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.data.model.SkipRule
import com.novelarrow.reader.util.AppLogger
import java.util.regex.Pattern

internal object HeaderFooterTrimmer {

    private const val TAG = "HeaderFooterTrimmer"

    fun applyHeaderAndFooterSkip(
        rawParagraphs: List<Pair<String, Boolean>>,
        headerSkipString: String = "",
        footerSkipString: String = "",
        headerSkipRules: List<SkipRule> = emptyList(),
        footerSkipRules: List<SkipRule> = emptyList()
    ): List<Pair<String, Boolean>> {
        return applyHeaderAndFooterSkipWithDiagnostics(
            rawParagraphs = rawParagraphs,
            headerSkipString = headerSkipString,
            footerSkipString = footerSkipString,
            headerSkipRules = headerSkipRules,
            footerSkipRules = footerSkipRules
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
                        AppLogger.w(TAG, "Error evaluating header skip rule '${rule.text}': ${e.message}", e)
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
                        AppLogger.w(TAG, "Error evaluating footer skip rule '${rule.text}': ${e.message}", e)
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
}
