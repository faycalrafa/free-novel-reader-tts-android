package com.novelarrow.reader.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.novelarrow.reader.data.model.HardwareButtonAction
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

import kotlinx.coroutines.withContext

object AudioPlaybackCoordinator {

    private const val TAG = "AudioPlaybackCoordinator"
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    var settingsRepository: SettingsRepository? = null
    var libraryRepository: LibraryRepository? = null

    private var ttsManager: TtsManager? = null
    private var attachedService: NovelAudioPlaybackService? = null

    private val _isPlayingState = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()
    val isPlaying: Boolean get() = _isPlayingState.value
    private val _currentInfo = MutableStateFlow("Ready")
    val currentInfo: StateFlow<String> = _currentInfo.asStateFlow()

    private val _playbackProgressState = MutableStateFlow(PlaybackProgress())
    val playbackProgressState: StateFlow<PlaybackProgress> = _playbackProgressState.asStateFlow()

    // Sub-delegates with explicit types to prevent recursive type checking
    private val sleepTimer: CoordinatorSleepTimer = CoordinatorSleepTimer(
        scope = coordinatorScope,
        settingsRepositoryProvider = { settingsRepository },
        isPlayingProvider = { _isPlayingState.value },
        onTimerExpired = { pause() }
    )

    private val audioFileDelegate: CoordinatorAudioFileDelegate = CoordinatorAudioFileDelegate(
        scope = coordinatorScope,
        serviceProvider = { attachedService },
        isPlayingProvider = { _isPlayingState.value },
        onPlaybackStarted = { file, index, playlist -> onAudioFilePlaybackStarted(file, index, playlist) },
        onPlaylistFinished = { pause() },
        progressUpdater = { pos, dur ->
            _playbackProgressState.update { it.copy(audioFilePositionMs = pos, audioFileDurationMs = dur) }
        }
    )

    private val voiceEngine: CoordinatorVoiceEngine = CoordinatorVoiceEngine(
        scope = coordinatorScope,
        ttsManagerProvider = { ttsManager },
        settingsRepositoryProvider = { settingsRepository },
        onSpeakRequested = {
            if (_isPlayingState.value && navigator.currentParagraphs.isNotEmpty()) {
                speechQueue.speakCurrentParagraph()
            }
        },
        onPauseRequested = { if (_isPlayingState.value) pause() },
        onVoicesUpdated = { voices ->
            _playbackProgressState.update { it.copy(availableVoices = voices) }
        }
    )

    private val navigator: CoordinatorPlaybackNavigator = CoordinatorPlaybackNavigator(
        scope = coordinatorScope,
        contextProvider = { appContext },
        libraryRepositoryProvider = { libraryRepository },
        settingsRepositoryProvider = { settingsRepository },
        voiceEngine = voiceEngine,
        audioFileDelegate = audioFileDelegate,
        isPlayingFlow = _isPlayingState,
        playbackProgressFlow = _playbackProgressState,
        currentInfoFlow = _currentInfo,
        onSpeakParagraph = { speechQueue.speakCurrentParagraph() },
        onPause = { pause() },
        updateServiceState = { updateServiceState() },
        ensureServiceRunning = { ensureServiceRunning(it) },
        startSleepTimerIfConfigured = {
            if (sleepTimer.sleepTimerMinutesState.value > 0) {
                sleepTimer.startSleepTimerCountdown()
            }
        }
    )

