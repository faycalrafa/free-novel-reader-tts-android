package com.novelarrow.reader.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextChapterSelectorTest {

    private fun isNegativeCandidate(text: String, href: String): Boolean {
        val txt = text.trim().lowercase()
        val h = href.trim().lowercase()

        val negativePattern = Regex("""home|index|toc|contents|catalog|chapter\s*list|all\s*chapters|prev|previous|back""", RegexOption.IGNORE_CASE)
        if (negativePattern.containsMatchIn(txt)) return true
        if (h == "#" || h == "" || h.startsWith("javascript:")) return true

        val hasChapterIdentifier = Regex("""chapter|chap|ch\d+|\d+""", RegexOption.IGNORE_CASE).containsMatchIn(h)
        if (!hasChapterIdentifier) {
            if (h == "/" || Regex("""/novel/[^/]+/?${'$'}""", RegexOption.IGNORE_CASE).containsMatchIn(h)) return true
            if (Regex("""/book/[^/]+/?${'$'}""", RegexOption.IGNORE_CASE).containsMatchIn(h)) return true
        }

        return false
    }

    private fun isPositiveCandidate(text: String, rel: String): Boolean {
        val txt = text.trim().lowercase()
        val r = rel.trim().lowercase()
        if (r == "next") return true
        val positivePattern = Regex("""^(next(\s*(chapter|chap|page|part)?)?|\s*»|\s*>)\s*${'$'}""", RegexOption.IGNORE_CASE)
        return positivePattern.matches(txt) || txt.contains("next chapter") || txt.contains("next")
    }

    private fun incrementChapterUrl(pathname: String): String? {
        val chapRegex = Regex("""(chapter[-_]?|chap[-_]?|ch[-_]?|c[-_]?)(\d+)(?=(\.html?|/|${'$'}))""", RegexOption.IGNORE_CASE)
        val allChapMatches = chapRegex.findAll(pathname).toList()
        val match = if (allChapMatches.isNotEmpty()) {
            allChapMatches.last()
        } else {
            val numRegex = Regex("""(/|-|_)?(\d+)(?=(\.html?|/|${'$'}))""", RegexOption.IGNORE_CASE)
            numRegex.findAll(pathname).toList().lastOrNull()
        } ?: return null

        val prefix = match.groupValues[1]
        val digits = match.groupValues[2]
        val len = digits.length
        val nextNum = digits.toInt() + 1
        val nextStr = nextNum.toString().padStart(len, '0')
        return pathname.substring(0, match.range.first) + prefix + nextStr + pathname.substring(match.range.last + 1)
    }

    @Test
    fun testNegativeFilteringRejectsHomeAndIndex() {
        assertTrue(isNegativeCandidate("Home", "/"))
        assertTrue(isNegativeCandidate("Home / Index", "/novel/shadow-monarch/"))
        assertTrue(isNegativeCandidate("Table of Contents", "/novel/shadow-monarch/toc"))
        assertTrue(isNegativeCandidate("TOC", "/toc"))
        assertTrue(isNegativeCandidate("Previous Chapter", "/novel/ch-1"))
        assertTrue(isNegativeCandidate("Prev", "/novel/ch-1"))
        assertTrue(isNegativeCandidate("Back", "/novel/ch-1"))
        assertTrue(isNegativeCandidate("All Chapters", "/novel/shadow-monarch/chapters"))
        assertTrue(isNegativeCandidate("Chapter List", "/novel/shadow-monarch/list"))
        assertTrue(isNegativeCandidate("Catalog", "/catalog"))
        assertTrue(isNegativeCandidate("Next", "#"))
        assertTrue(isNegativeCandidate("Next", "javascript:void(0)"))
    }

    @Test
    fun testPositiveNextCandidatesAccepted() {
        assertFalse(isNegativeCandidate("Next Chapter", "/novel/ch-3"))
        assertTrue(isPositiveCandidate("Next Chapter", ""))
        assertTrue(isPositiveCandidate("Next >", ""))
        assertTrue(isPositiveCandidate("Next »", ""))
        assertTrue(isPositiveCandidate("Next", ""))
        assertTrue(isPositiveCandidate("»", ""))
        assertTrue(isPositiveCandidate(">", ""))
        assertTrue(isPositiveCandidate("Read Next Chapter", ""))
        assertTrue(isPositiveCandidate("Anything", "next"))
    }

    @Test
    fun testChapterUrlIncrementing() {
        assertEquals("/novel/chapter-13.html", incrementChapterUrl("/novel/chapter-12.html"))
        assertEquals("/book/123/chapter_05.html", incrementChapterUrl("/book/123/chapter_04.html"))
        assertEquals("/ch99/", incrementChapterUrl("/ch98/"))
        assertEquals("/novel/my-novel/c100/", incrementChapterUrl("/novel/my-novel/c99/"))
        assertEquals("/reader/chapter-1/", incrementChapterUrl("/reader/chapter-0/"))
    }
}
