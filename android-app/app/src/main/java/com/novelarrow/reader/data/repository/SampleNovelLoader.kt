package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.util.SampleQuantumContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal object SampleNovelLoader {

    suspend fun createAndLoadSampleNovel(
        context: Context,
        roomDelegate: LibraryRoomDelegate
    ): Novel = withContext(Dispatchers.IO) {
        LibraryStateManager.dbMutex.withLock {
            val sampleDir = File(context.filesDir, "sample_novel_quantum")
            if (!sampleDir.exists()) sampleDir.mkdirs()

            val ch1File = File(sampleDir, "01_wave_particle_duality.txt")
            val ch2File = File(sampleDir, "02_superposition_schrodinger_cat.txt")
            val ch3File = File(sampleDir, "03_quantum_entanglement.txt")

            ch1File.writeText(SampleQuantumContent.CHAPTER_1)
            ch2File.writeText(SampleQuantumContent.CHAPTER_2)
            ch3File.writeText(SampleQuantumContent.CHAPTER_3)

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

            val currentDb = LibraryStateManager.loadDatabaseLocked(roomDelegate)
            val filteredNovels = currentDb.novels.filter { it.id != novelId }
            val updatedNovels = listOf(novel) + filteredNovels
            val updatedChapters = currentDb.chapters.toMutableMap().apply {
                put(novelId, chapters)
            }

            LibraryStateManager.saveDatabaseLocked(
                roomDelegate,
                currentDb.copy(
                    novels = updatedNovels,
                    chapters = updatedChapters,
                    lastActiveNovelId = novelId
                )
            )

            novel
        }
    }
}
