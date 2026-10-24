package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

sealed class LibraryUpdateEvent {
    data class ChapterDownloaded(val novelTitle: String, val chapterTitle: String) : LibraryUpdateEvent()
    data class BatchCommitted(val novelTitle: String) : LibraryUpdateEvent()
    object LibraryRefreshed : LibraryUpdateEvent()
    object LibraryChanged : LibraryUpdateEvent()
}

@Serializable
data class ChapterProgressEntry(
    val title: String,
    val fileName: String = "",
    val lastReadParagraphIndex: Int = 0,
    val lastScrollOffset: Int = 0,
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0
)

@Serializable
data class NovelFolderProgress(
    val novelTitle: String,
    val currentChapterIndex: Int = 0,
    val currentChapterTitle: String = "",
    val currentParagraphIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val chapters: List<ChapterProgressEntry> = emptyList(),
    val currentChapterId: String? = null
)

enum class ChapterSortPreset {
    NATURAL,
    REVERSE,
    FILE_NAME
}

class LibraryRepository(private val context: Context) {

    private val roomDelegate by lazy { LibraryRoomDelegate(context) }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val webDownloader by lazy { WebNovelDownloadHandler(context, roomDelegate, json) }
    private val rootScanner by lazy { RootDirectoryScanner(context, roomDelegate, json) }
    private val refreshScanner by lazy { LibraryRefreshScanner(context, roomDelegate, json) }
    private val chapterMutationHandler by lazy { ChapterMutationHandler(context, roomDelegate, json) }

    val libraryUpdateEvents: SharedFlow<LibraryUpdateEvent> get() = LibraryStateManager.libraryUpdateEvents

    companion object {
        val libraryUpdateEvents: SharedFlow<LibraryUpdateEvent> = LibraryStateManager.libraryUpdateEvents

        fun notifyLibraryUpdate(event: LibraryUpdateEvent) {
            LibraryStateManager.notifyUpdate(event)
        }

        fun clearCacheForTesting() {
            LibraryStateManager.clearCache()
        }

        fun normalizeForMatching(s: String): String = NovelDeduplicator.normalizeForMatching(s)

        fun deduplicateNovels(
            novels: List<Novel>,
            chaptersMap: Map<String, List<Chapter>>
        ): List<Novel> = NovelDeduplicator.deduplicateNovels(novels, chaptersMap)

        fun deduplicateChapters(chapters: List<Chapter>): List<Chapter> =
            NovelDeduplicator.deduplicateChapters(chapters)

        fun generateCanonicalChapterId(novelId: String, fileUriOrName: String): String =
            NovelDeduplicator.generateCanonicalChapterId(novelId, fileUriOrName)
    }

    fun readFolderProgress(
        folderUriOrPath: String?,
        novelTitle: String,
        folderDocId: String? = null
    ): NovelFolderProgress? = FolderProgressSync.readFolderProgress(context, folderUriOrPath, novelTitle, folderDocId, json)

    fun writeFolderProgress(
        folderUriOrPath: String?,
        novelTitle: String,
        progress: NovelFolderProgress,
        folderDocId: String? = null
    ) = FolderProgressSync.writeFolderProgress(context, folderUriOrPath, novelTitle, progress, folderDocId, json)

