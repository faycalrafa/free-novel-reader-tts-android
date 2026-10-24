package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.FileSchemeUtils
import com.novelarrow.reader.data.util.ProgressDebouncer
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

internal class LibraryRefreshScanner(
    private val context: Context,
    private val roomDelegate: LibraryRoomDelegate,
    private val json: Json
) {

    private val TAG = "LibraryRefreshScanner"

    suspend fun refreshAllLinkedFolders(
        rootScanner: RootDirectoryScanner,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        val initialDb = LibraryStateManager.dbMutex.withLock { LibraryStateManager.loadDatabaseLocked(roomDelegate) }
        val activeNovels = initialDb.novels.filter { !it.isArchived }

        val distinctRootUris = activeNovels
            .filter { !it.isWebNovel && it.rootTreeUri.isNotBlank() }
            .map { it.rootTreeUri }
            .distinct()

        AppLogger.i(TAG, "Refreshing linked folders. Found ${distinctRootUris.size} root folders to re-scan: $distinctRootUris")

        for (rootUriStr in distinctRootUris) {
            try {
                AppLogger.d(TAG, "Re-scanning root directory: $rootUriStr")
                if (rootUriStr.startsWith("content://")) {
                    val uri = Uri.parse(rootUriStr)
                    rootScanner.scanRootDirectoryInternal(rootUriStr, uri, onProgress)
                } else {
                    rootScanner.scanRootDirectoryInternal(rootUriStr, null, onProgress)
                }
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "Permission denied re-scanning root directory $rootUriStr during refresh: ${e.message}", e)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error re-scanning root directory $rootUriStr during refresh: ${e.message}", e)
            }
        }

        // Reload DB after directory scans so any newly discovered folders/novels are present
        val reloadedDb = LibraryStateManager.dbMutex.withLock { LibraryStateManager.loadDatabaseLocked(roomDelegate) }
        val refreshedActiveNovels = reloadedDb.novels.filter { !it.isArchived }
        val archivedNovels = reloadedDb.novels.filter { it.isArchived }

        // For any novels not covered by the distinct root scans (e.g. rootTreeUri is blank), do per-novel scan
        val novelsToRefresh = refreshedActiveNovels.filter { it.rootTreeUri !in distinctRootUris && !it.isWebNovel }

        if (novelsToRefresh.isEmpty()) {
            AppLogger.i(TAG, "Refresh complete across all linked root folders. Total active novels: ${refreshedActiveNovels.size}")
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryRefreshed)
            return@withContext refreshedActiveNovels
        }

        val debouncer = ProgressDebouncer(intervalMs = 80L, onProgress = onProgress)
        var totalFilesToScan = 0
        val filesPerNovel = mutableMapOf<String, List<ScannedFileItem>>()

        for (novel in novelsToRefresh) {
            val treeUriStr = novel.rootTreeUri
            if (treeUriStr.startsWith("content://")) {
                try {
                    val uri = Uri.parse(treeUriStr)
                    val files = mutableListOf<ScannedFileItem>()
                    val targetDocId = novel.folderDocId ?: try {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            DocumentsContract.getDocumentId(uri)
                        } else {
                            DocumentsContract.getTreeDocumentId(uri)
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed getting targetDocId for $uri: ${e.message}", e)
                        null
                    }

                    if (targetDocId != null) {
                        if (novel.isLooseRootFiles) {
                            val (_, loose) = SafTreeScanner.querySafChildrenFast(context, uri, targetDocId)
                            files.addAll(loose)
                        } else {
                            SafTreeScanner.collectSafChildrenRecursivelyFast(context, uri, targetDocId, files)
                        }
                    } else {
                        val rootDoc = DocumentFile.fromTreeUri(context, uri)
                        if (rootDoc != null && rootDoc.exists()) {
                            if (novel.isLooseRootFiles) {
                                for (f in rootDoc.listFiles()) {
                                    if (f.isFile && SafTreeScanner.isSupported(f.name)) {
                                        files.add(ScannedFileItem(f.uri.toString(), f.name ?: "", f.length()))
                                    }
                                }
                            } else {
                                SafTreeScanner.collectDocumentFiles(rootDoc, files)
                            }
                        }
                    }
                    filesPerNovel[novel.id] = files
                    totalFilesToScan += files.size
                } catch (e: SecurityException) {
                    AppLogger.w(TAG, "SAF permission denied scanning novel ${novel.title} (${novel.id}): ${e.message}", e)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed scanning SAF novel ${novel.title} (${novel.id}): ${e.message}", e)
                }
            } else if (FileSchemeUtils.isLocalFilePath(treeUriStr)) {
                try {
                    val targetPath = novel.subfolderPath ?: treeUriStr.removePrefix("file://")
                    val dir = File(targetPath)
                    if (dir.exists() && dir.isDirectory) {
                        val files = mutableListOf<ScannedFileItem>()
                        if (novel.isLooseRootFiles) {
                            val directFiles = dir.listFiles { f -> f.isFile && SafTreeScanner.isSupported(f.name) } ?: emptyArray()
                            for (f in directFiles) {
                                files.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
                            }
                        } else {
                            val sharesRoot = novel.subfolderPath == null && novelsToRefresh.count { it.rootTreeUri == treeUriStr } > 1
                            if (sharesRoot) {
                                val sub = File(dir, novel.title)
                                if (sub.exists() && sub.isDirectory) {
                                    SafTreeScanner.collectLocalFiles(sub, files)
                                } else {
                                    SafTreeScanner.collectLocalFiles(dir, files)
                                }
                            } else {
                                SafTreeScanner.collectLocalFiles(dir, files)
                            }
                        }
                        filesPerNovel[novel.id] = files
                        totalFilesToScan += files.size
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed scanning local novel ${novel.title} (${novel.id}): ${e.message}", e)
                }
            }
        }

        var processedFilesCount = 0
        val updatedNovels = mutableListOf<Novel>()
        val updatedChapters = reloadedDb.chapters.toMutableMap()

        for (novel in novelsToRefresh) {
            val files = filesPerNovel[novel.id]
            if (files != null && files.isNotEmpty()) {
                val existingChaps = reloadedDb.chapters[novel.id] ?: emptyList()
                val folderProgress = FolderProgressSync.readFolderProgress(
                    context = context,
                    folderUriOrPath = novel.subfolderPath ?: novel.rootTreeUri,
                    novelTitle = novel.title,
                    folderDocId = novel.folderDocId,
                    json = json
                )

                val newChaps = NovelDeduplicator.reconcileAndSortChapters(
                    novelId = novel.id,
                    novelTitle = novel.title,
                    scannedFiles = files,
                    existingChapters = existingChaps,
                    folderProgress = folderProgress
                ) { _, _, name ->
                    processedFilesCount++
                    debouncer.report(processedFilesCount, totalFilesToScan, name)
                }

                val useFolderProgress = folderProgress != null && folderProgress.lastUpdated > novel.lastUpdated
                val targetChapterId = if (useFolderProgress) {
                    folderProgress.currentChapterId ?: novel.currentChapterId
                } else {
                    novel.currentChapterId ?: folderProgress?.currentChapterId
                }
                val targetChapterTitle = if (useFolderProgress) folderProgress.currentChapterTitle.ifBlank { null } else null
                val resolvedIndex = if (targetChapterId != null) {
                    val idx = newChaps.indexOfFirst { it.id == targetChapterId }
                    if (idx >= 0) idx else (if (useFolderProgress) folderProgress.currentChapterIndex else novel.currentChapterIndex)
                } else if (!targetChapterTitle.isNullOrBlank()) {
                    val idx = newChaps.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                    if (idx >= 0) idx else novel.currentChapterIndex
                } else {
                    if (useFolderProgress) folderProgress.currentChapterIndex else novel.currentChapterIndex
                }.coerceIn(0, (newChaps.size - 1).coerceAtLeast(0))

                val completedCount = newChaps.count { it.isCompleted }
                val refreshedNovel = novel.copy(
                    chapterCount = newChaps.size,
                    completedCount = completedCount,
                    currentChapterIndex = resolvedIndex,
                    currentChapterId = newChaps.getOrNull(resolvedIndex)?.id,
                    lastUpdated = System.currentTimeMillis()
                )
                updatedNovels.add(refreshedNovel)
                updatedChapters[novel.id] = newChaps
            } else {
                updatedNovels.add(novel)
            }
        }

        val deduplicatedNovels = LibraryStateManager.dbMutex.withLock {
            val deduplicated = NovelDeduplicator.deduplicateNovels(updatedNovels + archivedNovels, updatedChapters)
            val finalDb = reloadedDb.copy(
                novels = deduplicated,
                chapters = updatedChapters
            )
            LibraryStateManager.saveDatabaseLocked(roomDelegate, finalDb)
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryRefreshed)
            deduplicated.filter { !it.isArchived }
        }
        debouncer.report(totalFilesToScan, totalFilesToScan, "Refresh complete!")
        deduplicatedNovels
    }

    suspend fun refreshEntireLibrary(
        rootScanner: RootDirectoryScanner,
        webDownloader: WebNovelDownloadHandler,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Refreshing entire library including linked folders and download directories...")
        webDownloader.scanDownloadedWebNovels()
        refreshAllLinkedFolders(rootScanner, onProgress)
    }
}
