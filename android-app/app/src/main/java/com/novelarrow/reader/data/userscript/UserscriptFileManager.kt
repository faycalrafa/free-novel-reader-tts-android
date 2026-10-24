package com.novelarrow.reader.data.userscript

import android.content.Context
import android.os.Environment
import com.novelarrow.reader.util.AppLogger
import java.io.File

internal object UserscriptFileManager {

    private const val TAG = "UserscriptFileManager"
    private const val FOLDER_NAME = "BookReader_Scripts"
    private const val LEGACY_FOLDER_NAME = "NovelArrow_Scripts"

    fun sanitizeFileName(name: String): String {
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
        return clean.ifBlank { "script_" + System.currentTimeMillis() }
    }

    fun getScriptsDirectory(context: Context): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(publicDir, FOLDER_NAME)
        val legacyTarget = File(publicDir, LEGACY_FOLDER_NAME)
        if (target.exists() && target.isDirectory && target.canWrite()) {
            return target
        }
        // If legacy folder exists, migrate files or rename if possible
        if (legacyTarget.exists() && legacyTarget.isDirectory && !target.exists()) {
            try {
                if (legacyTarget.renameTo(target)) {
                    return target
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to rename legacy scripts folder: ${e.message}", e)
            }
        }
        try {
            if (target.mkdirs() || target.exists()) {
                return target
            }
        } catch (_: Exception) {}

        // Fallback to app-specific external files dir
        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val appTarget = File(appExtDir, FOLDER_NAME)
        if (!appTarget.exists()) appTarget.mkdirs()
        return appTarget
    }

    fun ensureScriptsDirectory(context: Context): File {
        val dir = getScriptsDirectory(context)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun readScriptCode(filePath: String): String {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readText(Charsets.UTF_8)
            } else ""
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read script code from $filePath: ${e.message}", e)
            ""
        }
    }

    fun deletePhysicalScriptFile(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to delete physical file $filePath: ${e.message}", e)
        }
    }
}
