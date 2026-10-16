package com.novelarrow.reader.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.Voice
import android.util.Log
import androidx.core.content.ContextCompat
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
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

    private var currentSource: PlaybackSource = PlaybackSource.NONE
    private var currentNovelId: String? = null
    private var currentNovelTitle: String = "NovelArrow Reader"
    private var currentChapterId: String? = null
    private var currentChapterTitle: String = "No chapter loaded"
    private var currentProgressText: String = "Ready"

    private var currentChapterIndex: Int = 0
    private var totalChaptersCount: Int = 0
    private var currentParagraphIndex: Int = 0
    private var totalParagraphsCount: Int = 0
    private var currentParagraphs: List<Paragraph> = emptyList()
    private var autoNextChapterEnabled: Boolean = true

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
    private val _sleepTimerMinutesState = MutableStateFlow(0)
    val sleepTimerMinutesState: StateFlow<Int> = _sleepTimerMinutesState.asStateFlow()

    private val _sleepTimerRemainingSecondsState = MutableStateFlow(0)
    val sleepTimerRemainingSecondsState: StateFlow<Int> = _sleepTimerRemainingSecondsState.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        libraryRepository = LibraryRepository(application)
        settingsRepository = SettingsRepository(application)

        // Initialize TtsManager owned strictly by the coordinator
        ttsManager = TtsManager(application) { success ->
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
                applyTtsSettings()
            }
        }

        // Initialize persistent notification toolbar with last paused hearing session
        restoreLastHearingSession(autoPlay = false)
    }

    fun attachService(service: NovelAudioPlaybackService) {
        attachedService = service
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
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
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
        voiceName: String? = currentVoiceName
    ) {
        initialize(context)
        ensureServiceRunning(context)

        currentSource = PlaybackSource.READER
        currentNovelId = novelId
        currentNovelTitle = novelTitle
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        currentChapterIndex = chapterIndex
        totalChaptersCount = totalChapters
        currentParagraphs = paragraphs
        totalParagraphsCount = paragraphs.size
        currentParagraphIndex = startParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
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
                totalParagraphs = paragraphs.size,
                currentParagraphText = paragraphs.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }

        currentProgressText = if (totalChapters > 0) {
            "Ch. ${chapterIndex + 1}/$totalChapters • ¶ ${currentParagraphIndex + 1}/${paragraphs.size}"
        } else {
            "Ready"
        }

        updateServiceState()
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

        // Audio focus handoff: if switching away from Reader, persist reader progress to library folder
        if (currentSource == PlaybackSource.READER) {
            saveCurrentReaderProgress()
        }

        currentSource = PlaybackSource.BROWSER
        currentNovelId = null
        currentNovelTitle = "Web Reading"
        currentChapterId = null
        currentChapterTitle = chapterTitle
        currentChapterIndex = 0
        totalChaptersCount = 1
        currentParagraphs = paragraphs
        totalParagraphsCount = paragraphs.size
        currentParagraphIndex = startParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
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
                totalParagraphs = paragraphs.size,
                currentParagraphText = paragraphs.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }

        currentProgressText = "¶ ${currentParagraphIndex + 1}/${paragraphs.size}"
        updateServiceState()

        if (_sleepTimerMinutesState.value > 0) {
            if (_sleepTimerRemainingSecondsState.value <= 0) {
                _sleepTimerRemainingSecondsState.value = _sleepTimerMinutesState.value * 60
            }
            startSleepTimerCountdown()
        }

        speakCurrentParagraph()
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
                pause()
                _sleepTimerRemainingSecondsState.value = 0
            }
        }
    }

    fun restoreLastHearingSession(autoPlay: Boolean = false, callback: (() -> Unit)? = null) {
        coordinatorScope.launch(Dispatchers.IO) {
            val repo = libraryRepository ?: return@launch
            val lastActiveId = repo.getLastActiveNovelId()
            val novels = repo.getNovels()
            val targetNovel = (if (lastActiveId != null) novels.firstOrNull { it.id == lastActiveId } else null)
                ?: novels.firstOrNull() ?: return@launch
            val chapters = repo.getChapters(targetNovel.id)
            if (chapters.isEmpty()) return@launch

            val chapIdx = targetNovel.currentChapterIndex.coerceIn(0, chapters.size - 1)
            val chap = chapters[chapIdx]
            val content = try { repo.readChapterContent(chap) } catch (_: Exception) { "" }
            if (content.isBlank()) return@launch

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
                footerSkipRules = settings.footerSkipRules
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
                            footerSkipRules = settings.footerSkipRules
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
        ttsManager?.stop()
        sleepTimerJob?.cancel()
        _isPlayingState.value = false
        _playbackProgressState.update { it.copy(isPlaying = false) }
        updateServiceState()
    }

    fun togglePlayPause() {
        if (_isPlayingState.value) pause() else play()
    }

    fun stop() {
        pause()
        sleepTimerJob?.cancel()
        _sleepTimerRemainingSecondsState.value = 0
        currentSource = PlaybackSource.NONE
        _playbackProgressState.update { it.copy(isPlaying = false, source = PlaybackSource.NONE) }
        attachedService?.stopServicePlayback()
    }

    fun nextParagraph() {
        if (currentParagraphs.isEmpty()) {
            restoreLastHearingSession(autoPlay = false) {
                nextParagraph()
            }
            return
        }
        if (currentParagraphIndex + 1 < currentParagraphs.size) {
            currentParagraphIndex++
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        } else if (currentSource == PlaybackSource.READER && autoNextChapterEnabled) {
            advanceToNextChapter()
        }
    }

    fun previousParagraph() {
        if (currentParagraphIndex - 1 >= 0) {
            currentParagraphIndex--
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        }
    }

    fun seekToParagraph(targetPara: Int) {
        if (targetPara in currentParagraphs.indices) {
            currentParagraphIndex = targetPara
            _playbackProgressState.update { it.copy(currentParagraphIndex = targetPara) }
            speakCurrentParagraph()
        }
    }

    var browserNextChapterCallback: (() -> Unit)? = null
    var browserPrevChapterCallback: (() -> Unit)? = null

    fun nextChapter() {
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
        }
    }

    private fun applyTtsSettings() {
        ttsManager?.setRate(currentSpeechRate)
        ttsManager?.setPitch(currentSpeechPitch)
        ttsManager?.setVoiceByName(currentVoiceName)
    }

    private fun speakCurrentParagraph() {
        val paras = currentParagraphs
        val idx = currentParagraphIndex
        if (idx !in paras.indices) return

        val p = paras[idx]
        val spoken = p.spokenText.ifBlank { p.rawText }.trim()
        if (spoken.isBlank()) {
            handleParagraphDone(idx)
            return
        }

        ttsManager?.speak(
            index = idx,
            text = spoken,
            rate = currentSpeechRate,
            pitch = currentSpeechPitch
        )
    }

    private fun handleParagraphStart(index: Int) {
        currentParagraphIndex = index
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
        if (index != currentParagraphIndex) return
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

        if (nextIndex < total) {
            if (_isPlayingState.value) {
                currentParagraphIndex = nextIndex
                _playbackProgressState.update { it.copy(currentParagraphIndex = nextIndex) }
                speakCurrentParagraph()
            }
        } else {
            if (currentSource == PlaybackSource.READER && autoNextChapterEnabled) {
                advanceToNextChapter()
            } else {
                pause()
            }
        }
    }

    private fun handleParagraphError(index: Int, error: String) {
        Log.w(TAG, "TTS Error on paragraph $index: $error")
        val next = index + 1
        if (next < currentParagraphs.size && _isPlayingState.value) {
            coordinatorScope.launch {
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
                    headerSkipString = settings.headerSkipString,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipString = settings.footerSkipString,
                    footerSkipEnabled = settings.footerSkipEnabled,
                    headerSkipRules = settings.headerSkipRules,
                    footerSkipRules = settings.footerSkipRules
                )
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
                            paragraphs = parsedParas,
                            startParagraphIndex = 0,
                            autoNext = autoNextChapterEnabled
                        )
                    }
                }
            } else {
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
                    headerSkipString = settings.headerSkipString,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipString = settings.footerSkipString,
                    footerSkipEnabled = settings.footerSkipEnabled,
                    headerSkipRules = settings.headerSkipRules,
                    footerSkipRules = settings.footerSkipRules
                )
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
                            paragraphs = parsedParas,
                            startParagraphIndex = 0,
                            autoNext = autoNextChapterEnabled
                        )
                    }
                }
            }
        }
    }

    private fun updateServiceState() {
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
