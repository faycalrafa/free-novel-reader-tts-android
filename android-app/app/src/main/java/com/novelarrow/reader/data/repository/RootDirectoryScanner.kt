package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.NaturalOrderComparator
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

        var lastProgressTime = 0L
        fun reportProgress(cur: Int, total: Int, name: String) {
            val now = System.currentTimeMillis()
            if (now - lastProgressTime >= 80 || cur == total || cur <= 1) {
                lastProgressTime = now
                onProgress(cur, total, name)
            }
        }

        reportProgress(0, 0, "Inspecting folder...")

        val subdirFilesMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()

        val safSubdirDocIds = mutableMapOf<String, String>()
        var rootDocId: String? = null

        if (treeUriStr.startsWith("/") || treeUriStr.startsWith("file://")) {
            val dir = File(treeUriStr.removePrefix("file://"))
            if (dir.exists() && dir.isDirectory) {
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

        val rootDir = if (treeUriStr.startsWith("/") || treeUriStr.startsWith("file://")) {
            File(treeUriStr.removePrefix("file://"))
        } else null

        // 1. Process subdirectories as individual novel playlists
        for ((dirName, novelFiles) in subdirFilesMap) {
            val sortedFiles = novelFiles.sortedWith { a, b ->
                NaturalOrderComparator.compare(a.displayName, b.displayName)
            }

            val subfolderPath = rootDir?.let { File(it, dirName).absolutePath }
            val folderDocId = safSubdirDocIds[dirName]

            val existingDbNovel = currentDb.novels.firstOrNull {
                (folderDocId != null && it.folderDocId == folderDocId) ||
                (subfolderPath != null && it.subfolderPath == subfolderPath) ||
                (it.rootTreeUri == treeUriStr && it.title == dirName)
            }
            val novelId = existingDbNovel?.id ?: if (subfolderPath != null) {
                "folder_${Math.abs(subfolderPath.hashCode())}"
            } else if (folderDocId != null) {
                "saf_${Math.abs(folderDocId.hashCode())}"
            } else {
                UUID.randomUUID().toString()
            }
            val existingDbChapters = currentDb.chapters[novelId] ?: emptyList()
            val existingByUri = existingDbChapters.associateBy { it.fileUri }
            val existingByTitle = existingDbChapters.associateBy { it.title }

            val folderProgress = FolderProgressSync.readFolderProgress(context, subfolderPath ?: treeUriStr, dirName, folderDocId, json)
            val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
            val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

            val rawChapters = sortedFiles.mapIndexed { index, file ->
                currentProgressIndex++
                reportProgress(currentProgressIndex, totalFilesCount, "$dirName / ${file.displayName}")
                val fTitle = file.displayName.substringBeforeLast('.')
                val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                val prog = progressByTitle[fTitle] ?: progressByFile[file.displayName]
                val lastPara = maxOf(existing?.lastReadParagraphIndex ?: 0, prog?.lastReadParagraphIndex ?: 0)
                val lastOffset = maxOf(existing?.lastScrollOffset ?: 0, prog?.lastScrollOffset ?: 0)
                val isComp = (existing?.isCompleted == true) || (prog?.isCompleted == true)
                val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                Chapter(
                    id = existing?.id ?: NovelDeduplicator.generateCanonicalChapterId(novelId, file.displayName),
                    novelId = novelId,
                    title = fTitle,
                    fileUri = file.uriStr,
                    orderIndex = orderIdx,
                    sizeBytes = file.sizeBytes,
                    lastReadParagraphIndex = lastPara,
                    lastScrollOffset = lastOffset,
                    isCompleted = isComp
                )
            }
            val chapters = NovelDeduplicator.deduplicateChapters(rawChapters)
                .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

            val useFolderProgress = folderProgress != null && (existingDbNovel == null || folderProgress.lastUpdated > existingDbNovel.lastUpdated)
            val targetChapterId = if (useFolderProgress) {
                folderProgress.currentChapterId ?: existingDbNovel?.currentChapterId
            } else {
                existingDbNovel?.currentChapterId ?: folderProgress?.currentChapterId
            }
            val targetChapterTitle = if (useFolderProgress) {
                folderProgress.currentChapterTitle.ifBlank { null }
            } else {
                null
            }
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
                rootTreeUri = treeUriStr,
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
            val sortedFiles = looseFiles.sortedWith { a, b ->
                NaturalOrderComparator.compare(a.displayName, b.displayName)
            }
            val novelTitle = if (subdirFilesMap.isEmpty()) rootName else "$rootName (Chapters)"
            val subfolderPath = rootDir?.absolutePath
            val folderDocId = rootDocId

            val existingDbNovel = currentDb.novels.firstOrNull {
                (it.isLooseRootFiles && it.rootTreeUri == treeUriStr) ||
                (it.rootTreeUri == treeUriStr && it.title == novelTitle)
            }
            val novelId = existingDbNovel?.id ?: if (subfolderPath != null) {
                "loose_${Math.abs(subfolderPath.hashCode())}"
            } else if (folderDocId != null) {
                "loose_saf_${Math.abs(folderDocId.hashCode())}"
            } else {
                UUID.randomUUID().toString()
            }
            val existingDbChapters = currentDb.chapters[novelId] ?: emptyList()
            val existingByUri = existingDbChapters.associateBy { it.fileUri }
            val existingByTitle = existingDbChapters.associateBy { it.title }

            val folderProgress = FolderProgressSync.readFolderProgress(context, subfolderPath ?: treeUriStr, novelTitle, folderDocId, json)
            val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
            val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

            val rawChapters = sortedFiles.mapIndexed { index, file ->
                currentProgressIndex++
                reportProgress(currentProgressIndex, totalFilesCount, "$novelTitle / ${file.displayName}")
                val fTitle = file.displayName.substringBeforeLast('.')
                val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                val prog = progressByTitle[fTitle] ?: progressByFile[file.displayName]
                val lastPara = maxOf(existing?.lastReadParagraphIndex ?: 0, prog?.lastReadParagraphIndex ?: 0)
                val lastOffset = maxOf(existing?.lastScrollOffset ?: 0, prog?.lastScrollOffset ?: 0)
                val isComp = (existing?.isCompleted == true) || (prog?.isCompleted == true)
                val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                Chapter(
                    id = existing?.id ?: NovelDeduplicator.generateCanonicalChapterId(novelId, file.displayName),
                    novelId = novelId,
                    title = fTitle,
                    fileUri = file.uriStr,
                    orderIndex = orderIdx,
                    sizeBytes = file.sizeBytes,
                    lastReadParagraphIndex = lastPara,
                    lastScrollOffset = lastOffset,
                    isCompleted = isComp
                )
            }
            val chapters = NovelDeduplicator.deduplicateChapters(rawChapters)
                .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

            val useFolderProgress = folderProgress != null && (existingDbNovel == null || folderProgress.lastUpdated > existingDbNovel.lastUpdated)
            val targetChapterId = if (useFolderProgress) {
                folderProgress.currentChapterId ?: existingDbNovel?.currentChapterId
            } else {
                existingDbNovel?.currentChapterId ?: folderProgress?.currentChapterId
            }
            val targetChapterTitle = if (useFolderProgress) {
                folderProgress.currentChapterTitle.ifBlank { null }
            } else {
                null
            }
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
                rootTreeUri = treeUriStr,
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

        reportProgress(totalFilesCount, totalFilesCount, "Saving library database...")

        val mergedNovels = LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val mergedN = if (newNovels.isNotEmpty()) {
                val newIds = newNovels.map { it.id }.toSet()
                currentDb.novels.filterNot { it.rootTreeUri == treeUriStr || newIds.contains(it.id) } + newNovels
            } else {
                currentDb.novels
            }

            val mergedChapters = currentDb.chapters.toMutableMap()
            for ((k, v) in chaptersMap) {
                mergedChapters[k] = v
            }

            val updatedDb = currentDb.copy(
                novels = mergedN,
                chapters = mergedChapters,
                lastActiveNovelId = newNovels.firstOrNull()?.id ?: currentDb.lastActiveNovelId
            )
            LibraryStateManager.saveDatabaseLocked(roomDelegate, updatedDb)
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            mergedN
        }

        mergedNovels
    }
}
