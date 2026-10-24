package com.novelarrow.reader.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.novelarrow.reader.data.db.AppDatabase
import com.novelarrow.reader.data.db.AppMetaEntity
import com.novelarrow.reader.data.db.DatabaseMigrator
import com.novelarrow.reader.data.db.toEntity
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
internal data class LibraryDatabase(
    val novels: List<Novel> = emptyList(),
    val chapters: Map<String, List<Chapter>> = emptyMap(),
    val lastActiveNovelId: String? = null
)

internal class LibraryRoomDelegate(private val context: Context) {

    private val TAG = "LibraryRoomDelegate"
    private val database by lazy { AppDatabase.getInstance(context) }
    private val novelDao get() = database.novelDao()
    private val chapterDao get() = database.chapterDao()
    private val appMetaDao get() = database.appMetaDao()

    fun loadDatabase(): LibraryDatabase {
        return try {
            kotlinx.coroutines.runBlocking {
                DatabaseMigrator.ingestLegacyJsonIfNeeded(context, database)
                val novels = novelDao.getAllNovels().map { it.toDomain() }
                val chaptersMap = mutableMapOf<String, List<Chapter>>()
                for (n in novels) {
                    chaptersMap[n.id] = chapterDao.getChaptersForNovel(n.id).map { it.toDomain() }
                }
                val lastActive = appMetaDao.getMeta("lastActiveNovelId")
                LibraryDatabase(novels = novels, chapters = chaptersMap, lastActiveNovelId = lastActive)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading library from Room database: ${e.message}", e)
            LibraryDatabase()
        }
    }

    fun saveDatabase(db: LibraryDatabase) {
        try {
            kotlinx.coroutines.runBlocking {
                database.withTransaction {
                    val existingNovels = novelDao.getAllNovels()
                    val newNovelIds = db.novels.map { it.id }.toSet()
                    
                    // 1. Delete removed novels and their chapters
                    val removedNovels = existingNovels.filter { it.id !in newNovelIds }
                    for (removed in removedNovels) {
                        chapterDao.deleteChaptersForNovel(removed.id)
                        novelDao.deleteNovelById(removed.id)
                    }

                    // 2. Upsert active/new novels
                    if (db.novels.isNotEmpty()) {
                        novelDao.insertNovels(db.novels.map { it.toEntity() })
                    }

                    // 3. Upsert chapters per novel with targeted diffing
                    for (novel in db.novels) {
                        val currentChapters = db.chapters[novel.id] ?: emptyList()
                        val existingChapterEntities = chapterDao.getChaptersForNovel(novel.id)
                        val currentChapIds = currentChapters.map { it.id }.toSet()

                        // Delete removed chapters within this novel
                        val removedChaps = existingChapterEntities.filter { it.id !in currentChapIds }
                        for (rem in removedChaps) {
                            chapterDao.deleteChapterById(rem.id)
                        }

                        // Upsert new or modified chapters
                        if (currentChapters.isNotEmpty()) {
                            chapterDao.insertChapters(currentChapters.map { it.toEntity() })
                        }
                    }

                    // 4. Update metadata
                    if (db.lastActiveNovelId != null) {
                        appMetaDao.setMeta(AppMetaEntity("lastActiveNovelId", db.lastActiveNovelId))
                    } else {
                        appMetaDao.deleteMeta("lastActiveNovelId")
                    }
                }
            }

            try {
                CoroutineScope(Dispatchers.IO).launch {
                    com.novelarrow.reader.data.backup.BackupRestoreManager.saveLibraryBackup(
                        context,
                        db.novels,
                        db.lastActiveNovelId
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error initiating library backup: ${e.message}", e)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving library to Room database: ${e.message}", e)
        }
    }

    fun deleteChapterById(chapterId: String, reindexedChapters: List<Chapter>, novel: Novel?) {
        try {
            kotlinx.coroutines.runBlocking {
                database.withTransaction {
                    chapterDao.deleteChapterById(chapterId)
                    if (reindexedChapters.isNotEmpty()) {
                        chapterDao.insertChapters(reindexedChapters.map { it.toEntity() })
                    }
                    if (novel != null) {
                        novelDao.insertNovel(novel.toEntity())
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed targeted chapter deletion in Room: ${e.message}", e)
        }
    }

    fun updateParagraphProgress(
        novelId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        isCompleted: Boolean,
        targetChapterId: String?,
        completedCount: Int,
        timestamp: Long
    ) {
        try {
            kotlinx.coroutines.runBlocking {
                chapterDao.updateParagraphProgress(
                    novelId = novelId,
                    chapterIndex = chapterIndex,
                    paragraphIndex = paragraphIndex,
                    scrollOffset = scrollOffset,
                    isCompleted = isCompleted
                )
                novelDao.updateReadingProgress(
                    novelId = novelId,
                    chapterIndex = chapterIndex,
                    chapterId = targetChapterId,
                    completedCount = completedCount,
                    timestamp = timestamp
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating paragraph progress in Room: ${e.message}", e)
        }
    }

    fun deleteNovelDirect(novelId: String, lastActive: String?) {
        try {
            kotlinx.coroutines.runBlocking {
                database.withTransaction {
                    chapterDao.deleteChaptersForNovel(novelId)
                    novelDao.deleteNovelById(novelId)
                    if (lastActive != null) {
                        appMetaDao.setMeta(AppMetaEntity("lastActiveNovelId", lastActive))
                    } else {
                        appMetaDao.deleteMeta("lastActiveNovelId")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting novel from Room database: ${e.message}", e)
        }
    }

    fun getChaptersForNovelDirect(novelId: String): List<Chapter> {
        return try {
            kotlinx.coroutines.runBlocking {
                chapterDao.getChaptersForNovel(novelId).map { it.toDomain() }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error directly getting chapters for $novelId from Room: ${e.message}", e)
            emptyList()
        }
    }
}
