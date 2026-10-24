package com.novelarrow.reader.ui.reader.delegate

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.ui.reader.ReaderUiState
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderNovelOperationsDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val libraryRepository: LibraryRepository,
    private val selectNovel: (Novel) -> Unit,
    private val selectChapter: (Int) -> Unit,
    private val refreshCurrentNovelState: suspend (Novel) -> Unit
) {
    fun scanRootFolder(treeUri: Uri) {
        scope.launch {
            uiState.update {
                it.copy(
                    isLibraryScanning = true,
                    scanProgress = 0f,
                    scanCurrentIndex = 0,
                    scanTotalItems = 0,
                    scanCurrentItem = "Accessing folder..."
                )
            }
            try {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    application.contentResolver.takePersistableUriPermission(treeUri, flags)
                } catch (_: Exception) {
                    try {
                        application.contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }

                val novels = libraryRepository.scanRootDirectory(treeUri) { current, total, name ->
                    val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    uiState.update {
                        it.copy(
                            scanProgress = progress.coerceIn(0f, 1f),
                            scanCurrentIndex = current,
                            scanTotalItems = total,
                            scanCurrentItem = name
                        )
                    }
                }

                uiState.update {
                    it.copy(
                        novels = novels,
                        isLibraryScanning = false,
                        toastMessage = if (novels.isNotEmpty()) {
                            "Loaded ${novels.size} playlists from folder"
                        } else {
                            "No supported chapters found in selected folder."
                        }
                    )
                }
                if (novels.isNotEmpty()) {
                    selectNovel(novels.first())
                }
            } catch (e: Exception) {
                AppLogger.e("ReaderNovelOps", "Error importing folder: ${e.message}", e)
                uiState.update {
                    it.copy(
                        isLibraryScanning = false,
                        toastMessage = "Error importing folder: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadSampleNovel() {
        scope.launch {
            uiState.update { it.copy(isLoading = true) }
            val sampleNovel = libraryRepository.createAndLoadSampleNovel()
            val novels = libraryRepository.getNovels()
            uiState.update {
                it.copy(
                    novels = novels,
                    isLoading = false,
                    toastMessage = "Loaded sample: Introduction to Quantum Mechanics"
                )
            }
            selectNovel(sampleNovel)
        }
    }

    fun inspectNovel(novel: Novel) {
        scope.launch {
            try {
                val chapters = libraryRepository.getChapters(novel.id)
                uiState.update {
                    it.copy(
                        inspectedNovel = novel,
                        inspectedChapters = chapters
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ReaderNovelOps", "Failed to inspect novel ${novel.title}: ${e.message}", e)
            }
        }
    }

    fun selectChapterFromInspected(index: Int) {
        val novel = uiState.value.inspectedNovel
        val chapters = uiState.value.inspectedChapters
        if (novel != null && chapters.isNotEmpty() && index in chapters.indices) {
            uiState.update {
                it.copy(
                    currentNovel = novel,
                    chapters = chapters
                )
            }
            selectChapter(index)
        } else {
            selectChapter(index)
        }
    }

    fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) {
        val isCurrent = uiState.value.currentNovel?.id == novelId
        if (isCurrent) {
            AudioPlaybackCoordinator.stop()
        }
        scope.launch {
            val targetNovelTitle = uiState.value.novels.firstOrNull { it.id == novelId }?.title
                ?: uiState.value.archivedNovels.firstOrNull { it.id == novelId }?.title
                ?: "Folder"

            libraryRepository.deleteNovel(novelId, deleteFilesFromDisk)

            val remainingNovels = libraryRepository.getNovels()
            val remainingArchived = libraryRepository.getArchivedNovels()
            val isInspected = uiState.value.inspectedNovel?.id == novelId

            if (isCurrent) {
                if (remainingNovels.isNotEmpty()) {
                    val nextNovel = remainingNovels.first()
                    uiState.update {
                        it.copy(
                            novels = remainingNovels,
                            archivedNovels = remainingArchived,
                            inspectedNovel = if (isInspected) null else it.inspectedNovel,
                            inspectedChapters = if (isInspected) emptyList() else it.inspectedChapters,
                            toastMessage = "Removed folder: $targetNovelTitle"
                        )
                    }
                    selectNovel(nextNovel)
                } else {
                    uiState.update {
                        it.copy(
                            novels = emptyList(),
                            archivedNovels = remainingArchived,
                            currentNovel = null,
                            chapters = emptyList(),
                            currentChapter = null,
                            paragraphs = emptyList(),
                            currentParagraphIndex = 0,
                            isPlaying = false,
                            inspectedNovel = null,
                            inspectedChapters = emptyList(),
                            toastMessage = "Library is now empty."
                        )
                    }
                }
            } else {
                uiState.update {
                    it.copy(
                        novels = remainingNovels,
                        archivedNovels = remainingArchived,
                        inspectedNovel = if (isInspected) null else it.inspectedNovel,
                        inspectedChapters = if (isInspected) emptyList() else it.inspectedChapters,
                        toastMessage = "Removed folder: $targetNovelTitle"
                    )
                }
            }
        }
    }

    fun archiveNovel(novel: Novel) {
        val wasPlaying = uiState.value.isPlaying
        val isCurrent = uiState.value.currentNovel?.id == novel.id
        if (isCurrent && wasPlaying) {
            AudioPlaybackCoordinator.pause()
        }
        val isInspected = uiState.value.inspectedNovel?.id == novel.id
        if (isInspected) {
            uiState.update { it.copy(inspectedNovel = null, inspectedChapters = emptyList()) }
        }
        scope.launch {
            libraryRepository.archiveNovel(novel.id)
            refreshLibrary()
            uiState.update { it.copy(toastMessage = "Archived '${novel.title}'") }
        }
    }

    fun restoreNovel(novel: Novel) {
        scope.launch {
            libraryRepository.restoreNovel(novel.id)
            refreshLibrary(targetNovelId = novel.id)
            uiState.update { it.copy(toastMessage = "Restored '${novel.title}' to library") }
        }
    }

    fun updateNovelTitle(novelId: String, newTitle: String) {
        scope.launch {
            libraryRepository.updateNovelTitle(novelId, newTitle)
            val isInspected = uiState.value.inspectedNovel?.id == novelId
            val isCurrent = uiState.value.currentNovel?.id == novelId
            val updatedNovels = libraryRepository.getNovels()
            val target = updatedNovels.firstOrNull { it.id == novelId }
            uiState.update {
                it.copy(
                    novels = updatedNovels,
                    currentNovel = if (isCurrent && target != null) target else it.currentNovel,
                    inspectedNovel = if (isInspected && target != null) target else it.inspectedNovel
                )
            }
        }
    }

    fun refreshLinkedFolders() {
        scope.launch {
            uiState.update {
                it.copy(
                    isLibraryScanning = true,
                    scanProgress = 0f,
                    scanCurrentIndex = 0,
                    scanTotalItems = 0,
                    scanCurrentItem = "Discovering library files..."
                )
            }
            val novels = libraryRepository.refreshEntireLibrary { current, total, name ->
                val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                uiState.update {
                    it.copy(
                        scanProgress = progress.coerceIn(0f, 1f),
                        scanCurrentIndex = current,
                        scanTotalItems = total,
                        scanCurrentItem = name
                    )
                }
            }
            val archived = libraryRepository.getArchivedNovels()
            uiState.update {
                it.copy(
                    novels = novels,
                    archivedNovels = archived,
                    isLibraryScanning = false,
                    toastMessage = "Refreshed ${novels.size} novels"
                )
            }
            uiState.value.currentNovel?.let { cur ->
                val updated = novels.firstOrNull { it.id == cur.id }
                if (updated != null) {
                    refreshCurrentNovelState(updated)
                }
            }
        }
    }

    fun clearAllData() {
        scope.launch {
            libraryRepository.clearAll()
            uiState.update {
                ReaderUiState(settings = it.settings)
            }
        }
    }

    fun refreshLibrary(targetNovelId: String? = null) {
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }
                val allNovels = libraryRepository.refreshEntireLibrary()
                val unarchivedNovels = allNovels.filter { !it.isArchived }
                val archivedNovels = libraryRepository.getArchivedNovels()

                val current = uiState.value.currentNovel
                val updatedCurrent = if (current != null) {
                    allNovels.firstOrNull { it.id == current.id } ?: current
                } else null

                val updatedChapters = if (updatedCurrent != null) {
                    libraryRepository.getChapters(updatedCurrent.id)
                } else {
                    uiState.value.chapters
                }

                val currentChap = uiState.value.currentChapter
                val updatedChap = if (currentChap != null && updatedChapters.isNotEmpty()) {
                    updatedChapters.firstOrNull { it.id == currentChap.id }
                        ?: updatedChapters.firstOrNull { it.title.equals(currentChap.title, ignoreCase = true) }
                        ?: updatedChapters.getOrNull(updatedCurrent?.currentChapterIndex ?: 0)
                } else {
                    updatedChapters.getOrNull(updatedCurrent?.currentChapterIndex ?: 0)
                }

                uiState.update {
                    it.copy(
                        novels = unarchivedNovels,
                        archivedNovels = archivedNovels,
                        currentNovel = updatedCurrent,
                        chapters = updatedChapters,
                        currentChapter = updatedChap,
                        isLoading = false
                    )
                }

                if (targetNovelId != null) {
                    val target = unarchivedNovels.firstOrNull { it.id == targetNovelId }
                    if (target != null) {
                        selectNovel(target)
                    }
                } else if (uiState.value.currentNovel == null && unarchivedNovels.isNotEmpty()) {
                    selectNovel(unarchivedNovels.first())
                }
            } catch (e: Exception) {
                AppLogger.e("ReaderNovelOps", "Error during library refresh: ${e.message}", e)
                uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
