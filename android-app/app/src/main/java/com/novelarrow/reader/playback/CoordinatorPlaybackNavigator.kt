package com.novelarrow.reader.playback

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
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

internal class CoordinatorPlaybackNavigator(
    private val scope: CoroutineScope,
    private val contextProvider: () -> Context?,
    private val libraryRepositoryProvider: () -> LibraryRepository?,
    private val settingsRepositoryProvider: () -> SettingsRepository?,
    private val voiceEngine: CoordinatorVoiceEngine,
    private val audioFileDelegate: CoordinatorAudioFileDelegate,
    private val isPlayingFlow: MutableStateFlow<Boolean>,
    private val playbackProgressFlow: MutableStateFlow<PlaybackProgress>,
    private val currentInfoFlow: MutableStateFlow<String>,
    private val onSpeakParagraph: () -> Unit,
    private val onPause: () -> Unit,
    private val updateServiceState: () -> Unit,
    private val ensureServiceRunning: (Context) -> Unit,
    private val startSleepTimerIfConfigured: () -> Unit
) {

    private val TAG = "CoordinatorPlaybackNavigator"

    var currentSource: PlaybackSource = PlaybackSource.NONE
    var currentNovelId: String? = null
    var currentNovelTitle: String = "BookReader"
    var currentChapterId: String? = null
    var currentChapterTitle: String = "No chapter loaded"
    var currentChapterIndex: Int = 0
    var totalChaptersCount: Int = 0
    var currentParagraphs: List<Paragraph> = emptyList()
    var currentParagraphIndex: Int = 0
    var totalParagraphsCount: Int = 0
    var currentProgressText: String = "Ready"

    var autoNextChapterEnabled: Boolean = true
    var autoNextOnFinishedPage: Boolean = true
    var browserNextChapterCallback: (() -> Unit)? = null
    var browserPrevChapterCallback: (() -> Unit)? = null

    var lastActiveSource: PlaybackSource = PlaybackSource.NONE
    var lastActiveBrowserChapterTitle: String = ""
    var lastActiveBrowserParagraphs: List<Paragraph> = emptyList()
    var lastActiveBrowserParagraphIndex: Int = 0

    fun playReaderChapter(
        context: Context,
        novelId: String,
        novelTitle: String,
        chapterId: String,
        chapterTitle: String,
        chapterIndex: Int,
        totalChapters: Int,
        paragraphs: List<Paragraph>,
        startParagraphIndex: Int,
        autoNext: Boolean = true,
        rate: Float = voiceEngine.currentSpeechRate,
        pitch: Float = voiceEngine.currentSpeechPitch,
        voiceName: String? = voiceEngine.currentVoiceName,
        isPreMerged: Boolean = false
    ) {
        val effectiveParas = if (!isPreMerged && paragraphs.isNotEmpty()) {
            ChapterParser.mergeShortParagraphs(paragraphs, minWordThreshold = 10)
        } else paragraphs

        currentSource = PlaybackSource.READER
        lastActiveSource = PlaybackSource.READER
        currentNovelId = novelId
        currentNovelTitle = novelTitle
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        currentChapterIndex = chapterIndex
        totalChaptersCount = totalChapters
        currentParagraphs = effectiveParas
        totalParagraphsCount = effectiveParas.size
        autoNextChapterEnabled = autoNext

        val clampedStart = startParagraphIndex.coerceIn(0, (effectiveParas.size - 1).coerceAtLeast(0))
        currentParagraphIndex = clampedStart

        currentProgressText = "Ch. ${chapterIndex + 1}/$totalChapters • ¶ ${clampedStart + 1}/${effectiveParas.size}"

        playbackProgressFlow.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.READER,
                novelId = novelId,
                novelTitle = novelTitle,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                currentParagraphIndex = clampedStart,
                totalParagraphs = effectiveParas.size,
                currentParagraphText = effectiveParas.getOrNull(clampedStart)?.spokenText ?: ""
            )
        }
        isPlayingFlow.value = true

        voiceEngine.updateTtsSettings(rate, pitch, voiceName)
        ensureServiceRunning(context)
        updateServiceState()
        startSleepTimerIfConfigured()
        onSpeakParagraph()
    }

    fun playBrowserParagraphs(
        context: Context,
        chapterTitle: String,
        paragraphs: List<Paragraph>,
        startParagraphIndex: Int,
        rate: Float = voiceEngine.currentSpeechRate,
        pitch: Float = voiceEngine.currentSpeechPitch,
        voiceName: String? = voiceEngine.currentVoiceName,
        isPreMerged: Boolean = false
    ) {
        val effectiveParas = if (!isPreMerged && paragraphs.isNotEmpty()) {
            ChapterParser.mergeShortParagraphs(paragraphs, minWordThreshold = 10)
        } else paragraphs

        currentSource = PlaybackSource.BROWSER
        lastActiveSource = PlaybackSource.BROWSER
        currentNovelId = null
        currentNovelTitle = "Web Reading"
        currentChapterId = null
        currentChapterTitle = chapterTitle
        currentChapterIndex = 0
        totalChaptersCount = 1
        currentParagraphs = effectiveParas
        totalParagraphsCount = effectiveParas.size

        val clampedStart = startParagraphIndex.coerceIn(0, (effectiveParas.size - 1).coerceAtLeast(0))
        currentParagraphIndex = clampedStart
        lastActiveBrowserChapterTitle = chapterTitle
        lastActiveBrowserParagraphs = effectiveParas
        lastActiveBrowserParagraphIndex = clampedStart

        currentProgressText = "¶ ${clampedStart + 1}/${effectiveParas.size}"

        playbackProgressFlow.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.BROWSER,
                novelId = null,
                novelTitle = "Web Reading",
                chapterId = null,
                chapterTitle = chapterTitle,
                chapterIndex = 0,
                totalChapters = 1,
                currentParagraphIndex = clampedStart,
                totalParagraphs = effectiveParas.size,
                currentParagraphText = effectiveParas.getOrNull(clampedStart)?.spokenText ?: ""
            )
        }
        isPlayingFlow.value = true

        voiceEngine.updateTtsSettings(rate, pitch, voiceName)
        ensureServiceRunning(context)
        updateServiceState()
        startSleepTimerIfConfigured()
        onSpeakParagraph()
    }

    fun updateActiveParagraphs(newParas: List<Paragraph>, isPreMerged: Boolean = false) {
        val effective = if (!isPreMerged && newParas.isNotEmpty()) {
            ChapterParser.mergeShortParagraphs(newParas, minWordThreshold = 10)
        } else newParas
        currentParagraphs = effective
        totalParagraphsCount = effective.size
        currentParagraphIndex = currentParagraphIndex.coerceIn(0, (effective.size - 1).coerceAtLeast(0))
        playbackProgressFlow.update {
            it.copy(
                totalParagraphs = effective.size,
                currentParagraphIndex = currentParagraphIndex,
                currentParagraphText = effective.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }
        updateServiceState()
    }

    fun saveCurrentReaderProgress() {
        if (currentSource == PlaybackSource.READER && currentNovelId != null) {
            val novelId = currentNovelId!!
            val chapIdx = currentChapterIndex
            val paraIdx = currentParagraphIndex
            val total = totalParagraphsCount
            scope.launch(Dispatchers.IO) {
                try {
                    libraryRepositoryProvider()?.updateChapterProgress(
                        novelId = novelId,
                        chapterIndex = chapIdx,
                        paragraphIndex = paraIdx,
                        scrollOffset = 0,
                        isCompleted = total > 0 && paraIdx >= total - 1
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed saving current reader progress: ${e.message}", e)
                }
            }
        }
    }

    fun restoreLastHearingSession(autoPlay: Boolean = false, callback: (() -> Unit)? = null) {
        CoordinatorSessionRestorer.restoreLastHearingSession(
            scope = scope,
            navigator = this,
            contextProvider = contextProvider,
            libraryRepositoryProvider = libraryRepositoryProvider,
            settingsRepositoryProvider = settingsRepositoryProvider,
            isPlayingFlow = isPlayingFlow,
            playbackProgressFlow = playbackProgressFlow,
            onSpeakParagraph = onSpeakParagraph,
            updateServiceState = updateServiceState,
            ensureServiceRunning = ensureServiceRunning,
            autoPlay = autoPlay,
            callback = callback
        )
    }

    @Volatile
    private var preParsedChapterCache: Pair<String, List<Paragraph>>? = null

    private fun preFetchChapter(chap: Chapter) {
        val repo = libraryRepositoryProvider() ?: return
        val settingsRepo = settingsRepositoryProvider() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val content = repo.readChapterContent(chap)
                val settings = settingsRepo.getSettings()
                val parsed = ChapterParser.parseWithSettings(
                    content = content,
                    fileName = chap.title,
                    settings = settings,
                    folderPath = chap.fileUri,
                    novelUrl = chap.webSourceUrl
                )
                val merged = ChapterParser.mergeShortParagraphs(parsed, minWordThreshold = 10)
                preParsedChapterCache = Pair(chap.id, merged)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Pre-fetching chapter ${chap.title} failed: ${e.message}", e)
            }
        }
    }

    fun advanceToNextChapter() {
        val novelId = currentNovelId ?: return
        val repo = libraryRepositoryProvider() ?: return
        val settingsRepo = settingsRepositoryProvider() ?: return
        scope.launch(Dispatchers.IO) {
            val chapters = repo.getChapters(novelId)
            val nextIdx = currentChapterIndex + 1
            if (nextIdx in chapters.indices) {
                AudioCueManager.playChapterChange()
                val nextChap = chapters[nextIdx]
                val cached = preParsedChapterCache
                val mergedParas = if (cached != null && cached.first == nextChap.id) {
                    cached.second
                } else {
                    val content = repo.readChapterContent(nextChap)
                    val settings = settingsRepo.getSettings()
                    val parsedParas = ChapterParser.parseWithSettings(
                        content = content,
                        fileName = nextChap.title,
                        settings = settings,
                        folderPath = nextChap.fileUri,
                        novelUrl = nextChap.webSourceUrl
                    )
                    ChapterParser.mergeShortParagraphs(parsedParas, minWordThreshold = 10)
                }
                preParsedChapterCache = null

                // Pre-fetch following chapter in background
                if (nextIdx + 1 in chapters.indices) {
                    preFetchChapter(chapters[nextIdx + 1])
                }

                withContext(Dispatchers.Main) {
                    contextProvider()?.let { ctx ->
                        playReaderChapter(
                            context = ctx,
                            novelId = novelId,
                            novelTitle = currentNovelTitle,
                            chapterId = nextChap.id,
                            chapterTitle = nextChap.title,
                            chapterIndex = nextIdx,
                            totalChapters = chapters.size,
                            paragraphs = mergedParas,
                            startParagraphIndex = 0,
                            autoNext = autoNextChapterEnabled,
                            isPreMerged = true
                        )
                    }
                }
            } else {
                AppLogger.i(TAG, "All chapters in novel completed, playing folder done sound cue")
                AudioCueManager.playFolderDone()
                withContext(Dispatchers.Main) {
                    onPause()
                }
            }
        }
    }

    fun advanceToPreviousChapter() {
        val novelId = currentNovelId ?: return
        val repo = libraryRepositoryProvider() ?: return
        val settingsRepo = settingsRepositoryProvider() ?: return
        scope.launch(Dispatchers.IO) {
            val chapters = repo.getChapters(novelId)
            val prevIdx = currentChapterIndex - 1
            if (prevIdx in chapters.indices) {
                AudioCueManager.playChapterChange()
                val prevChap = chapters[prevIdx]
                val content = repo.readChapterContent(prevChap)
                val settings = settingsRepo.getSettings()
                val parsedParas = ChapterParser.parseWithSettings(
                    content = content,
                    fileName = prevChap.title,
                    settings = settings,
                    folderPath = prevChap.fileUri,
                    novelUrl = prevChap.webSourceUrl
                )
                val mergedParas = ChapterParser.mergeShortParagraphs(parsedParas, minWordThreshold = 10)
                preParsedChapterCache = null
                withContext(Dispatchers.Main) {
                    contextProvider()?.let { ctx ->
                        playReaderChapter(
                            context = ctx,
                            novelId = novelId,
                            novelTitle = currentNovelTitle,
                            chapterId = prevChap.id,
                            chapterTitle = prevChap.title,
                            chapterIndex = prevIdx,
                            totalChapters = chapters.size,
                            paragraphs = mergedParas,
                            startParagraphIndex = 0,
                            autoNext = autoNextChapterEnabled,
                            isPreMerged = true
                        )
                    }
                }
            }
        }
    }

    fun nextParagraph() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            audioFileDelegate.onAudioFileCompleted()
            return
        }
        val next = currentParagraphIndex + 1
        if (next < currentParagraphs.size) {
            currentParagraphIndex = next
            playbackProgressFlow.update { it.copy(currentParagraphIndex = next, currentParagraphText = currentParagraphs.getOrNull(next)?.spokenText ?: "") }
            onSpeakParagraph()
        } else {
            if (currentSource == PlaybackSource.READER) advanceToNextChapter()
            else triggerSiteNextChapter()
        }
    }

    fun previousParagraph() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            val list = audioFileDelegate.currentPlaylist
            val cur = audioFileDelegate.currentAudioFile
            val idx = list.indexOfFirst { it.canonicalPath == cur?.canonicalPath }
            if (idx > 0) {
                audioFileDelegate.playAudioFile(list[idx - 1], list, idx - 1)
            }
            return
        }
        val prev = currentParagraphIndex - 1
        if (prev >= 0) {
            currentParagraphIndex = prev
            playbackProgressFlow.update { it.copy(currentParagraphIndex = prev, currentParagraphText = currentParagraphs.getOrNull(prev)?.spokenText ?: "") }
            onSpeakParagraph()
        } else {
            if (currentSource == PlaybackSource.READER) advanceToPreviousChapter()
            else triggerSitePrevChapter()
        }
    }

    fun seekToParagraph(targetPara: Int) {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            val list = audioFileDelegate.currentPlaylist
            if (targetPara in list.indices) {
                audioFileDelegate.playAudioFile(list[targetPara], list, targetPara)
            }
            return
        }
        if (currentParagraphs.isEmpty()) return
        val target = targetPara.coerceIn(0, currentParagraphs.size - 1)
        currentParagraphIndex = target
        playbackProgressFlow.update {
            it.copy(
                currentParagraphIndex = target,
                currentParagraphText = currentParagraphs.getOrNull(target)?.spokenText ?: ""
            )
        }
        currentProgressText = if (currentSource == PlaybackSource.READER && totalChaptersCount > 0) {
            "Ch. ${currentChapterIndex + 1}/$totalChaptersCount • ¶ ${target + 1}/$totalParagraphsCount"
        } else {
            "¶ ${target + 1}/$totalParagraphsCount"
        }
        updateServiceState()
        if (isPlayingFlow.value) {
            onSpeakParagraph()
        }
    }

    fun nextChapter() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            audioFileDelegate.onAudioFileCompleted()
        } else if (currentSource == PlaybackSource.READER) {
            advanceToNextChapter()
        } else {
            triggerSiteNextChapter()
        }
    }

    fun previousChapter() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            previousParagraph()
        } else if (currentSource == PlaybackSource.READER) {
            advanceToPreviousChapter()
        } else {
            triggerSitePrevChapter()
        }
    }

    fun skipParagraphs(offset: Int) {
        if (currentParagraphs.isEmpty()) return
        val target = (currentParagraphIndex + offset).coerceIn(0, currentParagraphs.size - 1)
        seekToParagraph(target)
    }

    fun triggerSiteNextChapter() {
        browserNextChapterCallback?.invoke()
    }

    fun triggerSitePrevChapter() {
        browserPrevChapterCallback?.invoke()
    }

    fun onParagraphChanged(idx: Int, para: Paragraph?) {
        currentParagraphIndex = idx
        if (currentSource == PlaybackSource.BROWSER) {
            lastActiveBrowserParagraphIndex = idx
        }
        isPlayingFlow.value = true
        playbackProgressFlow.update {
            it.copy(
                isPlaying = true,
                currentParagraphIndex = idx,
                currentParagraphText = para?.spokenText ?: ""
            )
        }
        currentProgressText = if (currentSource == PlaybackSource.READER && totalChaptersCount > 0) {
            "Ch. ${currentChapterIndex + 1}/$totalChaptersCount • ¶ ${idx + 1}/$totalParagraphsCount"
        } else {
            "¶ ${idx + 1}/$totalParagraphsCount"
        }
        updateServiceState()
    }

    fun saveChapterProgress(novelId: String, chapIdx: Int, paraIdx: Int, isCompleted: Boolean) {
        scope.launch(Dispatchers.IO) {
            libraryRepositoryProvider()?.updateChapterProgress(
                novelId = novelId,
                chapterIndex = chapIdx,
                paragraphIndex = paraIdx,
                scrollOffset = 0,
                isCompleted = isCompleted
            )
        }
    }
}
