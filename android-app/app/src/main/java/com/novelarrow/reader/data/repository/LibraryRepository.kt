package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.NaturalOrderComparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

import android.os.Environment
import androidx.core.util.AtomicFile
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileOutputStream

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
    val orderIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class NovelFolderProgress(
    val novelTitle: String = "",
    val currentChapterIndex: Int = 0,
    val currentChapterTitle: String = "",
    val currentParagraphIndex: Int = 0,
    val chapters: List<ChapterProgressEntry> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val currentChapterId: String? = null
)

enum class ChapterSortPreset {
    NATURAL,
    REVERSE,
    FILE_NAME
}

@Serializable
private data class LibraryDatabase(
    val novels: List<Novel> = emptyList(),
    val chapters: Map<String, List<Chapter>> = emptyMap(),
    val lastActiveNovelId: String? = null
)

class LibraryRepository(private val context: Context) {

    companion object {
        private val dbMutex = Mutex()
        private var lastSaveTimeMs = 0L
        @Volatile
        private var cachedDb: LibraryDatabase? = null

        private val _libraryUpdateEvents = MutableSharedFlow<LibraryUpdateEvent>(extraBufferCapacity = 64)
        val libraryUpdateEvents: SharedFlow<LibraryUpdateEvent> = _libraryUpdateEvents.asSharedFlow()

        fun notifyLibraryUpdate(event: LibraryUpdateEvent) {
            _libraryUpdateEvents.tryEmit(event)
        }

        fun clearCacheForTesting() {
            cachedDb = null
        }

        fun normalizeForMatching(s: String): String {
            return s.replace("[_\\-\\s]+".toRegex(), " ").trim().lowercase(java.util.Locale.ROOT)
        }

        fun deduplicateNovels(
            novels: List<Novel>,
            chaptersMap: Map<String, List<Chapter>>
        ): List<Novel> {
            val grouped = novels.groupBy {
                Triple(
                    normalizeForMatching(it.title),
                    it.rootTreeUri,
                    it.subfolderPath ?: it.folderDocId ?: ""
                )
            }
            return grouped.values.map { list ->
                if (list.size == 1) list.first()
                else {
                    val wasArchived = list.any { it.isArchived }
                    val chosen = list.maxByOrNull { n -> chaptersMap[n.id]?.size ?: n.chapterCount } ?: list.first()
                    if (wasArchived) chosen.copy(isArchived = true) else chosen
                }
            }
        }

        fun deduplicateChapters(chapters: List<Chapter>): List<Chapter> {
            if (chapters.isEmpty()) return emptyList()
            val grouped = chapters.groupBy { ch ->
                val rawUri = ch.fileUri.removePrefix("file://")
                val fileName = try {
                    File(rawUri).name.lowercase(java.util.Locale.ROOT)
                } catch (_: Exception) { "" }
                val normTitle = normalizeForMatching(ch.title)
                val key = if (fileName.isNotBlank() && fileName.contains(".")) {
                    fileName.substringBeforeLast('.')
                } else if (normTitle.isNotBlank()) {
                    normTitle
                } else {
                    rawUri.lowercase(java.util.Locale.ROOT)
                }
                Pair(ch.novelId, key)
            }
            return grouped.values.map { dupList ->
                if (dupList.size == 1) dupList.first()
                else {
                    val isComp = dupList.any { it.isCompleted }
                    val maxPara = dupList.maxOf { it.lastReadParagraphIndex }
                    val maxOffset = dupList.maxOf { it.lastScrollOffset }
                    val bestUrl = dupList.firstOrNull { !it.webSourceUrl.isNullOrBlank() }?.webSourceUrl
                    val bestUri = dupList.firstOrNull {
                        try {
                            val f = File(it.fileUri.removePrefix("file://"))
                            f.exists() && f.length() > 0
                        } catch (_: Exception) { false }
                    }?.fileUri ?: dupList.first().fileUri
                    val chosen = dupList.first()
                    chosen.copy(
                        isCompleted = isComp,
                        lastReadParagraphIndex = maxPara,
                        lastScrollOffset = maxOffset,
                        webSourceUrl = bestUrl ?: chosen.webSourceUrl,
                        fileUri = bestUri
                    )
                }
            }.sortedBy { it.orderIndex }.mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }
        }

