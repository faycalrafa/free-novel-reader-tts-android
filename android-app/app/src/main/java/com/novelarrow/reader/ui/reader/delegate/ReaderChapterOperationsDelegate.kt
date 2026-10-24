package com.novelarrow.reader.ui.reader.delegate

import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.repository.ChapterSortPreset
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.ui.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderChapterOperationsDelegate(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val libraryRepository: LibraryRepository,
    private val loadChapterContent: (Chapter, Boolean) -> Unit,
    private val speakAtParagraph: (Int) -> Unit,
    private val updatePlaybackServiceNotification: () -> Unit,
    private val onRefreshLibrary: () -> Unit
) {
    fun openRawChapterEditor() {
        val chapter = uiState.value.currentChapter ?: return
        scope.launch {
            try {
                val content = libraryRepository.readChapterContent(chapter)
                uiState.update {
                    it.copy(
                        rawChapterContent = content,
                        showRawEditor = true
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(toastMessage = "Error loading raw chapter: ${e.message}") }
            }
        }
    }

    fun dismissRawEditor() {
        uiState.update { it.copy(showRawEditor = false, rawChapterContent = null) }
    }

    fun saveRawChapterContent(newContent: String) {
        val chapter = uiState.value.currentChapter ?: return
        scope.launch {
            val success = libraryRepository.saveChapterContent(chapter, newContent)
            if (success) {
                uiState.update {
                    it.copy(
                        toastMessage = "Saved chapter changes successfully",
                        showRawEditor = false,
                        rawChapterContent = null
                    )
                }
                loadChapterContent(chapter, true)
            } else {
                uiState.update { it.copy(toastMessage = "Failed to save chapter content") }
            }
        }
    }

    suspend fun getArchivedChapters(novelId: String): List<Chapter> {
        return libraryRepository.getChapters(novelId)
    }

    suspend fun deleteArchivedChapter(novelId: String, chapter: Chapter, deleteFromDisk: Boolean = false): List<Chapter> {
        val (_, updatedChapters) = libraryRepository.deleteChapter(
            novelId = novelId,
            chapterId = chapter.id,
            deleteFromDisk = deleteFromDisk
        )
        onRefreshLibrary()
        return updatedChapters
    }

    fun deleteChapter(chapter: Chapter, deleteFromDisk: Boolean = false) {
        val targetNovelId = chapter.novelId
        val wasPlaying = uiState.value.isPlaying
        val isDeletingCurrent = uiState.value.currentChapter?.id == chapter.id && uiState.value.currentNovel?.id == targetNovelId

        if (isDeletingCurrent && wasPlaying) {
            AudioPlaybackCoordinator.pause()
        }

        scope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.deleteChapter(
                novelId = targetNovelId,
                chapterId = chapter.id,
                deleteFromDisk = deleteFromDisk
            )

            val updatedNovels = uiState.value.novels.map { nov ->
                if (nov.id == targetNovelId && updatedNovel != null) updatedNovel else nov
            }

            val isTargetCurrentNovel = uiState.value.currentNovel?.id == targetNovelId
            val isTargetInspectedNovel = uiState.value.inspectedNovel?.id == targetNovelId

            if (isDeletingCurrent && isTargetCurrentNovel) {
                if (updatedChapters.isNotEmpty()) {
                    val nextIdx = chapter.orderIndex.coerceIn(0, updatedChapters.size - 1)
                    val nextChapter = updatedChapters[nextIdx]
                    uiState.update {
                        it.copy(
                            novels = updatedNovels,
                            currentNovel = updatedNovel ?: it.currentNovel,
                            chapters = updatedChapters,
                            currentChapter = nextChapter,
                            inspectedNovel = if (isTargetInspectedNovel) updatedNovel ?: it.inspectedNovel else it.inspectedNovel,
                            inspectedChapters = if (isTargetInspectedNovel) updatedChapters else it.inspectedChapters,
                            toastMessage = "Deleted chapter: ${chapter.title}"
                        )
                    }
                    loadChapterContent(nextChapter, false)
                    if (wasPlaying) {
                        speakAtParagraph(0)
                    }
                } else {
                    uiState.update {
                        it.copy(
                            novels = updatedNovels,
                            currentNovel = updatedNovel ?: it.currentNovel,
                            chapters = emptyList(),
                            currentChapter = null,
                            paragraphs = emptyList(),
                            isPlaying = false,
                            inspectedNovel = if (isTargetInspectedNovel) updatedNovel ?: it.inspectedNovel else it.inspectedNovel,
                            inspectedChapters = if (isTargetInspectedNovel) emptyList() else it.inspectedChapters,
                            toastMessage = "Deleted last chapter of novel."
                        )
                    }
                    AudioPlaybackCoordinator.stop()
                }
            } else {
                val currentChap = uiState.value.currentChapter
                val refreshedCurrentChap = if (isTargetCurrentNovel) {
                    updatedChapters.firstOrNull { it.id == currentChap?.id } ?: currentChap
                } else currentChap
                uiState.update {
                    it.copy(
                        novels = updatedNovels,
                        currentNovel = if (isTargetCurrentNovel) updatedNovel ?: it.currentNovel else it.currentNovel,
                        chapters = if (isTargetCurrentNovel) updatedChapters else it.chapters,
                        currentChapter = refreshedCurrentChap,
                        inspectedNovel = if (isTargetInspectedNovel) updatedNovel ?: it.inspectedNovel else it.inspectedNovel,
                        inspectedChapters = if (isTargetInspectedNovel) updatedChapters else it.inspectedChapters,
                        toastMessage = "Deleted chapter: ${chapter.title}"
                    )
                }
                if (isTargetCurrentNovel) {
                    updatePlaybackServiceNotification()
                }
            }
        }
    }

    fun moveChapter(fromIndex: Int, toIndex: Int) {
        val targetNovel = uiState.value.inspectedNovel ?: uiState.value.currentNovel ?: return
        scope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.reorderChapters(
                novelId = targetNovel.id,
                fromIndex = fromIndex,
                toIndex = toIndex
            )
            if (updatedNovel != null) {
                val updatedNovels = uiState.value.novels.map { nov ->
                    if (nov.id == targetNovel.id) updatedNovel else nov
                }
                val isCurrent = uiState.value.currentNovel?.id == targetNovel.id
                val isInspected = uiState.value.inspectedNovel?.id == targetNovel.id
                val currentChap = uiState.value.currentChapter
                val refreshedCurrentChap = if (isCurrent) {
                    updatedChapters.firstOrNull { it.id == currentChap?.id } ?: currentChap
                } else currentChap
                uiState.update {
                    it.copy(
                        novels = updatedNovels,
                        currentNovel = if (isCurrent) updatedNovel else it.currentNovel,
                        chapters = if (isCurrent) updatedChapters else it.chapters,
                        currentChapter = refreshedCurrentChap,
                        inspectedNovel = if (isInspected) updatedNovel else it.inspectedNovel,
                        inspectedChapters = if (isInspected) updatedChapters else it.inspectedChapters
                    )
                }
                if (isCurrent) {
                    updatePlaybackServiceNotification()
                }
            }
        }
    }

    fun sortChapters(preset: ChapterSortPreset) {
        val targetNovel = uiState.value.inspectedNovel ?: uiState.value.currentNovel ?: return
        scope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.sortChapters(
                novelId = targetNovel.id,
                preset = preset
            )
            if (updatedNovel != null) {
                val updatedNovels = uiState.value.novels.map { nov ->
                    if (nov.id == targetNovel.id) updatedNovel else nov
                }
                val isCurrent = uiState.value.currentNovel?.id == targetNovel.id
                val isInspected = uiState.value.inspectedNovel?.id == targetNovel.id
                val currentChap = uiState.value.currentChapter
                val refreshedCurrentChap = if (isCurrent) {
                    updatedChapters.firstOrNull { it.id == currentChap?.id }
                        ?: updatedChapters.getOrNull(updatedNovel.currentChapterIndex)
                        ?: updatedChapters.firstOrNull()
                } else currentChap
                uiState.update {
                    it.copy(
                        novels = updatedNovels,
                        currentNovel = if (isCurrent) updatedNovel else it.currentNovel,
                        chapters = if (isCurrent) updatedChapters else it.chapters,
                        currentChapter = refreshedCurrentChap,
                        inspectedNovel = if (isInspected) updatedNovel else it.inspectedNovel,
                        inspectedChapters = if (isInspected) updatedChapters else it.inspectedChapters
                    )
                }
                if (isCurrent) {
                    updatePlaybackServiceNotification()
                }
            }
        }
    }

    fun toggleChapterReadStatus(chapter: Chapter) {
        val targetNovelId = chapter.novelId
        scope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.toggleChapterReadStatus(
                novelId = targetNovelId,
                chapterId = chapter.id
            )
            if (updatedNovel != null) {
                val updatedNovels = uiState.value.novels.map { nov ->
                    if (nov.id == targetNovelId) updatedNovel else nov
                }
                val isCurrent = uiState.value.currentNovel?.id == targetNovelId
                val isInspected = uiState.value.inspectedNovel?.id == targetNovelId
                val currentChap = uiState.value.currentChapter
                val refreshedCurrentChap = if (isCurrent) {
                    updatedChapters.firstOrNull { it.id == currentChap?.id } ?: currentChap
                } else currentChap
                uiState.update {
                    it.copy(
                        novels = updatedNovels,
                        currentNovel = if (isCurrent) updatedNovel else it.currentNovel,
                        chapters = if (isCurrent) updatedChapters else it.chapters,
                        currentChapter = refreshedCurrentChap,
                        inspectedNovel = if (isInspected) updatedNovel else it.inspectedNovel,
                        inspectedChapters = if (isInspected) updatedChapters else it.inspectedChapters
                    )
                }
            }
        }
    }
}
