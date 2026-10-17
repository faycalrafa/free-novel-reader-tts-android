package com.novelarrow.reader.data.browser

/**
 * Utility for verifying dynamic DOM content transitions and preventing
 * duplicate chapter captures during web novel multi-chapter downloads.
 */
object DomContentVerifier {

    /**
     * Extracts a normalized body sample from the first few paragraphs.
     * Uses default count of 3 paragraphs joined as a single string.
     */
    fun createSample(paragraphs: List<String>, count: Int = 3): String {
        return paragraphs.take(count).joinToString().trim()
    }

    /**
     * Creates a robust structural fingerprint of the chapter content using paragraph count,
     * start, middle, and end paragraph snippets. This prevents false duplicates on websites
     * where every chapter page starts with the same header/disclaimer/title lines.
     */
    fun createChapterFingerprint(paragraphs: List<String>): String {
        if (paragraphs.isEmpty()) return ""
        if (paragraphs.size <= 3) return paragraphs.joinToString().trim()
        val mid = paragraphs.size / 2
        val pFirst = paragraphs.first().take(80).trim()
        val pMid = paragraphs[mid].take(80).trim()
        val pLast = paragraphs.last().take(80).trim()
        return "${paragraphs.size}:$pFirst::$pMid::$pLast"
    }

    /**
     * Evaluates whether DOM content has transitioned based on extracted heading and body sample
     * compared to previous chapter signatures.
     *
     * @param currentHeading The heading of the currently extracted page.
     * @param currentSample The body sample of the currently extracted page (e.g. first 3 paragraphs).
     * @param lastHeading The heading of the previously saved chapter.
     * @param lastSample The body sample of the previously saved chapter.
     * @return true if heading changed or body sample changed (indicating DOM transition).
     */
    fun hasContentTransitioned(
        currentHeading: String,
        currentSample: String,
        lastHeading: String,
        lastSample: String
    ): Boolean {
        // If there are no previous signatures (first chapter), any non-blank content is considered transitioned
        if (lastHeading.isBlank() && lastSample.isBlank()) {
            return currentHeading.isNotBlank() || currentSample.isNotBlank()
        }

        val headingChanged = currentHeading.isNotBlank() && currentHeading != lastHeading
        val bodyChanged = currentSample.isNotBlank() && currentSample != lastSample

        return headingChanged || bodyChanged
    }

    /**
     * Convenience overload accepting a list of paragraphs.
     */
    fun hasContentTransitioned(
        currentHeading: String,
        currentParagraphs: List<String>,
        lastHeading: String,
        lastSample: String
    ): Boolean {
        val currentSample = createSample(currentParagraphs)
        return hasContentTransitioned(currentHeading, currentSample, lastHeading, lastSample)
    }

    /**
     * Determines whether current content is identical to the previous chapter,
     * indicating a duplicate capture condition.
     *
     * @param urlChanged When true, indicates navigation to a new URL succeeded.
     * When the URL has changed, matching headings (e.g. static novel title <h1>Novel Title</h1>)
     * will NOT flag the chapter as duplicate as long as the body content differs.
     */
    fun isDuplicateContent(
        currentHeading: String,
        currentSample: String,
        lastHeading: String,
        lastSample: String,
        urlChanged: Boolean = false
    ): Boolean {
        // Cannot be duplicate if previous signatures are empty
        if (lastHeading.isBlank() && lastSample.isBlank()) return false
        // If current content is completely blank, it's not a valid duplicate yet
        if (currentHeading.isBlank() && currentSample.isBlank()) return false

        // If URL has changed and the body sample does NOT match lastSample,
        // it cannot be a duplicate (even if the heading is generic like the novel title)
        if (urlChanged && currentSample.isNotBlank() && currentSample.trim() != lastSample.trim()) {
            return false
        }

        val headingMatches = lastHeading.isNotBlank() && currentHeading.trim() == lastHeading.trim()
        val sampleMatches = lastSample.isNotBlank() && currentSample.trim() == lastSample.trim()

        return when {
            lastHeading.isNotBlank() && lastSample.isNotBlank() -> headingMatches && sampleMatches
            lastHeading.isNotBlank() -> if (urlChanged) false else headingMatches
            lastSample.isNotBlank() -> sampleMatches
            else -> false
        }
    }

    /**
     * Convenience overload for duplicate checking with a list of paragraphs.
     */
    fun isDuplicateContent(
        currentHeading: String,
        currentParagraphs: List<String>,
        lastHeading: String,
        lastSample: String,
        urlChanged: Boolean = false
    ): Boolean {
        val currentSample = createSample(currentParagraphs)
        return isDuplicateContent(currentHeading, currentSample, lastHeading, lastSample, urlChanged)
    }
}
