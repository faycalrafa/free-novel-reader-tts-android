package com.novelarrow.reader.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.repository.UserPreferencesBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class NovelSummaryBackup(
    val id: String,
    val title: String,
    val rootTreeUri: String,
    val chapterCount: Int = 0,
    val currentChapterIndex: Int = 0,
    val isWebNovel: Boolean = false,
    val webUrl: String? = null
)

@Serializable
data class LibraryBackup(
    val schemaVersion: Int = 1,
    val loadedFolders: List<String> = emptyList(),
    val novels: List<NovelSummaryBackup> = emptyList(),
    val lastActiveNovelId: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class ExistingConfigsReport(
    val hasConfigs: Boolean = false,
    val preferencesFiles: List<File> = emptyList(),
    val libraryFiles: List<File> = emptyList(),
    val progressFiles: List<File> = emptyList(),
    val loadedFolderPaths: List<String> = emptyList(),
    val preferencesBackup: UserPreferencesBackup? = null,
    val libraryBackup: LibraryBackup? = null,
    val detectedNovelsCount: Int = 0
) {
    val summaryText: String
        get() {
            val parts = mutableListOf<String>()
            if (preferencesBackup != null) {
                parts.add("Custom Settings & Voice Options")
            }
            if (detectedNovelsCount > 0) {
                parts.add("$detectedNovelsCount Novel Playlists")
            } else if (loadedFolderPaths.isNotEmpty()) {
                parts.add("${loadedFolderPaths.size} Loaded Folders")
            }
            if (progressFiles.isNotEmpty()) {
                parts.add("${progressFiles.size} Reading Progress Records")
            }
            return if (parts.isEmpty()) "Saved configuration files" else parts.joinToString(", ")
        }
}

object BackupRestoreManager {

    private const val TAG = "BackupRestoreMgr"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun getNovelReaderRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            roots.add(File(extDir, "novelreader"))
            roots.add(File(extDir, "NovelReader"))
        }

        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null) {
                roots.add(File(publicDownloads, "novelreader"))
                roots.add(File(publicDownloads, "NovelReader"))
            }

            val alt1 = File("/storage/emulated/0/Download")
            if (alt1.exists()) {
                roots.add(File(alt1, "novelreader"))
                roots.add(File(alt1, "NovelReader"))
            }
            val alt2 = File("/storage/emulated/0/Downloads")
            if (alt2.exists()) {
                roots.add(File(alt2, "novelreader"))
                roots.add(File(alt2, "NovelReader"))
            }
            val sdcard = Environment.getExternalStorageDirectory()
            if (sdcard != null && sdcard.exists()) {
                roots.add(File(sdcard, "novelreader"))
                roots.add(File(sdcard, "NovelReader"))
            }
            val altDirect = File("/storage/emulated/0/NovelReader")
            if (altDirect.exists()) {
                roots.add(altDirect)
            }
            val altDirectLower = File("/storage/emulated/0/novelreader")
            if (altDirectLower.exists()) {
                roots.add(altDirectLower)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error enumerating external roots: ${e.message}")
        }

        roots.add(File(context.filesDir, "novelreader"))
        roots.add(File(context.filesDir, "NovelReader"))
        return roots.distinctBy { it.absolutePath }
    }

    suspend fun saveLibraryBackup(
        context: Context,
        novels: List<Novel>,
        lastActiveNovelId: String?
    ) = withContext(Dispatchers.IO) {
        try {
            val loadedFolders = novels.mapNotNull { novel ->
                val uri = novel.rootTreeUri
                when {
                    uri.startsWith("file://") -> uri.removePrefix("file://")
                    uri.startsWith("/") -> uri
                    else -> null
                }
            }.distinct()

            val novelSummaries = novels.map {
                NovelSummaryBackup(
                    id = it.id,
                    title = it.title,
                    rootTreeUri = it.rootTreeUri,
                    chapterCount = it.chapterCount,
                    currentChapterIndex = it.currentChapterIndex,
                    isWebNovel = it.isWebNovel,
                    webUrl = it.webUrl
                )
            }

            val backup = LibraryBackup(
                schemaVersion = 1,
                loadedFolders = loadedFolders,
                novels = novelSummaries,
                lastActiveNovelId = lastActiveNovelId,
                lastUpdated = System.currentTimeMillis()
            )

            val jsonText = json.encodeToString(backup)
            val roots = getNovelReaderRoots(context)
            for (root in roots) {
                try {
                    if (!root.exists()) {
                        root.mkdirs()
                    }
                    if (root.exists() && root.isDirectory) {
                        File(root, ".novelreader_library.json").writeText(jsonText, Charsets.UTF_8)
                        File(root, ".novelreader_folders.json").writeText(jsonText, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Cannot write library backup to ${root.absolutePath}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save library backup: ${e.message}")
        }
    }

    suspend fun detectExistingConfigs(context: Context): ExistingConfigsReport = withContext(Dispatchers.IO) {
        val prefFiles = mutableListOf<File>()
        val libFiles = mutableListOf<File>()
        val progFiles = mutableListOf<File>()
        val folderPaths = mutableSetOf<String>()

        var latestPrefBackup: UserPreferencesBackup? = null
        var latestLibBackup: LibraryBackup? = null

        val roots = getNovelReaderRoots(context)
        val candidatePrefNames = listOf(".novelreader_preferences.json", ".preferences.json")
        val candidateLibNames = listOf(".novelreader_library.json", ".novelreader_folders.json")

        for (root in roots) {
            if (!root.exists() || !root.isDirectory) continue

            // 1. Check preference files
            for (pName in candidatePrefNames) {
                val f = File(root, pName)
                if (f.exists() && f.isFile && f.length() > 0L) {
                    prefFiles.add(f)
                    if (latestPrefBackup == null) {
                        try {
                            val text = f.readText(Charsets.UTF_8)
                            if (text.isNotBlank()) {
                                latestPrefBackup = json.decodeFromString<UserPreferencesBackup>(text)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing pref file ${f.absolutePath}: ${e.message}")
                        }
                    }
                }
            }

            // 2. Check library files
            for (lName in candidateLibNames) {
                val f = File(root, lName)
                if (f.exists() && f.isFile && f.length() > 0L) {
                    libFiles.add(f)
                    if (latestLibBackup == null) {
                        try {
                            val text = f.readText(Charsets.UTF_8)
                            if (text.isNotBlank()) {
                                latestLibBackup = json.decodeFromString<LibraryBackup>(text)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing library file ${f.absolutePath}: ${e.message}")
                        }
                    }
                }
            }

            // 3. Check direct subdirectories for .progress.json
            val subdirs = root.listFiles { it.isDirectory } ?: emptyArray()
            for (sub in subdirs) {
                folderPaths.add(sub.absolutePath)
                val pFile = File(sub, ".progress.json")
                if (pFile.exists() && pFile.isFile && pFile.length() > 0L) {
                    progFiles.add(pFile)
                }
            }

            // Root itself could hold progress if novel was stored in root
            val rootProgress = File(root, ".progress.json")
            if (rootProgress.exists() && rootProgress.isFile && rootProgress.length() > 0L) {
                progFiles.add(rootProgress)
                folderPaths.add(root.absolutePath)
            }
        }

        // Incorporate loaded folders from the backup
        latestLibBackup?.loadedFolders?.forEach { fPath ->
            folderPaths.add(fPath)
            val dir = File(fPath)
            if (dir.exists() && dir.isDirectory) {
                val pFile = File(dir, ".progress.json")
                if (pFile.exists() && pFile.isFile && pFile.length() > 0L && !progFiles.contains(pFile)) {
                    progFiles.add(pFile)
                }
            }
        }

        val totalNovels = latestLibBackup?.novels?.size ?: folderPaths.size
        val hasConfigs = prefFiles.isNotEmpty() || libFiles.isNotEmpty() || progFiles.isNotEmpty()

        ExistingConfigsReport(
            hasConfigs = hasConfigs,
            preferencesFiles = prefFiles.distinctBy { it.absolutePath },
            libraryFiles = libFiles.distinctBy { it.absolutePath },
            progressFiles = progFiles.distinctBy { it.absolutePath },
            loadedFolderPaths = folderPaths.toList(),
            preferencesBackup = latestPrefBackup,
            libraryBackup = latestLibBackup,
            detectedNovelsCount = totalNovels
        )
    }

    suspend fun deleteAllHiddenConfigs(
        context: Context,
        report: ExistingConfigsReport
    ): Boolean = withContext(Dispatchers.IO) {
        var allDeleted = true
        try {
            // 1. Delete preference files
            for (f in report.preferencesFiles) {
                try {
                    if (f.exists()) {
                        val deleted = f.delete()
                        Log.i(TAG, "Deleted pref file: ${f.absolutePath} (result=$deleted)")
                    }
                } catch (e: Exception) {
                    allDeleted = false
                    Log.w(TAG, "Failed to delete ${f.absolutePath}: ${e.message}")
                }
            }

            // 2. Delete library backup files
            for (f in report.libraryFiles) {
                try {
                    if (f.exists()) {
                        val deleted = f.delete()
                        Log.i(TAG, "Deleted library file: ${f.absolutePath} (result=$deleted)")
                    }
                } catch (e: Exception) {
                    allDeleted = false
                    Log.w(TAG, "Failed to delete ${f.absolutePath}: ${e.message}")
                }
            }

            // 3. Delete progress files
            for (f in report.progressFiles) {
                try {
                    if (f.exists()) {
                        val deleted = f.delete()
                        Log.i(TAG, "Deleted progress file: ${f.absolutePath} (result=$deleted)")
                    }
                } catch (e: Exception) {
                    allDeleted = false
                    Log.w(TAG, "Failed to delete ${f.absolutePath}: ${e.message}")
                }
            }

            // 4. Also sweep all detected folders for any remaining .progress.json or .novelreader_* files
            for (folderPath in report.loadedFolderPaths) {
                try {
                    val dir = File(folderPath)
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.filter {
                            it.name.startsWith(".novelreader") ||
                            it.name == ".progress.json" ||
                            it.name == ".preferences.json"
                        }?.forEach {
                            try {
                                it.delete()
                                Log.i(TAG, "Cleaned hidden file in folder: ${it.absolutePath}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed cleaning hidden file ${it.absolutePath}: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error listing files in folder $folderPath: ${e.message}", e)
                }
            }

            // 5. Sweep roots
            for (root in getNovelReaderRoots(context)) {
                try {
                    if (root.exists() && root.isDirectory) {
                        root.listFiles()?.filter {
                            it.name.startsWith(".novelreader") ||
                            it.name == ".progress.json" ||
                            it.name == ".preferences.json"
                        }?.forEach {
                            try {
                                it.delete()
                                Log.i(TAG, "Cleaned hidden file in root: ${it.absolutePath}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed cleaning hidden file in root ${it.absolutePath}: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error sweeping root ${root.absolutePath}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            allDeleted = false
            Log.e(TAG, "Error deleting hidden configs: ${e.message}", e)
        }
        allDeleted
    }

    suspend fun restoreAllData(
        context: Context,
        report: ExistingConfigsReport,
        settingsRepository: SettingsRepository,
        libraryRepository: LibraryRepository
    ) = withContext(Dispatchers.IO) {
        // 1. Restore preferences
        report.preferencesBackup?.let { backup ->
            settingsRepository.applyRestoredBackup(backup)
        }

        // 2. Restore library folders
        val foldersToScan = report.loadedFolderPaths.map { File(it) }.filter { it.exists() && it.isDirectory }
        if (foldersToScan.isNotEmpty()) {
            for (folder in foldersToScan) {
                try {
                    libraryRepository.scanRootDirectory(Uri.fromFile(folder))
                } catch (e: Exception) {
                    Log.w(TAG, "Error importing folder ${folder.absolutePath}: ${e.message}")
                }
            }
        } else {
            // Standard scan
            libraryRepository.scanDownloadedWebNovels()
        }

        // 3. Re-save backup to ensure sync
        val novels = libraryRepository.getNovels()
        val lastActive = report.libraryBackup?.lastActiveNovelId ?: libraryRepository.getLastActiveNovelId()
        saveLibraryBackup(context, novels, lastActive)
        settingsRepository.saveBackupToNovelReaderRoots()
    }
}
