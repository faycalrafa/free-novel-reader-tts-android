package com.novelarrow.reader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novelarrow.reader.data.model.Chapter

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["novelId", "orderIndex"]),
        Index(value = ["novelId"])
    ]
)
data class ChapterEntity(
    @PrimaryKey
    val id: String,
    val novelId: String,
    val title: String,
    val fileUri: String,
    val orderIndex: Int,
    val isCompleted: Boolean = false,
    val lastReadParagraphIndex: Int = 0,
    val lastScrollOffset: Int = 0,
    val sizeBytes: Long = 0,
    val webSourceUrl: String? = null
) {
    fun toDomain(): Chapter = Chapter(
        id = id,
        novelId = novelId,
        title = title,
        fileUri = fileUri,
        orderIndex = orderIndex,
        isCompleted = isCompleted,
        lastReadParagraphIndex = lastReadParagraphIndex,
        lastScrollOffset = lastScrollOffset,
        sizeBytes = sizeBytes,
        webSourceUrl = webSourceUrl
    )
}

fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    novelId = novelId,
    title = title,
    fileUri = fileUri,
    orderIndex = orderIndex,
    isCompleted = isCompleted,
    lastReadParagraphIndex = lastReadParagraphIndex,
    lastScrollOffset = lastScrollOffset,
    sizeBytes = sizeBytes,
    webSourceUrl = webSourceUrl
)
