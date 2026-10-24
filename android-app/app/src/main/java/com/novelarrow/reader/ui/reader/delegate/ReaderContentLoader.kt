package com.novelarrow.reader.ui.reader.delegate

import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.reader.ReaderUiState
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderContentLoader(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val libraryRepository: LibraryRepository,
    private val playbackDelegateProvider: () -> ReaderPlaybackDelegate
) {
    fun syncActiveProgress(
        novelId: String? = uiState.value.currentNovel?.id,
        chapterIndex: Int,
        chapterId: String? = null,
        paragraphIndex: Int = uiState.value.currentParagraphIndex,
        isCompleted: Boolean = false
    ) {
        if (novelId == null) return
        uiState.update { state ->
            val updatedNovels = state.novels.map { nov ->
                if (nov.id == novelId) nov.copy(
                    currentChapterIndex = chapterIndex,
                    currentChapterId = chapterId ?: nov.currentChapterId
                ) else nov
            }
            val updatedCurrentNovel = if (state.currentNovel?.id == novelId) {
                state.currentNovel.copy(
                    currentChapterIndex = chapterIndex,
                    currentChapterId = chapterId ?: state.currentNovel.currentChapterId
                )
            } else {
                state.currentNovel
            }
            val updatedChapters = state.chapters.map { chap ->
                if ((chapterId != null && chap.id == chapterId) || chap.orderIndex == chapterIndex) {
                    chap.copy(
                        lastReadParagraphIndex = paragraphIndex,
                        isCompleted = isCompleted || chap.isCompleted
                    )
                } else chap
            }
            val updatedCurrentChapter = if (state.currentChapter != null &&
                ((chapterId != null && state.currentChapter.id == chapterId) || state.currentChapter.orderIndex == chapterIndex)
            ) {
                state.currentChapter.copy(
                    lastReadParagraphIndex = paragraphIndex,
                    isCompleted = isCompleted || state.currentChapter.isCompleted
                )
            } else {
                state.currentChapter
            }

            state.copy(
                novels = updatedNovels,
                currentNovel = updatedCurrentNovel,
                chapters = updatedChapters,
                currentChapter = updatedCurrentChapter,
                currentParagraphIndex = paragraphIndex
            )
        }
    }

    suspend fun refreshCurrentNovelState(updatedNovel: Novel) {
        val chapters = libraryRepository.getChapters(updatedNovel.id)
        val curChap = uiState.value.currentChapter
        val newCurrentChap = if (curChap != null) {
            chapters.firstOrNull { it.id == curChap.id } ?: chapters.getOrNull(updatedNovel.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
        } else {
            chapters.getOrNull(updatedNovel.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
        }

        uiState.update {
            it.copy(
                currentNovel = updatedNovel,
                chapters = chapters,
                currentChapter = newCurrentChap ?: it.currentChapter
            )
        }
    }

    fun selectNovel(novel: Novel) {
        scope.launch {
            uiState.update { it.copy(isLoading = true, lastActiveNovelId = novel.id) }
            val freshNovel = libraryRepository.getNovel(novel.id) ?: novel
            uiState.update { it.copy(currentNovel = freshNovel) }
            val chapters = libraryRepository.getChapters(freshNovel.id)
            val activeProgress = AudioPlaybackCoordinator.playbackProgressState.value
            val isPlayingThisNovel = activeProgress.source == PlaybackSource.READER && activeProgress.novelId == freshNovel.id
            val chapterIdx = if (isPlayingThisNovel && activeProgress.chapterIndex in chapters.indices) {
                activeProgress.chapterIndex
            } else if (freshNovel.currentChapterId != null) {
                val idx = chapters.indexOfFirst { it.id == freshNovel.currentChapterId }
                if (idx >= 0) idx else freshNovel.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            } else {
                freshNovel.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            }
            val currentChapter = chapters.getOrNull(chapterIdx)

            uiState.update {
                it.copy(
                    chapters = chapters,
                    currentChapter = currentChapter,
                    isLoading = false
                )
            }

            if (currentChapter != null) {
                loadChapterContent(currentChapter, restoreProgress = true)
            }
        }
    }

    fun saveCurrentChapterProgress() {
        val novel = uiState.value.currentNovel ?: return
        val chapter = uiState.value.currentChapter ?: return
        val currentIdx = uiState.value.currentParagraphIndex
        val total = uiState.value.paragraphs.size
        val isCompleted = total > 0 && currentIdx >= total - 1

        syncActiveProgress(
            novelId = novel.id,
            chapterIndex = chapter.orderIndex,
            chapterId = chapter.id,
            paragraphIndex = currentIdx,
            isCompleted = isCompleted
        )

        scope.launch {
            libraryRepository.updateChapterProgress(
                novelId = novel.id,
                chapterIndex = chapter.orderIndex,
                paragraphIndex = currentIdx,
                scrollOffset = 0,
                isCompleted = isCompleted
            )
        }
    }

    fun selectChapter(index: Int, forceStartAtBeginning: Boolean = false) {
        val currentChaps = uiState.value.chapters
        if (index !in currentChaps.indices) return
        val wasPlaying = uiState.value.isPlaying

        if (wasPlaying) {
            AudioPlaybackCoordinator.pause()
        }

        val currentChap = uiState.value.currentChapter
        if (currentChap != null && currentChap.orderIndex != index) {
            saveCurrentChapterProgress()
        }

        val chapters = uiState.value.chapters
        val chapter = chapters[index]

        val curNovel = uiState.value.currentNovel
        if (curNovel != null) {
            syncActiveProgress(
                novelId = curNovel.id,
                chapterIndex = index,
                chapterId = chapter.id,
                paragraphIndex = if (forceStartAtBeginning) 0 else chapter.lastReadParagraphIndex
            )
        }

        scope.launch {
            loadChapterContent(chapter, restoreProgress = !forceStartAtBeginning)
            if (wasPlaying) {
                val startPara = if (forceStartAtBeginning) 0 else uiState.value.currentParagraphIndex
                playbackDelegateProvider().speakAtParagraph(startPara)
            }
        }
    }

    suspend fun loadChapterContent(chapter: Chapter, restoreProgress: Boolean) {
        uiState.update { it.copy(isLoading = true, currentChapter = chapter) }
        try {
            val content = libraryRepository.readChapterContent(chapter)
            val settings = uiState.value.settings
            val parseResult = withContext(Dispatchers.Default) {
                ChapterParser.parseWithDiagnostics(
                    content = content,
                    fileName = chapter.title,
                    contentSelector = settings.contentSelector,
                    contentSelectorEnabled = settings.contentSelectorEnabled,
                    pronunciationRules = settings.pronunciationRules,
                    pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                    skipWords = settings.skipWords,
                    skipWordsEnabled = settings.skipWordsEnabled,
                    skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols,
                    headerSkipString = settings.headerSkipString,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipString = settings.footerSkipString,
                    footerSkipEnabled = settings.footerSkipEnabled,
                    headerSkipRules = settings.headerSkipRules,
                    footerSkipRules = settings.footerSkipRules,
                    folderPath = chapter.fileUri.ifBlank { uiState.value.currentNovel?.rootTreeUri },
                    novelUrl = chapter.webSourceUrl ?: uiState.value.currentNovel?.webUrl,
                    contentSelectors = settings.contentSelectors,
                    ignoredSelectors = settings.ignoredSelectors
                )
            }
            val rawParagraphs = parseResult.paragraphs
            val paragraphs = ChapterParser.mergeShortParagraphs(rawParagraphs, minWordThreshold = 10)

            val novel = uiState.value.currentNovel
            val activeProgress = AudioPlaybackCoordinator.playbackProgressState.value
            val isPlayingThisChapter = activeProgress.source == PlaybackSource.READER &&
                activeProgress.novelId == novel?.id &&
                (activeProgress.chapterId == chapter.id || activeProgress.chapterIndex == chapter.orderIndex)

            val targetIndex = if (isPlayingThisChapter) {
                activeProgress.currentParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            } else if (restoreProgress) {
                chapter.lastReadParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            } else {
                0
            }

            if (novel != null) {
                syncActiveProgress(
                    novelId = novel.id,
                    chapterIndex = chapter.orderIndex,
                    chapterId = chapter.id,
                    paragraphIndex = targetIndex,
                    isCompleted = chapter.isCompleted
                )
            }

            uiState.update {
                it.copy(
                    paragraphs = paragraphs,
                    currentParagraphIndex = targetIndex,
                    isLoading = false,
                    activeRuleImpacts = parseResult.ruleImpacts
                )
            }

            if (novel != null && !isPlayingThisChapter) {
                libraryRepository.updateChapterProgress(
                    novelId = novel.id,
                    chapterIndex = chapter.orderIndex,
                    paragraphIndex = targetIndex,
                    scrollOffset = chapter.lastScrollOffset,
                    isCompleted = chapter.isCompleted
                )
            }
            playbackDelegateProvider().updatePlaybackServiceNotification()
        } catch (e: Exception) {
            AppLogger.e("ReaderContentLoader", "Failed to load chapter ${chapter.title}: ${e.message}", e)
            uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = "Failed to load chapter: ${e.message}"
                )
            }
        }
    }
}
