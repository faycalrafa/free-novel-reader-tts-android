package com.novelarrow.reader.playback

import android.content.Context
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object CoordinatorSessionRestorer {

    private const val TAG = "CoordinatorSessionRestorer"

    fun restoreLastHearingSession(
        scope: CoroutineScope,
        navigator: CoordinatorPlaybackNavigator,
        contextProvider: () -> Context?,
        libraryRepositoryProvider: () -> LibraryRepository?,
        settingsRepositoryProvider: () -> SettingsRepository?,
        isPlayingFlow: MutableStateFlow<Boolean>,
        playbackProgressFlow: MutableStateFlow<PlaybackProgress>,
        onSpeakParagraph: () -> Unit,
        updateServiceState: () -> Unit,
        ensureServiceRunning: (Context) -> Unit,
        autoPlay: Boolean = false,
        callback: (() -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            if (navigator.lastActiveSource == PlaybackSource.BROWSER && navigator.lastActiveBrowserParagraphs.isNotEmpty()) {
                val paragraphs = navigator.lastActiveBrowserParagraphs
                val title = navigator.lastActiveBrowserChapterTitle
                val idx = navigator.lastActiveBrowserParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
                withContext(Dispatchers.Main) {
                    navigator.currentSource = PlaybackSource.BROWSER
                    navigator.currentNovelId = null
                    navigator.currentNovelTitle = "Web Reading"
                    navigator.currentChapterId = null
                    navigator.currentChapterTitle = title
                    navigator.currentChapterIndex = 0
                    navigator.totalChaptersCount = 1
                    navigator.currentParagraphs = paragraphs
                    navigator.totalParagraphsCount = paragraphs.size
                    navigator.currentParagraphIndex = idx

                    playbackProgressFlow.update {
                        it.copy(
                            isPlaying = autoPlay,
                            source = PlaybackSource.BROWSER,
                            novelId = null,
                            novelTitle = "Web Reading",
                            chapterId = null,
                            chapterTitle = title,
                            chapterIndex = 0,
                            totalChapters = 1,
                            currentParagraphIndex = idx,
                            totalParagraphs = paragraphs.size,
                            currentParagraphText = paragraphs.getOrNull(idx)?.spokenText ?: ""
                        )
                    }

                    navigator.currentProgressText = "¶ ${idx + 1}/${paragraphs.size}"
                    contextProvider()?.let { ctx -> ensureServiceRunning(ctx) }
                    updateServiceState()

                    if (autoPlay) {
                        isPlayingFlow.value = true
                        onSpeakParagraph()
                    }
                    callback?.invoke()
                }
                return@launch
            }

            val repo = libraryRepositoryProvider()
            if (repo == null) {
                AppLogger.d(TAG, "Cannot restore hearing session: LibraryRepository is null")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }
            val lastActiveId = repo.getLastActiveNovelId()
            val novels = repo.getNovels()
            val targetNovel = (if (lastActiveId != null) novels.firstOrNull { it.id == lastActiveId } else null)
                ?: novels.firstOrNull()
            if (targetNovel == null) {
                AppLogger.d(TAG, "No novel available to restore hearing session")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }
            val chapters = repo.getChapters(targetNovel.id)
            if (chapters.isEmpty()) {
                AppLogger.d(TAG, "No chapters found for novel ${targetNovel.title} during session restore")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }

            val chapIdx = targetNovel.currentChapterIndex.coerceIn(0, chapters.size - 1)
            val chap = chapters[chapIdx]
            val content = try {
                repo.readChapterContent(chap)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to read chapter ${chap.title} content: ${e.message}", e)
                ""
            }
            if (content.isBlank()) {
                AppLogger.w(TAG, "Chapter ${chap.title} has blank content, cannot restore session")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }

            val settings = settingsRepositoryProvider()?.getSettings() ?: com.novelarrow.reader.data.model.ReaderSettings()
            val parsed = ChapterParser.parse(
                content = content,
                fileName = chap.title,
                contentSelector = settings.contentSelector,
                contentSelectorEnabled = settings.contentSelectorEnabled,
                pronunciationRules = settings.pronunciationRules,
                pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                skipWords = settings.skipWords,
                skipWordsEnabled = settings.skipWordsEnabled,
                headerSkipString = settings.headerSkipString,
                headerSkipEnabled = settings.headerSkipEnabled,
                footerSkipString = settings.footerSkipString,
                footerSkipEnabled = settings.footerSkipEnabled,
                headerSkipRules = settings.headerSkipRules,
                footerSkipRules = settings.footerSkipRules,
                folderPath = chap.fileUri.ifBlank { targetNovel.rootTreeUri },
                novelUrl = chap.webSourceUrl ?: targetNovel.webUrl,
                contentSelectors = settings.contentSelectors
            )

            val paraIdx = chap.lastReadParagraphIndex.coerceIn(0, (parsed.size - 1).coerceAtLeast(0))

            withContext(Dispatchers.Main) {
                navigator.currentSource = PlaybackSource.READER
                navigator.currentNovelId = targetNovel.id
                navigator.currentNovelTitle = targetNovel.title
                navigator.currentChapterId = chap.id
                navigator.currentChapterTitle = chap.title
                navigator.currentChapterIndex = chapIdx
                navigator.totalChaptersCount = chapters.size
                navigator.currentParagraphs = parsed
                navigator.totalParagraphsCount = parsed.size
                navigator.currentParagraphIndex = paraIdx

                playbackProgressFlow.update {
                    it.copy(
                        isPlaying = autoPlay,
                        source = PlaybackSource.READER,
                        novelId = targetNovel.id,
                        novelTitle = targetNovel.title,
                        chapterId = chap.id,
                        chapterTitle = chap.title,
                        chapterIndex = chapIdx,
                        totalChapters = chapters.size,
                        currentParagraphIndex = paraIdx,
                        totalParagraphs = parsed.size,
                        currentParagraphText = parsed.getOrNull(paraIdx)?.spokenText ?: ""
                    )
                }

                navigator.currentProgressText = "Ch. ${chapIdx + 1}/${chapters.size} • ¶ ${paraIdx + 1}/${parsed.size}"

                contextProvider()?.let { ctx -> ensureServiceRunning(ctx) }
                updateServiceState()

                if (autoPlay) {
                    isPlayingFlow.value = true
                    onSpeakParagraph()
                }
                callback?.invoke()
            }
        }
    }
}
