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
    val toastMessage: String? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var ttsManager: TtsManager? = null

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
                _uiState.update { it.copy(settings = settings) }
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

    fun selectChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index !in chapters.indices) return
        val chapter = chapters[index]
        val wasPlaying = _uiState.value.isPlaying

        viewModelScope.launch {
            loadChapterContent(chapter, restoreProgress = !wasPlaying)
            if (wasPlaying) {
                speakParagraph(0)
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
                    pronunciationRules = settings.pronunciationRules,
                    skipWords = settings.skipWords,
                    headerSkipString = settings.headerSkipString,
                    footerSkipString = settings.footerSkipString
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
    }

    fun pauseTts() {
        ttsManager?.stop()
        _uiState.update { it.copy(isPlaying = false) }
        updatePlaybackServiceNotification()
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pauseTts() else playTts()
    }

    fun stopTts() {
        ttsManager?.stop()
        _uiState.update { it.copy(isPlaying = false, isTtsDockVisible = false) }
        NovelAudioPlaybackService.stopService(getApplication())
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
                    nextChapter()
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
        val chapters = _uiState.value.chapters
        val currentIdx = _uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx + 1 < chapters.size) {
            selectChapter(currentIdx + 1)
        } else {
            _uiState.update { it.copy(toastMessage = "End of novel reached") }
            stopTts()
        }
    }

    fun previousChapter() {
        val currentIdx = _uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx - 1 >= 0) {
            selectChapter(currentIdx - 1)
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
        val current = _uiState.value.settings.speechRate
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val next = speeds.firstOrNull { it > current + 0.05f } ?: speeds.first()
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

    fun updateHeaderSkipString(headerStr: String) {
        viewModelScope.launch {
            settingsRepository.setHeaderSkipString(headerStr)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateFooterSkipString(footerStr: String) {
        viewModelScope.launch {
            settingsRepository.setFooterSkipString(footerStr)
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
        val progressText = if (total > 0) "¶ $cur / $total" else "Ready"

        NovelAudioPlaybackService.startOrUpdateService(
            context = context,
            isPlaying = state.isPlaying,
            novelTitle = novel,
            chapterTitle = chapter,
            progressText = progressText,
            currentParagraph = cur,
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
        ttsManager?.shutdown()
        super.onCleared()
    }
}
