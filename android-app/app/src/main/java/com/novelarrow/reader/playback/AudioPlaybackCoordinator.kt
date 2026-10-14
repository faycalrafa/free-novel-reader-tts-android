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
    private var settingsRepository: SettingsRepository? = null

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

    private var currentSpeechRate: Float = 1.0f
    private var currentSpeechPitch: Float = 1.0f
    private var currentVoiceName: String? = null
    private var languageOverride: Locale? = null

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
                currentSpeechPitch = settings.speechPitch
                currentVoiceName = settings.selectedVoiceName
                applyTtsSettings()
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

    fun setLanguageOverride(locale: Locale?) {
        languageOverride = locale
        ttsManager?.setLanguageOverride(locale)
    }

    private fun ensureServiceRunning(context: Context) {
        if (attachedService == null) {
            val intent = Intent(context.applicationContext, NovelAudioPlaybackService::class.java).apply {
                action = NovelAudioPlaybackService.ACTION_PLAY
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
        speakCurrentParagraph()
    }

    fun play() {
        if (!_isPlayingState.value) {
            _isPlayingState.value = true
            _playbackProgressState.update { it.copy(isPlaying = true) }
            updateServiceState()
            speakCurrentParagraph()
        }
    }

    fun pause() {
        ttsManager?.stop()
        _isPlayingState.value = false
        _playbackProgressState.update { it.copy(isPlaying = false) }
        updateServiceState()
    }

    fun togglePlayPause() {
        if (_isPlayingState.value) pause() else play()
    }

    fun stop() {
        pause()
        currentSource = PlaybackSource.NONE
        _playbackProgressState.update { it.copy(isPlaying = false, source = PlaybackSource.NONE) }
        attachedService?.stopServicePlayback()
    }

    fun nextParagraph() {
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

    fun nextChapter() {
        if (currentSource == PlaybackSource.READER) {
            advanceToNextChapter()
        }
    }

    fun previousChapter() {
        if (currentSource == PlaybackSource.READER) {
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

    fun updateTtsSettings(rate: Float, pitch: Float, voiceName: String?) {
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName
        applyTtsSettings()
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
                    footerSkipEnabled = settings.footerSkipEnabled
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
                    footerSkipEnabled = settings.footerSkipEnabled
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
    }
}