    private val speechQueue: CoordinatorSpeechQueue = CoordinatorSpeechQueue(
        scope = coordinatorScope,
        ttsManagerProvider = { ttsManager },
        settingsRepositoryProvider = { settingsRepository },
        voiceEngine = voiceEngine,
        paragraphsProvider = { navigator.currentParagraphs },
        currentParagraphIndexProvider = { navigator.currentParagraphIndex },
        isPlayingProvider = { _isPlayingState.value },
        currentSourceProvider = { navigator.currentSource },
        autoNextChapterProvider = { navigator.autoNextChapterEnabled },
        autoNextBrowserPageProvider = { navigator.autoNextOnFinishedPage },
        currentNovelIdProvider = { navigator.currentNovelId },
        currentChapterIndexProvider = { navigator.currentChapterIndex },
        onParagraphChanged = { idx, para -> navigator.onParagraphChanged(idx, para) },
        onChapterAdvanceNeeded = { navigator.advanceToNextChapter() },
        onBrowserNextPageNeeded = { navigator.triggerSiteNextChapter() },
        onPauseRequested = { pause() },
        onProgressSaveRequested = { novelId, chapIdx, paraIdx, isCompleted ->
            navigator.saveChapterProgress(novelId, chapIdx, paraIdx, isCompleted)
        },
        showToast = { showToast(it) }
    )

