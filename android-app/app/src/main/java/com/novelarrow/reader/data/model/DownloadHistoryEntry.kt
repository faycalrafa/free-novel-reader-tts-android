package com.novelarrow.reader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long = 0L,
    val downloadUrl: String = "",
    val novelTitle: String? = null,
    val chapterTitle: String? = null,
    val chapterNumber: Int? = null,
    val isBatchNovelChapter: Boolean = false,
    val mimeType: String = "text/html",
    val timestamp: Long = System.currentTimeMillis()
)
