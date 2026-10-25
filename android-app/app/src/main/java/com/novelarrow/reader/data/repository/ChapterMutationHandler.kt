package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.NaturalOrderComparator
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

internal class ChapterMutationHandler(
    private val context: Context,
    private val roomDelegate: LibraryRoomDelegate,
    private val json: Json
) {

    private val TAG = "ChapterMutationHandler"

    suspend fun deleteChapter(
        novelId: String,
        chapterId: String,
        deleteFromDisk: Boolean = false
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val chapterList = currentDb.chapters[novelId]?.toMutableList() ?: return@withContext Pair(null, emptyList())
            val targetChap = chapterList.firstOrNull { it.id == chapterId } ?: return@withContext Pair(null, chapterList)

            if (deleteFromDisk) {
                ChapterFileOperations.deleteSingleChapterFile(context, targetChap)
            }

            chapterList.remove(targetChap)
            val reindexedChapters = chapterList.mapIndexed { idx, ch ->
                ch.copy(orderIndex = idx)
            }

            val updatedNovels = currentDb.novels.map { nov ->
                if (nov.id == novelId) {
                    val newIdx = if (reindexedChapters.isEmpty()) 0 else nov.currentChapterIndex.coerceIn(0, reindexedChapters.size - 1)
                    nov.copy(
                        chapterCount = reindexedChapters.size,
                        completedCount = reindexedChapters.count { it.isCompleted },
                        currentChapterIndex = newIdx,
                        currentChapterId = reindexedChapters.getOrNull(newIdx)?.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else nov
            }

            val updatedChaptersMap = currentDb.chapters.toMutableMap().apply {
                put(novelId, reindexedChapters)
            }

            val updatedDb = currentDb.copy(novels = updatedNovels, chapters = updatedChaptersMap)
            LibraryStateManager.cachedDb = updatedDb

            val upNovel = updatedNovels.firstOrNull { it.id == novelId }
            roomDelegate.deleteChapterById(chapterId, reindexedChapters, upNovel)

            if (upNovel != null) {
                FolderProgressSync.writeFolderProgress(
                    context = context,
                    folderUriOrPath = upNovel.subfolderPath ?: upNovel.rootTreeUri,
                    novelTitle = upNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = upNovel.title,
                        currentChapterIndex = upNovel.currentChapterIndex,
                        currentChapterTitle = reindexedChapters.getOrNull(upNovel.currentChapterIndex)?.title ?: "",
                        currentParagraphIndex = reindexedChapters.getOrNull(upNovel.currentChapterIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = reindexedChapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (e: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        },
                        currentChapterId = upNovel.currentChapterId
                    ),
                    folderDocId = upNovel.folderDocId,
                    json = json
                )
            }

            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            Pair(upNovel, reindexedChapters)
        }
    }

    suspend fun reorderChapters(
        novelId: String,
        fromIndex: Int,
        toIndex: Int
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val chapterList = currentDb.chapters[novelId]?.toMutableList() ?: return@withContext Pair(null, emptyList())
            if (fromIndex !in chapterList.indices || toIndex !in chapterList.indices || fromIndex == toIndex) {
                return@withContext Pair(currentDb.novels.firstOrNull { it.id == novelId }, chapterList)
            }

            val movedItem = chapterList.removeAt(fromIndex)
            chapterList.add(toIndex, movedItem)

            val reindexedChapters = chapterList.mapIndexed { idx, ch ->
                ch.copy(orderIndex = idx)
            }

            val updatedNovels = currentDb.novels.map { nov ->
                if (nov.id == novelId) {
                    val currentIdx = nov.currentChapterIndex
                    val newCurrentIdx = when (currentIdx) {
                        fromIndex -> toIndex
                        in (fromIndex + 1)..toIndex -> currentIdx - 1
                        in toIndex until fromIndex -> currentIdx + 1
                        else -> currentIdx
                    }
                    val boundedIdx = newCurrentIdx.coerceIn(0, (reindexedChapters.size - 1).coerceAtLeast(0))
                    nov.copy(
                        currentChapterIndex = boundedIdx,
                        currentChapterId = reindexedChapters.getOrNull(boundedIdx)?.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else nov
            }

            val updatedChaptersMap = currentDb.chapters.toMutableMap().apply {
                put(novelId, reindexedChapters)
            }

            val updatedDb = currentDb.copy(novels = updatedNovels, chapters = updatedChaptersMap)
            LibraryStateManager.saveDatabaseLocked(roomDelegate, updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                FolderProgressSync.writeFolderProgress(
                    context = context,
                    folderUriOrPath = updatedNovel.subfolderPath ?: updatedNovel.rootTreeUri,
                    novelTitle = updatedNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = updatedNovel.title,
                        currentChapterIndex = updatedNovel.currentChapterIndex,
                        currentChapterTitle = reindexedChapters.getOrNull(updatedNovel.currentChapterIndex)?.title ?: "",
                        currentParagraphIndex = reindexedChapters.getOrNull(updatedNovel.currentChapterIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = reindexedChapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (e: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        },
                        currentChapterId = updatedNovel.currentChapterId
                    ),
                    folderDocId = updatedNovel.folderDocId,
                    json = json
                )
            }

            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, reindexedChapters)
        }
    }

    suspend fun sortChapters(
        novelId: String,
        preset: ChapterSortPreset
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val chapterList = currentDb.chapters[novelId] ?: return@withContext Pair(null, emptyList())
            val targetNovel = currentDb.novels.firstOrNull { it.id == novelId }
            val activeChapter = if (targetNovel != null && targetNovel.currentChapterIndex in chapterList.indices) {
                chapterList[targetNovel.currentChapterIndex]
            } else null

            val sorted = when (preset) {
                ChapterSortPreset.NATURAL -> chapterList.sortedWith { a, b ->
                    NaturalOrderComparator.compare(a.title, b.title)
                }
                ChapterSortPreset.REVERSE -> chapterList.reversed()
                ChapterSortPreset.FILE_NAME -> chapterList.sortedWith { a, b ->
                    val aFile = try { File(a.fileUri).name } catch (e: Exception) { a.title }
                    val bFile = try { File(b.fileUri).name } catch (e: Exception) { b.title }
                    aFile.compareTo(bFile, ignoreCase = true)
                }
            }

            val reindexedChapters = sorted.mapIndexed { idx, ch ->
                ch.copy(orderIndex = idx)
            }

            val newActiveIndex = if (activeChapter != null) {
                val idx = reindexedChapters.indexOfFirst { it.id == activeChapter.id }
                if (idx >= 0) idx else 0
            } else 0

            val updatedNovels = currentDb.novels.map { nov ->
                if (nov.id == novelId) {
                    nov.copy(
                        currentChapterIndex = newActiveIndex,
                        currentChapterId = reindexedChapters.getOrNull(newActiveIndex)?.id,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else nov
            }

            val updatedChaptersMap = currentDb.chapters.toMutableMap().apply {
                put(novelId, reindexedChapters)
            }

            val updatedDb = currentDb.copy(novels = updatedNovels, chapters = updatedChaptersMap)
            LibraryStateManager.saveDatabaseLocked(roomDelegate, updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                FolderProgressSync.writeFolderProgress(
                    context = context,
                    folderUriOrPath = updatedNovel.subfolderPath ?: updatedNovel.rootTreeUri,
                    novelTitle = updatedNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = updatedNovel.title,
                        currentChapterIndex = newActiveIndex,
                        currentChapterTitle = reindexedChapters.getOrNull(newActiveIndex)?.title ?: "",
                        currentParagraphIndex = reindexedChapters.getOrNull(newActiveIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = reindexedChapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (e: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        },
                        currentChapterId = updatedNovel.currentChapterId
                    ),
                    folderDocId = updatedNovel.folderDocId,
                    json = json
                )
            }

            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, reindexedChapters)
        }
    }

    suspend fun toggleChapterReadStatus(
        novelId: String,
        chapterId: String
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val db = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val chapters = db.chapters[novelId]?.toMutableList() ?: return@withContext Pair(null, emptyList())
            val idx = chapters.indexOfFirst { it.id == chapterId }
            if (idx == -1) return@withContext Pair(db.novels.firstOrNull { it.id == novelId }, chapters)

            val target = chapters[idx]
            chapters[idx] = target.copy(isCompleted = !target.isCompleted)

            val completedCount = chapters.count { it.isCompleted }
            val updatedNovels = db.novels.map { nov ->
                if (nov.id == novelId) {
                    nov.copy(
                        completedCount = completedCount,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else nov
            }

            val updatedChaptersMap = db.chapters.toMutableMap().apply {
                put(novelId, chapters)
            }

            val updatedDb = db.copy(novels = updatedNovels, chapters = updatedChaptersMap)
            LibraryStateManager.saveDatabaseLocked(roomDelegate, updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                FolderProgressSync.writeFolderProgress(
                    context = context,
                    folderUriOrPath = updatedNovel.subfolderPath ?: updatedNovel.rootTreeUri,
                    novelTitle = updatedNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = updatedNovel.title,
                        currentChapterIndex = updatedNovel.currentChapterIndex,
                        currentChapterTitle = chapters.getOrNull(updatedNovel.currentChapterIndex)?.title ?: "",
                        currentParagraphIndex = chapters.getOrNull(updatedNovel.currentChapterIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = chapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (e: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        },
                        currentChapterId = updatedNovel.currentChapterId
                    ),
                    folderDocId = updatedNovel.folderDocId,
                    json = json
                )
            }

            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, chapters)
        }
    }

    suspend fun updateChapterProgress(
        novelId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        isCompleted: Boolean
    ): Novel? = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val db = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val chapters = db.chapters[novelId]?.toMutableList() ?: return@withContext null
            if (chapterIndex !in chapters.indices) return@withContext null

            val target = chapters[chapterIndex]
            val newIsCompleted = isCompleted || target.isCompleted
            chapters[chapterIndex] = target.copy(
                lastReadParagraphIndex = paragraphIndex,
                lastScrollOffset = scrollOffset,
                isCompleted = newIsCompleted
            )

            val completedCount = chapters.count { it.isCompleted }
            val targetChapterId = chapters.getOrNull(chapterIndex)?.id
            val now = System.currentTimeMillis()

            val updatedNovels = db.novels.map { novel ->
                if (novel.id == novelId) {
                    novel.copy(
                        currentChapterIndex = chapterIndex,
                        currentChapterId = targetChapterId,
                        completedCount = completedCount,
                        lastUpdated = now
                    )
                } else novel
            }

            val updatedChapters = db.chapters.toMutableMap().apply {
                put(novelId, chapters)
            }

            val targetNovel = updatedNovels.firstOrNull { it.id == novelId }

            roomDelegate.updateParagraphProgress(
                novelId = novelId,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                scrollOffset = scrollOffset,
                isCompleted = isCompleted,
                targetChapterId = targetChapterId,
                completedCount = completedCount,
                timestamp = now
            )

            LibraryStateManager.cachedDb = db.copy(
                novels = updatedNovels,
                chapters = updatedChapters,
                lastActiveNovelId = novelId
            )

            val oldNovel = db.novels.firstOrNull { it.id == novelId }
            val isStatusChanged = (newIsCompleted != target.isCompleted)
            val isChapterChanged = (oldNovel?.currentChapterIndex != chapterIndex)
            val isThrottled = (now - LibraryStateManager.lastFolderProgressTimeMs > 15000L)

            if (targetNovel != null && (isStatusChanged || isChapterChanged || isThrottled)) {
                LibraryStateManager.lastFolderProgressTimeMs = now
                try {
                    FolderProgressSync.writeFolderProgress(
                        context = context,
                        folderUriOrPath = targetNovel.subfolderPath ?: targetNovel.rootTreeUri,
                        novelTitle = targetNovel.title,
                        progress = NovelFolderProgress(
                            novelTitle = targetNovel.title,
                            currentChapterIndex = chapterIndex,
                            currentChapterTitle = chapters.getOrNull(chapterIndex)?.title ?: "",
                            currentParagraphIndex = paragraphIndex,
                            chapters = chapters.map { ch ->
                                ChapterProgressEntry(
                                    title = ch.title,
                                    fileName = try { File(ch.fileUri).name } catch (e: Exception) { ch.title },
                                    lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                    lastScrollOffset = ch.lastScrollOffset,
                                    isCompleted = ch.isCompleted,
                                    orderIndex = ch.orderIndex
                                )
                            },
                            currentChapterId = targetNovel.currentChapterId
                        ),
                        folderDocId = targetNovel.folderDocId,
                        json = json
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error writing debounced folder progress: ${e.message}", e)
                }
            }
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
            targetNovel
        }
    }

    suspend fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val db = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val targetNovel = db.novels.find { it.id == novelId } ?: return@withContext
            val chapters = db.chapters[novelId] ?: roomDelegate.getChaptersForNovelDirect(novelId)

            if (deleteFilesFromDisk) {
                ChapterFileOperations.deleteNovelFilesFromDisk(context, targetNovel, chapters)
            } else {
                ChapterFileOperations.archiveNovelFiles(context, targetNovel)
            }

            val updatedNovels = db.novels.filterNot { it.id == novelId }
            val updatedChapters = db.chapters.toMutableMap().apply {
                remove(novelId)
            }
            val lastActive = if (db.lastActiveNovelId == novelId) updatedNovels.firstOrNull()?.id else db.lastActiveNovelId

            LibraryStateManager.cachedDb = db.copy(novels = updatedNovels, chapters = updatedChapters, lastActiveNovelId = lastActive)
            roomDelegate.deleteNovelDirect(novelId, lastActive)

            try {
                com.novelarrow.reader.data.backup.BackupRestoreManager.saveLibraryBackup(
                    context,
                    updatedNovels,
                    lastActive
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error saving library backup after delete: ${e.message}", e)
            }

            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
        }
    }
}
