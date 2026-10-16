package com.novelarrow.reader.data.export

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Handles fusing multiple chapters into a single file (.html, .txt, .md),
 * zipping novel directories, and dispatching shares.
 */
object FileFusionManager {

    private const val TAG = "FileFusionManager"
    private const val ONE_MB = 1024L * 1024L

    enum class FusionType {
        HTML,
        TXT,
        MD,
        MIXED
    }

    fun detectFusionType(chapters: List<Chapter>): FusionType {
        if (chapters.isEmpty()) return FusionType.TXT
        val isAllHtml = chapters.all {
            val uri = it.fileUri.lowercase()
            val title = it.title.lowercase()
            uri.endsWith(".html") || uri.endsWith(".htm") || uri.endsWith(".xhtml") ||
            title.endsWith(".html") || title.endsWith(".htm") || title.endsWith(".xhtml")
        }
        if (isAllHtml) return FusionType.HTML

        val isAllMd = chapters.all {
            val uri = it.fileUri.lowercase()
            val title = it.title.lowercase()
            uri.endsWith(".md") || uri.endsWith(".markdown") ||
            title.endsWith(".md") || title.endsWith(".markdown")
        }
        if (isAllMd) return FusionType.MD

        val isAllTxt = chapters.all {
            val uri = it.fileUri.lowercase()
            val title = it.title.lowercase()
            uri.endsWith(".txt") || uri.endsWith(".text") ||
            title.endsWith(".txt") || title.endsWith(".text")
        }
        if (isAllTxt) return FusionType.TXT

        return FusionType.MIXED
    }

