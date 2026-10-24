package com.novelarrow.reader.data.repository

import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.util.AppLogger
import java.io.File
import java.util.Locale

internal object NovelDeduplicator {

    private const val TAG = "NovelDeduplicator"

    fun normalizeForMatching(s: String): String {
        return s.replace("[_\\-\\s]+".toRegex(), " ").trim().lowercase(Locale.ROOT)
    }

    fun deduplicateNovels(
        novels: List<Novel>,
        chaptersMap: Map<String, List<Chapter>>
    ): List<Novel> {
        val grouped = novels.groupBy {
            Triple(
                normalizeForMatching(it.title),
                it.rootTreeUri,
                it.subfolderPath ?: it.folderDocId ?: ""
            )
        }
        return grouped.values.map { list ->
            if (list.size == 1) list.first()
            else {
                val wasArchived = list.any { it.isArchived }
                val chosen = list.maxByOrNull { n -> chaptersMap[n.id]?.size ?: n.chapterCount } ?: list.first()
                if (wasArchived) chosen.copy(isArchived = true) else chosen
            }
        }
    }

    fun deduplicateChapters(chapters: List<Chapter>): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        val grouped = chapters.groupBy { ch ->
            val rawUri = ch.fileUri.removePrefix("file://")
            val fileName = try {
                File(rawUri).name.lowercase(Locale.ROOT)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error resolving filename for $rawUri: ${e.message}", e)
                ""
            }
            val normTitle = normalizeForMatching(ch.title)
            val key = if (fileName.isNotBlank() && fileName.contains(".")) {
                fileName.substringBeforeLast('.')
            } else if (normTitle.isNotBlank()) {
                normTitle
            } else {
                rawUri.lowercase(Locale.ROOT)
            }
            Pair(ch.novelId, key)
        }
        return grouped.values.map { dupList ->
            if (dupList.size == 1) dupList.first()
            else {
                val isComp = dupList.any { it.isCompleted }
                val maxPara = dupList.maxOf { it.lastReadParagraphIndex }
                val maxOffset = dupList.maxOf { it.lastScrollOffset }
                val bestUrl = dupList.firstOrNull { !it.webSourceUrl.isNullOrBlank() }?.webSourceUrl
                val bestUri = dupList.firstOrNull {
                    try {
                        val f = File(it.fileUri.removePrefix("file://"))
                        f.exists() && f.length() > 0
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Error checking file existence for ${it.fileUri}: ${e.message}", e)
                        false
                    }
                }?.fileUri ?: dupList.first().fileUri
                val chosen = dupList.first()
                chosen.copy(
                    isCompleted = isComp,
                    lastReadParagraphIndex = maxPara,
                    lastScrollOffset = maxOffset,
                    webSourceUrl = bestUrl ?: chosen.webSourceUrl,
                    fileUri = bestUri
                )
            }
        }.sortedBy { it.orderIndex }.mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }
    }

    fun generateCanonicalChapterId(novelId: String, fileUriOrName: String): String {
        val rawName = try {
            File(fileUriOrName.removePrefix("file://")).name
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error getting name for $fileUriOrName: ${e.message}", e)
            fileUriOrName
        }
        val cleanName = rawName.substringBeforeLast('.').lowercase(Locale.ROOT).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return "${novelId}_$cleanName"
    }

    fun reconcileAndSortChapters(
        novelId: String,
        novelTitle: String,
        scannedFiles: List<ScannedFileItem>,
        existingChapters: List<Chapter>,
        folderProgress: NovelFolderProgress?,
        onProgressItem: ((index: Int, total: Int, name: String) -> Unit)? = null
    ): List<Chapter> {
        val existingByUri = existingChapters.associateBy { it.fileUri }
        val existingByTitle = existingChapters.associateBy { it.title }
        val progressByTitle = folderProgress?.chapters?.associateBy { it.title } ?: emptyMap()
        val progressByFile = folderProgress?.chapters?.associateBy { it.fileName } ?: emptyMap()

        val sortedFiles = scannedFiles.sortedWith { a, b ->
            com.novelarrow.reader.data.util.NaturalOrderComparator.compare(a.displayName, b.displayName)
        }

        val rawChapters = sortedFiles.mapIndexed { index, file ->
            onProgressItem?.invoke(index + 1, sortedFiles.size, "$novelTitle / ${file.displayName}")
            val fTitle = file.displayName.substringBeforeLast('.')
            val existing = existingByUri[file.uriStr] ?: existingByTitle[fTitle]
            val prog = progressByTitle[fTitle] ?: progressByFile[file.displayName]
            val lastPara = maxOf(existing?.lastReadParagraphIndex ?: 0, prog?.lastReadParagraphIndex ?: 0)
            val lastOffset = maxOf(existing?.lastScrollOffset ?: 0, prog?.lastScrollOffset ?: 0)
            val isComp = (existing?.isCompleted == true) || (prog?.isCompleted == true)
            val orderIdx = prog?.orderIndex ?: existing?.orderIndex ?: index
            Chapter(
                id = existing?.id ?: generateCanonicalChapterId(novelId, file.displayName),
                novelId = novelId,
                title = fTitle,
                fileUri = file.uriStr,
                orderIndex = orderIdx,
                sizeBytes = file.sizeBytes,
                lastReadParagraphIndex = lastPara,
                lastScrollOffset = lastOffset,
                isCompleted = isComp
            )
        }

        return deduplicateChapters(rawChapters)
            .sortedWith { a, b -> com.novelarrow.reader.data.util.NaturalOrderComparator.compare(a.title, b.title) }
            .mapIndexed { idx, ch -> ch.copy(orderIndex = idx) }
    }
}
