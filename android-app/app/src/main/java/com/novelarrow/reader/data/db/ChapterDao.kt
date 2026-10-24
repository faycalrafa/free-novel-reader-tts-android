package com.novelarrow.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC")
    fun observeChapters(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC")
    suspend fun getChaptersForNovel(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getChapterById(chapterId: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("""
        UPDATE chapters 
        SET lastReadParagraphIndex = :paragraphIndex, 
            lastScrollOffset = :scrollOffset, 
            isCompleted = CASE WHEN :isCompleted THEN 1 ELSE isCompleted END 
        WHERE novelId = :novelId AND orderIndex = :chapterIndex
    """)
    suspend fun updateParagraphProgress(
        novelId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        isCompleted: Boolean
    )

    @Query("UPDATE chapters SET isCompleted = :isCompleted WHERE id = :chapterId")
    suspend fun setChapterCompleted(chapterId: String, isCompleted: Boolean)

    @Query("DELETE FROM chapters WHERE id = :chapterId")
    suspend fun deleteChapterById(chapterId: String)

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteChaptersForNovel(novelId: String)

    @Query("DELETE FROM chapters")
    suspend fun clearAllChapters()

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId AND isCompleted = 1")
    suspend fun getCompletedChapterCount(novelId: String): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId")
    suspend fun getChapterCount(novelId: String): Int
}
