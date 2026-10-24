package com.novelarrow.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("SELECT * FROM novels WHERE isArchived = 0 ORDER BY lastUpdated DESC")
    fun observeActiveNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC")
    fun observeAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE isArchived = 1 ORDER BY lastUpdated DESC")
    fun observeArchivedNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE isArchived = 0 ORDER BY lastUpdated DESC")
    suspend fun getActiveNovels(): List<NovelEntity>

    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC")
    suspend fun getAllNovels(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE isArchived = 1 ORDER BY lastUpdated DESC")
    suspend fun getArchivedNovels(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE id = :novelId LIMIT 1")
    suspend fun getNovelById(novelId: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovels(novels: List<NovelEntity>)

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Query("UPDATE novels SET title = :newTitle, lastUpdated = :timestamp WHERE id = :novelId")
    suspend fun updateTitle(novelId: String, newTitle: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET isArchived = :isArchived, lastUpdated = :timestamp WHERE id = :novelId")
    suspend fun setArchived(novelId: String, isArchived: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET currentChapterIndex = :chapterIndex, currentChapterId = :chapterId, completedCount = :completedCount, lastUpdated = :timestamp WHERE id = :novelId")
    suspend fun updateReadingProgress(
        novelId: String,
        chapterIndex: Int,
        chapterId: String?,
        completedCount: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM novels WHERE id = :novelId")
    suspend fun deleteNovelById(novelId: String)

    @Query("DELETE FROM novels")
    suspend fun clearAllNovels()

    @Query("SELECT COUNT(*) FROM novels")
    suspend fun getNovelCount(): Int
}
