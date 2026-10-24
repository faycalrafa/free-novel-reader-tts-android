package com.novelarrow.reader.data.repository

import android.content.Context
import android.os.Environment
import com.novelarrow.reader.data.backup.BackupRestoreManager
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.NaturalOrderComparator
import com.novelarrow.reader.data.util.SampleQuantumContent
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

internal class WebNovelDownloadHandler(
    private val context: Context,
    private val roomDelegate: LibraryRoomDelegate,
    private val json: Json
) {

    private val TAG = "WebNovelDownloadHandler"

    private fun collectFilesRecursively(dir: File, baseDir: File = dir): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        val children = dir.listFiles() ?: return result
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                result.addAll(collectFilesRecursively(child, baseDir))
            } else if (child.isFile && SafTreeScanner.isSupported(child.name)) {
                val relPath = child.relativeTo(baseDir).path.replace(File.separatorChar, '/')
                result.add(Pair(child, relPath))
            }
        }
        return result
    }

    suspend fun addDownloadedChapter(
        novelTitle: String,
        chapterFile: File,
        chapterTitle: String,
        chapterNumber: Int,
        sourceUrl: String,
        commitNow: Boolean = true
    ): Novel = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
            val targetNovelId = "web_novel_${safeTitle}"
            val folder = chapterFile.parentFile ?: WebNovelDownloader.getNovelDownloadDirectory(context, novelTitle)

            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val existingNovel = currentDb.novels.firstOrNull {
                it.id == targetNovelId || it.title.equals(novelTitle, ignoreCase = true)
            }
            val existingChapters = (if (existingNovel != null) currentDb.chapters[existingNovel.id] else null)
                ?: currentDb.chapters[targetNovelId] ?: emptyList()

            val updatedChapters = existingChapters.toMutableList()
            val existingIdx = updatedChapters.indexOfFirst {
                it.fileUri == chapterFile.absolutePath || it.title == chapterFile.nameWithoutExtension
            }

            val newChapter = Chapter(
                id = if (existingIdx >= 0) updatedChapters[existingIdx].id else "${targetNovelId}_${chapterFile.name}",
                novelId = targetNovelId,
                title = chapterFile.nameWithoutExtension,
                fileUri = chapterFile.absolutePath,
                orderIndex = if (existingIdx >= 0) updatedChapters[existingIdx].orderIndex else updatedChapters.size,
                sizeBytes = chapterFile.length(),
                lastReadParagraphIndex = if (existingIdx >= 0) updatedChapters[existingIdx].lastReadParagraphIndex else 0,
                lastScrollOffset = if (existingIdx >= 0) updatedChapters[existingIdx].lastScrollOffset else 0,
                isCompleted = if (existingIdx >= 0) updatedChapters[existingIdx].isCompleted else false,
                webSourceUrl = sourceUrl.ifBlank { if (existingIdx >= 0) updatedChapters[existingIdx].webSourceUrl else null }
            )

            if (existingIdx >= 0) {
                updatedChapters[existingIdx] = newChapter
            } else {
                updatedChapters.add(newChapter)
            }

            val completedCount = updatedChapters.count { it.isCompleted }
            val updatedNovel = Novel(
                id = existingNovel?.id ?: targetNovelId,
                title = novelTitle,
                rootTreeUri = folder.absolutePath,
                chapterCount = updatedChapters.size,
                completedCount = completedCount,
                currentChapterIndex = existingNovel?.currentChapterIndex ?: 0,
                lastUpdated = System.currentTimeMillis(),
                isWebNovel = false,
                webUrl = sourceUrl.ifBlank { existingNovel?.webUrl },
                subfolderPath = folder.absolutePath,
                folderDocId = null,
                isLooseRootFiles = false,
                currentChapterId = updatedChapters.getOrNull(existingNovel?.currentChapterIndex ?: 0)?.id
            )

            val updatedNovels = currentDb.novels.filter {
                it.id != updatedNovel.id && !it.title.equals(novelTitle, ignoreCase = true)
            } + updatedNovel

            val updatedChaptersMap = currentDb.chapters.toMutableMap().apply {
                put(updatedNovel.id, updatedChapters)
                if (existingNovel != null && existingNovel.id != updatedNovel.id) {
                    remove(existingNovel.id)
                }
            }

            val newDb = currentDb.copy(
                novels = updatedNovels,
                chapters = updatedChaptersMap,
                lastActiveNovelId = updatedNovel.id
            )

            if (commitNow) {
                LibraryStateManager.saveDatabaseLocked(roomDelegate, newDb)
            } else {
                LibraryStateManager.cachedDb = newDb
                val now = System.currentTimeMillis()
                if (now - LibraryStateManager.lastSaveTimeMs > 3000L) {
                    LibraryStateManager.lastSaveTimeMs = now
                    LibraryStateManager.saveDatabaseLocked(roomDelegate, newDb)
                }
            }

            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.ChapterDownloaded(novelTitle, chapterTitle))
            updatedNovel
        }
    }

    suspend fun commitBatch(novelTitle: String? = null) = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            if (novelTitle != null) {
                val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
                val targetNovelId = "web_novel_${safeTitle}"
                val folder = WebNovelDownloader.getNovelDownloadDirectory(context, novelTitle)
                val files = (folder.listFiles() ?: emptyArray())
                    .filter { it.isFile && SafTreeScanner.isSupported(it.name) }
                    .sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }

                if (files.isNotEmpty()) {
                    val existingNovel = currentDb.novels.firstOrNull {
                        it.id == targetNovelId || it.title.equals(novelTitle, ignoreCase = true)
                    }
                    val existingChapters = (if (existingNovel != null) currentDb.chapters[existingNovel.id] else null)
                        ?: currentDb.chapters[targetNovelId] ?: emptyList()
                    val existingByUri = existingChapters.associateBy { it.fileUri }
                    val existingByTitle = existingChapters.associateBy { it.title }

                    val chapters = NovelDeduplicator.deduplicateChapters(files.mapIndexed { index, file ->
                        val existing = existingByUri[file.absolutePath] ?: existingByTitle[file.nameWithoutExtension]
                        Chapter(
                            id = existing?.id ?: NovelDeduplicator.generateCanonicalChapterId(targetNovelId, file.name),
                            novelId = targetNovelId,
                            title = file.nameWithoutExtension,
                            fileUri = file.absolutePath,
                            orderIndex = index,
                            sizeBytes = file.length(),
                            lastReadParagraphIndex = existing?.lastReadParagraphIndex ?: 0,
                            lastScrollOffset = existing?.lastScrollOffset ?: 0,
                            isCompleted = existing?.isCompleted ?: false,
                            webSourceUrl = existing?.webSourceUrl
                        )
                    })

                    val updatedNovel = Novel(
                        id = existingNovel?.id ?: targetNovelId,
                        title = novelTitle,
                        rootTreeUri = folder.absolutePath,
                        chapterCount = chapters.size,
                        completedCount = chapters.count { it.isCompleted },
                        currentChapterIndex = existingNovel?.currentChapterIndex ?: 0,
                        lastUpdated = System.currentTimeMillis(),
                        isWebNovel = false,
                        webUrl = existingNovel?.webUrl,
                        subfolderPath = folder.absolutePath,
                        folderDocId = null,
                        isLooseRootFiles = false,
                        currentChapterId = chapters.getOrNull(existingNovel?.currentChapterIndex ?: 0)?.id
                    )

                    val updatedNovels = currentDb.novels.filter {
                        it.id != updatedNovel.id && !it.title.equals(novelTitle, ignoreCase = true)
                    } + updatedNovel

                    val updatedChaptersMap = currentDb.chapters.toMutableMap().apply {
                        put(updatedNovel.id, chapters)
                    }

                    LibraryStateManager.saveDatabaseLocked(
                        roomDelegate,
                        currentDb.copy(
                            novels = updatedNovels,
                            chapters = updatedChaptersMap,
                            lastActiveNovelId = updatedNovel.id
                        )
                    )
                    LibraryStateManager.notifyUpdate(LibraryUpdateEvent.BatchCommitted(novelTitle))
                    return@withContext
                }
            }
            LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb)
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.BatchCommitted(novelTitle ?: ""))
        }
    }

    suspend fun scanDownloadedWebNovels(): List<Novel> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val novelsByNormTitle = mutableMapOf<String, Novel>()
            val chaptersByNovelId = mutableMapOf<String, MutableList<Chapter>>()

            val rootCandidates = BackupRestoreManager.getNovelReaderRoots(context)
                .filter { it.exists() && it.isDirectory }
                .toMutableList()
            if (rootCandidates.isEmpty()) {
                val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (extDir != null) {
                    val fallback = File(extDir, "NovelReader")
                    fallback.mkdirs()
                    rootCandidates.add(fallback)
                }
            }

            val seenPaths = mutableSetOf<String>()

            for (root in rootCandidates) {
                val subdirs = (root.listFiles { f -> f.isDirectory } ?: emptyArray()).toMutableList()
                val directFiles = (root.listFiles { f -> f.isFile && SafTreeScanner.isSupported(f.name) } ?: emptyArray())
                if (directFiles.isNotEmpty()) {
                    subdirs.add(0, root)
                }
                for (sub in subdirs) {
                    if (sub.name.equals("archive", ignoreCase = true) || sub.name.startsWith(".")) continue
                    if (seenPaths.contains(sub.canonicalPath)) continue
                    seenPaths.add(sub.canonicalPath)

                    val filesWithDisp = collectFilesRecursively(sub, sub)
                        .sortedWith { a, b -> NaturalOrderComparator.compare(a.second, b.second) }

                    if (filesWithDisp.isEmpty()) continue

                    val dirTitle = sub.name
                    val safeTitle = WebNovelDownloader.sanitizeFileName(dirTitle)
                    val normKey = NovelDeduplicator.normalizeForMatching(dirTitle)
                    val novelId = "web_novel_${safeTitle}"

                    val existingDbNovel = currentDb.novels.firstOrNull {
                        it.id == novelId || NovelDeduplicator.normalizeForMatching(it.title) == normKey ||
                        (it.rootTreeUri.isNotBlank() && (it.rootTreeUri == sub.absolutePath || it.rootTreeUri == "file://${sub.absolutePath}"))
                    }
                    val actualNovelId = existingDbNovel?.id ?: novelId
                    val existingDbChapters = (if (existingDbNovel != null) currentDb.chapters[existingDbNovel.id] else null)
                        ?: currentDb.chapters[actualNovelId] ?: currentDb.chapters[novelId] ?: emptyList()
                    val existingByUri = existingDbChapters.associateBy { it.fileUri }
                    val existingByTitle = existingDbChapters.associateBy { it.title }

                    val folderProgress = FolderProgressSync.readFolderProgress(context, sub.absolutePath, dirTitle, null, json)
                    val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
                    val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

                    val existingNovel = novelsByNormTitle[normKey]
                    if (existingNovel == null) {
                        val rawChapters = filesWithDisp.mapIndexed { index, (file, dispName) ->
                            val fTitle = dispName.substringBeforeLast('.')
                            val existing = existingByUri[file.absolutePath] ?: existingByTitle[fTitle] ?: existingByTitle[file.nameWithoutExtension]
                            val prog = progressByTitle[fTitle] ?: progressByTitle[file.nameWithoutExtension] ?: progressByFile[file.name]
                            val lastPara = maxOf(existing?.lastReadParagraphIndex ?: 0, prog?.lastReadParagraphIndex ?: 0)
                            val lastOffset = maxOf(existing?.lastScrollOffset ?: 0, prog?.lastScrollOffset ?: 0)
                            val isComp = (existing?.isCompleted == true) || (prog?.isCompleted == true)
                            val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                            Chapter(
                                id = existing?.id ?: NovelDeduplicator.generateCanonicalChapterId(actualNovelId, file.name),
                                novelId = actualNovelId,
                                title = fTitle,
                                fileUri = file.absolutePath,
                                orderIndex = orderIdx,
                                sizeBytes = file.length(),
                                lastReadParagraphIndex = lastPara,
                                lastScrollOffset = lastOffset,
                                isCompleted = isComp,
                                webSourceUrl = existing?.webSourceUrl
                            )
                        }
                        val chapters = NovelDeduplicator.deduplicateChapters(rawChapters)
                            .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                            .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }
                            .toMutableList()

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
                            id = actualNovelId,
                            title = existingDbNovel?.title ?: dirTitle,
                            rootTreeUri = sub.absolutePath,
                            chapterCount = chapters.size,
                            completedCount = chapters.count { it.isCompleted },
                            currentChapterIndex = resolvedIndex,
                            lastUpdated = existingDbNovel?.lastUpdated ?: System.currentTimeMillis(),
                            isWebNovel = existingDbNovel?.isWebNovel ?: false,
                            webUrl = existingDbNovel?.webUrl,
                            isArchived = existingDbNovel?.isArchived ?: false,
                            subfolderPath = sub.absolutePath,
                            folderDocId = null,
                            isLooseRootFiles = false,
                            currentChapterId = chapters.getOrNull(resolvedIndex)?.id
                        )
                        novelsByNormTitle[normKey] = novel
                        chaptersByNovelId[novel.id] = chapters
                    } else {
                        val currentChapters = chaptersByNovelId[existingNovel.id] ?: mutableListOf()
                        val existingFileKeys = currentChapters.map { ch ->
                            try {
                                File(ch.fileUri).name.lowercase(Locale.ROOT)
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Error getting name for chapter ${ch.id}: ${e.message}", e)
                                ch.title.lowercase(Locale.ROOT)
                            }
                        }.toSet()
                        for ((file, dispName) in filesWithDisp) {
                            val fileKey = file.name.lowercase(Locale.ROOT)
                            if (!existingFileKeys.contains(fileKey)) {
                                val fTitle = dispName.substringBeforeLast('.')
                                val existing = existingByUri[file.absolutePath] ?: existingByTitle[fTitle] ?: existingByTitle[file.nameWithoutExtension]
                                val prog = progressByTitle[fTitle] ?: progressByTitle[file.nameWithoutExtension] ?: progressByFile[file.name]
                                val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                                val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                                val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                                currentChapters.add(
                                    Chapter(
                                        id = existing?.id ?: NovelDeduplicator.generateCanonicalChapterId(existingNovel.id, file.name),
                                        novelId = existingNovel.id,
                                        title = fTitle,
                                        fileUri = file.absolutePath,
                                        orderIndex = currentChapters.size,
                                        sizeBytes = file.length(),
                                        lastReadParagraphIndex = lastPara,
                                        lastScrollOffset = lastOffset,
                                        isCompleted = isComp,
                                        webSourceUrl = existing?.webSourceUrl
                                    )
                                )
                            }
                        }
                        val deduped = NovelDeduplicator.deduplicateChapters(currentChapters)
                            .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                            .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }
                            .toMutableList()
                        chaptersByNovelId[existingNovel.id] = deduped
                        novelsByNormTitle[normKey] = existingNovel.copy(
                            chapterCount = deduped.size,
                            completedCount = deduped.count { it.isCompleted },
                            currentChapterIndex = existingNovel.currentChapterIndex.coerceIn(0, (deduped.size - 1).coerceAtLeast(0)),
                            currentChapterId = deduped.getOrNull(existingNovel.currentChapterIndex)?.id
                        )
                    }
                }
            }

            val discoveredNovels = novelsByNormTitle.values.toList()
            if (discoveredNovels.isNotEmpty()) {
                val existingIds = discoveredNovels.map { it.id }.toSet()
                val existingNormTitles = discoveredNovels.map { NovelDeduplicator.normalizeForMatching(it.title) }.toSet()
                val preservedNovels = currentDb.novels.filterNot {
                    existingIds.contains(it.id) || existingNormTitles.contains(NovelDeduplicator.normalizeForMatching(it.title))
                }
                val mergedNovels = NovelDeduplicator.deduplicateNovels(preservedNovels + discoveredNovels, chaptersByNovelId)
                val mergedChapters = currentDb.chapters.toMutableMap().apply {
                    putAll(chaptersByNovelId)
                }
                LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb.copy(novels = mergedNovels, chapters = mergedChapters))
                mergedNovels.filter { !it.isArchived }
            } else {
                val deduplicated = NovelDeduplicator.deduplicateNovels(currentDb.novels, currentDb.chapters)
                if (deduplicated.size != currentDb.novels.size) {
                    LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb.copy(novels = deduplicated))
                }
                deduplicated.filter { !it.isArchived }
            }
        }
    }

    suspend fun createAndLoadSampleNovel(): Novel = SampleNovelLoader.createAndLoadSampleNovel(context, roomDelegate)
}
