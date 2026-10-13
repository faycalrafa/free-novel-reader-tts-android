package com.novelarrow.reader.data.browser

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.Locale

data class DetectedNovelInfo(
    val novelTitle: String,
    val chapterNumber: Int
)

object WebNovelDownloader {
    private const val TAG = "WebNovelDownloader"

    /**
     * Autodetects the novel title and starting chapter number from the URL and page title.
     */
    fun detectNovelInfo(url: String, pageTitle: String): DetectedNovelInfo {
        var detectedTitle = ""
        var detectedChapter = 1

        try {
            val cleanUrl = url.trim()
            val pathOnly = cleanUrl.substringAfter("://").substringAfter("/", "")
                .substringBefore("?")
                .substringBefore("#")
            val pathSegments = pathOnly.split("/").filter { it.isNotBlank() }

            // 1. Check URL path patterns
            // Common patterns:
            // - /chapter/novel-name/chapter-123
            // - /novel-book/novel-name/chapter-123
            // - /novel-name/chapter-123.html
            // - /novel/novel-name/123
            // - /book/novel-name/c123
            val chapterRegex = Regex("""(?:chapter|chap|ch|c)[-_]?(\d+)""", RegexOption.IGNORE_CASE)
            val trailingNumRegex = Regex("""(\d+)(?:\.html?)?$""", RegexOption.IGNORE_CASE)

            for (i in pathSegments.indices.reversed()) {
                val seg = pathSegments[i]
                val chMatch = chapterRegex.find(seg)
                if (chMatch != null) {
                    detectedChapter = chMatch.groupValues[1].toIntOrNull() ?: detectedChapter
                    // Look for novel slug in preceding segments
                    for (j in 0 until i) {
                        val prev = pathSegments[j]
                        if (!isGenericSlug(prev)) {
                            detectedTitle = slugToTitle(prev)
                            break
                        }
                    }
                    break
                }
                val numMatch = trailingNumRegex.find(seg)
                if (numMatch != null) {
                    detectedChapter = numMatch.groupValues[1].toIntOrNull() ?: detectedChapter
                    for (j in 0 until i) {
                        val prev = pathSegments[j]
                        if (!isGenericSlug(prev)) {
                            detectedTitle = slugToTitle(prev)
                            break
                        }
                    }
                    break
                }
            }

            // 2. Fallback to extracting novel slug directly from path if title is still blank
            if (detectedTitle.isBlank()) {
                for (seg in pathSegments) {
                    if (!isGenericSlug(seg) && !chapterRegex.containsMatchIn(seg) && !seg.matches(Regex("""\d+.*"""))) {
                        detectedTitle = slugToTitle(seg)
                        break
                    }
                }
            }

            // 3. Fallback or refine using Page Title
            if (pageTitle.isNotBlank()) {
                // e.g. "Shadow Slave - Chapter 123 - Read Novel Online"
                // or "Chapter 123: The Awakening - Shadow Slave"
                val titlePattern = Regex("""^(.*?)\s*[-–—|:]\s*Chapter\s+(\d+)""", RegexOption.IGNORE_CASE)
                val titleMatch = titlePattern.find(pageTitle)
                if (titleMatch != null) {
                    val candidate = titleMatch.groupValues[1].trim()
                    if (candidate.isNotBlank() && candidate.length in 2..60 && !candidate.startsWith("http", ignoreCase = true)) {
                        if (detectedTitle.isBlank() || candidate.length > detectedTitle.length) {
                            detectedTitle = candidate
                        }
                    }
                    if (detectedChapter == 1) {
                        detectedChapter = titleMatch.groupValues[2].toIntOrNull() ?: 1
                    }
                } else {
                    // Try pattern: "Chapter 123 ... - Novel Name"
                    val reversePattern = Regex("""Chapter\s+(\d+).*?[-–—|]\s*(.*?)$""", RegexOption.IGNORE_CASE)
                    val revMatch = reversePattern.find(pageTitle)
                    if (revMatch != null) {
                        val candidate = revMatch.groupValues[2].trim()
                            .replace(Regex("""(?i)\s*(read novel online|read free|light novel|novelbin).*"""), "")
                            .trim()
                        if (candidate.isNotBlank() && candidate.length in 2..60) {
                            detectedTitle = candidate
                        }
                        if (detectedChapter == 1) {
                            detectedChapter = revMatch.groupValues[1].toIntOrNull() ?: 1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting novel info: ${e.message}")
        }

        if (detectedTitle.isBlank()) {
            detectedTitle = "Web Novel"
        }

        return DetectedNovelInfo(
            novelTitle = detectedTitle.trim(),
            chapterNumber = detectedChapter.coerceAtLeast(1)
        )
    }

    private fun isGenericSlug(slug: String): Boolean {
        val lower = slug.lowercase(Locale.ROOT)
        return lower in listOf(
            "novel", "novels", "novel-book", "book", "books", "read",
            "chapter", "chapters", "html", "web", "manga", "lightnovel",
            "index", "toc", "pages", "online"
        )
    }

    private fun slugToTitle(slug: String): String {
        val cleaned = slug.substringBeforeLast(".html")
            .substringBeforeLast(".htm")
            .replace(Regex("""[-_]+"""), " ")
            .trim()
        return cleaned.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
    }

    /**
     * Sanitizes novel title for filesystem usage.
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[/\\?%*:|"<>]+"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Web_Novel" }
    }

    /**
     * Returns the target folder under Downloads/NovelReader/<NovelTitle>/
     */
    fun getNovelDownloadDirectory(context: Context, novelTitle: String): File {
        val safeTitle = sanitizeFileName(novelTitle)
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val root = File(publicDownloads, "NovelReader")
        val target = File(root, safeTitle)

        try {
            if (!root.exists()) root.mkdirs()
            if (!target.exists()) target.mkdirs()
            if (target.exists() && target.canWrite()) {
                return target
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct public download mkdirs failed: ${e.message}")
        }

        return getAppFallbackDirectory(context, safeTitle)
    }

    private fun getAppFallbackDirectory(context: Context, safeTitle: String): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val root = if (external != null) File(external, "NovelReader") else File(context.filesDir, "NovelReader")
        val target = File(root, safeTitle)
        if (!target.exists()) target.mkdirs()
        return target
    }

    /**
     * Creates a formatted filename for the chapter.
     */
    fun formatChapterFileName(novelTitle: String, chapterNumber: Int): String {
        val safeTitle = sanitizeFileName(novelTitle)
        val paddedNum = chapterNumber.toString().padStart(4, '0')
        return "$safeTitle Chapter $paddedNum.html"
    }

    /**
     * Checks if a chapter file already exists on disk in the primary or fallback directory.
     */
    fun chapterFileExists(context: Context, novelTitle: String, chapterNumber: Int): Boolean {
        val fileName = formatChapterFileName(novelTitle, chapterNumber)
        val targetDir = getNovelDownloadDirectory(context, novelTitle)
        val primaryFile = File(targetDir, fileName)
        if (primaryFile.exists() && primaryFile.length() > 0L) return true

        val safeTitle = sanitizeFileName(novelTitle)
        val fallbackDir = getAppFallbackDirectory(context, safeTitle)
        val fallbackFile = File(fallbackDir, fileName)
        return fallbackFile.exists() && fallbackFile.length() > 0L
    }

    /**
     * Generates clean HTML containing only the novel chapter heading and paragraphs.
     */
    fun generateCleanChapterHtml(
        novelTitle: String,
        chapterTitle: String,
        chapterNumber: Int,
        paragraphs: List<String>,
        sourceUrl: String = ""
    ): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"en\">\n<head>\n")
        sb.append("  <meta charset=\"utf-8\">\n")
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("  <title>").append(escapeHtml(novelTitle)).append(" - Chapter ").append(chapterNumber).append("</title>\n")
        sb.append("  <style>\n")
        sb.append("    body { font-family: system-ui, -apple-system, sans-serif; line-height: 1.6; padding: 16px; max-width: 800px; margin: 0 auto; }\n")
        sb.append("    h1 { font-size: 1.4em; margin-bottom: 0.8em; }\n")
        sb.append("    p { margin-bottom: 1em; text-indent: 1.5em; }\n")
        sb.append("  </style>\n")
        sb.append("</head>\n<body>\n")
        sb.append("  <h1>").append(escapeHtml(chapterTitle.ifBlank { "$novelTitle Chapter $chapterNumber" })).append("</h1>\n")
        sb.append("  <div id=\"chapter-content\">\n")

        for (para in paragraphs) {
            val clean = para.trim()
            if (clean.isNotBlank()) {
                sb.append("    <p>").append(escapeHtml(clean)).append("</p>\n")
            }
        }

        sb.append("  </div>\n")
        sb.append("</body>\n</html>\n")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    /**
     * Saves file content to both public Downloads/NovelReader and MediaStore.
     */
    private fun writeContentToFileAndMediaStore(
        context: Context,
        novelTitle: String,
        fileName: String,
        content: String
    ): File {
        val safeTitle = sanitizeFileName(novelTitle)
        val targetDir = getNovelDownloadDirectory(context, novelTitle)
        val primaryFile = File(targetDir, fileName)

        // 1. Direct file write
        try {
            if (!primaryFile.parentFile!!.exists()) primaryFile.parentFile!!.mkdirs()
            primaryFile.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Direct file write error: ${e.message}")
        }

        // 2. Also ensure MediaStore creates the entry in Download/NovelReader/<novelTitle>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/NovelReader/$safeTitle"
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore insert error: ${e.message}")
            }
        }

        // 3. Fallback duplicate into app external dir for library scanning reliability
        try {
            val fallbackDir = getAppFallbackDirectory(context, safeTitle)
            val fallbackFile = File(fallbackDir, fileName)
            if (!fallbackFile.exists() || fallbackFile.length() == 0L) {
                fallbackFile.writeText(content, Charsets.UTF_8)
            }
        } catch (_: Exception) {}

        Log.i(TAG, "Saved $fileName to ${primaryFile.absolutePath} (${content.length} chars)")
        return primaryFile
    }

    /**
     * Saves the clean chapter content to an HTML file in the novel's directory.
     */
    fun saveChapter(
        context: Context,
        novelTitle: String,
        chapterTitle: String,
        chapterNumber: Int,
        paragraphs: List<String>,
        sourceUrl: String = ""
    ): File {
        val fileName = formatChapterFileName(novelTitle, chapterNumber)
        val html = generateCleanChapterHtml(
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            chapterNumber = chapterNumber,
            paragraphs = paragraphs,
            sourceUrl = sourceUrl
        )

        return writeContentToFileAndMediaStore(context, novelTitle, fileName, html)
    }

    /**
     * Saves frozen HTML (full webpage with inline CSS, base64 images, and floating next button).
     */
    fun saveFrozenHtml(
        context: Context,
        novelTitle: String,
        chapterTitle: String,
        chapterNumber: Int,
        frozenHtml: String,
        sourceUrl: String = ""
    ): File {
        val fileName = formatChapterFileName(novelTitle, chapterNumber)
        return writeContentToFileAndMediaStore(context, novelTitle, fileName, frozenHtml)
    }
}
