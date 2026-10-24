package com.novelarrow.reader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novelarrow.reader.data.model.Novel

@Entity(
    tableName = "novels",
    indices = [
        Index(value = ["isArchived"]),
        Index(value = ["lastUpdated"])
    ]
)
data class NovelEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val rootTreeUri: String = "",
    val chapterCount: Int = 0,
    val completedCount: Int = 0,
    val currentChapterIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isWebNovel: Boolean = false,
    val webUrl: String? = null,
    val isArchived: Boolean = false,
    val subfolderPath: String? = null,
    val folderDocId: String? = null,
    val isLooseRootFiles: Boolean = false,
    val currentChapterId: String? = null
) {
    fun toDomain(): Novel = Novel(
        id = id,
        title = title,
        rootTreeUri = rootTreeUri,
        chapterCount = chapterCount,
        completedCount = completedCount,
        currentChapterIndex = currentChapterIndex,
        lastUpdated = lastUpdated,
        isWebNovel = isWebNovel,
        webUrl = webUrl,
        isArchived = isArchived,
        subfolderPath = subfolderPath,
        folderDocId = folderDocId,
        isLooseRootFiles = isLooseRootFiles,
        currentChapterId = currentChapterId
    )
}

fun Novel.toEntity(): NovelEntity = NovelEntity(
    id = id,
    title = title,
    rootTreeUri = rootTreeUri,
    chapterCount = chapterCount,
    completedCount = completedCount,
    currentChapterIndex = currentChapterIndex,
    lastUpdated = lastUpdated,
    isWebNovel = isWebNovel,
    webUrl = webUrl,
    isArchived = isArchived,
    subfolderPath = subfolderPath,
    folderDocId = folderDocId,
    isLooseRootFiles = isLooseRootFiles,
    currentChapterId = currentChapterId
)