        fun generateCanonicalChapterId(novelId: String, fileUriOrName: String): String {
            val rawName = try {
                File(fileUriOrName.removePrefix("file://")).name
            } catch (_: Exception) {
                fileUriOrName
            }
            val cleanName = rawName.substringBeforeLast('.').lowercase(java.util.Locale.ROOT).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            return "${novelId}_$cleanName"
        }
    }

    val libraryUpdateEvents: SharedFlow<LibraryUpdateEvent> get() = Companion.libraryUpdateEvents

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dbFile: File
        get() = File(context.filesDir, "novel_library_v1.json")

    private val atomicDbFile: AtomicFile
        get() = AtomicFile(dbFile)

    private val supportedExtensions = setOf(
        "txt", "md", "markdown", "html", "htm", "xhtml", "xml", "json", "csv", "log", "rtf", "epub", "text", "mhtml"
    )

    fun readFolderProgress(
        folderUriOrPath: String,
        novelTitle: String,
        folderDocId: String? = null
    ): NovelFolderProgress? {
        try {
            if (folderUriOrPath.startsWith("/") || folderUriOrPath.startsWith("file://")) {
                val dir = File(folderUriOrPath.removePrefix("file://"))
                val progressFile = File(dir, ".progress.json")
                if (progressFile.exists() && progressFile.isFile) {
                    val text = progressFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) return json.decodeFromString<NovelFolderProgress>(text)
                }
            } else if (folderUriOrPath.startsWith("content://")) {
                val uri = Uri.parse(folderUriOrPath)
                if (folderDocId != null) {
                    try {
                        val (_, files) = querySafChildrenFast(uri, folderDocId)
                        val progressItem = files.firstOrNull { it.displayName == ".progress.json" }
                        if (progressItem != null) {
                            context.contentResolver.openInputStream(Uri.parse(progressItem.uriStr))?.use { stream ->
                                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                                if (text.isNotBlank()) return json.decodeFromString<NovelFolderProgress>(text)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Error reading SAF subfolder progress for $novelTitle (docId: $folderDocId)", e)
                    }
                } else {
                    val rootDoc = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Failed resolving DocumentFile from $uri for $novelTitle", e)
                        null
                    }
                    val progressFile = rootDoc?.findFile(".progress.json")
                    if (progressFile != null && progressFile.exists()) {
                        context.contentResolver.openInputStream(progressFile.uri)?.use { stream ->
                            val text = stream.bufferedReader(Charsets.UTF_8).readText()
                            if (text.isNotBlank()) return json.decodeFromString<NovelFolderProgress>(text)
                        }
                    }
                }
            }

            // Fallback: check standard NovelReader directory for this novel title
            val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
            val candidateDirs = mutableListOf<File>()
            candidateDirs.add(File(context.filesDir, "NovelReader/$safeTitle"))
            candidateDirs.add(File(context.filesDir, "NovelReader/$novelTitle"))
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add(File(it, "NovelReader/$safeTitle"))
                candidateDirs.add(File(it, "NovelReader/$novelTitle"))
            }
            for (root in com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)) {
                candidateDirs.add(File(root, safeTitle))
                candidateDirs.add(File(root, novelTitle))
            }
            for (candidate in candidateDirs.distinctBy { it.absolutePath }) {
                val pFile = File(candidate, ".progress.json")
                if (pFile.exists() && pFile.isFile && pFile.length() > 0L) {
                    val text = pFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) return json.decodeFromString<NovelFolderProgress>(text)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("LibraryRepository", "Failed reading folder progress for $novelTitle: ${e.message}", e)
        }
        return null
    }

    fun writeFolderProgress(
        folderUriOrPath: String,
        novelTitle: String,
        progress: NovelFolderProgress,
        folderDocId: String? = null
    ) {
        try {
            val jsonText = json.encodeToString(progress)
            if (folderUriOrPath.startsWith("/") || folderUriOrPath.startsWith("file://")) {
                val dir = File(folderUriOrPath.removePrefix("file://"))
                if (dir.exists() && dir.isDirectory) {
                    File(dir, ".progress.json").writeText(jsonText, Charsets.UTF_8)
                }
            } else if (folderUriOrPath.startsWith("content://")) {
                val uri = Uri.parse(folderUriOrPath)
                if (folderDocId != null) {
                    try {
                        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(uri, folderDocId)
                        val (_, files) = querySafChildrenFast(uri, folderDocId)
                        val existingProgress = files.firstOrNull { it.displayName == ".progress.json" }
                        val targetUri = if (existingProgress != null) {
                            Uri.parse(existingProgress.uriStr)
                        } else {
                            DocumentsContract.createDocument(context.contentResolver, parentDocUri, "application/json", ".progress.json")
                        }
                        targetUri?.let { pUri ->
                            context.contentResolver.openOutputStream(pUri, "wt")?.use { out ->
                                out.write(jsonText.toByteArray(Charsets.UTF_8))
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Error writing SAF subfolder progress for $novelTitle (docId: $folderDocId)", e)
                    }
                } else {
                    val rootDoc = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Failed resolving tree uri for $folderUriOrPath", e)
                        null
                    }
                    if (rootDoc != null && rootDoc.exists() && rootDoc.canWrite()) {
                        var progressFile = rootDoc.findFile(".progress.json")
                        if (progressFile == null) {
                            progressFile = rootDoc.createFile("application/json", ".progress.json")
                        }
                        progressFile?.uri?.let { pUri ->
                            context.contentResolver.openOutputStream(pUri, "wt")?.use { out ->
                                out.write(jsonText.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
            }

            val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
            val candidateDirs = mutableListOf<File>()
            candidateDirs.add(File(context.filesDir, "NovelReader/$safeTitle"))
            candidateDirs.add(File(context.filesDir, "NovelReader/$novelTitle"))
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add(File(it, "NovelReader/$safeTitle"))
                candidateDirs.add(File(it, "NovelReader/$novelTitle"))
            }
            for (root in com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)) {
                candidateDirs.add(File(root, safeTitle))
                candidateDirs.add(File(root, novelTitle))
            }
            for (candidate in candidateDirs.distinctBy { it.absolutePath }) {
                if (candidate.exists() && candidate.isDirectory) {
                    try {
                        File(candidate, ".progress.json").writeText(jsonText, Charsets.UTF_8)
                    } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Failed writing candidate progress at ${candidate.path}", e)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("LibraryRepository", "Failed writing folder progress for $novelTitle", e)
        }
    }

    suspend fun getNovels(includeArchived: Boolean = false): List<Novel> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val deduplicated = deduplicateNovels(currentDb.novels, currentDb.chapters)
            if (deduplicated.size != currentDb.novels.size) {
                saveDatabaseLocked(currentDb.copy(novels = deduplicated))
            }
            if (includeArchived) deduplicated else deduplicated.filter { !it.isArchived }
        }
    }

    suspend fun getArchivedNovels(): List<Novel> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val deduplicated = deduplicateNovels(currentDb.novels, currentDb.chapters)
            deduplicated.filter { it.isArchived }
        }
    }

    suspend fun archiveNovel(novelId: String) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val targetNovel = currentDb.novels.find { it.id == novelId }
            val normTargetTitle = targetNovel?.title?.trim()?.lowercase(java.util.Locale.ROOT)
            val updatedNovels = currentDb.novels.map { novel ->
                if (novel.id == novelId || (normTargetTitle != null && novel.title.trim().lowercase(java.util.Locale.ROOT) == normTargetTitle)) {
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
            saveDatabaseLocked(currentDb.copy(novels = updatedNovels, lastActiveNovelId = lastActive))
        }
    }

    suspend fun restoreNovel(novelId: String) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val targetNovel = currentDb.novels.find { it.id == novelId }
            val normTargetTitle = targetNovel?.title?.trim()?.lowercase(java.util.Locale.ROOT)
            val updatedNovels = currentDb.novels.map { novel ->
                if (novel.id == novelId || (normTargetTitle != null && novel.title.trim().lowercase(java.util.Locale.ROOT) == normTargetTitle)) {
                    novel.copy(isArchived = false)
                } else {
                    novel
                }
            }
            saveDatabaseLocked(currentDb.copy(novels = updatedNovels))
        }
    }

    suspend fun updateNovelTitle(novelId: String, newTitle: String) = withContext(Dispatchers.IO) {
        if (newTitle.isBlank()) return@withContext
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val updatedNovels = currentDb.novels.map { novel ->
                if (novel.id == novelId) {
                    novel.copy(title = newTitle.trim())
                } else {
                    novel
                }
            }
            saveDatabaseLocked(currentDb.copy(novels = updatedNovels))
        }
    }



    suspend fun getChapters(novelId: String): List<Chapter> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val raw = loadDatabaseLocked().chapters[novelId] ?: emptyList()
            deduplicateChapters(raw)
        }
    }

    suspend fun getLastActiveNovelId(): String? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            loadDatabaseLocked().lastActiveNovelId
        }
    }

    suspend fun setLastActiveNovelId(novelId: String?) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val current = loadDatabaseLocked()
            saveDatabaseLocked(current.copy(lastActiveNovelId = novelId))
        }
    }

    suspend fun addDownloadedChapter(
        novelTitle: String,
        chapterFile: File,
        chapterTitle: String,
        chapterNumber: Int,
        sourceUrl: String,
        commitNow: Boolean = true
    ): Novel = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
            val targetNovelId = "web_novel_${safeTitle}"
            val folder = chapterFile.parentFile ?: WebNovelDownloader.getNovelDownloadDirectory(context, novelTitle)

            val currentDb = loadDatabaseLocked()
            val existingNovel = currentDb.novels.firstOrNull {
                it.id == targetNovelId || it.title.equals(novelTitle, ignoreCase = true)
            }
            val existingChapters = (if (existingNovel != null) currentDb.chapters[existingNovel.id] else null)
                ?: currentDb.chapters[targetNovelId] ?: emptyList()

            // Incrementally append or update chapter in memory instead of rescanning and sorting all files on disk
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
                saveDatabaseLocked(newDb)
            } else {
                cachedDb = newDb
                val now = System.currentTimeMillis()
                if (now - lastSaveTimeMs > 3000L) {
                    lastSaveTimeMs = now
                    saveDatabaseLocked(newDb)
                }
            }

            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.ChapterDownloaded(novelTitle, chapterTitle))
            updatedNovel
        }
    }

    suspend fun commitBatch(novelTitle: String? = null) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            if (novelTitle != null) {
                val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
                val targetNovelId = "web_novel_${safeTitle}"
                val folder = WebNovelDownloader.getNovelDownloadDirectory(context, novelTitle)
                val files = (folder.listFiles() ?: emptyArray())
                    .filter { it.isFile && isSupported(it.name) }
                    .sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }

                if (files.isNotEmpty()) {
                    val existingNovel = currentDb.novels.firstOrNull {
                        it.id == targetNovelId || it.title.equals(novelTitle, ignoreCase = true)
                    }
                    val existingChapters = (if (existingNovel != null) currentDb.chapters[existingNovel.id] else null)
                        ?: currentDb.chapters[targetNovelId] ?: emptyList()
                    val existingByUri = existingChapters.associateBy { it.fileUri }
                    val existingByTitle = existingChapters.associateBy { it.title }

                    val chapters = deduplicateChapters(files.mapIndexed { index, file ->
                        val existing = existingByUri[file.absolutePath] ?: existingByTitle[file.nameWithoutExtension]
                        Chapter(
                            id = existing?.id ?: generateCanonicalChapterId(targetNovelId, file.name),
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

                    saveDatabaseLocked(
                        currentDb.copy(
                            novels = updatedNovels,
                            chapters = updatedChaptersMap,
                            lastActiveNovelId = updatedNovel.id
                        )
                    )
                    _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.BatchCommitted(novelTitle))
                    return@withContext
                }
            }
            saveDatabaseLocked(currentDb)
            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.BatchCommitted(novelTitle ?: ""))
        }
    }

    private fun collectFilesRecursively(dir: File, baseDir: File = dir): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        val children = dir.listFiles() ?: return result
        for (f in children) {
            if (f.isDirectory) {
                result.addAll(collectFilesRecursively(f, baseDir))
            } else if (f.isFile && isSupported(f.name)) {
                result.add(Pair(f, f.name))
            }
        }
        return result
    }

    suspend fun scanDownloadedWebNovels(): List<Novel> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val novelsByNormTitle = mutableMapOf<String, Novel>()
            val chaptersByNovelId = mutableMapOf<String, MutableList<Chapter>>()

            val rootCandidates = com.novelarrow.reader.data.backup.BackupRestoreManager.getNovelReaderRoots(context)
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
                val directFiles = (root.listFiles { f -> f.isFile && isSupported(f.name) } ?: emptyArray())
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
                    val normKey = normalizeForMatching(dirTitle)
                    val novelId = "web_novel_${safeTitle}"

                    val existingDbNovel = currentDb.novels.firstOrNull {
                        it.id == novelId || normalizeForMatching(it.title) == normKey ||
                        (it.rootTreeUri.isNotBlank() && (it.rootTreeUri == sub.absolutePath || it.rootTreeUri == "file://${sub.absolutePath}"))
                    }
                    val actualNovelId = existingDbNovel?.id ?: novelId
                    val existingDbChapters = (if (existingDbNovel != null) currentDb.chapters[existingDbNovel.id] else null)
                        ?: currentDb.chapters[actualNovelId] ?: currentDb.chapters[novelId] ?: emptyList()
                    val existingByUri = existingDbChapters.associateBy { it.fileUri }
                    val existingByTitle = existingDbChapters.associateBy { it.title }

                    val folderProgress = readFolderProgress(sub.absolutePath, dirTitle)
                    val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
                    val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

                    val existingNovel = novelsByNormTitle[normKey]
                    if (existingNovel == null) {
                        val rawChapters = filesWithDisp.mapIndexed { index, (file, dispName) ->
                            val fTitle = dispName.substringBeforeLast('.')
                            val existing = existingByUri[file.absolutePath] ?: existingByTitle[fTitle] ?: existingByTitle[file.nameWithoutExtension]
                            val prog = progressByTitle[fTitle] ?: progressByTitle[file.nameWithoutExtension] ?: progressByFile[file.name]
                            val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                            val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                            val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                            val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                            Chapter(
                                id = existing?.id ?: generateCanonicalChapterId(actualNovelId, file.name),
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
                        val chapters = deduplicateChapters(rawChapters)
                            .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                            .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }
                            .toMutableList()

                        val targetChapterId = folderProgress?.currentChapterId ?: existingDbNovel?.currentChapterId
                        val targetChapterTitle = folderProgress?.currentChapterTitle
                        val resolvedIndex = if (targetChapterId != null) {
                            val idx = chapters.indexOfFirst { it.id == targetChapterId }
                            if (idx >= 0) idx else (folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0)
                        } else if (!targetChapterTitle.isNullOrBlank()) {
                            val idx = chapters.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                            if (idx >= 0) idx else (folderProgress.currentChapterIndex)
                        } else {
                            folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0
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
                            try { File(ch.fileUri).name.lowercase(java.util.Locale.ROOT) } catch (_: Exception) { ch.title.lowercase(java.util.Locale.ROOT) }
                        }.toSet()
                        for ((file, dispName) in filesWithDisp) {
                            val fileKey = file.name.lowercase(java.util.Locale.ROOT)
                            if (!existingFileKeys.contains(fileKey)) {
                                val fTitle = dispName.substringBeforeLast('.')
                                val existing = existingByUri[file.absolutePath] ?: existingByTitle[fTitle] ?: existingByTitle[file.nameWithoutExtension]
                                val prog = progressByTitle[fTitle] ?: progressByTitle[file.nameWithoutExtension] ?: progressByFile[file.name]
                                val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                                val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                                val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                                currentChapters.add(
                                    Chapter(
                                        id = existing?.id ?: generateCanonicalChapterId(existingNovel.id, file.name),
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
                        val deduped = deduplicateChapters(currentChapters)
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
                val existingNormTitles = discoveredNovels.map { normalizeForMatching(it.title) }.toSet()
                val preservedNovels = currentDb.novels.filterNot {
                    existingIds.contains(it.id) || existingNormTitles.contains(normalizeForMatching(it.title))
                }
                val mergedNovels = deduplicateNovels(preservedNovels + discoveredNovels, chaptersByNovelId)
                val mergedChapters = currentDb.chapters.toMutableMap().apply {
                    putAll(chaptersByNovelId)
                }
                saveDatabaseLocked(currentDb.copy(novels = mergedNovels, chapters = mergedChapters))
                mergedNovels.filter { !it.isArchived }
            } else {
                val deduplicated = deduplicateNovels(currentDb.novels, currentDb.chapters)
                if (deduplicated.size != currentDb.novels.size) {
                    saveDatabaseLocked(currentDb.copy(novels = deduplicated))
                }
                deduplicated.filter { !it.isArchived }
            }
        }
    }

    suspend fun createAndLoadSampleNovel(): Novel = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val sampleDir = File(context.filesDir, "sample_novel_quantum")
            if (!sampleDir.exists()) sampleDir.mkdirs()

            val ch1File = File(sampleDir, "01_wave_particle_duality.txt")
            val ch2File = File(sampleDir, "02_superposition_schrodinger_cat.txt")
            val ch3File = File(sampleDir, "03_quantum_entanglement.txt")

            ch1File.writeText(com.novelarrow.reader.data.util.SampleQuantumContent.CHAPTER_1)
            ch2File.writeText(com.novelarrow.reader.data.util.SampleQuantumContent.CHAPTER_2)
            ch3File.writeText(com.novelarrow.reader.data.util.SampleQuantumContent.CHAPTER_3)

            val novelId = "sample_quantum_mechanics"
            val chapters = listOf(
                Chapter(
                    id = "${novelId}_1",
                    novelId = novelId,
                    title = "Chapter 1: The Wave-Particle Duality",
                    fileUri = ch1File.absolutePath,
                    orderIndex = 0,
                    sizeBytes = ch1File.length()
                ),
                Chapter(
                    id = "${novelId}_2",
                    novelId = novelId,
                    title = "Chapter 2: Superposition and Schrödinger's Cat",
                    fileUri = ch2File.absolutePath,
                    orderIndex = 1,
                    sizeBytes = ch2File.length()
                ),
                Chapter(
                    id = "${novelId}_3",
                    novelId = novelId,
                    title = "Chapter 3: Quantum Entanglement and Spooky Action",
                    fileUri = ch3File.absolutePath,
                    orderIndex = 2,
                    sizeBytes = ch3File.length()
                )
            )

            val novel = Novel(
                id = novelId,
                title = "Introduction to Quantum Mechanics",
                rootTreeUri = "sample://quantum_mechanics",
                chapterCount = chapters.size,
                completedCount = 0,
                currentChapterIndex = 0
            )

            val currentDb = loadDatabaseLocked()
            val filteredNovels = currentDb.novels.filter { it.id != novelId }
            val updatedNovels = listOf(novel) + filteredNovels
            val updatedChapters = currentDb.chapters.toMutableMap().apply {
                put(novelId, chapters)
            }

            saveDatabaseLocked(
                currentDb.copy(
                    novels = updatedNovels,
                    chapters = updatedChapters,
                    lastActiveNovelId = novelId
                )
            )

            novel
        }
    }

    data class ScannedFileItem(
        val uriStr: String,
        val displayName: String,
        val sizeBytes: Long
    )

    private fun querySafChildrenFast(
        treeUri: Uri,
        parentDocId: String
    ): Pair<List<Pair<String, String>>, List<ScannedFileItem>> {
        val subdirs = mutableListOf<Pair<String, String>>()
        val files = mutableListOf<ScannedFileItem>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = (if (idIdx >= 0) cursor.getString(idIdx) else null) ?: continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "Untitled"
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subdirs.add(Pair(docId, name))
                    } else if (isSupported(name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                        files.add(ScannedFileItem(docUri, name, size))
                    }
                }
            }
        } catch (e: Exception) {
            com.novelarrow.reader.util.AppLogger.e("LibraryRepository", "Failed to query SAF children for parentDocId $parentDocId: ${e.message}", e)
        }
        return Pair(subdirs, files)
    }

    private fun collectSafChildrenRecursivelyFast(
        treeUri: Uri,
        dirDocId: String,
        out: MutableList<ScannedFileItem>,
        pathPrefix: String = ""
    ) {
        val (subdirs, files) = querySafChildrenFast(treeUri, dirDocId)
        for (file in files) {
            out.add(file)
        }
        for ((subDocId, subName) in subdirs) {
            collectSafChildrenRecursivelyFast(treeUri, subDocId, out, "")
        }
    }

    private fun scanDocumentFileFallback(rootDoc: DocumentFile): Pair<Map<String, List<ScannedFileItem>>, List<ScannedFileItem>> {
        val subdirMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()
        for (f in rootDoc.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                val list = mutableListOf<ScannedFileItem>()
                collectDocumentFiles(f, list, "")
                if (list.isNotEmpty()) subdirMap[name] = list
            } else if (f.isFile && isSupported(name)) {
                looseFiles.add(ScannedFileItem(f.uri.toString(), name, f.length()))
            }
        }
        return Pair(subdirMap, looseFiles)
    }

    private fun collectDocumentFiles(dir: DocumentFile, out: MutableList<ScannedFileItem>, pathPrefix: String = "") {
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                collectDocumentFiles(f, out, "")
            } else if (f.isFile && isSupported(name)) {
                out.add(ScannedFileItem(f.uri.toString(), name, f.length()))
            }
        }
    }

    private fun scanLocalDirectory(dir: File): Pair<Map<String, List<ScannedFileItem>>, List<ScannedFileItem>> {
        val subdirMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()
        val children = dir.listFiles() ?: return Pair(emptyMap(), emptyList())
        for (f in children) {
            if (f.isDirectory) {
                val recursiveFiles = collectFilesRecursively(f, f)
                if (recursiveFiles.isNotEmpty()) {
                    subdirMap[f.name] = recursiveFiles.map { (file, _) ->
                        ScannedFileItem(file.absolutePath, file.name, file.length())
                    }
                }
            } else if (f.isFile && isSupported(f.name)) {
                looseFiles.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
            }
        }
        return Pair(subdirMap, looseFiles)
    }

    private fun collectLocalFiles(dir: File, out: MutableList<ScannedFileItem>) {
        val recursiveFiles = collectFilesRecursively(dir, dir)
        for ((f, _) in recursiveFiles) {
            out.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
        }
    }

    suspend fun scanRootDirectory(
        treeUri: Uri,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = scanRootDirectoryInternal(treeUri.toString(), treeUri, onProgress)

    suspend fun scanRootDirectory(
        folderPath: String,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = scanRootDirectoryInternal(folderPath, null, onProgress)

    private suspend fun scanRootDirectoryInternal(
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
                val (subdirs, loose) = scanLocalDirectory(dir)
                subdirFilesMap.putAll(subdirs)
                looseFiles.addAll(loose)
            }
        } else {
            val resolvedUri = safTreeUri ?: try { Uri.parse(treeUriStr) } catch (_: Exception) { null }
            val rootDoc = resolvedUri?.let { uri ->
                try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) {
                    AppLogger.w("LibraryRepository", "Failed resolving rootDoc for $treeUriStr", e)
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
                    AppLogger.w("LibraryRepository", "Failed extracting rootDocId for $treeUriStr", e)
                    null
                }
            }

            val targetDocId = rootDocId
            if (targetDocId != null && resolvedUri != null) {
                try {
                    val (subdirs, files) = querySafChildrenFast(resolvedUri, targetDocId)
                    if (subdirs.isNotEmpty() || files.isNotEmpty()) {
                        scannedViaSafContract = true
                        looseFiles.addAll(files)
                        for ((subDocId, subName) in subdirs) {
                            safSubdirDocIds[subName] = subDocId
                            val subFiles = mutableListOf<ScannedFileItem>()
                            collectSafChildrenRecursivelyFast(resolvedUri, subDocId, subFiles)
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
                val (subdirs, files) = scanDocumentFileFallback(rootDoc)
                subdirFilesMap.putAll(subdirs)
                looseFiles.addAll(files)
            }
        }

        val totalFilesCount = looseFiles.size + subdirFilesMap.values.sumOf { it.size }
        val currentDb = dbMutex.withLock { loadDatabaseLocked() }
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

            val folderProgress = readFolderProgress(subfolderPath ?: treeUriStr, dirName, folderDocId)
            val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
            val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

            val rawChapters = sortedFiles.mapIndexed { index, file ->
                currentProgressIndex++
                reportProgress(currentProgressIndex, totalFilesCount, "$dirName / ${file.displayName}")
                val fTitle = file.displayName.substringBeforeLast('.')
                val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                val prog = progressByTitle[fTitle] ?: progressByFile[file.displayName]
                val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                Chapter(
                    id = existing?.id ?: generateCanonicalChapterId(novelId, file.displayName),
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
            val chapters = deduplicateChapters(rawChapters)
                .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

            val targetChapterId = folderProgress?.currentChapterId ?: existingDbNovel?.currentChapterId
            val targetChapterTitle = folderProgress?.currentChapterTitle
            val resolvedIndex = if (targetChapterId != null) {
                val idx = chapters.indexOfFirst { it.id == targetChapterId }
                if (idx >= 0) idx else (folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0)
            } else if (!targetChapterTitle.isNullOrBlank()) {
                val idx = chapters.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                if (idx >= 0) idx else (folderProgress.currentChapterIndex)
            } else {
                folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0
            }.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))

            val novel = Novel(
                id = novelId,
                title = dirName,
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
            // If there are no subdirectories, the imported folder itself is the novel!
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

            val folderProgress = readFolderProgress(subfolderPath ?: treeUriStr, novelTitle, folderDocId)
            val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
            val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

            val rawChapters = sortedFiles.mapIndexed { index, file ->
                currentProgressIndex++
                reportProgress(currentProgressIndex, totalFilesCount, "$novelTitle / ${file.displayName}")
                val fTitle = file.displayName.substringBeforeLast('.')
                val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                val prog = progressByTitle[fTitle] ?: progressByFile[file.displayName]
                val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                Chapter(
                    id = existing?.id ?: generateCanonicalChapterId(novelId, file.displayName),
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
            val chapters = deduplicateChapters(rawChapters)
                .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

            val targetChapterId = folderProgress?.currentChapterId ?: existingDbNovel?.currentChapterId
            val targetChapterTitle = folderProgress?.currentChapterTitle
            val resolvedIndex = if (targetChapterId != null) {
                val idx = chapters.indexOfFirst { it.id == targetChapterId }
                if (idx >= 0) idx else (folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0)
            } else if (!targetChapterTitle.isNullOrBlank()) {
                val idx = chapters.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                if (idx >= 0) idx else (folderProgress.currentChapterIndex)
            } else {
                folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0
            }.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))

            val novel = Novel(
                id = novelId,
                title = novelTitle,
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

        val mergedNovels = dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
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
            saveDatabaseLocked(updatedDb)
            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
            mergedN
        }

        mergedNovels
    }

    suspend fun refreshAllLinkedFolders(
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        val reloadedDb = dbMutex.withLock { loadDatabaseLocked() }
        val activeNovels = reloadedDb.novels.filter { !it.isArchived }
        val archivedNovels = reloadedDb.novels.filter { it.isArchived }

        var lastProgressTime = 0L
        fun reportProgress(cur: Int, total: Int, name: String) {
            val now = System.currentTimeMillis()
            if (now - lastProgressTime >= 80 || cur == total || cur <= 1) {
                lastProgressTime = now
                onProgress(cur, total, name)
            }
        }

        val novelsToRefresh = activeNovels
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
                    } catch (_: Exception) { null }

                    if (targetDocId != null) {
                        if (novel.isLooseRootFiles) {
                            val (_, loose) = querySafChildrenFast(uri, targetDocId)
                            files.addAll(loose)
                        } else {
                            collectSafChildrenRecursivelyFast(uri, targetDocId, files, pathPrefix = "")
                        }
                    } else {
                        val rootDoc = DocumentFile.fromTreeUri(context, uri)
                        if (rootDoc != null && rootDoc.exists()) {
                            if (novel.isLooseRootFiles) {
                                for (f in rootDoc.listFiles()) {
                                    if (f.isFile && isSupported(f.name)) {
                                        files.add(ScannedFileItem(f.uri.toString(), f.name ?: "", f.length()))
                                    }
                                }
                            } else {
                                collectDocumentFiles(rootDoc, files, "")
                            }
                        }
                    }
                    filesPerNovel[novel.id] = files
                    totalFilesToScan += files.size
                } catch (e: Exception) {
                    AppLogger.w("LibraryRepository", "Failed scanning SAF novel ${novel.title} (${novel.id}): ${e.message}", e)
                }
            } else if (treeUriStr.startsWith("/") || treeUriStr.startsWith("file://")) {
                try {
                    val targetPath = novel.subfolderPath ?: treeUriStr.removePrefix("file://")
                    val dir = File(targetPath)
                    if (dir.exists() && dir.isDirectory) {
                        val files = mutableListOf<ScannedFileItem>()
                        if (novel.isLooseRootFiles) {
                            val directFiles = dir.listFiles { f -> f.isFile && isSupported(f.name) } ?: emptyArray()
                            for (f in directFiles) {
                                files.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
                            }
                        } else {
                            val sharesRoot = novel.subfolderPath == null && novelsToRefresh.count { it.rootTreeUri == treeUriStr } > 1
                            if (sharesRoot) {
                                val sub = File(dir, novel.title)
                                if (sub.exists() && sub.isDirectory) {
                                    collectLocalFiles(sub, files)
                                } else {
                                    collectLocalFiles(dir, files)
                                }
                            } else {
                                collectLocalFiles(dir, files)
                            }
                        }
                        filesPerNovel[novel.id] = files
                        totalFilesToScan += files.size
                    }
                } catch (e: Exception) {
                    AppLogger.w("LibraryRepository", "Failed scanning local novel ${novel.title} (${novel.id}): ${e.message}", e)
                }
            }
        }

        var processedFilesCount = 0
        val updatedNovels = mutableListOf<Novel>()
        val updatedChapters = reloadedDb.chapters.toMutableMap()

        for (novel in novelsToRefresh) {
            val files = filesPerNovel[novel.id]
            if (files != null && files.isNotEmpty()) {
                val sortedFiles = files.sortedWith { a, b ->
                    NaturalOrderComparator.compare(a.displayName, b.displayName)
                }
                val existingChaps = reloadedDb.chapters[novel.id] ?: emptyList()
                val existingByUri = existingChaps.associateBy { it.fileUri }
                val existingByTitle = existingChaps.associateBy { it.title }

                val folderProgress = readFolderProgress(novel.subfolderPath ?: novel.rootTreeUri, novel.title, novel.folderDocId)
                val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
                val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

                val rawChaps = sortedFiles.mapIndexed { index, file ->
                    processedFilesCount++
                    reportProgress(processedFilesCount, totalFilesToScan, "${novel.title} / ${file.displayName}")
                    val fTitle = file.displayName.substringBeforeLast('.')
                    val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                    val prog = progressByTitle[fTitle] ?: progressByFile[file.displayName]
                    val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                    val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                    val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                    val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                    Chapter(
                        id = existing?.id ?: generateCanonicalChapterId(novel.id, file.displayName),
                        novelId = novel.id,
                        title = fTitle,
                        fileUri = file.uriStr,
                        orderIndex = orderIdx,
                        sizeBytes = file.sizeBytes,
                        lastReadParagraphIndex = lastPara,
                        lastScrollOffset = lastOffset,
                        isCompleted = isComp
                    )
                }
                val newChaps = deduplicateChapters(rawChaps)
                    .sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                    .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

                val targetChapterId = folderProgress?.currentChapterId ?: novel.currentChapterId
                val targetChapterTitle = folderProgress?.currentChapterTitle
                val resolvedIndex = if (targetChapterId != null) {
                    val idx = newChaps.indexOfFirst { it.id == targetChapterId }
                    if (idx >= 0) idx else novel.currentChapterIndex
                } else if (!targetChapterTitle.isNullOrBlank()) {
                    val idx = newChaps.indexOfFirst { it.title.equals(targetChapterTitle, ignoreCase = true) }
                    if (idx >= 0) idx else novel.currentChapterIndex
                } else {
                    folderProgress?.currentChapterIndex ?: novel.currentChapterIndex
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

        val deduplicatedNovels = dbMutex.withLock {
            val deduplicated = deduplicateNovels(updatedNovels + archivedNovels, updatedChapters)
            val finalDb = reloadedDb.copy(
                novels = deduplicated,
                chapters = updatedChapters
            )
            saveDatabaseLocked(finalDb)
            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryRefreshed)
            deduplicated.filter { !it.isArchived }
        }
        reportProgress(totalFilesToScan, totalFilesToScan, "Refresh complete!")
        deduplicatedNovels
    }

    suspend fun refreshEntireLibrary(
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        AppLogger.i("LibraryRepository", "Refreshing entire library including linked folders and download directories...")
        scanDownloadedWebNovels()
        val refreshed = refreshAllLinkedFolders(onProgress)
        _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryRefreshed)
        refreshed
    }

    suspend fun deleteChapter(
        novelId: String,
        chapterId: String,
        deleteFromDisk: Boolean = false
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val chapterList = currentDb.chapters[novelId]?.toMutableList() ?: return@withContext Pair(null, emptyList())
            val targetChap = chapterList.firstOrNull { it.id == chapterId } ?: return@withContext Pair(null, chapterList)

            if (deleteFromDisk) {
                try {
                    if (targetChap.fileUri.startsWith("/") || targetChap.fileUri.startsWith("file://")) {
                        val f = File(targetChap.fileUri.removePrefix("file://"))
                        if (f.exists()) f.delete()
                    } else if (targetChap.fileUri.startsWith("content://")) {
                        val uri = Uri.parse(targetChap.fileUri)
                        val doc = DocumentFile.fromSingleUri(context, uri)
                        if (doc != null && doc.exists()) {
                            doc.delete()
                        } else {
                            DocumentsContract.deleteDocument(context.contentResolver, uri)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("LibraryRepository", "Failed deleting chapter file ${targetChap.fileUri} from disk", e)
                }
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
            saveDatabaseLocked(updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                writeFolderProgress(
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
                    folderDocId = updatedNovel.folderDocId
                )
            }

            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, reindexedChapters)
        }
    }

    suspend fun reorderChapters(
        novelId: String,
        fromIndex: Int,
        toIndex: Int
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
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
            saveDatabaseLocked(updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                writeFolderProgress(
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
                    folderDocId = updatedNovel.folderDocId
                )
            }

            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, reindexedChapters)
        }
    }

    suspend fun sortChapters(
        novelId: String,
        preset: ChapterSortPreset
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
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
            saveDatabaseLocked(updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                writeFolderProgress(
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
                    folderDocId = updatedNovel.folderDocId
                )
            }

            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, reindexedChapters)
        }
    }

    suspend fun toggleChapterReadStatus(
        novelId: String,
        chapterId: String
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val db = loadDatabaseLocked()
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
            saveDatabaseLocked(updatedDb)

            val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
            if (updatedNovel != null) {
                writeFolderProgress(
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
                    folderDocId = updatedNovel.folderDocId
                )
            }

            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
            Pair(updatedNovel, chapters)
        }
    }

    suspend fun readChapterContent(chapter: Chapter): String = withContext(Dispatchers.IO) {
        val fileUriStr = chapter.fileUri
        val localFile = File(fileUriStr)
        if (localFile.exists() && localFile.isFile) {
            localFile.readText(Charsets.UTF_8)
        } else {
            val uri = Uri.parse(fileUriStr)
            if (uri.scheme == "file") {
                File(uri.path ?: "").readText(Charsets.UTF_8)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("Could not read chapter file at ${chapter.fileUri}")
            }
        }
    }

    suspend fun saveChapterContent(chapter: Chapter, newContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileUriStr = chapter.fileUri
            val localFile = File(fileUriStr)
            if (localFile.exists() && localFile.isFile) {
                localFile.writeText(newContent, Charsets.UTF_8)
                return@withContext true
            }
            val uri = Uri.parse(fileUriStr)
            if (uri.scheme == "file") {
                File(uri.path ?: "").writeText(newContent, Charsets.UTF_8)
                return@withContext true
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(newContent)
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            AppLogger.w("LibraryRepository", "Failed saving content to chapter ${chapter.fileUri}", e)
            false
        }
    }

    suspend fun updateChapterProgress(
        novelId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        isCompleted: Boolean
    ): Novel? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val db = loadDatabaseLocked()
            val chapters = db.chapters[novelId]?.toMutableList() ?: return@withContext null
            if (chapterIndex !in chapters.indices) return@withContext null

            val target = chapters[chapterIndex]
            chapters[chapterIndex] = target.copy(
                lastReadParagraphIndex = paragraphIndex,
                lastScrollOffset = scrollOffset,
                isCompleted = isCompleted || target.isCompleted
            )

            val completedCount = chapters.count { it.isCompleted }
            val targetChapterId = chapters.getOrNull(chapterIndex)?.id
            val updatedNovels = db.novels.map { novel ->
                if (novel.id == novelId) {
                    novel.copy(
                        currentChapterIndex = chapterIndex,
                        currentChapterId = targetChapterId,
                        completedCount = completedCount,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else novel
            }

            val updatedChapters = db.chapters.toMutableMap().apply {
                put(novelId, chapters)
            }

            val targetNovel = updatedNovels.firstOrNull { it.id == novelId }

            saveDatabaseLocked(
                db.copy(
                    novels = updatedNovels,
                    chapters = updatedChapters,
                    lastActiveNovelId = novelId
                )
            )

            if (targetNovel != null) {
                writeFolderProgress(
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
                    folderDocId = targetNovel.folderDocId
                )
            }
            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
            targetNovel
        }
    }

    suspend fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val db = loadDatabaseLocked()
            val targetNovel = db.novels.find { it.id == novelId } ?: return@withContext

            if (deleteFilesFromDisk) {
                deleteNovelFilesFromDisk(targetNovel, db.chapters[novelId])
            }

            val updatedNovels = db.novels.filterNot { it.id == novelId }
            val updatedChapters = db.chapters.toMutableMap().apply {
                remove(novelId)
            }
            val lastActive = if (db.lastActiveNovelId == novelId) updatedNovels.firstOrNull()?.id else db.lastActiveNovelId
            saveDatabaseLocked(db.copy(novels = updatedNovels, chapters = updatedChapters, lastActiveNovelId = lastActive))
            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
        }
    }

    private fun deleteNovelFilesFromDisk(novel: Novel, chapters: List<Chapter>?) {
        try {
            AppLogger.i("LibraryRepository", "Deleting files from disk for novel: ${novel.title} (id=${novel.id}, loose=${novel.isLooseRootFiles}, subfolder=${novel.subfolderPath}, docId=${novel.folderDocId})")

            // 1. If novel is loose root files, DO NOT delete the root folder! Only delete its chapters.
            if (novel.isLooseRootFiles) {
                AppLogger.i("LibraryRepository", "Novel is loose root files: preserving parent folder ${novel.rootTreeUri}")
            } else if (novel.subfolderPath != null) {
                // Partitioned local subfolder: delete ONLY the subfolder, preserving sibling folders and root
                val subDir = File(novel.subfolderPath)
                if (subDir.exists() && subDir.isDirectory) {
                    AppLogger.i("LibraryRepository", "Deleting local subfolder: ${subDir.absolutePath}")
                    subDir.deleteRecursively()
                }
            } else if (novel.folderDocId != null && novel.rootTreeUri.startsWith("content://")) {
                // Partitioned SAF subfolder: delete ONLY the subfolder document, preserving sibling subfolders and root
                try {
                    val treeUri = Uri.parse(novel.rootTreeUri)
                    val subfolderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, novel.folderDocId)
                    AppLogger.i("LibraryRepository", "Deleting SAF subfolder document: ${novel.folderDocId}")
                    val deleted = DocumentsContract.deleteDocument(context.contentResolver, subfolderUri)
                    if (!deleted) {
                        AppLogger.w("LibraryRepository", "SAF deleteDocument returned false for ${novel.folderDocId}")
                    }
                } catch (e: Exception) {
                    AppLogger.w("LibraryRepository", "Failed deleting SAF subfolder document for ${novel.title}", e)
                }
            } else {
                // Not a partitioned subfolder and not loose root files (e.g. single dedicated novel directory)
                if (novel.rootTreeUri.startsWith("/") || novel.rootTreeUri.startsWith("file://")) {
                    val path = novel.rootTreeUri.removePrefix("file://")
                    val dir = File(path)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                } else if (novel.rootTreeUri.startsWith("content://")) {
                    try {
                        val treeUri = Uri.parse(novel.rootTreeUri)
                        val docFile = DocumentFile.fromTreeUri(context, treeUri)
                        docFile?.delete()
                    } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Failed deleting SAF root DocumentFile for ${novel.title}", e)
                    }
                }
            }

            // 2. Delete web novel downloaded directory if this is a web novel
            if (novel.isWebNovel) {
                val safeTitle = WebNovelDownloader.sanitizeFileName(novel.title)
                val dirCandidates = mutableListOf<File>()

                try {
                    val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    dirCandidates.add(File(File(publicDownloads, "NovelReader"), safeTitle))
                    dirCandidates.add(File(File(publicDownloads, "NovelReader"), novel.title))
                } catch (e: Exception) {
                    AppLogger.w("LibraryRepository", "Error resolving public downloads candidate dir", e)
                }

                val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (extDir != null) {
                    dirCandidates.add(File(File(extDir, "NovelReader"), safeTitle))
                    dirCandidates.add(File(File(extDir, "NovelReader"), novel.title))
                }

                val internalRoot = File(context.filesDir, "NovelReader")
                dirCandidates.add(File(internalRoot, safeTitle))
                dirCandidates.add(File(internalRoot, novel.title))

                for (dir in dirCandidates) {
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                }
            }

            // 3. Delete any individual chapter files (especially important for loose files)
            if (chapters != null) {
                for (ch in chapters) {
                    try {
                        if (ch.fileUri.startsWith("/") || ch.fileUri.startsWith("file://")) {
                            val f = File(ch.fileUri.removePrefix("file://"))
                            if (f.exists()) f.delete()
                        } else if (ch.fileUri.startsWith("content://")) {
                            val uri = Uri.parse(ch.fileUri)
                            val doc = DocumentFile.fromSingleUri(context, uri)
                            if (doc != null && doc.exists()) {
                                doc.delete()
                            } else {
                                DocumentsContract.deleteDocument(context.contentResolver, uri)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w("LibraryRepository", "Failed deleting chapter file at ${ch.fileUri}", e)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LibraryRepository", "Failed during deleteNovelFilesFromDisk for ${novel.title}", e)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            saveDatabaseLocked(LibraryDatabase())
            _libraryUpdateEvents.tryEmit(LibraryUpdateEvent.LibraryChanged)
        }
    }

    private fun collectFilesRecursively(
        dir: DocumentFile,
        out: MutableList<DocumentFile>,
        onFileFound: ((DocumentFile) -> Unit)? = null
    ) {
        for (f in dir.listFiles()) {
            if (f.isFile && isSupported(f.name)) {
                out.add(f)
                onFileFound?.invoke(f)
            } else if (f.isDirectory) {
                collectFilesRecursively(f, out, onFileFound)
            }
        }
    }

    fun isSupported(fileName: String?): Boolean {
        if (fileName == null || fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in supportedExtensions
    }

    private fun loadDatabaseLocked(): LibraryDatabase {
        cachedDb?.let { return it }
        val loaded = try {
            if (!dbFile.exists()) LibraryDatabase()
            else {
                val bytes = atomicDbFile.readFully()
                val text = String(bytes, Charsets.UTF_8)
                if (text.isBlank()) LibraryDatabase()
                else json.decodeFromString<LibraryDatabase>(text)
            }
        } catch (e: Exception) {
            AppLogger.e("LibraryRepository", "Error loading library database from disk", e)
            LibraryDatabase()
        }
        cachedDb = loaded
        return loaded
    }

    private fun saveDatabaseLocked(db: LibraryDatabase) {
        cachedDb = db
        var fos: FileOutputStream? = null
        try {
            val text = json.encodeToString(db)
            val bytes = text.toByteArray(Charsets.UTF_8)
            fos = atomicDbFile.startWrite()
            fos.write(bytes)
            atomicDbFile.finishWrite(fos)

            try {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    com.novelarrow.reader.data.backup.BackupRestoreManager.saveLibraryBackup(
                        context,
                        db.novels,
                        db.lastActiveNovelId
                    )
                }
            } catch (e: Exception) {
                AppLogger.w("LibraryRepository", "Error initiating library backup", e)
            }
        } catch (e: Exception) {
            AppLogger.e("LibraryRepository", "Error saving library database", e)
            if (fos != null) {
                atomicDbFile.failWrite(fos)
            }
        }
    }
}
