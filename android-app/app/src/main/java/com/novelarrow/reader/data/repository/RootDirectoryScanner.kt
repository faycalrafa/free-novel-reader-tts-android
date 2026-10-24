package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.FileSchemeUtils
import com.novelarrow.reader.data.util.NaturalOrderComparator
import com.novelarrow.reader.data.util.ProgressDebouncer
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

internal class RootDirectoryScanner(
    private val context: Context,
    private val roomDelegate: LibraryRoomDelegate,
    private val json: Json
) {

    private val TAG = "RootDirectoryScanner"

    suspend fun scanRootDirectoryInternal(
        treeUriStr: String,
        safTreeUri: Uri?,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit
    ): List<Novel> = withContext(Dispatchers.IO) {
        var rootName = "Imported Folder"
        val debouncer = ProgressDebouncer(intervalMs = 80L, onProgress = onProgress)

        debouncer.report(0, 0, "Inspecting folder...")

        val subdirFilesMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()
        val safSubdirDocIds = mutableMapOf<String, String>()
        var rootDocId: String? = null

        val isDirectLocal = FileSchemeUtils.isLocalFilePath(treeUriStr)
        if (isDirectLocal) {
            val dir = FileSchemeUtils.toLocalFile(treeUriStr)
            if (dir != null && dir.exists() && dir.isDirectory) {
                rootName = dir.name
                val (subdirs, loose) = SafTreeScanner.scanLocalDirectory(dir)
                subdirFilesMap.putAll(subdirs)
                looseFiles.addAll(loose)
            }
        } else {
            val resolvedUri = safTreeUri ?: try {
                Uri.parse(treeUriStr)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed parsing treeUriStr $treeUriStr: ${e.message}", e)
                null
            }
            val rootDoc = resolvedUri?.let { uri ->
                try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed resolving rootDoc for $treeUriStr", e)
                    null
                }
            }
            if (rootDoc != null) {
                rootName = rootDoc.name ?: "Imported Folder"
            }

            var scannedViaSafContract = false
            rootDocId = resolvedUri?.let { uri ->
                try {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        DocumentsContract.getDocumentId(uri)
                    } else {
                        DocumentsContract.getTreeDocumentId(uri)
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed extracting rootDocId for $treeUriStr", e)
                    null
                }
            }

            val targetDocId = rootDocId
            if (targetDocId != null && resolvedUri != null) {
                try {
                    val (subdirs, files) = SafTreeScanner.querySafChildrenFast(context, resolvedUri, targetDocId)
                    if (subdirs.isNotEmpty() || files.isNotEmpty()) {
                        scannedViaSafContract = true
                        looseFiles.addAll(files)
                        for ((subDocId, subName) in subdirs) {
                            safSubdirDocIds[subName] = subDocId
                            val subFiles = mutableListOf<ScannedFileItem>()
                            SafTreeScanner.collectSafChildrenRecursivelyFast(context, resolvedUri, subDocId, subFiles)
                            if (subFiles.isNotEmpty()) {
                                subdirFilesMap[subName] = subFiles
                            }
                        }
                    }
                } catch (e: Exception) {
                    scannedViaSafContract = false
                }
            }

            if (!scannedViaSafContract && rootDoc != null && rootDoc.exists()) {
                val (subdirs, files) = SafTreeScanner.scanDocumentFileFallback(rootDoc)
                subdirFilesMap.putAll(subdirs)
                looseFiles.addAll(files)
            }
        }

        val totalFilesCount = looseFiles.size + subdirFilesMap.values.sumOf { it.size }
        val currentDb = LibraryStateManager.dbMutex.withLock { LibraryStateManager.loadDatabaseLocked(roomDelegate) }
        var currentProgressIndex = 0
        val newNovels = mutableListOf<Novel>()
        val chaptersMap = mutableMapOf<String, List<Chapter>>()

        val rootDir = if (isDirectLocal) FileSchemeUtils.toLocalFile(treeUriStr) else null

        // 1. Process subdirectories as individual novel playlists
        for ((dirName, novelFiles) in subdirFilesMap) {
            val localNovelDir = if (!isDirectLocal) {
                ChapterImportStorageHelper.getNovelStorageDir(context, dirName)
            } else null

            // Copy to app route if source is external SAF
            val effectiveFiles: List<ScannedFileItem> = if (localNovelDir != null) {
                novelFiles.mapNotNull { item ->
                    val copied = ChapterImportStorageHelper.copyFileToNovelDir(
                        context = context,
                        sourceUriOrPath = item.uriStr,
                        targetDir = localNovelDir,
                        displayName = item.displayName,
                        sourceSizeBytes = item.sizeBytes
                    )
                    if (copied != null) {
                        ScannedFileItem(copied.absolutePath, item.displayName, copied.length())
                    } else null
                }
            } else {
                novelFiles
            }

            val subfolderPath = localNovelDir?.absolutePath ?: rootDir?.let { File(it, dirName).absolutePath }
            val effectiveRootUri = localNovelDir?.parentFile?.absolutePath ?: treeUriStr
            val folderDocId = if (localNovelDir != null) null else safSubdirDocIds[dirName]

            val existingDbNovel = currentDb.novels.firstOrNull {
                (folderDocId != null && it.folderDocId == folderDocId) ||
                (subfolderPath != null && it.subfolderPath == subfolderPath) ||
                (it.rootTreeUri == effectiveRootUri && it.title == dirName)
            }
            val novelId = existingDbNovel?.id ?: if (subfolderPath != null) {
                "folder_${Math.abs(subfolderPath.hashCode())}"
            } else if (folderDocId != null) {
                "saf_${Math.abs(folderDocId.hashCode())}"
            } else {
                UUID.randomUUID().toString()
            }
            val existingDbChapters = currentDb.chapters[novelId] ?: emptyList()

            val folderProgress = FolderProgressSync.readFolderProgress(
                context = context,
                folderUriOrPath = subfolderPath ?: treeUriStr,
                novelTitle = dirName,
                folderDocId = folderDocId,
                json = json
            )

            val chapters = NovelDeduplicator.reconcileAndSortChapters(
                novelId = novelId,
                novelTitle = dirName,
                scannedFiles = effectiveFiles,
                existingChapters = existingDbChapters,
                folderProgress = folderProgress
            ) { _, _, name ->
                currentProgressIndex++
                debouncer.report(currentProgressIndex, totalFilesCount, name)
            }

            val useFolderProgress = folderProgress != null && (existingDbNovel == null || folderProgress.lastUpdated > existingDbNovel.lastUpdated)
            val targetChapterId = if (useFolderProgress) {
                folderProgress.currentChapterId ?: existingDbNovel?.currentChapterId
            } else {
                existingDbNovel?.currentChapterId ?: folderProgress?.currentChapterId
            }
            val targetChapterTitle = if (useFolderProgress) folderProgress.currentChapterTitle.ifBlank { null } else null
            val resolvedIndex = if (targetChapterId != null) {
                val idx = chapters.indexOfFirst { it.id == targetChapterId }
                if (idx >= 0) idx else (if (useFolderProgress) folderProgress.currentChapterIndex else existingDbNovel?.currentChapterIndex ?: folderProgress?.currentChapterIndex ?: 0)
            } else if (!targetChapterTitle.isNullOrBlank()) {
                val idx = chapters.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                if (idx >= 0) idx else (folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0)
            } else {
                if (useFolderProgress) folderProgress.currentChapterIndex else existingDbNovel?.currentChapterIndex ?: folderProgress?.currentChapterIndex ?: 0
            }.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))

            val novel = Novel(
                id = novelId,
                title = existingDbNovel?.title ?: dirName,
                rootTreeUri = effectiveRootUri,
                chapterCount = chapters.size,
                completedCount = chapters.count { it.isCompleted },
                currentChapterIndex = resolvedIndex,
                subfolderPath = subfolderPath,
                folderDocId = folderDocId,
                isLooseRootFiles = false,
                currentChapterId = chapters.getOrNull(resolvedIndex)?.id
            )

            newNovels.add(novel)
            chaptersMap[novelId] = chapters
        }

        // 2. Loose files in root folder
        if (looseFiles.isNotEmpty()) {
            val novelTitle = if (subdirFilesMap.isEmpty()) rootName else "$rootName (Chapters)"
            val localNovelDir = if (!isDirectLocal) {
                ChapterImportStorageHelper.getNovelStorageDir(context, novelTitle)
            } else null

            val effectiveFiles: List<ScannedFileItem> = if (localNovelDir != null) {
                looseFiles.mapNotNull { item ->
                    val copied = ChapterImportStorageHelper.copyFileToNovelDir(
                        context = context,
                        sourceUriOrPath = item.uriStr,
                        targetDir = localNovelDir,
                        displayName = item.displayName,
                        sourceSizeBytes = item.sizeBytes
                    )
                    if (copied != null) {
                        ScannedFileItem(copied.absolutePath, item.displayName, copied.length())
                    } else null
                }
            } else {
                looseFiles
            }

            val subfolderPath = localNovelDir?.absolutePath ?: rootDir?.absolutePath
            val effectiveRootUri = localNovelDir?.absolutePath ?: treeUriStr
            val folderDocId = if (localNovelDir != null) null else rootDocId

            val existingDbNovel = currentDb.novels.firstOrNull {
                (it.isLooseRootFiles && it.rootTreeUri == effectiveRootUri) ||
                (it.rootTreeUri == effectiveRootUri && it.title == novelTitle)
            }
            val novelId = existingDbNovel?.id ?: if (subfolderPath != null) {
                "loose_${Math.abs(subfolderPath.hashCode())}"
            } else if (folderDocId != null) {
                "loose_saf_${Math.abs(folderDocId.hashCode())}"
            } else {
                UUID.randomUUID().toString()
            }
            val existingDbChapters = currentDb.chapters[novelId] ?: emptyList()

            val folderProgress = FolderProgressSync.readFolderProgress(
                context = context,
                folderUriOrPath = subfolderPath ?: treeUriStr,
                novelTitle = novelTitle,
                folderDocId = folderDocId,
                json = json
            )

            val chapters = NovelDeduplicator.reconcileAndSortChapters(
                novelId = novelId,
                novelTitle = novelTitle,
                scannedFiles = effectiveFiles,
                existingChapters = existingDbChapters,
                folderProgress = folderProgress
            ) { _, _, name ->
                currentProgressIndex++
                debouncer.report(currentProgressIndex, totalFilesCount, name)
            }

            val useFolderProgress = folderProgress != null && (existingDbNovel == null || folderProgress.lastUpdated > existingDbNovel.lastUpdated)
            val targetChapterId = if (useFolderProgress) {
                folderProgress.currentChapterId ?: existingDbNovel?.currentChapterId
            } else {
                existingDbNovel?.currentChapterId ?: folderProgress?.currentChapterId
            }
            val targetChapterTitle = if (useFolderProgress) folderProgress.currentChapterTitle.ifBlank { null } else null
            val resolvedIndex = if (targetChapterId != null) {
                val idx = chapters.indexOfFirst { it.id == targetChapterId }
                if (idx >= 0) idx else (if (useFolderProgress) folderProgress.currentChapterIndex else existingDbNovel?.currentChapterIndex ?: folderProgress?.currentChapterIndex ?: 0)
            } else if (!targetChapterTitle.isNullOrBlank()) {
                val idx = chapters.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                if (idx >= 0) idx else (folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0)
            } else {
                if (useFolderProgress) folderProgress.currentChapterIndex else existingDbNovel?.currentChapterIndex ?: folderProgress?.currentChapterIndex ?: 0
            }.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))

            val novel = Novel(
                id = novelId,
                title = existingDbNovel?.title ?: novelTitle,
                rootTreeUri = effectiveRootUri,
                chapterCount = chapters.size,
                completedCount = chapters.count { it.isCompleted },
                currentChapterIndex = resolvedIndex,
                subfolderPath = subfolderPath,
                folderDocId = folderDocId,
                isLooseRootFiles = true,
                currentChapterId = chapters.getOrNull(resolvedIndex)?.id
            )

            newNovels.add(novel)
            chaptersMap[novelId] = chapters
        }

        debouncer.report(totalFilesCount, totalFilesCount, "Saving library database...")

        val mergedNovels = LibraryStateManager.dbMutex.withLock {
            val dbBefore = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val mergedN = if (newNovels.isNotEmpty()) {
                val newIds = newNovels.map { it.id }.toSet()
                dbBefore.novels.filterNot { it.rootTreeUri == treeUriStr || newIds.contains(it.id) } + newNovels
            } else {
                dbBefore.novels
            }

            val mergedChapters = dbBefore.chapters.toMutableMap()
            for ((k, v) in chaptersMap) {
                mergedChapters[k] = v
            }

            val updatedDb = dbBefore.copy(
                novels = mergedN,
                chapters = mergedChapters,
                lastActiveNovelId = newNovels.firstOrNull()?.id ?: dbBefore.lastActiveNovelId
            )
            LibraryStateManager.saveDatabaseLocked(roomDelegate, updatedDb)
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            mergedN
        }

        mergedNovels
    }
}
