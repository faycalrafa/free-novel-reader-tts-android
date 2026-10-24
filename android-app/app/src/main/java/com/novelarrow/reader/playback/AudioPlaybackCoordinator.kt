package com.novelarrow.reader.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.Voice
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Singleton coordinator bridging UI (ReaderViewModel, BrowserViewModel) and Foreground Service
 * (NovelAudioPlaybackService). Owns the Text-to-Speech engine (TtsManager) and acts as the
 * single source of truth for audio playback state and controls across the application.
 */
object AudioPlaybackCoordinator {

    private const val TAG = "AudioCoordinator"

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var libraryRepository: LibraryRepository? = null
    var settingsRepository: SettingsRepository? = null
        private set

    private var ttsManager: TtsManager? = null
    private var attachedService: NovelAudioPlaybackService? = null

    private val _isPlayingState = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

    private val _currentInfo = MutableStateFlow("Idle")
    val currentInfo: StateFlow<String> = _currentInfo.asStateFlow()

    private val _playbackProgressState = MutableStateFlow(PlaybackProgress())
    val playbackProgressState: StateFlow<PlaybackProgress> = _playbackProgressState.asStateFlow()

    private val _availableVoicesState = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoicesState: StateFlow<List<Voice>> = _availableVoicesState.asStateFlow()

    private val _previewingVoiceName = MutableStateFlow<String?>(null)
    val previewingVoiceName: StateFlow<String?> = _previewingVoiceName.asStateFlow()

    var lastActiveSource: PlaybackSource = PlaybackSource.NONE
        private set
    var lastActiveBrowserChapterTitle: String = ""
        private set
    var lastActiveBrowserParagraphs: List<Paragraph> = emptyList()
        private set
    var lastActiveBrowserParagraphIndex: Int = 0
        private set

    private var currentSource: PlaybackSource = PlaybackSource.NONE
    private var currentNovelId: String? = null
    private var currentNovelTitle: String = "BookReader"
    private var currentChapterId: String? = null
    private var currentChapterTitle: String = "No chapter loaded"
    private var currentProgressText: String = "Ready"

    private var currentChapterIndex: Int = 0
    private var totalChaptersCount: Int = 0
    private var currentParagraphIndex: Int = 0
    private var totalParagraphsCount: Int = 0
    private var consecutiveErrorCount: Int = 0
    private var currentParagraphs: List<Paragraph> = emptyList()
    private var autoNextChapterEnabled: Boolean = true
    var autoNextOnFinishedPage: Boolean = true
    private var currentPlaylist: List<File> = emptyList()
    private var currentAudioFile: File? = null
    private var pendingAudioFile: File? = null

    var currentSpeechRate: Float = 1.0f
        private set
    private val _currentSpeechRateState = MutableStateFlow(1.0f)
    val currentSpeechRateState: StateFlow<Float> = _currentSpeechRateState.asStateFlow()

    var currentSpeechPitch: Float = 1.0f
        private set
    var currentVoiceName: String? = null
        private set
    private var languageOverride: Locale? = null

    private var sleepTimerJob: Job? = null
    private var audioFileProgressJob: Job? = null
    private val _sleepTimerMinutesState = MutableStateFlow(0)
    val sleepTimerMinutesState: StateFlow<Int> = _sleepTimerMinutesState.asStateFlow()

    private val _sleepTimerRemainingSecondsState = MutableStateFlow(0)
    val sleepTimerRemainingSecondsState: StateFlow<Int> = _sleepTimerRemainingSecondsState.asStateFlow()

    private fun startAudioFileProgressTicker() {
        audioFileProgressJob?.cancel()
        if (attachedService == null) return
        audioFileProgressJob = coordinatorScope.launch(Dispatchers.Main) {
            while (currentSource == PlaybackSource.AUDIO_FILE && _isPlayingState.value && attachedService != null) {
                val service = attachedService
                if (service != null) {
                    val pos = service.getAudioFilePositionMs()
                    val dur = service.getAudioFileDurationMs()
                    _playbackProgressState.update {
                        it.copy(
                            audioFilePositionMs = pos,
                            audioFileDurationMs = dur
                        )
                    }
                }
                delay(250)
            }
        }
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        libraryRepository = LibraryRepository(application)
        settingsRepository = SettingsRepository(application)

        val initialEngine = settingsRepository?.getSettings()?.selectedTtsEnginePackage

        AudioCueManager.initialize(application, settingsRepository)

        // Initialize TtsManager owned strictly by the coordinator
        ttsManager = TtsManager(application, initialEngine) { success ->
            if (success) {
                val voices = ttsManager?.getAvailableVoices().orEmpty()
                _availableVoicesState.value = voices
                _playbackProgressState.update { it.copy(availableVoices = voices) }
                applyTtsSettings()
            }
        }.apply {
            onParagraphStart = { index -> handleParagraphStart(index) }
            onParagraphDone = { index -> handleParagraphDone(index) }
            onError = { index, error -> handleParagraphError(index, error) }
            onPreviewDone = {
                _previewingVoiceName.value = null
                applyTtsSettings()
            }
        }

        coordinatorScope.launch {
            settingsRepository?.settingsFlow?.collect { settings ->
                currentSpeechRate = settings.speechRate
                _currentSpeechRateState.value = settings.speechRate
                currentSpeechPitch = settings.speechPitch
                currentVoiceName = settings.selectedVoiceName
                if (_sleepTimerMinutesState.value != settings.sleepTimerMinutes) {
                    _sleepTimerMinutesState.value = settings.sleepTimerMinutes
                    if (settings.sleepTimerMinutes == 0) {
                        sleepTimerJob?.cancel()
                        _sleepTimerRemainingSecondsState.value = 0
                    }
                }
                if (settings.selectedTtsEnginePackage != ttsManager?.getActiveEnginePackage()) {
                    ttsManager?.setEngine(settings.selectedTtsEnginePackage) { success ->
                        if (success) {
                            val voices = ttsManager?.getAvailableVoices().orEmpty()
                            _availableVoicesState.value = voices
                            _playbackProgressState.update { it.copy(availableVoices = voices) }
                            applyTtsSettings()
                        }
                    }
                } else {
                    applyTtsSettings()
                }
            }
        }

        // Initialize persistent notification toolbar with last paused hearing session
        restoreLastHearingSession(autoPlay = false)
    }

