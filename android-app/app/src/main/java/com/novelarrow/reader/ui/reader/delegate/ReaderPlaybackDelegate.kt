package com.novelarrow.reader.ui.reader.delegate

import android.app.Application
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderPlaybackDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val settingsRepository: SettingsRepository,
    private val saveCurrentChapterProgress: () -> Unit,
    private val syncActiveProgress: (novelId: String?, chapterIndex: Int, chapterId: String?, paragraphIndex: Int, isCompleted: Boolean) -> Unit,
    private val selectChapter: (index: Int, forceStartAtBeginning: Boolean) -> Unit,
    private val reloadCurrentChapter: (Chapter) -> Unit
) {
    fun playTts(startIndex: Int? = null) {
        val state = uiState.value
        val novel = state.currentNovel ?: return
        val chapter = state.currentChapter ?: return
        val paragraphs = state.paragraphs
        if (paragraphs.none { it.spokenText.isNotBlank() }) {
            uiState.update {
                it.copy(
                    flashHandleRed = true,
                    isTtsDockVisible = true,
                    toastMessage = "No text detected. Check if filters or selectors are misconfigured."
                )
            }
            return
        }

        val targetIndex = (startIndex ?: state.currentParagraphIndex).coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        uiState.update { it.copy(isPlaying = true, isTtsDockVisible = true, flashHandleRed = false, currentParagraphIndex = targetIndex) }

        AudioPlaybackCoordinator.playReaderChapter(
            context = application,
            novelId = novel.id,
            novelTitle = novel.title,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            chapterIndex = chapter.orderIndex,
            totalChapters = state.chapters.size,
            paragraphs = paragraphs,
            startParagraphIndex = targetIndex,
            autoNext = state.settings.autoNextChapter || state.isAutoLoopActive,
            rate = state.settings.speechRate,
            pitch = state.settings.speechPitch,
            voiceName = state.settings.selectedVoiceName,
            isPreMerged = true
        )
    }

    fun resetFlashHandleRed() {
        uiState.update { it.copy(flashHandleRed = false) }
    }

    fun toggleActiveRule(rule: RuleImpact, enabled: Boolean) {
        scope.launch {
            when (rule.ruleType) {
                RuleType.CONTENT_CONTAINER, RuleType.CONTENT_SELECTOR -> {
                    settingsRepository.toggleContentSelector(rule.id, enabled)
                }
                RuleType.IGNORED_SELECTOR -> {
                    val cleanSel = rule.name
                    if (enabled) {
                        settingsRepository.addBrowserIgnoredSelector(cleanSel)
                    } else {
                        settingsRepository.removeBrowserIgnoredSelector(cleanSel)
                    }
                }
                RuleType.SKIP_WORD -> {
                    settingsRepository.toggleSkipWord(rule.id, enabled)
                }
                RuleType.PRONUNCIATION -> {
                    settingsRepository.togglePronunciationRule(rule.id, enabled)
                }
                RuleType.HEADER_SKIP -> {
                    settingsRepository.setHeaderSkipEnabled(enabled)
                }
                RuleType.FOOTER_SKIP -> {
                    settingsRepository.setFooterSkipEnabled(enabled)
                }
            }
            val currentChap = uiState.value.currentChapter
            if (currentChap != null) {
                reloadCurrentChapter(currentChap)
            }
        }
    }

    fun pauseTts() {
        AudioPlaybackCoordinator.pause()
        uiState.update { it.copy(isPlaying = false) }
    }

    fun togglePlayPause() {
        if (uiState.value.isPlaying) pauseTts() else playTts()
    }

    fun stopTts() {
        AudioPlaybackCoordinator.stop()
        uiState.update { it.copy(isPlaying = false, isTtsDockVisible = false) }
    }

    fun setSleepTimer(minutes: Int) {
        AudioPlaybackCoordinator.setSleepTimer(minutes)
    }

    fun seekToParagraph(index: Int) {
        speakAtParagraph(index)
    }

    fun speakAtParagraph(index: Int) {
        val state = uiState.value
        val paragraphs = state.paragraphs
        if (paragraphs.isEmpty() || index !in paragraphs.indices) return
        val novel = state.currentNovel ?: return
        val chapter = state.currentChapter ?: return

        // Find the first readable paragraph starting from index, or backwards if none after
        val targetIndex = (index until paragraphs.size).firstOrNull { paragraphs[it].spokenText.isNotBlank() }
            ?: (index downTo 0).firstOrNull { paragraphs[it].spokenText.isNotBlank() }
            ?: index

        uiState.update {
            it.copy(
                currentParagraphIndex = targetIndex,
                isPlaying = true,
                isTtsDockVisible = true,
                flashHandleRed = false
            )
        }

        val progress = AudioPlaybackCoordinator.playbackProgressState.value
        val isSameChapter = progress.source == PlaybackSource.READER &&
            progress.novelId == novel.id &&
            progress.chapterId == chapter.id

        if (isSameChapter && AudioPlaybackCoordinator.isPlaying) {
            AudioPlaybackCoordinator.seekToParagraph(targetIndex)
        } else {
            AudioPlaybackCoordinator.playReaderChapter(
                context = application,
                novelId = novel.id,
                novelTitle = novel.title,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                chapterIndex = chapter.orderIndex,
                totalChapters = state.chapters.size,
                paragraphs = paragraphs,
                startParagraphIndex = targetIndex,
                autoNext = state.settings.autoNextChapter || state.isAutoLoopActive,
                rate = state.settings.speechRate,
                pitch = state.settings.speechPitch,
                voiceName = state.settings.selectedVoiceName,
                isPreMerged = true
            )
        }
    }

    fun nextParagraph() {
        val current = uiState.value.currentParagraphIndex
        val total = uiState.value.paragraphs.size
        if (current + 1 < total) {
            if (uiState.value.isPlaying) {
                AudioPlaybackCoordinator.nextParagraph()
            } else {
                speakAtParagraph(current + 1)
            }
        } else {
            nextChapter()
        }
    }

    fun previousParagraph() {
        val current = uiState.value.currentParagraphIndex
        if (current - 1 >= 0) {
            if (uiState.value.isPlaying) {
                AudioPlaybackCoordinator.previousParagraph()
            } else {
                speakAtParagraph(current - 1)
            }
        }
    }

    fun nextChapter() {
        saveCurrentChapterProgress()
        val chapters = uiState.value.chapters
        val currentIdx = uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx + 1 < chapters.size) {
            val nextIdx = currentIdx + 1
            val novel = uiState.value.currentNovel
            if (novel != null && nextIdx in chapters.indices) {
                syncActiveProgress(
                    novel.id,
                    nextIdx,
                    chapters[nextIdx].id,
                    chapters[nextIdx].lastReadParagraphIndex,
                    false
                )
            }
            if (uiState.value.isPlaying) {
                AudioPlaybackCoordinator.nextChapter()
            } else {
                selectChapter(nextIdx, false)
            }
        } else {
            uiState.update { it.copy(toastMessage = "End of novel reached") }
            stopTts()
        }
    }

    fun previousChapter() {
        saveCurrentChapterProgress()
        val chapters = uiState.value.chapters
        val currentIdx = uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx - 1 >= 0) {
            val prevIdx = currentIdx - 1
            val novel = uiState.value.currentNovel
            if (novel != null && prevIdx in chapters.indices) {
                syncActiveProgress(
                    novel.id,
                    prevIdx,
                    chapters[prevIdx].id,
                    chapters[prevIdx].lastReadParagraphIndex,
                    false
                )
            }
            if (uiState.value.isPlaying) {
                AudioPlaybackCoordinator.previousChapter()
            } else {
                selectChapter(prevIdx, false)
            }
        }
    }

    fun toggleAutoLoop() {
        uiState.update {
            val next = !it.isAutoLoopActive
            it.copy(
                isAutoLoopActive = next,
                loopRemaining = if (next) it.settings.loopCount else 0,
                toastMessage = if (next) "Auto-next loop active" else "Auto-next loop stopped"
            )
        }
    }

    fun toggleTtsDock() {
        uiState.update { it.copy(isTtsDockVisible = !it.isTtsDockVisible) }
    }

    fun updatePlaybackServiceNotification() {
        val state = uiState.value
        val novel = state.currentNovel?.title ?: "BookReader"
        val chapter = state.currentChapter?.title ?: "No chapter loaded"
        val total = state.paragraphs.size
        val currentChapIdx = state.currentChapter?.orderIndex ?: 0
        val totalChaps = state.chapters.size

        AudioPlaybackCoordinator.updateMetadata(
            novelTitle = novel,
            chapterTitle = chapter,
            chapterIndex = currentChapIdx,
            totalChapters = totalChaps,
            currentParagraph = state.currentParagraphIndex,
            totalParagraphs = total
        )
    }
}