    private fun onAudioFilePlaybackStarted(file: File, index: Int, playlist: List<File>) {
        navigator.currentSource = PlaybackSource.AUDIO_FILE
        val albumName = file.parentFile?.name ?: "Audio Studio"
        navigator.currentNovelTitle = albumName
        navigator.currentChapterTitle = file.nameWithoutExtension
        navigator.currentParagraphIndex = index
        navigator.totalParagraphsCount = playlist.size
        navigator.currentProgressText = "Track ${index + 1}/${playlist.size} • ${file.name}"

        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.AUDIO_FILE,
                novelId = null,
                novelTitle = albumName,
                chapterId = null,
                chapterTitle = file.nameWithoutExtension,
                chapterIndex = 0,
                totalChapters = 1,
                currentParagraphIndex = index,
                totalParagraphs = playlist.size,
                currentParagraphText = file.name
            )
        }
        _isPlayingState.value = true
        appContext?.let { ensureServiceRunning(it) }
        updateServiceState()
        if (sleepTimer.sleepTimerMinutesState.value > 0) {
            sleepTimer.startSleepTimerCountdown()
        }
    }

    // Public properties mapped to delegates
    val availableVoicesState: StateFlow<List<Voice>> get() = voiceEngine.availableVoicesState
    val currentSpeechRateState: StateFlow<Float> get() = voiceEngine.currentSpeechRateState
    val sleepTimerMinutesState: StateFlow<Int> get() = sleepTimer.sleepTimerMinutesState
    val sleepTimerRemainingSecondsState: StateFlow<Int> get() = sleepTimer.sleepTimerRemainingSecondsState
    val previewingVoiceName: StateFlow<String?> get() = voiceEngine.previewingVoiceName

    val currentSpeechRate: Float get() = voiceEngine.currentSpeechRate
    val currentSpeechPitch: Float get() = voiceEngine.currentSpeechPitch
    val currentVoiceName: String? get() = voiceEngine.currentVoiceName

    var autoNextChapterEnabled: Boolean
        get() = navigator.autoNextChapterEnabled
        set(value) { navigator.autoNextChapterEnabled = value }

    var autoNextOnFinishedPage: Boolean
        get() = navigator.autoNextOnFinishedPage
        set(value) { navigator.autoNextOnFinishedPage = value }

    var browserNextChapterCallback: (() -> Unit)?
        get() = navigator.browserNextChapterCallback
        set(value) { navigator.browserNextChapterCallback = value }

    var browserPrevChapterCallback: (() -> Unit)?
        get() = navigator.browserPrevChapterCallback
        set(value) { navigator.browserPrevChapterCallback = value }

    fun initialize(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        settingsRepository = SettingsRepository(application)
        libraryRepository = LibraryRepository(application)

        val initialEngine = settingsRepository?.getSettings()?.selectedTtsEnginePackage
        AudioCueManager.initialize(application, settingsRepository)

        ttsManager = TtsManager(application, initialEngine) { success ->
            if (success) {
                val voices = ttsManager?.getAvailableVoices().orEmpty()
                voiceEngine.updateAvailableVoices(voices)
                voiceEngine.applyTtsSettings()
            }
        }.apply {
            onParagraphStart = { index -> speechQueue.handleParagraphStart(index) }
            onParagraphDone = { index -> speechQueue.handleParagraphDone(index) }
            onError = { index, error -> speechQueue.handleParagraphError(index, error) }
            onPreviewDone = { voiceEngine.stopVoicePreview() }
        }

        coordinatorScope.launch {
            settingsRepository?.settingsFlow?.collect { settings ->
                voiceEngine.initSettings(
                    rate = settings.speechRate,
                    pitch = settings.speechPitch,
                    voiceName = settings.selectedVoiceName
                )
                if (sleepTimer.sleepTimerMinutesState.value != settings.sleepTimerMinutes) {
                    sleepTimer.setSleepTimer(settings.sleepTimerMinutes)
                }
                if (settings.selectedTtsEnginePackage != ttsManager?.getActiveEnginePackage()) {
                    ttsManager?.setEngine(settings.selectedTtsEnginePackage) { success ->
                        if (success) {
                            val voices = ttsManager?.getAvailableVoices().orEmpty()
                            voiceEngine.updateAvailableVoices(voices)
                            voiceEngine.applyTtsSettings()
                        }
                    }
                }
            }
        }
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

    fun setLanguageOverride(locale: Locale?) = voiceEngine.setLanguageOverride(locale)

    fun ensureServiceRunning(context: Context) {
        if (attachedService != null) return
        val intent = Intent(context, NovelAudioPlaybackService::class.java).apply {
            action = NovelAudioPlaybackService.ACTION_UPDATE_STATE
            putExtra(NovelAudioPlaybackService.EXTRA_IS_PLAYING, _isPlayingState.value)
            putExtra(NovelAudioPlaybackService.EXTRA_NOVEL_TITLE, navigator.currentNovelTitle)
            putExtra(NovelAudioPlaybackService.EXTRA_CHAPTER_TITLE, navigator.currentChapterTitle)
            putExtra(NovelAudioPlaybackService.EXTRA_PROGRESS_TEXT, navigator.currentProgressText)
            putExtra(NovelAudioPlaybackService.EXTRA_CHAPTER_INDEX, navigator.currentChapterIndex)
            putExtra(NovelAudioPlaybackService.EXTRA_TOTAL_CHAPTERS, navigator.totalChaptersCount)
            putExtra(NovelAudioPlaybackService.EXTRA_PROGRESS_CURRENT, navigator.currentParagraphIndex)
            putExtra(NovelAudioPlaybackService.EXTRA_PROGRESS_TOTAL, navigator.totalParagraphsCount)
        }
        try {
            if (_isPlayingState.value) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed ensuring NovelAudioPlaybackService running: ${e.message}", e)
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
        rate: Float = voiceEngine.currentSpeechRate,
        pitch: Float = voiceEngine.currentSpeechPitch,
        voiceName: String? = voiceEngine.currentVoiceName,
        isPreMerged: Boolean = false
    ) = navigator.playReaderChapter(
        context = context,
        novelId = novelId,
        novelTitle = novelTitle,
        chapterId = chapterId,
        chapterTitle = chapterTitle,
        chapterIndex = chapterIndex,
        totalChapters = totalChapters,
        paragraphs = paragraphs,
        startParagraphIndex = startParagraphIndex,
        autoNext = autoNext,
        rate = rate,
        pitch = pitch,
        voiceName = voiceName,
        isPreMerged = isPreMerged
    )

    fun playBrowserParagraphs(
        context: Context,
        chapterTitle: String,
        paragraphs: List<Paragraph>,
        startParagraphIndex: Int,
        rate: Float = voiceEngine.currentSpeechRate,
        pitch: Float = voiceEngine.currentSpeechPitch,
        voiceName: String? = voiceEngine.currentVoiceName,
        isPreMerged: Boolean = false
    ) = navigator.playBrowserParagraphs(
        context = context,
        chapterTitle = chapterTitle,
        paragraphs = paragraphs,
        startParagraphIndex = startParagraphIndex,
        rate = rate,
        pitch = pitch,
        voiceName = voiceName,
        isPreMerged = isPreMerged
    )

    fun updateActiveParagraphs(newParas: List<Paragraph>, isPreMerged: Boolean = false) =
        navigator.updateActiveParagraphs(newParas, isPreMerged)

    fun saveCurrentReaderProgress() = navigator.saveCurrentReaderProgress()

    fun playAudioFile(file: File, playlist: List<File> = listOf(file), index: Int = 0) =
        audioFileDelegate.playAudioFile(file, playlist, index)

    fun playAudioFolder(folder: File) = audioFileDelegate.playAudioFolder(folder)

    fun onAudioFileCompleted() = audioFileDelegate.onAudioFileCompleted()

    fun setSleepTimer(minutes: Int) = sleepTimer.setSleepTimer(minutes)

    fun cycleSleepTimer() = sleepTimer.cycleSleepTimer()

    fun formatSleepTimerForNotification(): String = sleepTimer.formatSleepTimerForNotification()

    fun restoreLastHearingSession(autoPlay: Boolean = false, callback: (() -> Unit)? = null) =
        navigator.restoreLastHearingSession(autoPlay, callback)

    fun play() {
        if (!_isPlayingState.value) {
            _isPlayingState.value = true
            _playbackProgressState.update { it.copy(isPlaying = true) }
            appContext?.let { ensureServiceRunning(it) }
            updateServiceState()

            if (sleepTimer.sleepTimerMinutesState.value > 0) {
                sleepTimer.startSleepTimerCountdown()
            }
            if (navigator.currentSource == PlaybackSource.AUDIO_FILE) {
                attachedService?.resumeAudioFile()
                audioFileDelegate.startAudioFileProgressTicker()
                return
            }

            if (navigator.currentParagraphs.isEmpty()) {
                restoreLastHearingSession(autoPlay = true)
            } else {
                speechQueue.speakCurrentParagraph()
            }
        }
    }

    fun pause() {
        speechQueue.stopWatchdog()
        speechQueue.lastEnqueuedParagraphIndex = -1
        ttsManager?.stop()
        sleepTimer.cancel()
        audioFileDelegate.stopTicker()
        _isPlayingState.value = false
        _playbackProgressState.update { it.copy(isPlaying = false) }
        if (navigator.currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.pauseAudioFile()
        }
        navigator.saveCurrentReaderProgress()
        updateServiceState()
    }

    fun togglePlayPause() {
        if (_isPlayingState.value) pause() else play()
    }

    fun stop() {
        pause()
        speechQueue.stopWatchdog()
        speechQueue.lastEnqueuedParagraphIndex = -1
        sleepTimer.cancel(resetRemaining = true)
        audioFileDelegate.stopTicker()
        if (navigator.currentSource == PlaybackSource.AUDIO_FILE) {
            attachedService?.stopAudioFile()
        }
        navigator.currentSource = PlaybackSource.NONE
        _playbackProgressState.update { it.copy(source = PlaybackSource.NONE) }
        updateServiceState()
    }

    fun seekAudioFile(positionMs: Long) = audioFileDelegate.seekAudioFile(positionMs)

    fun nextParagraph() = navigator.nextParagraph()

    fun previousParagraph() = navigator.previousParagraph()

    fun seekToParagraph(targetPara: Int) = navigator.seekToParagraph(targetPara)

    fun nextChapter() = navigator.nextChapter()

    fun previousChapter() = navigator.previousChapter()

    fun triggerSiteNextChapter() = navigator.triggerSiteNextChapter()

    fun triggerSitePrevChapter() = navigator.triggerSitePrevChapter()

    fun updateMetadata(
        novelTitle: String,
        chapterTitle: String,
        chapterIndex: Int,
        totalChapters: Int,
        currentParagraph: Int,
        totalParagraphs: Int
    ) {
        navigator.currentNovelTitle = novelTitle
        navigator.currentChapterTitle = chapterTitle
        navigator.currentChapterIndex = chapterIndex
        navigator.totalChaptersCount = totalChapters
        navigator.currentParagraphIndex = currentParagraph
        navigator.totalParagraphsCount = totalParagraphs
        navigator.currentProgressText = if (totalChapters > 0) {
            "Ch. ${chapterIndex + 1}/$totalChapters • ¶ ${currentParagraph + 1}/$totalParagraphs"
        } else {
            "Ready"
        }
        updateServiceState()
    }

    fun cycleSpeechRate() = voiceEngine.cycleSpeechRate()
    fun updateSpeechRate(rate: Float) = voiceEngine.updateSpeechRate(rate)
    fun adjustSpeechRate(delta: Float) = voiceEngine.adjustSpeechRate(delta)
    fun skipParagraphs(offset: Int) = navigator.skipParagraphs(offset)

    fun executeHardwareAction(action: HardwareButtonAction, context: Context? = null) {
        when (action) {
            HardwareButtonAction.NONE, HardwareButtonAction.DEFAULT_VOLUME -> {}
            HardwareButtonAction.PLAY_PAUSE -> togglePlayPause()
            HardwareButtonAction.NEXT_PARAGRAPH -> nextParagraph()
            HardwareButtonAction.PREV_PARAGRAPH -> previousParagraph()
            HardwareButtonAction.NEXT_CHAPTER -> nextChapter()
            HardwareButtonAction.PREV_CHAPTER -> previousChapter()
            HardwareButtonAction.INCREASE_SPEED -> adjustSpeechRate(0.1f)
            HardwareButtonAction.DECREASE_SPEED -> adjustSpeechRate(-0.1f)
            HardwareButtonAction.SKIP_FORWARD_5 -> skipParagraphs(5)
            HardwareButtonAction.SKIP_BACKWARD_5 -> skipParagraphs(-5)
        }
    }

    private fun showToast(msg: String) {
        val ctx = appContext ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSpeechPitch(pitch: Float) = voiceEngine.updateSpeechPitch(pitch)
    fun updateVoice(voiceName: String?) = voiceEngine.updateVoice(voiceName)
    fun updateTtsSettings(rate: Float, pitch: Float, voiceName: String?) = voiceEngine.updateTtsSettings(rate, pitch, voiceName)
    fun setVoice(voiceName: String?) = voiceEngine.setVoice(voiceName)
    fun previewVoice(voice: Voice, sampleText: String? = null) = voiceEngine.previewVoice(voice, sampleText)
    fun stopVoicePreview() = voiceEngine.stopVoicePreview()
    fun setSelectedEngineType(engineType: String) = voiceEngine.setSelectedEngineType(engineType)
    fun getDefaultTtsEngine(): String? = voiceEngine.getDefaultTtsEngine()
    fun getActiveTtsEngine(): String? = voiceEngine.getActiveTtsEngine()
    fun getInstalledTtsEngines(): List<TextToSpeech.EngineInfo> = voiceEngine.getInstalledTtsEngines()
    fun switchTtsEngine(enginePackage: String?, onComplete: ((Boolean) -> Unit)? = null) = voiceEngine.switchTtsEngine(enginePackage, onComplete)
    fun getAvailableVoices(): List<Voice> = voiceEngine.getAvailableVoices()

    private fun updateServiceState() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            coordinatorScope.launch(Dispatchers.Main) {
                updateServiceState()
            }
            return
        }
        attachedService?.updatePlaybackState(
            isPlaying = _isPlayingState.value,
            novelTitle = navigator.currentNovelTitle,
            chapterTitle = navigator.currentChapterTitle,
            paragraphProgress = navigator.currentProgressText,
            chapterIndex = navigator.currentChapterIndex,
            totalChapters = navigator.totalChaptersCount,
            currentParagraph = navigator.currentParagraphIndex,
            totalParagraphs = navigator.totalParagraphsCount
        )
        appContext?.let { ctx ->
            QuickToolbarNotificationManager.updateToolbar(ctx)
        }
    }
}
