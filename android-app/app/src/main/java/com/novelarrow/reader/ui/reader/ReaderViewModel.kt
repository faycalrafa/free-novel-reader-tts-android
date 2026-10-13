package com.novelarrow.reader.ui.reader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.Voice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.ReaderSettings
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.NovelAudioPlaybackService
import com.novelarrow.reader.playback.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val novels: List<Novel> = emptyList(),
    val currentNovel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val paragraphs: List<Paragraph> = emptyList(),
    val currentParagraphIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val isTtsDockVisible: Boolean = false,
    val isAutoLoopActive: Boolean = false,
    val loopRemaining: Int = 0,
    val availableVoices: List<Voice> = emptyList(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val toastMessage: String? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var ttsManager: TtsManager? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    init {
        initTts(application)
        observeSettings()
        loadInitialLibrary()
        setupPlaybackServiceCallbacks()
    }

    private fun initTts(context: Context) {
        ttsManager = TtsManager(context) { success ->
            if (success) {
                val voices = ttsManager?.getAvailableVoices() ?: emptyList()
                _uiState.update { it.copy(availableVoices = voices) }
                applyVoiceSettings()
            }
        }.apply {
            onParagraphStart = { index ->
                _uiState.update { it.copy(currentParagraphIndex = index, isPlaying = true) }
                updatePlaybackServiceNotification()
            }
            onParagraphDone = { index ->
                handleParagraphDone(index)
            }
            onError = { index, _ ->
                // Automatically advance on error after short delay to prevent getting stuck
                viewModelScope.launch {
                    val next = index + 1
                    if (next < _uiState.value.paragraphs.size && _uiState.value.isPlaying) {
                        speakParagraph(next)
                    }
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        sleepTimerMinutes = settings.sleepTimerMinutes,
                        sleepTimerRemainingSeconds = if (it.sleepTimerRemainingSeconds == 0) settings.sleepTimerMinutes * 60 else it.sleepTimerRemainingSeconds
                    )
                }
                applyVoiceSettings()
            }
        }
    }

    private fun applyVoiceSettings() {
        val settings = _uiState.value.settings
        ttsManager?.setVoiceByName(settings.selectedVoiceName)
    }

    private fun setupPlaybackServiceCallbacks() {
        NovelAudioPlaybackService.onPlayRequested = { playTts() }
        NovelAudioPlaybackService.onPauseRequested = { pauseTts() }
        NovelAudioPlaybackService.onStopRequested = { stopTts() }
        NovelAudioPlaybackService.onNextParaRequested = { nextParagraph() }
        NovelAudioPlaybackService.onPrevParaRequested = { previousParagraph() }
        NovelAudioPlaybackService.onNextChapRequested = { nextChapter() }
        NovelAudioPlaybackService.onPrevChapRequested = { previousChapter() }
        NovelAudioPlaybackService.onSeekToChapterRequested = { targetChapter ->
            selectChapter(targetChapter, forceStartAtBeginning = false)
        }
        NovelAudioPlaybackService.onSeekToParaRequested = { targetPara ->
            speakAtParagraph(targetPara)
        }
    }

    private fun loadInitialLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val novels = libraryRepository.getNovels()
            val lastActiveId = libraryRepository.getLastActiveNovelId()
            val targetNovel = novels.firstOrNull { it.id == lastActiveId } ?: novels.firstOrNull()

            _uiState.update { it.copy(novels = novels, isLoading = false) }

            if (targetNovel != null) {
                selectNovel(targetNovel)
            }
        }
    }

    fun scanRootFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Take persistable permission so it's permanently granted
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val novels = libraryRepository.scanRootDirectory(treeUri)
            _uiState.update {
                it.copy(
                    novels = novels,
                    isLoading = false,
                    toastMessage = "Loaded ${novels.size} playlists from folder"
                )
            }
            if (novels.isNotEmpty()) {
                selectNovel(novels.first())
            }
        }
    }

    fun selectNovel(novel: Novel) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentNovel = novel) }
            val chapters = libraryRepository.getChapters(novel.id)
            val chapterIdx = novel.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            val currentChapter = chapters.getOrNull(chapterIdx)

            _uiState.update {
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
        val novel = _uiState.value.currentNovel ?: return
        val chapter = _uiState.value.currentChapter ?: return
        val currentIdx = _uiState.value.currentParagraphIndex
        val total = _uiState.value.paragraphs.size
        val isCompleted = total > 0 && currentIdx >= total - 1

        viewModelScope.launch {
            libraryRepository.updateChapterProgress(
                novelId = novel.id,
                chapterIndex = chapter.orderIndex,
                paragraphIndex = currentIdx,
                scrollOffset = 0,
                isCompleted = isCompleted
            )
        }

        _uiState.update { state ->
            val updatedChapters = state.chapters.map { chap ->
                if (chap.id == chapter.id) {
                    chap.copy(
                        lastReadParagraphIndex = currentIdx,
                        isCompleted = isCompleted || chap.isCompleted
                    )
                } else chap
            }
            state.copy(
                chapters = updatedChapters,
                currentChapter = chapter.copy(lastReadParagraphIndex = currentIdx)
            )
        }
    }

    fun selectChapter(index: Int, forceStartAtBeginning: Boolean = false) {
        val chapters = _uiState.value.chapters
        if (index !in chapters.indices) return
        val chapter = chapters[index]
        val wasPlaying = _uiState.value.isPlaying

        val currentChap = _uiState.value.currentChapter
        if (currentChap != null && currentChap.id != chapter.id) {
            saveCurrentChapterProgress()
        }

        viewModelScope.launch {
            loadChapterContent(chapter, restoreProgress = !forceStartAtBeginning)
            if (wasPlaying) {
                val startPara = if (forceStartAtBeginning) 0 else _uiState.value.currentParagraphIndex
                speakParagraph(startPara)
            }
        }
    }

    private suspend fun loadChapterContent(chapter: Chapter, restoreProgress: Boolean) {
        _uiState.update { it.copy(isLoading = true, currentChapter = chapter) }
        try {
            val content = libraryRepository.readChapterContent(chapter)
            val settings = _uiState.value.settings
            val paragraphs = withContext(Dispatchers.Default) {
                ChapterParser.parse(
                    content = content,
                    fileName = chapter.title,
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
            }

            val targetIndex = if (restoreProgress) {
                chapter.lastReadParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            } else 0

            _uiState.update {
                it.copy(
                    paragraphs = paragraphs,
                    currentParagraphIndex = targetIndex,
                    isLoading = false
                )
            }

            val novel = _uiState.value.currentNovel
            if (novel != null) {
                libraryRepository.updateChapterProgress(
                    novelId = novel.id,
                    chapterIndex = chapter.orderIndex,
                    paragraphIndex = targetIndex,
                    scrollOffset = chapter.lastScrollOffset,
                    isCompleted = chapter.isCompleted
                )
            }
            updatePlaybackServiceNotification()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = "Failed to load chapter: ${e.message}"
                )
            }
        }
    }

    fun playTts() {
        if (_uiState.value.paragraphs.isEmpty()) return
        _uiState.update { it.copy(isPlaying = true, isTtsDockVisible = true) }
        updatePlaybackServiceNotification()
        speakParagraph(_uiState.value.currentParagraphIndex)

        // Reset and restart sleep timer if configured
        val timerMinutes = _uiState.value.sleepTimerMinutes
        if (timerMinutes > 0) {
            _uiState.update { it.copy(sleepTimerRemainingSeconds = timerMinutes * 60) }
            startSleepTimerCountdown()
        }
    }

    fun pauseTts() {
        ttsManager?.stop()
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
        updatePlaybackServiceNotification()
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pauseTts() else playTts()
    }

    fun stopTts() {
        ttsManager?.stop()
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(isPlaying = false, isTtsDockVisible = false) }
        NovelAudioPlaybackService.stopService(getApplication())
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setSleepTimerMinutes(minutes)
        }
        _uiState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = minutes * 60
            )
        }
        sleepTimerJob?.cancel()
        if (minutes > 0 && _uiState.value.isPlaying) {
            startSleepTimerCountdown()
        }
    }

    private fun startSleepTimerCountdown() {
        sleepTimerJob?.cancel()
        val totalSecs = if (_uiState.value.sleepTimerRemainingSeconds > 0) {
            _uiState.value.sleepTimerRemainingSeconds
        } else {
            _uiState.value.sleepTimerMinutes * 60
        }
        _uiState.update { it.copy(sleepTimerRemainingSeconds = totalSecs) }

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSecs
            while (remaining > 0 && _uiState.value.isPlaying) {
                kotlinx.coroutines.delay(1000L)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
            if (remaining <= 0 && _uiState.value.isPlaying) {
                pauseTts()
                _uiState.update { it.copy(sleepTimerRemainingSeconds = 0, toastMessage = "Sleep timer paused playback") }
            }
        }
    }

    fun speakAtParagraph(index: Int) {
        val total = _uiState.value.paragraphs.size
        if (index !in 0 until total) return
        _uiState.update { it.copy(currentParagraphIndex = index, isPlaying = true, isTtsDockVisible = true) }
        speakParagraph(index)
    }

    private fun speakParagraph(index: Int) {
        val paragraphs = _uiState.value.paragraphs
        if (index !in paragraphs.indices) return

        val paragraph = paragraphs[index]
        val spoken = paragraph.spokenText.ifBlank { paragraph.rawText }.trim()
        if (spoken.isBlank()) {
            handleParagraphDone(index)
            return
        }

        val settings = _uiState.value.settings
        ttsManager?.speak(
            index = index,
            text = spoken,
            rate = settings.speechRate,
            pitch = settings.speechPitch
        )
    }

    private fun handleParagraphDone(index: Int) {
        viewModelScope.launch {
            val total = _uiState.value.paragraphs.size
            val nextIndex = index + 1

            // Persist progress
            val novel = _uiState.value.currentNovel
            val chapter = _uiState.value.currentChapter
            if (novel != null && chapter != null && _uiState.value.settings.rememberProgress) {
                libraryRepository.updateChapterProgress(
                    novelId = novel.id,
                    chapterIndex = chapter.orderIndex,
                    paragraphIndex = index,
                    scrollOffset = 0,
                    isCompleted = nextIndex >= total
                )
            }

            if (nextIndex < total) {
                if (_uiState.value.isPlaying) {
                    _uiState.update { it.copy(currentParagraphIndex = nextIndex) }
                    speakParagraph(nextIndex)
                }
            } else {
                // End of chapter reached
                if (_uiState.value.settings.autoNextChapter || _uiState.value.isAutoLoopActive) {
                    val chapters = _uiState.value.chapters
                    val currentIdx = _uiState.value.currentChapter?.orderIndex ?: 0
                    if (currentIdx + 1 < chapters.size) {
                        selectChapter(currentIdx + 1, forceStartAtBeginning = true)
                    } else {
                        _uiState.update { it.copy(toastMessage = "End of novel reached") }
                        stopTts()
                    }
                } else {
                    pauseTts()
                }
            }
        }
    }

    fun nextParagraph() {
        val current = _uiState.value.currentParagraphIndex
        val total = _uiState.value.paragraphs.size
        if (current + 1 < total) {
            speakAtParagraph(current + 1)
        } else {
            nextChapter()
        }
    }

    fun previousParagraph() {
        val current = _uiState.value.currentParagraphIndex
        if (current - 1 >= 0) {
            speakAtParagraph(current - 1)
        }
    }

    fun nextChapter() {
        saveCurrentChapterProgress()
        val chapters = _uiState.value.chapters
        val currentIdx = _uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx + 1 < chapters.size) {
            selectChapter(currentIdx + 1, forceStartAtBeginning = false)
        } else {
            _uiState.update { it.copy(toastMessage = "End of novel reached") }
            stopTts()
        }
    }

    fun previousChapter() {
        saveCurrentChapterProgress()
        val chapters = _uiState.value.chapters
        val currentIdx = _uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx - 1 >= 0) {
            selectChapter(currentIdx - 1, forceStartAtBeginning = false)
        }
    }

    fun toggleAutoLoop() {
        _uiState.update {
            val next = !it.isAutoLoopActive
            it.copy(
                isAutoLoopActive = next,
                loopRemaining = if (next) it.settings.loopCount else 0,
                toastMessage = if (next) "Auto-next loop active" else "Auto-next loop stopped"
            )
        }
    }

    fun toggleTtsDock() {
        _uiState.update { it.copy(isTtsDockVisible = !it.isTtsDockVisible) }
    }

    fun deleteNovel(novelId: String) {
        viewModelScope.launch {
            libraryRepository.deleteNovel(novelId)
            loadInitialLibrary()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            libraryRepository.clearAll()
            _uiState.update {
                ReaderUiState(settings = it.settings)
            }
        }
    }

    fun updateTheme(theme: ThemeMode) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun updateFontSize(sizeSp: Float) {
        viewModelScope.launch { settingsRepository.setFontSize(sizeSp) }
    }

    fun updateSpeechRate(rate: Float) {
        viewModelScope.launch {
            settingsRepository.setSpeechRate(rate)
            ttsManager?.setRate(rate)
        }
    }

    fun cycleSpeechRate() {
        val standardSpeeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = _uiState.value.settings.speechRate
        val next = standardSpeeds.firstOrNull { it > current + 0.05f } ?: standardSpeeds.first()
        updateSpeechRate(next)
    }

    fun updateSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            settingsRepository.setSpeechPitch(pitch)
            ttsManager?.setPitch(pitch)
        }
    }

    fun updateVoice(voiceName: String?) {
        viewModelScope.launch { settingsRepository.setVoice(voiceName) }
    }

    fun updateAutoScroll(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoScroll(enabled) }
    }

    fun updateAutoNext(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoNext(enabled) }
    }

    fun updateContentSelector(selector: String) {
        viewModelScope.launch {
            settingsRepository.setContentSelector(selector)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateContentSelectorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContentSelectorEnabled(enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateHeaderSkipString(headerStr: String) {
        viewModelScope.launch {
            settingsRepository.setHeaderSkipString(headerStr)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateHeaderSkipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHeaderSkipEnabled(enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateFooterSkipString(footerStr: String) {
        viewModelScope.launch {
            settingsRepository.setFooterSkipString(footerStr)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateFooterSkipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFooterSkipEnabled(enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateSkipWordsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSkipWordsEnabled(enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun addSkipWord(word: String, exactMatch: Boolean) {
        viewModelScope.launch {
            settingsRepository.addSkipWord(com.novelarrow.reader.data.model.SkipWord(text = word, exactMatch = exactMatch))
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun removeSkipWord(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeSkipWord(index)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updatePronunciationRulesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPronunciationRulesEnabled(enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun addPronunciationRule(from: String, to: String, exactMatch: Boolean) {
        viewModelScope.launch {
            settingsRepository.addPronunciationRule(com.novelarrow.reader.data.model.PronunciationRule(from = from, to = to, exactMatch = exactMatch))
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun removePronunciationRule(index: Int) {
        viewModelScope.launch {
            settingsRepository.removePronunciationRule(index)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun updatePlaybackServiceNotification() {
        val context = getApplication<Application>()
        val state = _uiState.value
        val novel = state.currentNovel?.title ?: "NovelArrow Reader"
        val chapter = state.currentChapter?.title ?: "No chapter loaded"
        val total = state.paragraphs.size
        val cur = (state.currentParagraphIndex + 1).coerceAtMost(total)
        val currentChapIdx = state.currentChapter?.orderIndex ?: 0
        val totalChaps = state.chapters.size
        val progressText = if (totalChaps > 0) "Ch. ${currentChapIdx + 1}/$totalChaps • ¶ $cur/$total" else "Ready"

        NovelAudioPlaybackService.startOrUpdateService(
            context = context,
            isPlaying = state.isPlaying,
            novelTitle = novel,
            chapterTitle = chapter,
            progressText = progressText,
            currentChapterIndex = currentChapIdx,
            totalChapters = totalChaps,
            currentParagraph = state.currentParagraphIndex,
            totalParagraphs = total
        )
    }

    override fun onCleared() {
        NovelAudioPlaybackService.onPlayRequested = null
        NovelAudioPlaybackService.onPauseRequested = null
        NovelAudioPlaybackService.onStopRequested = null
        NovelAudioPlaybackService.onNextParaRequested = null
        NovelAudioPlaybackService.onPrevParaRequested = null
        NovelAudioPlaybackService.onNextChapRequested = null
        NovelAudioPlaybackService.onPrevChapRequested = null
        NovelAudioPlaybackService.onSeekToChapterRequested = null
        NovelAudioPlaybackService.onSeekToParaRequested = null
        ttsManager?.shutdown()
        super.onCleared()
    }
}
