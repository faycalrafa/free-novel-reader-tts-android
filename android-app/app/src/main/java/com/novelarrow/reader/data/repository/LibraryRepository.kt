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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileOutputStream

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
    val lastUpdated: Long = System.currentTimeMillis()
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

        fun clearCacheForTesting() {
            cachedDb = null
        }
    }

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

    fun readFolderProgress(folderUriOrPath: String, novelTitle: String): NovelFolderProgress? {
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
                val rootDoc = try { DocumentFile.fromTreeUri(context, uri) } catch (_: Exception) { null }
                val progressFile = rootDoc?.findFile(".progress.json")
                if (progressFile != null && progressFile.exists()) {
                    context.contentResolver.openInputStream(progressFile.uri)?.use { stream ->
                        val text = stream.bufferedReader(Charsets.UTF_8).readText()
                        if (text.isNotBlank()) return json.decodeFromString<NovelFolderProgress>(text)
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
            e.printStackTrace()
        }
        return null
    }

    fun writeFolderProgress(
        folderUriOrPath: String,
        novelTitle: String,
        progress: NovelFolderProgress
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
                val rootDoc = try { DocumentFile.fromTreeUri(context, uri) } catch (_: Exception) { null }
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
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun normalizeForMatching(s: String): String {
        return s.replace("[_\\-\\s]+".toRegex(), " ").trim().lowercase(java.util.Locale.ROOT)
    }

    private fun deduplicateNovels(
        novels: List<Novel>,
        chaptersMap: Map<String, List<Chapter>>
    ): List<Novel> {
        val grouped = novels.groupBy { normalizeForMatching(it.title) }
        return grouped.values.map { list ->
            if (list.size == 1) list.first()
            else {
                val wasArchived = list.any { it.isArchived }
                val chosen = list.maxByOrNull { n -> chaptersMap[n.id]?.size ?: n.chapterCount } ?: list.first()
                if (wasArchived) chosen.copy(isArchived = true) else chosen
            }
        }
    }

    suspend fun getChapters(novelId: String): List<Chapter> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            loadDatabaseLocked().chapters[novelId] ?: emptyList()
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
                webUrl = sourceUrl.ifBlank { existingNovel?.webUrl }
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

                    val chapters = files.mapIndexed { index, file ->
                        val existing = existingByUri[file.absolutePath] ?: existingByTitle[file.nameWithoutExtension]
                        Chapter(
                            id = existing?.id ?: "${targetNovelId}_${file.name}",
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
                    }

                    val updatedNovel = Novel(
                        id = existingNovel?.id ?: targetNovelId,
                        title = novelTitle,
                        rootTreeUri = folder.absolutePath,
                        chapterCount = chapters.size,
                        completedCount = chapters.count { it.isCompleted },
                        currentChapterIndex = existingNovel?.currentChapterIndex ?: 0,
                        lastUpdated = System.currentTimeMillis(),
                        isWebNovel = false,
                        webUrl = existingNovel?.webUrl
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
                    return@withContext
                }
            }
            saveDatabaseLocked(currentDb)
        }
    }

    private fun collectFilesRecursively(dir: File, baseDir: File = dir): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        val children = dir.listFiles() ?: return result
        for (f in children) {
            if (f.isDirectory) {
                result.addAll(collectFilesRecursively(f, baseDir))
            } else if (f.isFile && isSupported(f.name)) {
                val rel = f.relativeTo(baseDir)
                val parentPath = rel.parent
                val displayName = if (!parentPath.isNullOrBlank()) "[$parentPath] ${f.name}" else f.name
                result.add(Pair(f, displayName))
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
                        val chapters = filesWithDisp.mapIndexed { index, (file, dispName) ->
                            val fTitle = dispName.substringBeforeLast('.')
                            val existing = existingByUri[file.absolutePath] ?: existingByTitle[fTitle] ?: existingByTitle[file.nameWithoutExtension]
                            val prog = progressByTitle[fTitle] ?: progressByTitle[file.nameWithoutExtension] ?: progressByFile[file.name]
                            val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                            val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                            val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                            val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
                            Chapter(
                                id = existing?.id ?: "${actualNovelId}_${file.relativeTo(sub).path.replace(File.separatorChar, '_')}",
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
                        }.sortedBy { it.orderIndex }.mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }.toMutableList()

                        val novel = Novel(
                            id = actualNovelId,
                            title = existingDbNovel?.title ?: dirTitle,
                            rootTreeUri = sub.absolutePath,
                            chapterCount = chapters.size,
                            completedCount = chapters.count { it.isCompleted },
                            currentChapterIndex = folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0,
                            lastUpdated = existingDbNovel?.lastUpdated ?: System.currentTimeMillis(),
                            isWebNovel = existingDbNovel?.isWebNovel ?: false,
                            webUrl = existingDbNovel?.webUrl,
                            isArchived = existingDbNovel?.isArchived ?: false
                        )
                        novelsByNormTitle[normKey] = novel
                        chaptersByNovelId[novel.id] = chapters
                    } else {
                        val currentChapters = chaptersByNovelId[existingNovel.id] ?: mutableListOf()
                        val existingFileUris = currentChapters.map { it.fileUri }.toSet()
                        for ((file, dispName) in filesWithDisp) {
                            if (!existingFileUris.contains(file.absolutePath)) {
                                val fTitle = dispName.substringBeforeLast('.')
                                val existing = existingByUri[file.absolutePath] ?: existingByTitle[fTitle] ?: existingByTitle[file.nameWithoutExtension]
                                val prog = progressByTitle[fTitle] ?: progressByTitle[file.nameWithoutExtension] ?: progressByFile[file.name]
                                val lastPara = prog?.lastReadParagraphIndex ?: existing?.lastReadParagraphIndex ?: 0
                                val lastOffset = prog?.lastScrollOffset ?: existing?.lastScrollOffset ?: 0
                                val isComp = prog?.isCompleted ?: existing?.isCompleted ?: false
                                currentChapters.add(
                                    Chapter(
                                        id = existing?.id ?: "${existingNovel.id}_${file.relativeTo(sub).path.replace(File.separatorChar, '_')}",
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
                        currentChapters.sortWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
                        novelsByNormTitle[normKey] = existingNovel.copy(
                            chapterCount = currentChapters.size,
                            completedCount = currentChapters.count { it.isCompleted }
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
            val displayName = if (pathPrefix.isNotBlank()) "[$pathPrefix] ${file.displayName}" else file.displayName
            out.add(file.copy(displayName = displayName))
        }
        for ((subDocId, subName) in subdirs) {
            val nextPrefix = if (pathPrefix.isBlank()) subName else "$pathPrefix/$subName"
            collectSafChildrenRecursivelyFast(treeUri, subDocId, out, nextPrefix)
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
                val nextPrefix = if (pathPrefix.isBlank()) name else "$pathPrefix/$name"
                collectDocumentFiles(f, out, nextPrefix)
            } else if (f.isFile && isSupported(name)) {
                val displayName = if (pathPrefix.isNotBlank()) "[$pathPrefix] $name" else name
                out.add(ScannedFileItem(f.uri.toString(), displayName, f.length()))
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
                    subdirMap[f.name] = recursiveFiles.map { (file, dispName) ->
                        ScannedFileItem(file.absolutePath, dispName, file.length())
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
        for ((f, dispName) in recursiveFiles) {
            out.add(ScannedFileItem(f.absolutePath, dispName, f.length()))
        }
    }

    suspend fun scanRootDirectory(
        treeUri: Uri,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        val treeUriStr = treeUri.toString()
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

        if (treeUriStr.startsWith("/") || treeUriStr.startsWith("file://")) {
            val dir = File(treeUriStr.removePrefix("file://"))
            if (dir.exists() && dir.isDirectory) {
                rootName = dir.name
                val (subdirs, loose) = scanLocalDirectory(dir)
                subdirFilesMap.putAll(subdirs)
                looseFiles.addAll(loose)
            }
        } else {
            val rootDoc = try { DocumentFile.fromTreeUri(context, treeUri) } catch (_: Exception) { null }
            if (rootDoc != null) {
                rootName = rootDoc.name ?: "Imported Folder"
            }

            var scannedViaSafContract = false
            val rootDocId = try {
                if (DocumentsContract.isDocumentUri(context, treeUri)) {
                    DocumentsContract.getDocumentId(treeUri)
                } else {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }
            } catch (_: Exception) { null }

            if (rootDocId != null) {
                try {
                    val (subdirs, files) = querySafChildrenFast(treeUri, rootDocId)
                    if (subdirs.isNotEmpty() || files.isNotEmpty()) {
                        scannedViaSafContract = true
                        looseFiles.addAll(files)
                        for ((subDocId, subName) in subdirs) {
                            val subFiles = mutableListOf<ScannedFileItem>()
                            collectSafChildrenRecursivelyFast(treeUri, subDocId, subFiles)
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

        // 1. Process subdirectories as individual novel playlists
        for ((dirName, novelFiles) in subdirFilesMap) {
            val sortedFiles = novelFiles.sortedWith { a, b ->
                NaturalOrderComparator.compare(a.displayName, b.displayName)
            }

            val existingDbNovel = currentDb.novels.firstOrNull {
                it.rootTreeUri == treeUriStr && it.title == dirName
            }
            val novelId = existingDbNovel?.id ?: UUID.randomUUID().toString()
            val existingDbChapters = currentDb.chapters[novelId] ?: emptyList()
            val existingByUri = existingDbChapters.associateBy { it.fileUri }
            val existingByTitle = existingDbChapters.associateBy { it.title }

            val folderProgress = readFolderProgress(treeUriStr, dirName)
            val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
            val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

            val chapters = sortedFiles.mapIndexed { index, file ->
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
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    novelId = novelId,
                    title = fTitle,
                    fileUri = file.uriStr,
                    orderIndex = orderIdx,
                    sizeBytes = file.sizeBytes,
                    lastReadParagraphIndex = lastPara,
                    lastScrollOffset = lastOffset,
                    isCompleted = isComp
                )
            }.sortedBy { it.orderIndex }.mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

            val novel = Novel(
                id = novelId,
                title = dirName,
                rootTreeUri = treeUriStr,
                chapterCount = chapters.size,
                completedCount = chapters.count { it.isCompleted },
                currentChapterIndex = folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0
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
            val existingDbNovel = currentDb.novels.firstOrNull {
                it.rootTreeUri == treeUriStr && it.title == novelTitle
            }
            val novelId = existingDbNovel?.id ?: UUID.randomUUID().toString()
            val existingDbChapters = currentDb.chapters[novelId] ?: emptyList()
            val existingByUri = existingDbChapters.associateBy { it.fileUri }
            val existingByTitle = existingDbChapters.associateBy { it.title }

            val folderProgress = readFolderProgress(treeUriStr, novelTitle)
            val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
            val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

            val chapters = sortedFiles.mapIndexed { index, file ->
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
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    novelId = novelId,
                    title = fTitle,
                    fileUri = file.uriStr,
                    orderIndex = orderIdx,
                    sizeBytes = file.sizeBytes,
                    lastReadParagraphIndex = lastPara,
                    lastScrollOffset = lastOffset,
                    isCompleted = isComp
                )
            }.sortedBy { it.orderIndex }.mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

            val novel = Novel(
                id = novelId,
                title = novelTitle,
                rootTreeUri = treeUriStr,
                chapterCount = chapters.size,
                completedCount = chapters.count { it.isCompleted },
                currentChapterIndex = folderProgress?.currentChapterIndex ?: existingDbNovel?.currentChapterIndex ?: 0
            )

            newNovels.add(novel)
            chaptersMap[novelId] = chapters
        }

        reportProgress(totalFilesCount, totalFilesCount, "Saving library database...")

        val mergedNovels = dbMutex.withLock {
            val currentDb = loadDatabaseLocked()
            val mergedN = if (newNovels.isNotEmpty()) {
                currentDb.novels.filter { it.rootTreeUri != treeUriStr } + newNovels
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
                    val rootDocId = try {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            DocumentsContract.getDocumentId(uri)
                        } else {
                            DocumentsContract.getTreeDocumentId(uri)
                        }
                    } catch (_: Exception) { null }

                    if (rootDocId != null) {
                        collectSafChildrenRecursivelyFast(uri, rootDocId, files)
                    } else {
                        val rootDoc = DocumentFile.fromTreeUri(context, uri)
                        if (rootDoc != null && rootDoc.exists()) {
                            collectDocumentFiles(rootDoc, files)
                        }
                    }
                    filesPerNovel[novel.id] = files
                    totalFilesToScan += files.size
                } catch (e: Exception) {
                    com.novelarrow.reader.util.AppLogger.w("LibraryRepository", "Failed scanning SAF novel ${novel.title} (${novel.id}): ${e.message}", e)
                }
            } else if (treeUriStr.startsWith("/") || treeUriStr.startsWith("file://")) {
                val dir = File(treeUriStr.removePrefix("file://"))
                if (dir.exists() && dir.isDirectory) {
                    val files = mutableListOf<ScannedFileItem>()
                    collectLocalFiles(dir, files)
                    filesPerNovel[novel.id] = files
                    totalFilesToScan += files.size
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

                val folderProgress = readFolderProgress(novel.rootTreeUri, novel.title)
                val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
                val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

                val newChaps = sortedFiles.mapIndexed { index, file ->
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
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        novelId = novel.id,
                        title = fTitle,
                        fileUri = file.uriStr,
                        orderIndex = orderIdx,
                        sizeBytes = file.sizeBytes,
                        lastReadParagraphIndex = lastPara,
                        lastScrollOffset = lastOffset,
                        isCompleted = isComp
                    )
                }.sortedBy { it.orderIndex }.mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }

                val completedCount = newChaps.count { it.isCompleted }
                val refreshedNovel = novel.copy(
                    chapterCount = newChaps.size,
                    completedCount = completedCount,
                    currentChapterIndex = folderProgress?.currentChapterIndex ?: novel.currentChapterIndex,
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
            deduplicated.filter { !it.isArchived }
        }
        reportProgress(totalFilesToScan, totalFilesToScan, "Refresh complete!")
        deduplicatedNovels
    }

    suspend fun refreshEntireLibrary(
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        com.novelarrow.reader.util.AppLogger.i("LibraryRepository", "Refreshing entire library including linked folders and download directories...")
        refreshAllLinkedFolders(onProgress)
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
                        doc?.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
                    nov.copy(
                        currentChapterIndex = newCurrentIdx.coerceIn(0, (reindexedChapters.size - 1).coerceAtLeast(0)),
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
                    folderUriOrPath = updatedNovel.rootTreeUri,
                    novelTitle = updatedNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = updatedNovel.title,
                        currentChapterIndex = updatedNovel.currentChapterIndex,
                        currentChapterTitle = reindexedChapters.getOrNull(updatedNovel.currentChapterIndex)?.title ?: "",
                        currentParagraphIndex = reindexedChapters.getOrNull(updatedNovel.currentChapterIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = reindexedChapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (_: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        }
                    )
                )
            }

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
                    val aFile = try { File(a.fileUri).name } catch (_: Exception) { a.title }
                    val bFile = try { File(b.fileUri).name } catch (_: Exception) { b.title }
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
                    folderUriOrPath = updatedNovel.rootTreeUri,
                    novelTitle = updatedNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = updatedNovel.title,
                        currentChapterIndex = newActiveIndex,
                        currentChapterTitle = reindexedChapters.getOrNull(newActiveIndex)?.title ?: "",
                        currentParagraphIndex = reindexedChapters.getOrNull(newActiveIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = reindexedChapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (_: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        }
                    )
                )
            }

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
                    folderUriOrPath = updatedNovel.rootTreeUri,
                    novelTitle = updatedNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = updatedNovel.title,
                        currentChapterIndex = updatedNovel.currentChapterIndex,
                        currentChapterTitle = chapters.getOrNull(updatedNovel.currentChapterIndex)?.title ?: "",
                        currentParagraphIndex = chapters.getOrNull(updatedNovel.currentChapterIndex)?.lastReadParagraphIndex ?: 0,
                        chapters = chapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (_: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        }
                    )
                )
            }

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
            e.printStackTrace()
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
            val updatedNovels = db.novels.map { novel ->
                if (novel.id == novelId) {
                    novel.copy(
                        currentChapterIndex = chapterIndex,
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
                    folderUriOrPath = targetNovel.rootTreeUri,
                    novelTitle = targetNovel.title,
                    progress = NovelFolderProgress(
                        novelTitle = targetNovel.title,
                        currentChapterIndex = chapterIndex,
                        currentChapterTitle = chapters.getOrNull(chapterIndex)?.title ?: "",
                        currentParagraphIndex = paragraphIndex,
                        chapters = chapters.map { ch ->
                            ChapterProgressEntry(
                                title = ch.title,
                                fileName = try { File(ch.fileUri).name } catch (_: Exception) { ch.title },
                                lastReadParagraphIndex = ch.lastReadParagraphIndex,
                                lastScrollOffset = ch.lastScrollOffset,
                                isCompleted = ch.isCompleted,
                                orderIndex = ch.orderIndex
                            )
                        }
                    )
                )
            }
            targetNovel
        }
    }

    suspend fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val db = loadDatabaseLocked()
            val targetNovel = db.novels.find { it.id == novelId }
            val normTargetTitle = targetNovel?.title?.trim()?.lowercase(java.util.Locale.ROOT)

            // Match target novel and any duplicates sharing the same title
            val matchingNovels = db.novels.filter {
                it.id == novelId || (normTargetTitle != null && it.title.trim().lowercase(java.util.Locale.ROOT) == normTargetTitle)
            }
            val matchingIds = matchingNovels.map { it.id }.toSet()

            // Only delete files on disk if explicitly requested by user (default is false)
            if (deleteFilesFromDisk) {
                for (novel in matchingNovels) {
                    deleteNovelFilesFromDisk(novel, db.chapters[novel.id])
                }
            }

            val updatedNovels = db.novels.filterNot { matchingIds.contains(it.id) }
            val updatedChapters = db.chapters.toMutableMap().apply {
                for (id in matchingIds) remove(id)
            }
            val lastActive = if (matchingIds.contains(db.lastActiveNovelId)) updatedNovels.firstOrNull()?.id else db.lastActiveNovelId
            saveDatabaseLocked(db.copy(novels = updatedNovels, chapters = updatedChapters, lastActiveNovelId = lastActive))
        }
    }

    private fun deleteNovelFilesFromDisk(novel: Novel, chapters: List<Chapter>?) {
        try {
            // 1. If novel has rootTreeUri pointing to local file path
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
                } catch (_: Exception) {}
            }

            // 2. Delete from standard NovelReader download locations by title
            val safeTitle = WebNovelDownloader.sanitizeFileName(novel.title)
            val dirCandidates = mutableListOf<File>()

            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dirCandidates.add(File(File(publicDownloads, "NovelReader"), safeTitle))
                dirCandidates.add(File(File(publicDownloads, "NovelReader"), novel.title))
            } catch (_: Exception) {}

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

            // 3. Delete any individual chapter files
            if (chapters != null) {
                for (ch in chapters) {
                    try {
                        if (ch.fileUri.startsWith("/") || ch.fileUri.startsWith("file://")) {
                            val f = File(ch.fileUri.removePrefix("file://"))
                            if (f.exists()) f.delete()
                        } else if (ch.fileUri.startsWith("content://")) {
                            val uri = Uri.parse(ch.fileUri)
                            val doc = DocumentFile.fromSingleUri(context, uri)
                            doc?.delete()
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            saveDatabaseLocked(LibraryDatabase())
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
            e.printStackTrace()
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
            } catch (_: Exception) {}
        } catch (e: Exception) {
            e.printStackTrace()
            if (fos != null) {
                atomicDbFile.failWrite(fos)
            }
        }
    }
}