    suspend fun getNovels(includeArchived: Boolean = false): List<Novel> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val deduplicated = deduplicateNovels(currentDb.novels, currentDb.chapters)
            if (deduplicated.size != currentDb.novels.size) {
                LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb.copy(novels = deduplicated))
            }
            if (includeArchived) deduplicated else deduplicated.filter { !it.isArchived }
        }
    }

    suspend fun getNovel(novelId: String): Novel? = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            LibraryStateManager.loadDatabaseLocked(roomDelegate).novels.find { it.id == novelId }
        }
    }

    suspend fun getArchivedNovels(): List<Novel> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val deduplicated = deduplicateNovels(currentDb.novels, currentDb.chapters)
            deduplicated.filter { it.isArchived }
        }
    }

    suspend fun archiveNovel(novelId: String) = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val targetNovel = currentDb.novels.find { it.id == novelId }
            val normTargetTitle = targetNovel?.title?.trim()?.lowercase(Locale.ROOT)
            val updatedNovels = currentDb.novels.map { novel ->
                if (novel.id == novelId || (normTargetTitle != null && novel.title.trim().lowercase(Locale.ROOT) == normTargetTitle)) {
                    novel.copy(isArchived = true)
                } else {
                    novel
                }
            }
            val lastActive = if (currentDb.lastActiveNovelId == novelId) {
                updatedNovels.firstOrNull { !it.isArchived }?.id
            } else {
                currentDb.lastActiveNovelId
            }
            LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb.copy(novels = updatedNovels, lastActiveNovelId = lastActive))
        }
    }

    suspend fun restoreNovel(novelId: String) = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val targetNovel = currentDb.novels.find { it.id == novelId }
            val normTargetTitle = targetNovel?.title?.trim()?.lowercase(Locale.ROOT)
            val updatedNovels = currentDb.novels.map { novel ->
                if (novel.id == novelId || (normTargetTitle != null && novel.title.trim().lowercase(Locale.ROOT) == normTargetTitle)) {
                    novel.copy(isArchived = false)
                } else {
                    novel
                }
            }
            LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb.copy(novels = updatedNovels))
        }
    }

    suspend fun updateNovelTitle(novelId: String, newTitle: String) = withContext(Dispatchers.IO) {
        if (newTitle.isBlank()) return@withContext
        LibraryStateManager.dbMutex.withLock {
            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val updatedNovels = currentDb.novels.map { novel ->
                if (novel.id == novelId) {
                    novel.copy(title = newTitle.trim())
                } else {
                    novel
                }
            }
            LibraryStateManager.saveDatabaseLocked(roomDelegate, currentDb.copy(novels = updatedNovels))
        }
    }

    suspend fun getChapters(novelId: String): List<Chapter> = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val raw = LibraryStateManager.loadDatabaseLocked(roomDelegate).chapters[novelId] ?: emptyList()
            deduplicateChapters(raw)
        }
    }

    suspend fun getLastActiveNovelId(): String? = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            LibraryStateManager.loadDatabaseLocked(roomDelegate).lastActiveNovelId
        }
    }

    suspend fun setLastActiveNovelId(novelId: String?) = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val current = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            LibraryStateManager.saveDatabaseLocked(roomDelegate, current.copy(lastActiveNovelId = novelId))
        }
    }

    suspend fun addDownloadedChapter(
        novelTitle: String,
        chapterFile: java.io.File,
        chapterTitle: String,
        chapterNumber: Int,
        sourceUrl: String,
        commitNow: Boolean = true
    ): Novel = webDownloader.addDownloadedChapter(
        novelTitle = novelTitle,
        chapterFile = chapterFile,
        chapterTitle = chapterTitle,
        chapterNumber = chapterNumber,
        sourceUrl = sourceUrl,
        commitNow = commitNow
    )

    suspend fun commitBatch(novelTitle: String? = null) = webDownloader.commitBatch(novelTitle)

    suspend fun scanDownloadedWebNovels(): List<Novel> = webDownloader.scanDownloadedWebNovels()

    suspend fun createAndLoadSampleNovel(): Novel = webDownloader.createAndLoadSampleNovel()

    suspend fun scanRootDirectory(
        treeUri: Uri,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = rootScanner.scanRootDirectoryInternal(treeUri.toString(), treeUri, onProgress)

    suspend fun scanRootDirectory(
        folderPath: String,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = rootScanner.scanRootDirectoryInternal(folderPath, null, onProgress)

    suspend fun refreshAllLinkedFolders(
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = refreshScanner.refreshAllLinkedFolders(rootScanner, onProgress)

    suspend fun refreshEntireLibrary(
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = refreshScanner.refreshEntireLibrary(rootScanner, webDownloader, onProgress)

    suspend fun deleteChapter(
        novelId: String,
        chapterId: String,
        deleteFromDisk: Boolean = false
    ): Pair<Novel?, List<Chapter>> = chapterMutationHandler.deleteChapter(novelId, chapterId, deleteFromDisk)

    suspend fun reorderChapters(
        novelId: String,
        fromIndex: Int,
        toIndex: Int
    ): Pair<Novel?, List<Chapter>> = chapterMutationHandler.reorderChapters(novelId, fromIndex, toIndex)

    suspend fun sortChapters(
        novelId: String,
        preset: ChapterSortPreset
    ): Pair<Novel?, List<Chapter>> = chapterMutationHandler.sortChapters(novelId, preset)

    suspend fun toggleChapterReadStatus(
        novelId: String,
        chapterId: String
    ): Pair<Novel?, List<Chapter>> = chapterMutationHandler.toggleChapterReadStatus(novelId, chapterId)

    suspend fun readChapterContent(chapter: Chapter): String = withContext(Dispatchers.IO) {
        ChapterFileOperations.readChapterContent(context, chapter)
    }

    suspend fun saveChapterContent(chapter: Chapter, newContent: String): Boolean = withContext(Dispatchers.IO) {
        ChapterFileOperations.saveChapterContent(context, chapter, newContent)
    }

    suspend fun updateChapterProgress(
        novelId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        isCompleted: Boolean
    ): Novel? = chapterMutationHandler.updateChapterProgress(
        novelId = novelId,
        chapterIndex = chapterIndex,
        paragraphIndex = paragraphIndex,
        scrollOffset = scrollOffset,
        isCompleted = isCompleted
    )

    suspend fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) =
        chapterMutationHandler.deleteNovel(novelId, deleteFilesFromDisk)

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            LibraryStateManager.saveDatabaseLocked(roomDelegate, LibraryDatabase())
            LibraryStateManager.notifyUpdate(LibraryUpdateEvent.LibraryChanged)
        }
    }

    fun isSupported(fileName: String?): Boolean = SafTreeScanner.isSupported(fileName)
}
