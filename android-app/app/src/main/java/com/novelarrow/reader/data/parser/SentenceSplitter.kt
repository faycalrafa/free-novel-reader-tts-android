package com.novelarrow.reader.data.parser

import java.util.regex.Pattern

internal object SentenceSplitter {

    val SENTENCE_PATTERN: Pattern = Pattern.compile(
        "[^.!?。！？]+(?:[.!?]+[\"'”’」』）］｝»)\\]}]*(?=\\s|$)|[。！？]+[\"'”’」』）］｝»)\\]}]*)|[^.!?。！？]+$"
    )

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
}
