package com.novelarrow.reader.data.repository

import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDeduplicationTest {

    @Test
    fun testGenerateCanonicalChapterIdIsDeterministic() {
        val novelId = "novel_123"
        val fileName1 = "Chapter 001 - The Beginning.txt"
        val fileName2 = "chapter 001 - the beginning.txt"

        val id1 = LibraryRepository.generateCanonicalChapterId(novelId, fileName1)
        val id2 = LibraryRepository.generateCanonicalChapterId(novelId, fileName2)

        assertEquals("Canonical chapter ID must normalize case and produce deterministic output", id1, id2)
        assertTrue("Canonical chapter ID must start with novelId prefix", id1.startsWith("novel_123_"))

        val diffNovelId = LibraryRepository.generateCanonicalChapterId("novel_456", fileName1)
        assertNotEquals("Different novel IDs must produce different chapter IDs", id1, diffNovelId)
    }

    @Test
    fun testDeduplicateChaptersCollapsesMultiRootDuplicates() {
        val novelId = "web_novel_renegade_immortal"

        // Emulate the duplicate files discovered across getExternalFilesDir, MediaStore, and filesDir
        val chapterList = listOf(
            Chapter(
                id = "${novelId}_1_ext",
                novelId = novelId,
                title = "Chapter 1",
                fileUri = "/storage/emulated/0/Android/data/com.novelarrow.reader/files/Download/NovelReader/Renegade/Chapter_1.txt",
                orderIndex = 0,
                lastReadParagraphIndex = 10,
                isCompleted = false
            ),
            Chapter(
                id = "${novelId}_1_public",
                novelId = novelId,
                title = "Chapter 1",
                fileUri = "/storage/emulated/0/Download/NovelReader/Renegade/Chapter_1.txt",
                orderIndex = 0,
                lastReadParagraphIndex = 45,
                isCompleted = true
            ),
            Chapter(
                id = "${novelId}_1_internal",
                novelId = novelId,
                title = "Chapter 1",
                fileUri = "/data/user/0/com.novelarrow.reader/files/NovelReader/Renegade/Chapter_1.txt",
                orderIndex = 0,
                lastReadParagraphIndex = 5,
                isCompleted = false
            ),
            Chapter(
                id = "${novelId}_2_public",
                novelId = novelId,
                title = "Chapter 2",
                fileUri = "/storage/emulated/0/Download/NovelReader/Renegade/Chapter_2.txt",
                orderIndex = 1,
                lastReadParagraphIndex = 0,
                isCompleted = false
            )
        )

        val deduplicated = LibraryRepository.deduplicateChapters(chapterList)

        // Only Chapter 1 and Chapter 2 should remain
        assertEquals("Multi-root duplicate chapters must collapse into exactly 2 canonical chapters", 2, deduplicated.size)

        val chap1 = deduplicated.first { it.title == "Chapter 1" }
        // Should preserve the highest progress and completed status
        assertEquals(45, chap1.lastReadParagraphIndex)
        assertTrue(chap1.isCompleted)

        val chap2 = deduplicated.first { it.title == "Chapter 2" }
        assertEquals(0, chap2.lastReadParagraphIndex)
    }

    @Test
    fun testDeduplicateNovelsCollapsesDuplicateNovelMetadata() {
        val n1 = Novel(
            id = "n_1",
            title = "Martial World",
            rootTreeUri = "/storage/downloads/MartialWorld",
            subfolderPath = "/storage/downloads/MartialWorld",
            chapterCount = 10,
            completedCount = 2,
            currentChapterIndex = 3
        )
        val n2 = Novel(
            id = "n_2",
            title = "Martial World",
            rootTreeUri = "/storage/downloads/MartialWorld",
            subfolderPath = "/storage/downloads/MartialWorld",
            chapterCount = 12,
            completedCount = 5,
            currentChapterIndex = 6
        )
        val nDifferent = Novel(
            id = "n_3",
            title = "A Will Eternal",
            rootTreeUri = "/storage/downloads/AWillEternal",
            subfolderPath = "/storage/downloads/AWillEternal",
            chapterCount = 5,
            completedCount = 1,
            currentChapterIndex = 1
        )

        val chaptersMap = mapOf(
            "n_1" to listOf(Chapter(id = "c1", novelId = "n_1", title = "C1", fileUri = "/f1", orderIndex = 0)),
            "n_2" to listOf(Chapter(id = "c1", novelId = "n_2", title = "C1", fileUri = "/f1", orderIndex = 0), Chapter(id = "c2", novelId = "n_2", title = "C2", fileUri = "/f2", orderIndex = 1)),
            "n_3" to listOf(Chapter(id = "c3", novelId = "n_3", title = "C3", fileUri = "/f3", orderIndex = 0))
        )

        val deduplicated = LibraryRepository.deduplicateNovels(listOf(n1, n2, nDifferent), chaptersMap)

        assertEquals("Duplicates with same title + rootTreeUri + subfolderPath must collapse", 2, deduplicated.size)
        val mw = deduplicated.first { it.title == "Martial World" }
        // It should take the one with higher chapter count / progress
        assertEquals(12, mw.chapterCount)
        assertEquals(5, mw.completedCount)
        assertEquals(6, mw.currentChapterIndex)
    }
}
