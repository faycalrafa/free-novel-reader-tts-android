package com.novelarrow.reader.data.parser

import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.PronunciationRule
import com.novelarrow.reader.data.model.SkipWord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object ChapterParser {

    private val IGNORE_TAGS = setOf(
        "script", "style", "nav", "footer", "header", "aside",
        "button", "a", "form", "input", "textarea", "select",
        "noscript", "iframe"
    )

    private val SENTENCE_PATTERN = Pattern.compile("[^.!?。！？]+[.!?。！？]+(?=\\s|$)|[^.!?。！？]+$")

    fun parse(
        content: String,
        fileName: String,
        contentSelector: String = "",
        pronunciationRules: List<PronunciationRule> = emptyList(),
        skipWords: List<SkipWord> = emptyList()
    ): List<Paragraph> {
        val isHtml = fileName.endsWith(".html", ignoreCase = true) ||
                fileName.endsWith(".htm", ignoreCase = true) ||
                fileName.endsWith(".xhtml", ignoreCase = true) ||
                content.contains("<html", ignoreCase = true) ||
                content.contains("<p>", ignoreCase = true)

        val rawParagraphs = if (isHtml) {
            parseHtml(content, contentSelector)
        } else {
            parsePlainText(content)
        }

        val result = mutableListOf<Paragraph>()
        var counter = 0

        for ((text, isHeader) in rawParagraphs) {
            val cleaned = text.trim()
            if (cleaned.length < 2) continue

            // Sentence-level chunking for long paragraphs (over 240 chars)
            val chunks = if (cleaned.length > 240 && containsPunctuation(cleaned)) {
                splitIntoSentences(cleaned)
            } else {
                listOf(cleaned)
            }

            for (chunk in chunks) {
                val trimmedChunk = chunk.trim()
                if (trimmedChunk.length < 2) continue

                val spoken = applyRulesAndSkips(trimmedChunk, pronunciationRules, skipWords)
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

    private fun parseHtml(html: String, contentSelector: String): List<Pair<String, Boolean>> {
        val doc = Jsoup.parse(html)
        doc.select(IGNORE_TAGS.joinToString(", ")).remove()
        doc.select(".ads, .ad, .advertisement, [role=navigation]").remove()

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
            if (text.length >= 2) {
                val isHeading = el.tagName().startsWith("h")
                pairs.add(text to isHeading)
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
            if (trimmed.length >= 2) {
                val isHeading = trimmed.startsWith("#") ||
                        trimmed.startsWith("Chapter", ignoreCase = true) ||
                        trimmed.length < 50 && !trimmed.endsWith(".")
                pairs.add(trimmed to isHeading)
            }
        }

        return pairs
    }

    private fun containsPunctuation(text: String): Boolean {
        return text.any { it in ".!?。！？" }
    }

    private fun splitIntoSentences(text: String): List<String> {
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

    fun applyRulesAndSkips(
        text: String,
        pronunciationRules: List<PronunciationRule>,
        skipWords: List<SkipWord>
    ): String {
        var processed = text

        for (rule in pronunciationRules) {
            if (rule.from.isBlank()) continue
            processed = if (rule.exactMatch) {
                processed.replace(Regex("\\b${Regex.escape(rule.from)}\\b", RegexOption.IGNORE_CASE), rule.to)
            } else {
                processed.replace(rule.from, rule.to, ignoreCase = true)
            }
        }

        for (skip in skipWords) {
            if (skip.text.isBlank()) continue
            processed = if (skip.exactMatch) {
                processed.replace(Regex("\\b${Regex.escape(skip.text)}\\b", RegexOption.IGNORE_CASE), "")
            } else {
                processed.replace(skip.text, "", ignoreCase = true)
            }
        }

        return processed.replace(Regex("\\s+"), " ").trim()
    }
}