    suspend fun fuseChapters(
        context: Context,
        novel: Novel,
        chapters: List<Chapter>,
        libraryRepository: LibraryRepository,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val fusionType = detectFusionType(chapters)
        val safeTitle = sanitizeFilename(novel.title)
        val ext = when (fusionType) {
            FusionType.HTML, FusionType.MIXED -> "html"
            FusionType.TXT -> "txt"
            FusionType.MD -> "md"
        }

        val tempDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(tempDir, "${safeTitle}_fused.$ext")

        AppLogger.i(TAG, "Starting fusion of ${chapters.size} chapters into ${targetFile.name} (type: $fusionType)")

        FileOutputStream(targetFile).bufferedWriter(Charsets.UTF_8).use { writer ->
            when (fusionType) {
                FusionType.HTML, FusionType.MIXED -> {
                    writer.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                    writer.write("<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                    writer.write("<title>${escapeHtml(novel.title)}</title>\n")
                    writer.write("<style>\n")
                    writer.write("body { font-family: system-ui, -apple-system, sans-serif; line-height: 1.6; margin: 2rem auto; max-width: 800px; padding: 0 1rem; color: #222; background: #faf9f6; }\n")
                    writer.write(".novel-title { text-align: center; margin-bottom: 2.5rem; border-bottom: 2px solid #ddd; padding-bottom: 1rem; }\n")
                    writer.write(".chapter-section { margin-bottom: 4rem; page-break-after: always; }\n")
                    writer.write(".chapter-heading { color: #8b4513; border-bottom: 1px solid #e0d0b0; padding-bottom: 0.4rem; }\n")
                    writer.write("p { margin: 0.8rem 0; text-align: justify; }\n")
                    writer.write("</style>\n</head>\n<body>\n")
                    writer.write("<h1 class=\"novel-title\">${escapeHtml(novel.title)}</h1>\n")

                    chapters.forEachIndexed { index, chapter ->
                        onProgress?.invoke(index + 1, chapters.size)
                        val content = try {
                            libraryRepository.readChapterContent(chapter)
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Could not read chapter: ${chapter.title}", e)
                            ""
                        }

                        val bodyHtml = if (content.contains("<html", ignoreCase = true) || content.contains("<body", ignoreCase = true)) {
                            try {
                                Jsoup.parse(content).body().html()
                            } catch (_: Exception) {
                                escapeHtml(content).replace("\n", "<br>\n")
                            }
                        } else {
                            content.split("\n\n").joinToString("\n") { "<p>${escapeHtml(it.trim())}</p>" }
                        }

                        writer.write("<section class=\"chapter-section\" id=\"chap_${index + 1}\">\n")
                        writer.write("<h2 class=\"chapter-heading\">${escapeHtml(chapter.title)}</h2>\n")
                        writer.write("<div class=\"chapter-body\">\n$bodyHtml\n</div>\n")
                        writer.write("</section>\n")
                    }

                    writer.write("</body>\n</html>")
                }

                FusionType.TXT -> {
                    writer.write("====================================================\n")
                    writer.write("   ${novel.title.uppercase()}\n")
                    writer.write("====================================================\n\n")

                    chapters.forEachIndexed { index, chapter ->
                        onProgress?.invoke(index + 1, chapters.size)
                        val rawContent = try {
                            libraryRepository.readChapterContent(chapter)
                        } catch (e: Exception) {
                            ""
                        }
                        val plainText = if (rawContent.contains("<") && rawContent.contains(">")) {
                            Jsoup.parse(rawContent).text()
                        } else rawContent

                        writer.write("--- CHAPTER ${index + 1}: ${chapter.title} ---\n\n")
                        writer.write(plainText.trim())
                        writer.write("\n\n\n")
                    }
                }

                FusionType.MD -> {
                    writer.write("# ${novel.title}\n\n")

                    chapters.forEachIndexed { index, chapter ->
                        onProgress?.invoke(index + 1, chapters.size)
                        val rawContent = try {
                            libraryRepository.readChapterContent(chapter)
                        } catch (e: Exception) {
                            ""
                        }
                        val textContent = if (rawContent.contains("<") && rawContent.contains(">")) {
                            Jsoup.parse(rawContent).text()
                        } else rawContent

                        writer.write("## ${chapter.title}\n\n")
                        writer.write(textContent.trim())
                        writer.write("\n\n---\n\n")
                    }
                }
            }
        }

        AppLogger.i(TAG, "Fusion complete. Output size: ${targetFile.length()} bytes")
        saveToDownloadsIfLarge(context, targetFile)
    }

    suspend fun zipNovelFolder(
        context: Context,
        novel: Novel,
        chapters: List<Chapter>,
        libraryRepository: LibraryRepository,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(novel.title)
        val tempDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(tempDir, "${safeTitle}_archive.zip")

        AppLogger.i(TAG, "Starting zip of ${chapters.size} chapters for '${novel.title}'")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            chapters.forEachIndexed { index, chapter ->
                onProgress?.invoke(index + 1, chapters.size)
                val ext = getExtension(chapter.fileUri)
                val entryName = "${String.format("%03d", index + 1)}_${sanitizeFilename(chapter.title)}.$ext"
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)

                try {
                    val rawFile = File(chapter.fileUri)
                    if (rawFile.exists() && rawFile.canRead()) {
                        rawFile.inputStream().use { input -> input.copyTo(zos) }
                    } else {
                        val content = libraryRepository.readChapterContent(chapter)
                        zos.write(content.toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to write zip entry: $entryName", e)
                }
                zos.closeEntry()
            }
        }

        AppLogger.i(TAG, "Zip complete: ${zipFile.length()} bytes")
        saveToDownloadsIfLarge(context, zipFile)
    }

    fun saveToDownloadsIfLarge(context: Context, file: File): File {
        if (file.length() < ONE_MB) return file

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDownloadsDir = File(downloadsDir, "BookReader").apply { mkdirs() }
            val destFile = File(appDownloadsDir, file.name)
            file.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            AppLogger.i(TAG, "Large file (>1MB) saved to public Downloads: ${destFile.absolutePath}")
            destFile
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed saving to public Downloads: ${e.message}", e)
            file
        }
    }

    fun createShareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        val meaningful = cleaned.filter { it.isLetterOrDigit() }
        return if (meaningful.isBlank()) "bookreader_novel" else cleaned
    }

    internal fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    internal fun getExtension(path: String): String {
        val idx = path.lastIndexOf('.')
        return if (idx >= 0 && idx < path.length - 1) path.substring(idx + 1) else "txt"
    }
}
