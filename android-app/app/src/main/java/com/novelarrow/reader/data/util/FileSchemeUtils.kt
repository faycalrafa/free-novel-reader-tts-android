package com.novelarrow.reader.data.util

import android.net.Uri
import java.io.File
import java.util.Locale

object FileSchemeUtils {
    val SUPPORTED_EXTENSIONS = setOf(
        "txt", "md", "markdown", "html", "htm", "xhtml", "xml", "json", "csv", "log", "rtf", "epub", "text", "mhtml"
    )

    fun isSupportedFile(fileName: String?): Boolean {
        if (fileName == null || fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in SUPPORTED_EXTENSIONS
    }

    fun isLocalFilePath(uriOrPath: String?): Boolean {
        if (uriOrPath == null) return false
        return uriOrPath.startsWith("/") || uriOrPath.startsWith("file://")
    }

    fun toLocalFile(uriOrPath: String?): File? {
        if (uriOrPath == null) return null
        return if (isLocalFilePath(uriOrPath)) {
            File(uriOrPath.removePrefix("file://"))
        } else null
    }

    fun extractFileName(uriOrPath: String?): String {
        if (uriOrPath == null) return ""
        val local = toLocalFile(uriOrPath)
        return local?.name ?: try {
            Uri.parse(uriOrPath).lastPathSegment ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