    fun attachService(service: NovelAudioPlaybackService) {
        attachedService = service
        val pending = pendingAudioFile
        if (pending != null) {
            pendingAudioFile = null
            service.playAudioFile(pending)
        }
        updateServiceState()
    }

    fun detachService(service: NovelAudioPlaybackService) {
        if (attachedService == service) {
            attachedService = null
        }
    }

    fun setLanguageOverride(locale: Locale?) {
        languageOverride = locale
        ttsManager?.setLanguageOverride(locale)
    }

    fun ensureServiceRunning(context: Context) {
        if (attachedService == null) {
            val intent = Intent(context.applicationContext, NovelAudioPlaybackService::class.java).apply {
                action = NovelAudioPlaybackService.ACTION_UPDATE_STATE
                putExtra(NovelAudioPlaybackService.EXTRA_IS_PLAYING, _isPlayingState.value)
                putExtra(NovelAudioPlaybackService.EXTRA_NOVEL_TITLE, currentNovelTitle)
                putExtra(NovelAudioPlaybackService.EXTRA_CHAPTER_TITLE, currentChapterTitle)
                putExtra(NovelAudioPlaybackService.EXTRA_PROGRESS_TEXT, currentProgressText)
                putExtra(NovelAudioPlaybackService.EXTRA_CHAPTER_INDEX, currentChapterIndex)
                putExtra(NovelAudioPlaybackService.EXTRA_TOTAL_CHAPTERS, totalChaptersCount)
                putExtra(NovelAudioPlaybackService.EXTRA_PROGRESS_CURRENT, currentParagraphIndex)
                putExtra(NovelAudioPlaybackService.EXTRA_PROGRESS_TOTAL, totalParagraphsCount)
            }
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start foreground service: ${e.message}", e)
            }
        }
    }

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
        rate: Float = currentSpeechRate,
        pitch: Float = currentSpeechPitch,
        voiceName: String? = currentVoiceName,
        isPreMerged: Boolean = false
    ) {
        initialize(context)
        ensureServiceRunning(context)

        if (currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.stopAudioFile()
        }

        val isChapterTransition = currentChapterId != null && currentChapterId != chapterId
        if (isChapterTransition) {
            AudioCueManager.playChapterChange()
        }

        currentSource = PlaybackSource.READER
        lastActiveSource = PlaybackSource.READER
        currentNovelId = novelId
        currentNovelTitle = novelTitle
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        currentChapterIndex = chapterIndex
        val mergedParas = if (isPreMerged) {
            paragraphs
        } else {
            com.novelarrow.reader.data.parser.ChapterParser.mergeShortParagraphs(paragraphs, minWordThreshold = 10)
        }
        currentParagraphs = mergedParas
        totalParagraphsCount = mergedParas.size
        currentParagraphIndex = startParagraphIndex.coerceIn(0, (mergedParas.size - 1).coerceAtLeast(0))
        autoNextChapterEnabled = autoNext
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName

        applyTtsSettings()

        _isPlayingState.value = true
        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.READER,
                novelId = novelId,
                novelTitle = novelTitle,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                currentParagraphIndex = currentParagraphIndex,
                totalParagraphs = mergedParas.size,
                currentParagraphText = mergedParas.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }

        currentProgressText = if (totalChapters > 0) {
            "Ch. ${chapterIndex + 1}/$totalChapters • ¶ ${currentParagraphIndex + 1}/${mergedParas.size}"
        } else {
            "Ready"
        }

        updateServiceState()
        if (novelId.isNotBlank()) {
            coordinatorScope.launch(Dispatchers.IO) {
                try {
                    libraryRepository?.updateChapterProgress(
                        novelId = novelId,
                        chapterIndex = chapterIndex,
                        paragraphIndex = currentParagraphIndex,
                        scrollOffset = 0,
                        isCompleted = false
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to update chapter progress on playReaderChapter: ${e.message}", e)
                }
            }
        }
        if (_sleepTimerMinutesState.value > 0) {
            if (_sleepTimerRemainingSecondsState.value <= 0) {
                _sleepTimerRemainingSecondsState.value = _sleepTimerMinutesState.value * 60
            }
            startSleepTimerCountdown()
        }
        speakCurrentParagraph()
    }

    fun playBrowserParagraphs(
        context: Context,
        chapterTitle: String,
        paragraphs: List<Paragraph>,
        startParagraphIndex: Int,
        rate: Float = currentSpeechRate,
        pitch: Float = currentSpeechPitch,
        voiceName: String? = currentVoiceName
    ) {
        initialize(context)
        ensureServiceRunning(context)

        if (currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.stopAudioFile()
        }

        // Audio focus handoff: if switching away from Reader, persist reader progress to library folder
        if (currentSource == PlaybackSource.READER) {
            saveCurrentReaderProgress()
        }

        currentSource = PlaybackSource.BROWSER
        lastActiveSource = PlaybackSource.BROWSER
        lastActiveBrowserChapterTitle = chapterTitle
        lastActiveBrowserParagraphs = paragraphs
        lastActiveBrowserParagraphIndex = startParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        currentNovelId = null
        currentNovelTitle = "Web Reading"
        currentChapterId = null
        currentChapterTitle = chapterTitle
        currentChapterIndex = 0
        val mergedParas = com.novelarrow.reader.data.parser.ChapterParser.mergeShortParagraphs(paragraphs)
        lastActiveBrowserParagraphs = mergedParas
        currentParagraphs = mergedParas
        totalParagraphsCount = mergedParas.size
        currentParagraphIndex = startParagraphIndex.coerceIn(0, (mergedParas.size - 1).coerceAtLeast(0))
        autoNextChapterEnabled = false
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName

        applyTtsSettings()

        _isPlayingState.value = true
        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.BROWSER,
                novelId = null,
                novelTitle = "Web Reading",
                chapterId = null,
                chapterTitle = chapterTitle,
                chapterIndex = 0,
                totalChapters = 1,
                currentParagraphIndex = currentParagraphIndex,
                totalParagraphs = mergedParas.size,
                currentParagraphText = mergedParas.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }

        currentProgressText = "¶ ${currentParagraphIndex + 1}/${mergedParas.size}"
        updateServiceState()

        if (_sleepTimerMinutesState.value > 0) {
            if (_sleepTimerRemainingSecondsState.value <= 0) {
                _sleepTimerRemainingSecondsState.value = _sleepTimerMinutesState.value * 60
            }
            startSleepTimerCountdown()
        }

        speakCurrentParagraph()
    }

    fun updateActiveParagraphs(newParas: List<Paragraph>) {
        val mergedParas = com.novelarrow.reader.data.parser.ChapterParser.mergeShortParagraphs(newParas)
        currentParagraphs = mergedParas
        totalParagraphsCount = mergedParas.size
        _playbackProgressState.update {
            it.copy(
                totalParagraphs = mergedParas.size,
                currentParagraphText = mergedParas.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }
        if (_isPlayingState.value && currentParagraphs.isNotEmpty()) {
            speakCurrentParagraph()
        }
    }

    fun saveCurrentReaderProgress() {
        val novelId = currentNovelId ?: return
        val chapIdx = currentChapterIndex
        val paraIdx = currentParagraphIndex
        val total = currentParagraphs.size
        coordinatorScope.launch(Dispatchers.IO) {
            libraryRepository?.updateChapterProgress(
                novelId = novelId,
                chapterIndex = chapIdx,
                paragraphIndex = paraIdx,
                scrollOffset = 0,
                isCompleted = total > 0 && paraIdx >= total - 1
            )
        }
    }

    private fun isSupportedAudioFile(name: String?): Boolean {
        if (name == null || name.startsWith(".")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    }

    fun playAudioFile(file: File, playlist: List<File> = listOf(file), index: Int = 0) {
        ttsManager?.stop()

        if (currentSource == PlaybackSource.READER) {
            saveCurrentReaderProgress()
        }

        currentSource = PlaybackSource.AUDIO_FILE
        // If playlist has only 1 file, automatically discover siblings in same folder so Next/Back buttons always navigate
        val effectivePlaylist = if (playlist.size > 1) {
            playlist
        } else {
            val siblings = file.parentFile?.listFiles { f -> f.isFile && f.length() > 0 && f.extension.equals(file.extension, ignoreCase = true) }
                ?.sortedWith { a, b -> com.novelarrow.reader.data.util.NaturalOrderComparator.compare(a.name, b.name) }
            if (!siblings.isNullOrEmpty()) siblings else listOf(file)
        }

        currentPlaylist = effectivePlaylist
        val resolvedIndex = if (playlist.size > 1) {
            index.coerceIn(0, currentPlaylist.size - 1)
        } else {
            currentPlaylist.indexOfFirst { it.canonicalPath == file.canonicalPath }.coerceAtLeast(0)
        }

        val activeFile = currentPlaylist.getOrNull(resolvedIndex) ?: file
        currentAudioFile = activeFile

        val albumName = activeFile.parentFile?.name ?: "Audio Studio"
        currentNovelId = null
        currentNovelTitle = albumName
        currentChapterId = null
        currentChapterTitle = activeFile.nameWithoutExtension
        currentChapterIndex = 0
        totalChaptersCount = 1
        currentParagraphs = emptyList()
        currentParagraphIndex = resolvedIndex
        totalParagraphsCount = currentPlaylist.size
        currentProgressText = "Track ${resolvedIndex + 1}/${currentPlaylist.size} • ${activeFile.name}"

        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.AUDIO_FILE,
                novelId = null,
                novelTitle = albumName,
                chapterId = null,
                chapterTitle = activeFile.nameWithoutExtension,
                chapterIndex = 0,
                totalChapters = 1,
                currentParagraphIndex = resolvedIndex,
                totalParagraphs = currentPlaylist.size,
                currentParagraphText = activeFile.name
            )
        }
        _isPlayingState.value = true

        appContext?.let { ctx ->
            ensureServiceRunning(ctx)
        }
        val service = attachedService
        if (service != null) {
            pendingAudioFile = null
            service.playAudioFile(activeFile)
        } else {
            pendingAudioFile = activeFile
        }
        updateServiceState()

        if (_sleepTimerMinutesState.value > 0) {
            if (_sleepTimerRemainingSecondsState.value <= 0) {
                _sleepTimerRemainingSecondsState.value = _sleepTimerMinutesState.value * 60
            }
            startSleepTimerCountdown()
        }

        startAudioFileProgressTicker()

        AppLogger.i(TAG, "playAudioFile: Playing '${activeFile.name}' (track ${resolvedIndex + 1}/${currentPlaylist.size})")
    }

    fun playAudioFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            AppLogger.w(TAG, "playAudioFolder: Directory does not exist or is not a directory: ${folder.absolutePath}")
            return
        }
        val mp3Files = folder.listFiles { f -> f.isFile && f.length() > 0 && f.extension.equals("mp3", ignoreCase = true) }
            ?.sortedWith { a, b -> com.novelarrow.reader.data.util.NaturalOrderComparator.compare(a.name, b.name) }
            ?.toList() ?: emptyList()

        if (mp3Files.isNotEmpty()) {
            playAudioFile(file = mp3Files.first(), playlist = mp3Files, index = 0)
        } else {
            AppLogger.w(TAG, "playAudioFolder: No MP3 files found in folder: ${folder.absolutePath}")
        }
    }

    fun onAudioFileCompleted() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            val nextIndex = currentParagraphIndex + 1
            if (nextIndex < currentPlaylist.size) {
                AppLogger.i(TAG, "Track completed, advancing to track ${nextIndex + 1}/${currentPlaylist.size}")
                playAudioFile(currentPlaylist[nextIndex], currentPlaylist, nextIndex)
            } else {
                AppLogger.i(TAG, "Playlist finished in audio folder, playing folder done sound cue")
                AudioCueManager.playFolderDone()
                pause()
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutesState.value = minutes
        val totalSecs = minutes * 60
        _sleepTimerRemainingSecondsState.value = totalSecs
        coordinatorScope.launch(Dispatchers.IO) {
            settingsRepository?.setSleepTimerMinutes(minutes)
        }
        sleepTimerJob?.cancel()
        if (minutes > 0 && _isPlayingState.value) {
            startSleepTimerCountdown()
        }
    }

    fun cycleSleepTimer() {
        val presets = listOf(0, 15, 30, 45, 60)
        val current = _sleepTimerMinutesState.value
        val currentIndex = presets.indexOf(current)
        val nextMinutes = if (currentIndex == -1 || currentIndex == presets.lastIndex) {
            presets[0]
        } else {
            presets[currentIndex + 1]
        }
        setSleepTimer(nextMinutes)
    }

    fun formatSleepTimerForNotification(): String {
        return QuickToolbarForegroundService.formatSleepTimer(
            mins = _sleepTimerMinutesState.value,
            remainingSecs = _sleepTimerRemainingSecondsState.value
        )
    }

    private fun startSleepTimerCountdown() {
        sleepTimerJob?.cancel()
        val totalSecs = if (_sleepTimerRemainingSecondsState.value > 0) {
            _sleepTimerRemainingSecondsState.value
        } else {
            _sleepTimerMinutesState.value * 60
        }
        _sleepTimerRemainingSecondsState.value = totalSecs

        sleepTimerJob = coordinatorScope.launch {
            var remaining = totalSecs
            while (remaining > 0 && _isPlayingState.value) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSecondsState.value = remaining
            }
            if (remaining <= 0 && _isPlayingState.value) {
                AppLogger.i(TAG, "Sleep timer reached zero, playing timer stop sound cue and pausing playback")
                AudioCueManager.playTimerStop()
                pause()
                _sleepTimerRemainingSecondsState.value = 0
            }
        }
    }

    fun restoreLastHearingSession(autoPlay: Boolean = false, callback: (() -> Unit)? = null) {
        coordinatorScope.launch(Dispatchers.IO) {
            if (lastActiveSource == PlaybackSource.BROWSER && lastActiveBrowserParagraphs.isNotEmpty()) {
                val paragraphs = lastActiveBrowserParagraphs
                val title = lastActiveBrowserChapterTitle
                val idx = lastActiveBrowserParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
                withContext(Dispatchers.Main) {
                    currentSource = PlaybackSource.BROWSER
                    currentNovelId = null
                    currentNovelTitle = "Web Reading"
                    currentChapterId = null
                    currentChapterTitle = title
                    currentChapterIndex = 0
                    totalChaptersCount = 1
                    currentParagraphs = paragraphs
                    totalParagraphsCount = paragraphs.size
                    currentParagraphIndex = idx

                    _playbackProgressState.update {
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

                    currentProgressText = "¶ ${idx + 1}/${paragraphs.size}"
                    appContext?.let { ctx ->
                        ensureServiceRunning(ctx)
                    }
                    updateServiceState()

                    if (autoPlay) {
                        _isPlayingState.value = true
                        speakCurrentParagraph()
                    }
                    callback?.invoke()
                }
                return@launch
            }

            val repo = libraryRepository
            if (repo == null) {
                AppLogger.d("AudioPlaybackCoordinator", "Cannot restore hearing session: LibraryRepository is null")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }
            val lastActiveId = repo.getLastActiveNovelId()
            val novels = repo.getNovels()
            val targetNovel = (if (lastActiveId != null) novels.firstOrNull { it.id == lastActiveId } else null)
                ?: novels.firstOrNull()
            if (targetNovel == null) {
                AppLogger.d("AudioPlaybackCoordinator", "No novel available to restore hearing session")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }
            val chapters = repo.getChapters(targetNovel.id)
            if (chapters.isEmpty()) {
                AppLogger.d("AudioPlaybackCoordinator", "No chapters found for novel ${targetNovel.title} during session restore")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }

            val chapIdx = targetNovel.currentChapterIndex.coerceIn(0, chapters.size - 1)
            val chap = chapters[chapIdx]
            val content = try {
                repo.readChapterContent(chap)
            } catch (e: Exception) {
                AppLogger.w("AudioPlaybackCoordinator", "Failed to read chapter ${chap.title} content: ${e.message}", e)
                ""
            }
            if (content.isBlank()) {
                AppLogger.w("AudioPlaybackCoordinator", "Chapter ${chap.title} has blank content, cannot restore session")
                withContext(Dispatchers.Main) { callback?.invoke() }
                return@launch
            }

            val settings = settingsRepository?.getSettings() ?: com.novelarrow.reader.data.model.ReaderSettings()
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
                currentSource = PlaybackSource.READER
                currentNovelId = targetNovel.id
                currentNovelTitle = targetNovel.title
                currentChapterId = chap.id
                currentChapterTitle = chap.title
                currentChapterIndex = chapIdx
                totalChaptersCount = chapters.size
                currentParagraphs = parsed
                totalParagraphsCount = parsed.size
                currentParagraphIndex = paraIdx

                _playbackProgressState.update {
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

                currentProgressText = "Ch. ${chapIdx + 1}/${chapters.size} • ¶ ${paraIdx + 1}/${parsed.size}"

                appContext?.let { ctx ->
                    ensureServiceRunning(ctx)
                }
                updateServiceState()

                if (autoPlay) {
                    _isPlayingState.value = true
                    speakCurrentParagraph()
                }
                callback?.invoke()
            }
        }
    }

    fun play() {
        if (!_isPlayingState.value) {
            _isPlayingState.value = true
            _playbackProgressState.update { it.copy(isPlaying = true) }
            updateServiceState()

            if (_sleepTimerMinutesState.value > 0) {
                if (_sleepTimerRemainingSecondsState.value <= 0) {
                    _sleepTimerRemainingSecondsState.value = _sleepTimerMinutesState.value * 60
                }
                startSleepTimerCountdown()
            }

            if (currentSource == PlaybackSource.AUDIO_FILE) {
                attachedService?.resumeAudioFile()
                startAudioFileProgressTicker()
                return
            }

            // If empty paragraphs, restore from last paused hearing session or reload
            if (currentParagraphs.isEmpty()) {
                if (currentSource == PlaybackSource.READER && currentNovelId != null) {
                    coordinatorScope.launch(Dispatchers.IO) {
                        val repo = libraryRepository ?: return@launch
                        val chapters = repo.getChapters(currentNovelId!!)
                        val chap = chapters.getOrNull(currentChapterIndex) ?: chapters.firstOrNull() ?: return@launch
                        val content = repo.readChapterContent(chap)
                        val settings = settingsRepository?.getSettings() ?: com.novelarrow.reader.data.model.ReaderSettings()
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
                            folderPath = chap.fileUri,
                            novelUrl = chap.webSourceUrl,
                            contentSelectors = settings.contentSelectors
                        )
                        withContext(Dispatchers.Main) {
                            currentParagraphs = parsed
                            totalParagraphsCount = parsed.size
                            speakCurrentParagraph()
                        }
                    }
                } else {
                    restoreLastHearingSession(autoPlay = true)
                }
            } else {
                speakCurrentParagraph()
            }
        }
    }

    fun pause() {
        stopWatchdog()
        lastEnqueuedParagraphIndex = -1
        ttsManager?.stop()
        sleepTimerJob?.cancel()
        audioFileProgressJob?.cancel()
        _isPlayingState.value = false
        _playbackProgressState.update { it.copy(isPlaying = false) }
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.pauseAudioFile()
        }
        if (currentSource == PlaybackSource.READER && currentNovelId != null) {
            val novelId = currentNovelId!!
            val chapIdx = currentChapterIndex
            val paraIdx = currentParagraphIndex
            val total = totalParagraphsCount
            coordinatorScope.launch(Dispatchers.IO) {
                try {
                    libraryRepository?.updateChapterProgress(
                        novelId = novelId,
                        chapterIndex = chapIdx,
                        paragraphIndex = paraIdx,
                        scrollOffset = 0,
                        isCompleted = total > 0 && paraIdx >= total - 1
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to save chapter progress on pause: ${e.message}", e)
                }
            }
        }
        updateServiceState()
    }

    fun togglePlayPause() {
        if (_isPlayingState.value) pause() else play()
    }

    fun stop() {
        pause()
        stopWatchdog()
        lastEnqueuedParagraphIndex = -1
        sleepTimerJob?.cancel()
        audioFileProgressJob?.cancel()
        _sleepTimerRemainingSecondsState.value = 0
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.stopAudioFile()
        }
        currentSource = PlaybackSource.NONE
        currentPlaylist = emptyList()
        currentAudioFile = null
        pendingAudioFile = null
        _playbackProgressState.update {
            it.copy(
                isPlaying = false,
                source = PlaybackSource.NONE,
                audioFilePositionMs = 0L,
                audioFileDurationMs = 0L
            )
        }
        attachedService?.stopServicePlayback()
    }

    fun seekAudioFile(positionMs: Long) {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.seekAudioFile(positionMs)
            _playbackProgressState.update { it.copy(audioFilePositionMs = positionMs) }
        }
    }

    fun nextParagraph() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            val nextIdx = currentParagraphIndex + 1
            if (nextIdx < currentPlaylist.size) {
                playAudioFile(currentPlaylist[nextIdx], currentPlaylist, nextIdx)
            } else {
                pause()
            }
            return
        }
        if (currentParagraphs.isEmpty()) {
            restoreLastHearingSession(autoPlay = false) {
                nextParagraph()
            }
            return
        }
        if (currentParagraphIndex + 1 < currentParagraphs.size) {
            lastEnqueuedParagraphIndex = -1
            currentParagraphIndex++
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        } else if (currentSource == PlaybackSource.READER && autoNextChapterEnabled) {
            advanceToNextChapter()
        } else if (currentSource == PlaybackSource.BROWSER && autoNextOnFinishedPage) {
            AppLogger.i(TAG, "Browser playback manual next at end of page, triggering next chapter")
            browserNextChapterCallback?.invoke()
        }
    }

    fun previousParagraph() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            val prevIdx = currentParagraphIndex - 1
            if (prevIdx >= 0 && prevIdx < currentPlaylist.size) {
                playAudioFile(currentPlaylist[prevIdx], currentPlaylist, prevIdx)
            }
            return
        }
        if (currentParagraphIndex - 1 >= 0) {
            lastEnqueuedParagraphIndex = -1
            currentParagraphIndex--
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        }
    }

    fun seekToParagraph(targetPara: Int) {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            if (targetPara in currentPlaylist.indices) {
                playAudioFile(currentPlaylist[targetPara], currentPlaylist, targetPara)
            }
            return
        }
        if (targetPara in currentParagraphs.indices) {
            lastEnqueuedParagraphIndex = -1
            currentParagraphIndex = targetPara
            _playbackProgressState.update { it.copy(currentParagraphIndex = targetPara) }
            speakCurrentParagraph()
            if (currentSource == PlaybackSource.READER && currentNovelId != null) {
                val novelId = currentNovelId!!
                val chapIdx = currentChapterIndex
                coordinatorScope.launch(Dispatchers.IO) {
                    try {
                        libraryRepository?.updateChapterProgress(
                            novelId = novelId,
                            chapterIndex = chapIdx,
                            paragraphIndex = targetPara,
                            scrollOffset = 0,
                            isCompleted = false
                        )
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to save chapter progress on seek: ${e.message}", e)
                    }
                }
            }
        }
    }

    var browserNextChapterCallback: (() -> Unit)? = null
    var browserPrevChapterCallback: (() -> Unit)? = null

    fun nextChapter() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            nextParagraph()
            return
        }
        if (currentSource == PlaybackSource.READER) {
            advanceToNextChapter()
        } else if (currentSource == PlaybackSource.BROWSER) {
            browserNextChapterCallback?.invoke() ?: run {
                val target = (currentParagraphIndex + 5).coerceAtMost(currentParagraphs.size - 1)
                seekToParagraph(target)
            }
        }
    }

    fun previousChapter() {
        if (currentSource == PlaybackSource.AUDIO_FILE) {
            previousParagraph()
            return
        }
        if (currentSource == PlaybackSource.READER) {
            advanceToPreviousChapter()
        } else if (currentSource == PlaybackSource.BROWSER) {
            browserPrevChapterCallback?.invoke() ?: run {
                val target = (currentParagraphIndex - 5).coerceAtLeast(0)
                seekToParagraph(target)
            }
        }
    }

    fun triggerSiteNextChapter() {
        if (currentSource == PlaybackSource.BROWSER) {
            browserNextChapterCallback?.invoke() ?: nextChapter()
        } else {
            advanceToNextChapter()
        }
    }

    fun triggerSitePrevChapter() {
        if (currentSource == PlaybackSource.BROWSER) {
            browserPrevChapterCallback?.invoke() ?: previousChapter()
        } else {
            advanceToPreviousChapter()
        }
    }

    fun updateMetadata(
        novelTitle: String,
        chapterTitle: String,
        chapterIndex: Int,
        totalChapters: Int,
        currentParagraph: Int,
        totalParagraphs: Int
    ) {
        currentNovelTitle = novelTitle
        currentChapterTitle = chapterTitle
        currentChapterIndex = chapterIndex
        totalChaptersCount = totalChapters
        currentParagraphIndex = currentParagraph
        totalParagraphsCount = totalParagraphs
        currentProgressText = if (totalChapters > 0) {
            "Ch. ${chapterIndex + 1}/$totalChapters • ¶ ${currentParagraph + 1}/$totalParagraphs"
        } else {
            "Ready"
        }
        updateServiceState()
    }

    fun cycleSpeechRate() {
        val standardSpeeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = currentSpeechRate
        val next = standardSpeeds.firstOrNull { it > current + 0.05f } ?: standardSpeeds.first()
        updateSpeechRate(next)
    }

    fun updateSpeechRate(rate: Float) {
        currentSpeechRate = rate
        _currentSpeechRateState.value = rate
        applyTtsSettings()
        if (_isPlayingState.value && currentParagraphs.isNotEmpty()) {
            speakCurrentParagraph()
        }
        coordinatorScope.launch(Dispatchers.IO) {
            settingsRepository?.setSpeechRate(rate)
        }
    }

    fun adjustSpeechRate(delta: Float) {
        val next = kotlin.math.round((currentSpeechRate + delta) * 10f) / 10f
        updateSpeechRate(next.coerceIn(0.5f, 3.0f))
    }

    fun skipParagraphs(offset: Int) {
        if (currentParagraphs.isEmpty()) return
        val target = (currentParagraphIndex + offset).coerceIn(0, currentParagraphs.size - 1)
        seekToParagraph(target)
    }

    fun executeHardwareAction(action: com.novelarrow.reader.data.model.HardwareButtonAction, context: Context? = null) {
        when (action) {
            com.novelarrow.reader.data.model.HardwareButtonAction.NONE -> {}
            com.novelarrow.reader.data.model.HardwareButtonAction.DEFAULT_VOLUME -> {
                // Handled by volume stream caller
            }
            com.novelarrow.reader.data.model.HardwareButtonAction.PLAY_PAUSE -> togglePlayPause()
            com.novelarrow.reader.data.model.HardwareButtonAction.NEXT_PARAGRAPH -> nextParagraph()
            com.novelarrow.reader.data.model.HardwareButtonAction.PREV_PARAGRAPH -> previousParagraph()
            com.novelarrow.reader.data.model.HardwareButtonAction.NEXT_CHAPTER -> nextChapter()
            com.novelarrow.reader.data.model.HardwareButtonAction.PREV_CHAPTER -> previousChapter()
            com.novelarrow.reader.data.model.HardwareButtonAction.INCREASE_SPEED -> adjustSpeechRate(0.1f)
            com.novelarrow.reader.data.model.HardwareButtonAction.DECREASE_SPEED -> adjustSpeechRate(-0.1f)
            com.novelarrow.reader.data.model.HardwareButtonAction.SKIP_FORWARD_5 -> skipParagraphs(5)
            com.novelarrow.reader.data.model.HardwareButtonAction.SKIP_BACKWARD_5 -> skipParagraphs(-5)
        }
    }

    private fun showToast(msg: String) {
        val ctx = appContext ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSpeechPitch(pitch: Float) {
        currentSpeechPitch = pitch
        applyTtsSettings()
        coordinatorScope.launch(Dispatchers.IO) {
            settingsRepository?.setSpeechPitch(pitch)
        }
    }

    fun updateVoice(voiceName: String?) {
        currentVoiceName = voiceName
        applyTtsSettings()
        coordinatorScope.launch(Dispatchers.IO) {
            settingsRepository?.setVoice(voiceName)
            settingsRepository?.setFavoriteVoice("en", voiceName)
        }
    }

    fun updateTtsSettings(rate: Float, pitch: Float, voiceName: String?) {
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName
        applyTtsSettings()
        coordinatorScope.launch(Dispatchers.IO) {
            settingsRepository?.setSpeechRate(rate)
            settingsRepository?.setSpeechPitch(pitch)
            settingsRepository?.setVoice(voiceName)
            settingsRepository?.setFavoriteVoice("en", voiceName)
        }
    }

    fun setVoice(voiceName: String?) {
        currentVoiceName = voiceName
        applyTtsSettings()
        coordinatorScope.launch(Dispatchers.IO) {
            settingsRepository?.setVoice(voiceName)
            settingsRepository?.setFavoriteVoice("en", voiceName)
        }
    }

    fun previewVoice(voice: Voice, sampleText: String? = null) {
        if (_previewingVoiceName.value == voice.name) {
            stopVoicePreview()
            return
        }

        // Pause active reading if playing
        if (_isPlayingState.value) {
            pause()
        }

        _previewingVoiceName.value = voice.name
        val text = sampleText ?: TtsManager.getSampleTextForLocale(voice.locale)
        AppLogger.d("AudioPlaybackCoordinator", "Previewing voice ${voice.name} with sample text: \"$text\"")
        ttsManager?.speakPreview(voice, text)
    }

    fun stopVoicePreview() {
        if (_previewingVoiceName.value != null) {
            _previewingVoiceName.value = null
            ttsManager?.stopPreview()
            applyTtsSettings()
            AppLogger.d("AudioPlaybackCoordinator", "Stopped voice preview and restored TTS settings")
        }
    }

    fun setSelectedEngineType(engineType: String) {
        coordinatorScope.launch {
            settingsRepository?.setSelectedEngineType(engineType)
        }
    }

    fun getDefaultTtsEngine(): String? = ttsManager?.getDefaultEngineName()

    fun getActiveTtsEngine(): String? = ttsManager?.getActiveEnginePackage()

    fun getInstalledTtsEngines(): List<android.speech.tts.TextToSpeech.EngineInfo> = ttsManager?.getEngines().orEmpty()

    fun switchTtsEngine(enginePackage: String?, onComplete: ((Boolean) -> Unit)? = null) {
        coordinatorScope.launch {
            settingsRepository?.setSelectedTtsEnginePackage(enginePackage)
            ttsManager?.setEngine(enginePackage) { success ->
                if (success) {
                    val voices = ttsManager?.getAvailableVoices().orEmpty()
                    _availableVoicesState.value = voices
                    _playbackProgressState.update { it.copy(availableVoices = voices) }
                    applyTtsSettings()
                }
                onComplete?.invoke(success)
            }
        }
    }

    fun getAvailableVoices(): List<Voice> = ttsManager?.getAvailableVoices().orEmpty()

    private fun applyTtsSettings() {
        ttsManager?.setRate(currentSpeechRate)
        ttsManager?.setPitch(currentSpeechPitch)
        ttsManager?.setVoiceByName(currentVoiceName)
    }

    private var lastEnqueuedParagraphIndex = -1
    private var lastProgressTimestamp = System.currentTimeMillis()
    private var ttsWatchdogJob: Job? = null

    private fun startWatchdog() {
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = coordinatorScope.launch(Dispatchers.Main) {
            while (_isPlayingState.value && (currentSource == PlaybackSource.READER || currentSource == PlaybackSource.BROWSER)) {
                delay(3000L)
                if (!_isPlayingState.value) break
                val now = System.currentTimeMillis()
                val isSpeaking = ttsManager?.isSpeaking() == true
                if (!isSpeaking && (now - lastProgressTimestamp) > 4500L && currentParagraphs.isNotEmpty()) {
                    AppLogger.w(TAG, "Watchdog detected TTS stall at paragraph $currentParagraphIndex. Auto-recovering...")
                    lastProgressTimestamp = now
                    speakCurrentParagraph()
                }
            }
        }
    }

    private fun stopWatchdog() {
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
    }

    private fun enqueueParagraph(idx: Int, queueMode: Int) {
        val paras = currentParagraphs
        if (idx !in paras.indices) return

        val p = paras[idx]
        val spoken = p.spokenText.trim()
        if (spoken.isBlank()) {
            if (queueMode == android.speech.tts.TextToSpeech.QUEUE_FLUSH) {
                handleParagraphDone(idx)
            }
            return
        }

        val detectedLang = LanguageDetectionEngine.detectLanguage(spoken)
        val settings = settingsRepository?.getSettings()
        val voiceForLang = when (detectedLang) {
            SupportedLanguage.ARABIC -> settings?.favoriteVoiceArabic ?: currentVoiceName
            SupportedLanguage.FRENCH -> settings?.favoriteVoiceFrench ?: currentVoiceName
            SupportedLanguage.ENGLISH -> currentVoiceName ?: settings?.favoriteVoiceEnglish
        }
        val targetVoice = voiceForLang ?: currentVoiceName
        ttsManager?.setVoiceByName(targetVoice)

        ttsManager?.speak(
            index = idx,
            text = spoken,
            rate = currentSpeechRate,
            pitch = currentSpeechPitch,
            queueMode = queueMode
        )
        lastEnqueuedParagraphIndex = maxOf(lastEnqueuedParagraphIndex, idx)
    }

    private fun speakCurrentParagraph() {
        val paras = currentParagraphs
        val idx = currentParagraphIndex
        if (idx !in paras.indices) return

        ttsManager?.stop()
        lastEnqueuedParagraphIndex = idx
        lastProgressTimestamp = System.currentTimeMillis()

        // 1. Enqueue current paragraph with QUEUE_FLUSH
        enqueueParagraph(idx, android.speech.tts.TextToSpeech.QUEUE_FLUSH)

        // 2. Lookahead: Enqueue next paragraph with QUEUE_ADD for seamless 0-gap transition
        val lookaheadIdx = idx + 1
        if (lookaheadIdx < paras.size) {
            enqueueParagraph(lookaheadIdx, android.speech.tts.TextToSpeech.QUEUE_ADD)
        }

        startWatchdog()
    }

    private fun handleParagraphStart(index: Int) {
        lastProgressTimestamp = System.currentTimeMillis()
        consecutiveErrorCount = 0
        currentParagraphIndex = index
        if (currentSource == PlaybackSource.BROWSER) {
            lastActiveBrowserParagraphIndex = index
        }
        _isPlayingState.value = true
        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                currentParagraphIndex = index,
                currentParagraphText = currentParagraphs.getOrNull(index)?.spokenText ?: ""
            )
        }
        currentProgressText = if (currentSource == PlaybackSource.READER && totalChaptersCount > 0) {
            "Ch. ${currentChapterIndex + 1}/$totalChaptersCount • ¶ ${index + 1}/$totalParagraphsCount"
        } else {
            "¶ ${index + 1}/$totalParagraphsCount"
        }
        updateServiceState()
    }

    private fun handleParagraphDone(index: Int) {
        lastProgressTimestamp = System.currentTimeMillis()
        val total = currentParagraphs.size
        val nextIndex = index + 1

        if (currentSource == PlaybackSource.READER && currentNovelId != null) {
            val novelId = currentNovelId!!
            val chapIdx = currentChapterIndex
            coordinatorScope.launch(Dispatchers.IO) {
                libraryRepository?.updateChapterProgress(
                    novelId = novelId,
                    chapterIndex = chapIdx,
                    paragraphIndex = index,
                    scrollOffset = 0,
                    isCompleted = nextIndex >= total
                )
            }
        }

        if (!_isPlayingState.value) return

        if (nextIndex < total) {
            // Paragraph (index + 1) is already speaking or enqueued via lookahead buffer!
            // Enqueue paragraph (lastEnqueuedParagraphIndex + 1) to maintain the lookahead pipeline
            val nextLookahead = lastEnqueuedParagraphIndex + 1
            if (nextLookahead < total) {
                enqueueParagraph(nextLookahead, android.speech.tts.TextToSpeech.QUEUE_ADD)
            }
        } else {
            if (currentSource == PlaybackSource.READER && autoNextChapterEnabled) {
                advanceToNextChapter()
            } else if (currentSource == PlaybackSource.BROWSER && autoNextOnFinishedPage) {
                AppLogger.i(TAG, "Browser playback finished last paragraph, advancing to next page")
                browserNextChapterCallback?.invoke()
            } else {
                pause()
            }
        }
    }

    private fun handleParagraphError(index: Int, error: String) {
        AppLogger.w(TAG, "TTS Error on paragraph $index: $error")
        consecutiveErrorCount++
        if (consecutiveErrorCount >= 3) {
            AppLogger.e(TAG, "Stopping playback: encountered $consecutiveErrorCount consecutive TTS errors (last error: $error)")
            showToast("Playback stopped due to recurring TTS error: $error")
            pause()
            consecutiveErrorCount = 0
            return
        }

        val next = index + 1
        if (next < currentParagraphs.size && _isPlayingState.value) {
            coordinatorScope.launch(Dispatchers.Main) {
                delay(120)
                if (_isPlayingState.value) {
                    currentParagraphIndex = next
                    _playbackProgressState.update { it.copy(currentParagraphIndex = next) }
                    speakCurrentParagraph()
                }
            }
        } else {
            pause()
        }
    }

    private fun advanceToNextChapter() {
        val novelId = currentNovelId ?: return
        val repo = libraryRepository ?: return
        val settingsRepo = settingsRepository ?: return
        coordinatorScope.launch(Dispatchers.IO) {
            val chapters = repo.getChapters(novelId)
            val nextIdx = currentChapterIndex + 1
            if (nextIdx in chapters.indices) {
                AudioCueManager.playChapterChange()
                val nextChap = chapters[nextIdx]
                val content = repo.readChapterContent(nextChap)
                val settings = settingsRepo.getSettings()
                val parsedParas = ChapterParser.parse(
                    content = content,
                    fileName = nextChap.title,
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
                    folderPath = nextChap.fileUri,
                    novelUrl = nextChap.webSourceUrl,
                    contentSelectors = settings.contentSelectors,
                    ignoredSelectors = settings.ignoredSelectors
                )
                val mergedParas = ChapterParser.mergeShortParagraphs(parsedParas, minWordThreshold = 10)
                withContext(Dispatchers.Main) {
                    appContext?.let { ctx ->
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
                    pause()
                }
            }
        }
    }

    private fun advanceToPreviousChapter() {
        val novelId = currentNovelId ?: return
        val repo = libraryRepository ?: return
        val settingsRepo = settingsRepository ?: return
        coordinatorScope.launch(Dispatchers.IO) {
            val chapters = repo.getChapters(novelId)
            val prevIdx = currentChapterIndex - 1
            if (prevIdx in chapters.indices) {
                AudioCueManager.playChapterChange()
                val prevChap = chapters[prevIdx]
                val content = repo.readChapterContent(prevChap)
                val settings = settingsRepo.getSettings()
                val parsedParas = ChapterParser.parse(
                    content = content,
                    fileName = prevChap.title,
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
                    folderPath = prevChap.fileUri,
                    novelUrl = prevChap.webSourceUrl,
                    contentSelectors = settings.contentSelectors,
                    ignoredSelectors = settings.ignoredSelectors
                )
                val mergedParas = ChapterParser.mergeShortParagraphs(parsedParas, minWordThreshold = 10)
                withContext(Dispatchers.Main) {
                    appContext?.let { ctx ->
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

    private fun updateServiceState() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            coordinatorScope.launch(Dispatchers.Main) {
                updateServiceState()
            }
            return
        }
        attachedService?.updatePlaybackState(
            isPlaying = _isPlayingState.value,
            novelTitle = currentNovelTitle,
            chapterTitle = currentChapterTitle,
            paragraphProgress = currentProgressText,
            chapterIndex = currentChapterIndex,
            totalChapters = totalChaptersCount,
            currentParagraph = currentParagraphIndex,
            totalParagraphs = totalParagraphsCount
        )
        appContext?.let { ctx ->
            QuickToolbarNotificationManager.updateToolbar(ctx)
        }
    }
}
