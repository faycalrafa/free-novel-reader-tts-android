package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.PronunciationScopeType
import com.novelarrow.reader.data.model.SkipWord
import com.novelarrow.reader.util.AppLogger
import java.util.regex.Matcher

internal object PronunciationFilterEngine {

    private const val TAG = "PronunciationFilterEngine"

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
            AppLogger.w(TAG, "Error compiling scope pattern regex '$pattern' against '$target': ${e.message}", e)
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
                    val safeReplacement = Matcher.quoteReplacement(rule.to)
                    processed.replace(Regex(pattern), safeReplacement)
                } else {
                    processed.replace(rule.from, rule.to, ignoreCase = true)
                }
                if (pronunCharImpacts != null && processed.length != beforeLen) {
                    val diff = kotlin.math.abs(processed.length - beforeLen) + rule.from.length
                    pronunCharImpacts[rule.id] = (pronunCharImpacts[rule.id] ?: 0) + diff
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed applying pronunciation rule '${rule.from}' ➔ '${rule.to}': ${e.message}", e)
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
                AppLogger.w(TAG, "Failed applying skip word '${skip.text}': ${e.message}", e)
            }
        }

        return processed.replace(Regex("\\s+"), " ").trim()
    }
}
