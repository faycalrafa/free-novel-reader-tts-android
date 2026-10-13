package com.novelarrow.reader.data.repository

import android.content.Context
import android.net.Uri
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
        "txt", "md", "markdown", "html", "htm", "xhtml", "xml", "json", "csv", "log", "rtf"
    )

    suspend fun getNovels(): List<Novel> = withContext(Dispatchers.IO) {
        loadDatabase().novels
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

    suspend fun scanRootDirectory(
        treeUri: Uri,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit = { _, _, _ -> }
    ): List<Novel> = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val rootName = rootDoc.name ?: "Root Library"

        onProgress(0, 0, "Inspecting folder: $rootName...")

        val subdirs = mutableListOf<DocumentFile>()
        val looseFiles = mutableListOf<DocumentFile>()

        for (file in rootDoc.listFiles()) {
            if (file.isDirectory) {
                subdirs.add(file)
            } else if (file.isFile && isSupported(file.name)) {
                looseFiles.add(file)
            }
        }

        val totalItems = subdirs.size + (if (looseFiles.isNotEmpty()) 1 else 0)
        var currentProgressIndex = 0

        val newNovels = mutableListOf<Novel>()
        val chaptersMap = mutableMapOf<String, List<Chapter>>()

        // Each subdirectory becomes its own novel playlist
        for (subdir in subdirs) {
            val dirName = subdir.name ?: "Untitled Novel"
            currentProgressIndex++
            onProgress(currentProgressIndex, totalItems, "Scanning novel: $dirName")

            val novelFiles = mutableListOf<DocumentFile>()
            collectFilesRecursively(subdir, novelFiles) { file ->
                onProgress(currentProgressIndex, totalItems, "$dirName / ${file.name ?: ""}")
            }
            if (novelFiles.isEmpty()) continue

            novelFiles.sortWith { a, b ->
                NaturalOrderComparator.compare(a.name ?: "", b.name ?: "")
            }

            val novelId = UUID.randomUUID().toString()
            val chapters = novelFiles.mapIndexed { index, file ->
                Chapter(
                    id = UUID.randomUUID().toString(),
                    novelId = novelId,
                    title = (file.name ?: "Chapter ${index + 1}").substringBeforeLast('.'),
                    fileUri = file.uri.toString(),
                    orderIndex = index,
                    sizeBytes = file.length()
                )
            }

            val novel = Novel(
                id = novelId,
                title = dirName,
                rootTreeUri = treeUri.toString(),
                chapterCount = chapters.size,
                completedCount = 0,
                currentChapterIndex = 0
            )

            newNovels.add(novel)
            chaptersMap[novelId] = chapters
        }

        // Loose files at root
        if (looseFiles.isNotEmpty()) {
            currentProgressIndex++
            onProgress(currentProgressIndex, totalItems, "Scanning root files ($rootName)")

            looseFiles.sortWith { a, b ->
                NaturalOrderComparator.compare(a.name ?: "", b.name ?: "")
            }
            val novelId = UUID.randomUUID().toString()
            val chapters = looseFiles.mapIndexed { index, file ->
                Chapter(
                    id = UUID.randomUUID().toString(),
                    novelId = novelId,
                    title = (file.name ?: "Chapter ${index + 1}").substringBeforeLast('.'),
                    fileUri = file.uri.toString(),
                    orderIndex = index,
                    sizeBytes = file.length()
                )
            }
            val novel = Novel(
                id = novelId,
                title = "$rootName (root files)",
                rootTreeUri = treeUri.toString(),
                chapterCount = chapters.size,
                completedCount = 0,
                currentChapterIndex = 0
            )
            newNovels.add(novel)
            chaptersMap[novelId] = chapters
        }

        onProgress(totalItems, totalItems, "Saving library database...")

        // Merge with existing library
        val currentDb = loadDatabase()
        val mergedNovels = currentDb.novels.filter { it.rootTreeUri != treeUri.toString() } + newNovels
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

    suspend fun deleteNovel(novelId: String) = withContext(Dispatchers.IO) {
        val db = loadDatabase()
        val updatedNovels = db.novels.filter { it.id != novelId }
        val updatedChapters = db.chapters.toMutableMap().apply { remove(novelId) }
        val lastActive = if (db.lastActiveNovelId == novelId) updatedNovels.firstOrNull()?.id else db.lastActiveNovelId
        saveDatabase(db.copy(novels = updatedNovels, chapters = updatedChapters, lastActiveNovelId = lastActive))
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
