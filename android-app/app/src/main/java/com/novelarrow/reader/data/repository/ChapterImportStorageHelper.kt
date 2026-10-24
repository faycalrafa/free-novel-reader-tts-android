package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import com.novelarrow.reader.data.util.FileSchemeUtils
import com.novelarrow.reader.util.AppLogger
import java.io.File
import java.io.FileOutputStream

internal object ChapterImportStorageHelper {
    private const val TAG = "ChapterImportStorageHelper"
    private const val IMPORTED_FOLDER_NAME = "imported_novels"

    fun getImportedNovelsRoot(context: Context): File {
        val root = File(context.filesDir, IMPORTED_FOLDER_NAME)
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    fun getNovelStorageDir(context: Context, novelTitle: String): File {
        val safeTitle = sanitizeFileName(novelTitle)
        val dir = File(getImportedNovelsRoot(context), safeTitle)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyFileToNovelDir(
        context: Context,
        sourceUriOrPath: String,
        targetDir: File,
        displayName: String,
        sourceSizeBytes: Long = 0L
    ): File? {
        val safeName = sanitizeFileName(displayName)
        val targetFile = File(targetDir, safeName)

        // If file already exists with same size, avoid redundant copy
        if (targetFile.exists() && targetFile.length() > 0L && (sourceSizeBytes <= 0L || targetFile.length() == sourceSizeBytes)) {
            return targetFile
        }

        return try {
            val localSource = FileSchemeUtils.toLocalFile(sourceUriOrPath)
            if (localSource != null && localSource.exists()) {
                localSource.copyTo(targetFile, overwrite = true)
            } else {
                val uri = Uri.parse(sourceUriOrPath)
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: throw IllegalStateException("Cannot open input stream for $sourceUriOrPath")
            }
            targetFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed copying $sourceUriOrPath to ${targetFile.absolutePath}: ${e.message}", e)
            null
        }
    }

    fun copyProgressFileIfPresent(
        context: Context,
        sourceDirUriOrPath: String,
        targetNovelDir: File
    ) {
        try {
            val localDir = FileSchemeUtils.toLocalFile(sourceDirUriOrPath)
            if (localDir != null && localDir.exists() && localDir.isDirectory) {
                val progressFile = File(localDir, ".progress.json")
                if (progressFile.exists() && progressFile.isFile) {
                    val dest = File(targetNovelDir, ".progress.json")
                    progressFile.copyTo(dest, overwrite = true)
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed copying .progress.json for $sourceDirUriOrPath: ${e.message}", e)
        }
    }

    fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim().ifBlank { "Untitled" }
    }
}
