package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

import android.os.Environment
import com.novelarrow.reader.data.browser.WebNovelDownloader

@Serializable
private data class LibraryDatabase(
    val novels: List<Novel> = emptyList(),
    val chapters: Map<String, List<Chapter>> = emptyMap(),
    val lastActiveNovelId: String? = null
)

class LibraryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dbFile: File
        get() = File(context.filesDir, "novel_library_v1.json")

    private val supportedExtensions = setOf(
        "txt", "md", "markdown", "html", "htm", "xhtml", "xml", "json", "csv", "log", "rtf", "epub", "text", "mhtml"
    )

    suspend fun getNovels(): List<Novel> = withContext(Dispatchers.IO) {
        val currentDb = loadDatabase()
        val deduplicated = deduplicateNovels(currentDb.novels, currentDb.chapters)
        if (deduplicated.size != currentDb.novels.size) {
            saveDatabase(currentDb.copy(novels = deduplicated))
        }
        deduplicated
    }

    private fun deduplicateNovels(
        novels: List<Novel>,
        chaptersMap: Map<String, List<Chapter>>
    ): List<Novel> {
        val grouped = novels.groupBy { it.title.trim().lowercase(java.util.Locale.ROOT) }
        return grouped.values.map { list ->
            if (list.size == 1) list.first()
            else {
                // Find the entry with the most chapters or newest
                list.maxByOrNull { n -> chaptersMap[n.id]?.size ?: n.chapterCount } ?: list.first()
            }
        }
    }

    suspend fun getChapters(novelId: String): List<Chapter> = withContext(Dispatchers.IO) {
        loadDatabase().chapters[novelId] ?: emptyList()
    }

    suspend fun getLastActiveNovelId(): String? = withContext(Dispatchers.IO) {
        loadDatabase().lastActiveNovelId
    }

    suspend fun setLastActiveNovelId(novelId: String?) = withContext(Dispatchers.IO) {
        val current = loadDatabase()
        saveDatabase(current.copy(lastActiveNovelId = novelId))
    }

    suspend fun addDownloadedChapter(
        novelTitle: String,
        chapterFile: File,
        chapterTitle: String,
        chapterNumber: Int,
        sourceUrl: String
    ): Novel = withContext(Dispatchers.IO) {
        val safeTitle = WebNovelDownloader.sanitizeFileName(novelTitle)
        val targetNovelId = "web_novel_${safeTitle}"
        val folder = chapterFile.parentFile ?: WebNovelDownloader.getNovelDownloadDirectory(context, novelTitle)

        val files = (folder.listFiles() ?: emptyArray())
            .filter { it.isFile && isSupported(it.name) }
            .sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }

        val currentDb = loadDatabase()
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
                webSourceUrl = if (file.name == chapterFile.name) sourceUrl else existing?.webSourceUrl
            )
        }

        val completedCount = chapters.count { it.isCompleted }
        val updatedNovel = Novel(
            id = existingNovel?.id ?: targetNovelId,
            title = novelTitle,
            rootTreeUri = folder.absolutePath,
            chapterCount = chapters.size,
            completedCount = completedCount,
            currentChapterIndex = existingNovel?.currentChapterIndex ?: 0,
            lastUpdated = System.currentTimeMillis(),
            isWebNovel = true,
            webUrl = sourceUrl.ifBlank { existingNovel?.webUrl }
        )

        // Filter out any duplicates matching by title case-insensitively or by id
        val updatedNovels = currentDb.novels.filter {
            it.id != updatedNovel.id && !it.title.equals(novelTitle, ignoreCase = true)
        } + updatedNovel

        val updatedChapters = currentDb.chapters.toMutableMap().apply {
            put(updatedNovel.id, chapters)
            if (existingNovel != null && existingNovel.id != updatedNovel.id) {
                remove(existingNovel.id)
            }
        }

        saveDatabase(
            currentDb.copy(
                novels = updatedNovels,
                chapters = updatedChapters,
                lastActiveNovelId = updatedNovel.id
            )
        )

        updatedNovel
    }

    suspend fun scanDownloadedWebNovels(): List<Novel> = withContext(Dispatchers.IO) {
        val currentDb = loadDatabase()
        val novelsByNormTitle = mutableMapOf<String, Novel>()
        val chaptersByNovelId = mutableMapOf<String, MutableList<Chapter>>()

        val rootCandidates = mutableListOf<File>()
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicRoot = File(publicDownloads, "NovelReader")
            if (publicRoot.exists() && publicRoot.isDirectory) rootCandidates.add(publicRoot)
        } catch (_: Exception) {}

        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            val extRoot = File(extDir, "NovelReader")
            if (extRoot.exists() && extRoot.isDirectory) rootCandidates.add(extRoot)
        }

        val internalRoot = File(context.filesDir, "NovelReader")
        if (internalRoot.exists() && internalRoot.isDirectory) rootCandidates.add(internalRoot)

        val seenPaths = mutableSetOf<String>()

        for (root in rootCandidates) {
            val subdirs = root.listFiles { f -> f.isDirectory } ?: continue
            for (sub in subdirs) {
                if (seenPaths.contains(sub.canonicalPath)) continue
                seenPaths.add(sub.canonicalPath)

                val files = (sub.listFiles() ?: emptyArray())
                    .filter { it.isFile && isSupported(it.name) }
                    .sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }

                if (files.isEmpty()) continue

                val dirTitle = sub.name
                val safeTitle = WebNovelDownloader.sanitizeFileName(dirTitle)
                val normKey = dirTitle.trim().lowercase(java.util.Locale.ROOT)
                val novelId = "web_novel_${safeTitle}"

                val existingDbNovel = currentDb.novels.firstOrNull {
                    it.id == novelId || it.title.trim().lowercase(java.util.Locale.ROOT) == normKey
                }
                val existingDbChapters = (if (existingDbNovel != null) currentDb.chapters[existingDbNovel.id] else null)
                    ?: currentDb.chapters[novelId] ?: emptyList()
                val existingByUri = existingDbChapters.associateBy { it.fileUri }
                val existingByTitle = existingDbChapters.associateBy { it.title }

                val existingNovel = novelsByNormTitle[normKey]
                if (existingNovel == null) {
                    val chapters = files.mapIndexed { index, file ->
                        val existing = existingByUri[file.absolutePath] ?: existingByTitle[file.nameWithoutExtension]
                        Chapter(
                            id = existing?.id ?: "${novelId}_${file.name}",
                            novelId = novelId,
                            title = file.nameWithoutExtension,
                            fileUri = file.absolutePath,
                            orderIndex = index,
                            sizeBytes = file.length(),
                            lastReadParagraphIndex = existing?.lastReadParagraphIndex ?: 0,
                            lastScrollOffset = existing?.lastScrollOffset ?: 0,
                            isCompleted = existing?.isCompleted ?: false,
                            webSourceUrl = existing?.webSourceUrl
                        )
                    }.toMutableList()

                    val novel = Novel(
                        id = existingDbNovel?.id ?: novelId,
                        title = dirTitle,
                        rootTreeUri = sub.absolutePath,
                        chapterCount = chapters.size,
                        completedCount = chapters.count { it.isCompleted },
                        currentChapterIndex = existingDbNovel?.currentChapterIndex ?: 0,
                        lastUpdated = existingDbNovel?.lastUpdated ?: System.currentTimeMillis(),
                        isWebNovel = true,
                        webUrl = existingDbNovel?.webUrl
                    )
                    novelsByNormTitle[normKey] = novel
                    chaptersByNovelId[novel.id] = chapters
                } else {
                    val currentChapters = chaptersByNovelId[existingNovel.id] ?: mutableListOf()
                    val existingFileNames = currentChapters.map { File(it.fileUri).name }.toSet()
                    for (file in files) {
                        if (!existingFileNames.contains(file.name)) {
                            val existing = existingByUri[file.absolutePath] ?: existingByTitle[file.nameWithoutExtension]
                            currentChapters.add(
                                Chapter(
                                    id = existing?.id ?: "${existingNovel.id}_${file.name}",
                                    novelId = existingNovel.id,
                                    title = file.nameWithoutExtension,
                                    fileUri = file.absolutePath,
                                    orderIndex = currentChapters.size,
                                    sizeBytes = file.length(),
                                    lastReadParagraphIndex = existing?.lastReadParagraphIndex ?: 0,
                                    lastScrollOffset = existing?.lastScrollOffset ?: 0,
                                    isCompleted = existing?.isCompleted ?: false,
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
            val currentDb = loadDatabase()
            val existingIds = discoveredNovels.map { it.id }.toSet()
            val existingTitles = discoveredNovels.map { it.title.trim().lowercase(java.util.Locale.ROOT) }.toSet()
            val preservedNovels = currentDb.novels.filterNot {
                existingIds.contains(it.id) || existingTitles.contains(it.title.trim().lowercase(java.util.Locale.ROOT))
            }
            val mergedNovels = deduplicateNovels(preservedNovels + discoveredNovels, chaptersByNovelId)
            val mergedChapters = currentDb.chapters.toMutableMap().apply {
                putAll(chaptersByNovelId)
            }
            saveDatabase(currentDb.copy(novels = mergedNovels, chapters = mergedChapters))
            mergedNovels
        } else {
            getNovels()
        }
    }

    suspend fun createAndLoadSampleNovel(): Novel = withContext(Dispatchers.IO) {
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

        val currentDb = loadDatabase()
        val filteredNovels = currentDb.novels.filter { it.id != novelId }
        val updatedNovels = listOf(novel) + filteredNovels
        val updatedChapters = currentDb.chapters.toMutableMap().apply {
            put(novelId, chapters)
        }

        saveDatabase(
            currentDb.copy(
                novels = updatedNovels,
                chapters = updatedChapters,
                lastActiveNovelId = novelId
            )
        )

        novel
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
            e.printStackTrace()
        }
        return Pair(subdirs, files)
    }

    private fun collectSafChildrenRecursivelyFast(
        treeUri: Uri,
        dirDocId: String,
        out: MutableList<ScannedFileItem>
    ) {
        val (subdirs, files) = querySafChildrenFast(treeUri, dirDocId)
        out.addAll(files)
        for ((subDocId, _) in subdirs) {
            collectSafChildrenRecursivelyFast(treeUri, subDocId, out)
        }
    }

    private fun scanDocumentFileFallback(rootDoc: DocumentFile): Pair<Map<String, List<ScannedFileItem>>, List<ScannedFileItem>> {
        val subdirMap = mutableMapOf<String, List<ScannedFileItem>>()
        val looseFiles = mutableListOf<ScannedFileItem>()
        for (f in rootDoc.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                val list = mutableListOf<ScannedFileItem>()
                collectDocumentFiles(f, list)
                if (list.isNotEmpty()) subdirMap[name] = list
            } else if (f.isFile && isSupported(name)) {
                looseFiles.add(ScannedFileItem(f.uri.toString(), name, f.length()))
            }
        }
        return Pair(subdirMap, looseFiles)
    }

    private fun collectDocumentFiles(dir: DocumentFile, out: MutableList<ScannedFileItem>) {
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                collectDocumentFiles(f, out)
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
                val list = mutableListOf<ScannedFileItem>()
                collectLocalFiles(f, list)
                if (list.isNotEmpty()) subdirMap[f.name] = list
            } else if (f.isFile && isSupported(f.name)) {
                looseFiles.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
            }
        }
        return Pair(subdirMap, looseFiles)
    }

    private fun collectLocalFiles(dir: File, out: MutableList<ScannedFileItem>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                collectLocalFiles(f, out)
            } else if (f.isFile && isSupported(f.name)) {
                out.add(ScannedFileItem(f.absolutePath, f.name, f.length()))
            }
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
        val currentDb = loadDatabase()
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

            val chapters = sortedFiles.mapIndexed { index, file ->
                currentProgressIndex++
                reportProgress(currentProgressIndex, totalFilesCount, "$dirName / ${file.displayName}")
                val fTitle = file.displayName.substringBeforeLast('.')
                val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                Chapter(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    novelId = novelId,
                    title = fTitle,
                    fileUri = file.uriStr,
                    orderIndex = index,
                    sizeBytes = file.sizeBytes,
                    lastReadParagraphIndex = existing?.lastReadParagraphIndex ?: 0,
                    lastScrollOffset = existing?.lastScrollOffset ?: 0,
                    isCompleted = existing?.isCompleted ?: false
                )
            }

            val novel = Novel(
                id = novelId,
                title = dirName,
                rootTreeUri = treeUriStr,
                chapterCount = chapters.size,
                completedCount = chapters.count { it.isCompleted },
                currentChapterIndex = existingDbNovel?.currentChapterIndex ?: 0
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

            val chapters = sortedFiles.mapIndexed { index, file ->
                currentProgressIndex++
                reportProgress(currentProgressIndex, totalFilesCount, "$novelTitle / ${file.displayName}")
                val fTitle = file.displayName.substringBeforeLast('.')
                val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                Chapter(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    novelId = novelId,
                    title = fTitle,
                    fileUri = file.uriStr,
                    orderIndex = index,
                    sizeBytes = file.sizeBytes,
                    lastReadParagraphIndex = existing?.lastReadParagraphIndex ?: 0,
                    lastScrollOffset = existing?.lastScrollOffset ?: 0,
                    isCompleted = existing?.isCompleted ?: false
                )
            }

            val novel = Novel(
                id = novelId,
                title = novelTitle,
                rootTreeUri = treeUriStr,
                chapterCount = chapters.size,
                completedCount = chapters.count { it.isCompleted },
                currentChapterIndex = existingDbNovel?.currentChapterIndex ?: 0
            )

            newNovels.add(novel)
            chaptersMap[novelId] = chapters
        }

        reportProgress(totalFilesCount, totalFilesCount, "Saving library database...")

        val mergedNovels = if (newNovels.isNotEmpty()) {
            currentDb.novels.filter { it.rootTreeUri != treeUriStr } + newNovels
        } else {
            currentDb.novels
        }

        val mergedChapters = currentDb.chapters.toMutableMap()
        for ((k, v) in chaptersMap) {
            mergedChapters[k] = v
        }

        val updatedDb = currentDb.copy(
            novels = mergedNovels,
            chapters = mergedChapters,
            lastActiveNovelId = newNovels.firstOrNull()?.id ?: currentDb.lastActiveNovelId
        )
        saveDatabase(updatedDb)

        mergedNovels
    }

    suspend fun refreshAllLinkedFolders(
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        // 1. Scan downloaded web novels first to sync disk changes
        scanDownloadedWebNovels()
        val reloadedDb = loadDatabase()

        var lastProgressTime = 0L
        fun reportProgress(cur: Int, total: Int, name: String) {
            val now = System.currentTimeMillis()
            if (now - lastProgressTime >= 80 || cur == total || cur <= 1) {
                lastProgressTime = now
                onProgress(cur, total, name)
            }
        }

        val novelsToRefresh = reloadedDb.novels
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
                } catch (_: Exception) {}
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

                val newChaps = sortedFiles.mapIndexed { index, file ->
                    processedFilesCount++
                    reportProgress(processedFilesCount, totalFilesToScan, "${novel.title} / ${file.displayName}")
                    val fTitle = file.displayName.substringBeforeLast('.')
                    val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
                    Chapter(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        novelId = novel.id,
                        title = fTitle,
                        fileUri = file.uriStr,
                        orderIndex = index,
                        sizeBytes = file.sizeBytes,
                        lastReadParagraphIndex = existing?.lastReadParagraphIndex ?: 0,
                        lastScrollOffset = existing?.lastScrollOffset ?: 0,
                        isCompleted = existing?.isCompleted ?: false
                    )
                }

                val completedCount = newChaps.count { it.isCompleted }
                val refreshedNovel = novel.copy(
                    chapterCount = newChaps.size,
                    completedCount = completedCount,
                    lastUpdated = System.currentTimeMillis()
                )
                updatedNovels.add(refreshedNovel)
                updatedChapters[novel.id] = newChaps
            } else {
                updatedNovels.add(novel)
            }
        }

        val finalDb = reloadedDb.copy(
            novels = updatedNovels,
            chapters = updatedChapters
        )
        saveDatabase(finalDb)
        reportProgress(totalFilesToScan, totalFilesToScan, "Refresh complete!")
        updatedNovels
    }

    suspend fun deleteChapter(
        novelId: String,
        chapterId: String,
        deleteFromDisk: Boolean = false
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        val currentDb = loadDatabase()
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
        saveDatabase(updatedDb)

        val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
        Pair(updatedNovel, reindexedChapters)
    }

    suspend fun reorderChapters(
        novelId: String,
        fromIndex: Int,
        toIndex: Int
    ): Pair<Novel?, List<Chapter>> = withContext(Dispatchers.IO) {
        val currentDb = loadDatabase()
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
        saveDatabase(updatedDb)

        val updatedNovel = updatedNovels.firstOrNull { it.id == novelId }
        Pair(updatedNovel, reindexedChapters)
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

    suspend fun updateChapterProgress(
        novelId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        isCompleted: Boolean
    ) = withContext(Dispatchers.IO) {
        val db = loadDatabase()
        val chapters = db.chapters[novelId]?.toMutableList() ?: return@withContext
        if (chapterIndex !in chapters.indices) return@withContext

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

        saveDatabase(
            db.copy(
                novels = updatedNovels,
                chapters = updatedChapters,
                lastActiveNovelId = novelId
            )
        )
    }

    suspend fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) = withContext(Dispatchers.IO) {
        val db = loadDatabase()
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
        saveDatabase(db.copy(novels = updatedNovels, chapters = updatedChapters, lastActiveNovelId = lastActive))
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
        saveDatabase(LibraryDatabase())
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

    private fun isSupported(fileName: String?): Boolean {
        if (fileName == null) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in supportedExtensions
    }

    private fun loadDatabase(): LibraryDatabase {
        return try {
            if (!dbFile.exists()) return LibraryDatabase()
            val text = dbFile.readText()
            json.decodeFromString<LibraryDatabase>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            LibraryDatabase()
        }
    }

    private fun saveDatabase(db: LibraryDatabase) {
        try {
            val text = json.encodeToString(db)
            dbFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
