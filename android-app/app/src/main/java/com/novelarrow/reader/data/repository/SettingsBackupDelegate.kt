package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.util.AppLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal object SettingsBackupDelegate {

    private const val TAG = "SettingsBackupDelegate"

    private fun getNovelReaderRoots(context: Context): List<File> {
        return com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)
    }

    fun saveBackupToNovelReaderRoots(context: Context, backup: UserPreferencesBackup, json: Json) {
        try {
            val jsonText = json.encodeToString(backup)
            val roots = getNovelReaderRoots(context)
            for (root in roots) {
                try {
                    if (!root.exists()) {
                        root.mkdirs()
                    }
                    if (root.exists() && root.isDirectory) {
                        File(root, ".novelreader_preferences.json").writeText(jsonText, Charsets.UTF_8)
                        File(root, ".preferences.json").writeText(jsonText, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    AppLogger.d(TAG, "Cannot write backup to ${root.absolutePath}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to backup preferences: ${e.message}", e)
        }
    }

    fun restoreBackupFromNovelReaderRoots(context: Context, json: Json): UserPreferencesBackup? {
        val candidateFileNames = listOf(".novelreader_preferences.json", ".preferences.json")
        for (root in getNovelReaderRoots(context)) {
            if (!root.exists() || !root.isDirectory) continue
            for (fName in candidateFileNames) {
                val f = File(root, fName)
                if (f.exists() && f.isFile && f.length() > 0L) {
                    try {
                        val text = f.readText(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            val backup = json.decodeFromString<UserPreferencesBackup>(text)
                            AppLogger.i(TAG, "Loaded preferences from ${f.absolutePath} (theme=${backup.readerSettings.theme}, rate=${backup.sharedTts.speechRate})")
                            return backup
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to parse backup from ${f.absolutePath}: ${e.message}", e)
                    }
                }
            }
        }
        return null
    }
}
