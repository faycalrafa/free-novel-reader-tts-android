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
import com.novelarrow.reader.playback.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.novelarrow.reader.data.updater.AppUpdateManager
import com.novelarrow.reader.data.updater.CheckUpdateResult
import com.novelarrow.reader.data.updater.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderUiState(
    val novels: List<Novel> = emptyList(),
    val currentNovel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val paragraphs: List<Paragraph> = emptyList(),
    val currentParagraphIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isLibraryScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scanCurrentIndex: Int = 0,
    val scanTotalItems: Int = 0,
    val scanCurrentItem: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val isTtsDockVisible: Boolean = false,
    val isAutoLoopActive: Boolean = false,
    val loopRemaining: Int = 0,
    val availableVoices: List<Voice> = emptyList(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val toastMessage: String? = null,
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateProgress: Float = 0f,
    val updateDownloadedBytes: Long = 0L,
    val updateTotalBytes: Long = 0L,
    val downloadedApk: File? = null,
    val installedVersionName: String = "2.0.1"
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var downloadJob: Job? = null

    init {
        observeSettings()
        observePlaybackService()
        loadInitialLibrary()
        val installedVer = AppUpdateManager.getInstalledVersionName(application)
        _uiState.update { it.copy(installedVersionName = installedVer) }
        checkForUpdate(silent = true)
    }

    private fun observePlaybackService() {
        viewModelScope.launch {
            NovelAudioPlaybackService.playbackProgressState.collect { progress ->
                if (progress.source == PlaybackSource.READER) {
                    val curNovel = _uiState.value.currentNovel
                    val isMatchingNovel = curNovel != null && progress.novelId == curNovel.id
                    if (isMatchingNovel) {
                        val currentChap = _uiState.value.currentChapter
                        val isSameChapter = currentChap != null && (progress.chapterId == currentChap.id || progress.chapterIndex == currentChap.orderIndex)
                        if (isSameChapter) {
                            _uiState.update {
                                it.copy(
                                    isPlaying = progress.isPlaying,
                                    currentParagraphIndex = progress.currentParagraphIndex
                                )
                            }
                        } else {
                            val chapters = _uiState.value.chapters
                            val newChap = chapters.firstOrNull { it.id == progress.chapterId }
                                ?: chapters.getOrNull(progress.chapterIndex)
                            if (newChap != null && newChap.id != currentChap?.id) {
                                _uiState.update {
                                    it.copy(
                                        currentChapter = newChap,
                                        isPlaying = progress.isPlaying,
                                        currentParagraphIndex = progress.currentParagraphIndex
                                    )
                                }
                                loadChapterContent(newChap, restoreProgress = false)
                            }
                        }
                    } else if (progress.novelId == null) {
                        _uiState.update { it.copy(isPlaying = progress.isPlaying) }
                    }
                } else if (progress.source == PlaybackSource.NONE) {
                    _uiState.update { it.copy(isPlaying = false) }
                }
            }
        }
        viewModelScope.launch {
            NovelAudioPlaybackService.availableVoicesState.collect { voices ->
                if (voices.isNotEmpty()) {
                    _uiState.update { it.copy(availableVoices = voices) }
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
            }
        }
    }

    private fun loadInitialLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val novels = libraryRepository.scanDownloadedWebNovels()
            val lastActiveId = libraryRepository.getLastActiveNovelId()
            val targetNovel = novels.firstOrNull { it.id == lastActiveId } ?: novels.firstOrNull()

            _uiState.update { it.copy(novels = novels, isLoading = false) }

            if (targetNovel != null) {
                selectNovel(targetNovel)
            }
        }
    }

    fun refreshLibrary(targetNovelId: String? = null) {
        viewModelScope.launch {
            val novels = libraryRepository.scanDownloadedWebNovels()
            val target = if (targetNovelId != null) {
                novels.firstOrNull { it.id == targetNovelId }
            } else {
                _uiState.value.currentNovel ?: novels.firstOrNull()
            }
            _uiState.update { it.copy(novels = novels) }
            if (target != null) {
                selectNovel(target)
            }
        }
    }

    fun scanRootFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLibraryScanning = true,
                    scanProgress = 0f,
                    scanCurrentIndex = 0,
                    scanTotalItems = 0,
                    scanCurrentItem = "Accessing folder..."
                )
            }
            try {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(treeUri, flags)
                } catch (_: Exception) {
                    try {
                        getApplication<Application>().contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }

                val novels = libraryRepository.scanRootDirectory(treeUri) { current, total, name ->
                    val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    _uiState.update {
                        it.copy(
                            scanProgress = progress.coerceIn(0f, 1f),
                            scanCurrentIndex = current,
                            scanTotalItems = total,
                            scanCurrentItem = name
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        novels = novels,
                        isLibraryScanning = false,
                        toastMessage = if (novels.isNotEmpty()) {
                            "Loaded ${novels.size} playlists from folder"
                        } else {
                            "No supported chapters found in selected folder."
                        }
                    )
                }
                if (novels.isNotEmpty()) {
                    selectNovel(novels.first())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLibraryScanning = false,
                        toastMessage = "Error importing folder: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadSampleNovel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val sampleNovel = libraryRepository.createAndLoadSampleNovel()
            val novels = libraryRepository.getNovels()
            _uiState.update {
                it.copy(
                    novels = novels,
                    isLoading = false,
                    toastMessage = "Loaded sample: Introduction to Quantum Mechanics"
                )
            }
            selectNovel(sampleNovel)
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
                if (chap.id == chapter.id || chap.orderIndex == chapter.orderIndex) {
                    chap.copy(
                        lastReadParagraphIndex = currentIdx,
                        isCompleted = isCompleted || chap.isCompleted
                    )
                } else chap
            }
            state.copy(
                chapters = updatedChapters,
                currentChapter = chapter.copy(
                    lastReadParagraphIndex = currentIdx,
                    isCompleted = isCompleted || chapter.isCompleted
                )
            )
        }
    }

    fun selectChapter(index: Int, forceStartAtBeginning: Boolean = false) {
        val currentChaps = _uiState.value.chapters
        if (index !in currentChaps.indices) return
        val wasPlaying = _uiState.value.isPlaying

        if (wasPlaying) {
            NovelAudioPlaybackService.dispatchPause()
        }

        val currentChap = _uiState.value.currentChapter
        if (currentChap != null && currentChap.orderIndex != index) {
            saveCurrentChapterProgress()
        }

        val chapters = _uiState.value.chapters
        val chapter = chapters[index]

        viewModelScope.launch {
            loadChapterContent(chapter, restoreProgress = !forceStartAtBeginning)
            if (wasPlaying) {
                val startPara = if (forceStartAtBeginning) 0 else _uiState.value.currentParagraphIndex
                speakAtParagraph(startPara)
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
        val state = _uiState.value
        val novel = state.currentNovel ?: return
        val chapter = state.currentChapter ?: return
        val paragraphs = state.paragraphs
        if (paragraphs.isEmpty()) return

        _uiState.update { it.copy(isPlaying = true, isTtsDockVisible = true) }

        NovelAudioPlaybackService.playReaderChapter(
            context = getApplication(),
            novelId = novel.id,
            novelTitle = novel.title,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            chapterIndex = chapter.orderIndex,
            totalChapters = state.chapters.size,
            paragraphs = paragraphs,
            startParagraphIndex = state.currentParagraphIndex,
            autoNext = state.settings.autoNextChapter || state.isAutoLoopActive,
            rate = state.settings.speechRate,
            pitch = state.settings.speechPitch,
            voiceName = state.settings.selectedVoiceName
        )

        // Sleep timer:
        // - If remaining <= 0 (e.g. previous timer finished and paused), restart timer from configured minutes.
        // - If remaining > 0 (user paused mid-timer), continue where left off!
        val timerMinutes = _uiState.value.sleepTimerMinutes
        if (timerMinutes > 0) {
            val remainingSecs = _uiState.value.sleepTimerRemainingSeconds
            if (remainingSecs <= 0) {
                _uiState.update { it.copy(sleepTimerRemainingSeconds = timerMinutes * 60) }
            }
            startSleepTimerCountdown()
        }
    }

    fun pauseTts() {
        NovelAudioPlaybackService.dispatchPause()
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pauseTts() else playTts()
    }

    fun stopTts() {
        NovelAudioPlaybackService.dispatchStop()
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(isPlaying = false, isTtsDockVisible = false) }
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
        val state = _uiState.value
        val novel = state.currentNovel ?: return
        val chapter = state.currentChapter ?: return

        _uiState.update { it.copy(currentParagraphIndex = index, isPlaying = true, isTtsDockVisible = true) }

        val progress = NovelAudioPlaybackService.playbackProgressState.value
        if (progress.source == PlaybackSource.READER &&
            progress.novelId == novel.id &&
            progress.chapterId == chapter.id &&
            NovelAudioPlaybackService.instance != null
        ) {
            NovelAudioPlaybackService.dispatchSeekToPara(index)
        } else {
            NovelAudioPlaybackService.playReaderChapter(
                context = getApplication(),
                novelId = novel.id,
                novelTitle = novel.title,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                chapterIndex = chapter.orderIndex,
                totalChapters = state.chapters.size,
                paragraphs = state.paragraphs,
                startParagraphIndex = index,
                autoNext = state.settings.autoNextChapter || state.isAutoLoopActive,
                rate = state.settings.speechRate,
                pitch = state.settings.speechPitch,
                voiceName = state.settings.selectedVoiceName
            )
        }
    }

    fun nextParagraph() {
        val current = _uiState.value.currentParagraphIndex
        val total = _uiState.value.paragraphs.size
        if (current + 1 < total) {
            if (_uiState.value.isPlaying) {
                NovelAudioPlaybackService.dispatchNextPara()
            } else {
                speakAtParagraph(current + 1)
            }
        } else {
            nextChapter()
        }
    }

    fun previousParagraph() {
        val current = _uiState.value.currentParagraphIndex
        if (current - 1 >= 0) {
            if (_uiState.value.isPlaying) {
                NovelAudioPlaybackService.dispatchPrevPara()
            } else {
                speakAtParagraph(current - 1)
            }
        }
    }

    fun nextChapter() {
        saveCurrentChapterProgress()
        val chapters = _uiState.value.chapters
        val currentIdx = _uiState.value.currentChapter?.orderIndex ?: 0
        if (currentIdx + 1 < chapters.size) {
            if (_uiState.value.isPlaying) {
                NovelAudioPlaybackService.dispatchNextChap()
            } else {
                selectChapter(currentIdx + 1, forceStartAtBeginning = false)
            }
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
            if (_uiState.value.isPlaying) {
                NovelAudioPlaybackService.dispatchPrevChap()
            } else {
                selectChapter(currentIdx - 1, forceStartAtBeginning = false)
            }
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

    fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) {
        viewModelScope.launch {
            libraryRepository.deleteNovel(novelId, deleteFilesFromDisk)
            loadInitialLibrary()
        }
    }

    fun deleteChapter(chapter: Chapter, deleteFromDisk: Boolean = false) {
        val currentNovel = _uiState.value.currentNovel ?: return
        val wasPlaying = _uiState.value.isPlaying
        val isDeletingCurrent = _uiState.value.currentChapter?.id == chapter.id

        if (isDeletingCurrent && wasPlaying) {
            NovelAudioPlaybackService.dispatchPause()
        }

        viewModelScope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.deleteChapter(
                novelId = currentNovel.id,
                chapterId = chapter.id,
                deleteFromDisk = deleteFromDisk
            )

            val updatedNovels = _uiState.value.novels.map { nov ->
                if (nov.id == currentNovel.id && updatedNovel != null) updatedNovel else nov
            }

            if (isDeletingCurrent) {
                if (updatedChapters.isNotEmpty()) {
                    val nextIdx = chapter.orderIndex.coerceIn(0, updatedChapters.size - 1)
                    val nextChapter = updatedChapters[nextIdx]
                    _uiState.update {
                        it.copy(
                            novels = updatedNovels,
                            currentNovel = updatedNovel ?: currentNovel,
                            chapters = updatedChapters,
                            currentChapter = nextChapter,
                            toastMessage = "Deleted chapter: ${chapter.title}"
                        )
                    }
                    loadChapterContent(nextChapter, restoreProgress = false)
                    if (wasPlaying) {
                        speakAtParagraph(0)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            novels = updatedNovels,
                            currentNovel = updatedNovel ?: currentNovel,
                            chapters = emptyList(),
                            currentChapter = null,
                            paragraphs = emptyList(),
                            isPlaying = false,
                            toastMessage = "Deleted last chapter of novel."
                        )
                    }
                    NovelAudioPlaybackService.stopService(getApplication())
                }
            } else {
                val currentChap = _uiState.value.currentChapter
                val refreshedCurrentChap = updatedChapters.firstOrNull { it.id == currentChap?.id } ?: currentChap
                _uiState.update {
                    it.copy(
                        novels = updatedNovels,
                        currentNovel = updatedNovel ?: currentNovel,
                        chapters = updatedChapters,
                        currentChapter = refreshedCurrentChap,
                        toastMessage = "Deleted chapter: ${chapter.title}"
                    )
                }
                updatePlaybackServiceNotification()
            }
        }
    }

    fun moveChapter(fromIndex: Int, toIndex: Int) {
        val currentNovel = _uiState.value.currentNovel ?: return
        viewModelScope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.reorderChapters(
                novelId = currentNovel.id,
                fromIndex = fromIndex,
                toIndex = toIndex
            )
            if (updatedNovel != null) {
                val updatedNovels = _uiState.value.novels.map { nov ->
                    if (nov.id == currentNovel.id) updatedNovel else nov
                }
                val currentChap = _uiState.value.currentChapter
                val refreshedCurrentChap = updatedChapters.firstOrNull { it.id == currentChap?.id } ?: currentChap
                _uiState.update {
                    it.copy(
                        novels = updatedNovels,
                        currentNovel = updatedNovel,
                        chapters = updatedChapters,
                        currentChapter = refreshedCurrentChap
                    )
                }
                updatePlaybackServiceNotification()
            }
        }
    }

    fun refreshLinkedFolders() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLibraryScanning = true,
                    scanProgress = 0f,
                    scanCurrentIndex = 0,
                    scanTotalItems = 0,
                    scanCurrentItem = "Discovering linked folder files..."
                )
            }
            val novels = libraryRepository.refreshAllLinkedFolders { current, total, name ->
                val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                _uiState.update {
                    it.copy(
                        scanProgress = progress.coerceIn(0f, 1f),
                        scanCurrentIndex = current,
                        scanTotalItems = total,
                        scanCurrentItem = name
                    )
                }
            }
            _uiState.update {
                it.copy(
                    novels = novels,
                    isLibraryScanning = false,
                    toastMessage = "Refreshed ${novels.size} folders"
                )
            }
            _uiState.value.currentNovel?.let { cur ->
                val updated = novels.firstOrNull { it.id == cur.id }
                if (updated != null) {
                    selectNovel(updated)
                }
            }
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
            NovelAudioPlaybackService.instance?.updateTtsSettings(
                rate = rate,
                pitch = _uiState.value.settings.speechPitch,
                voiceName = _uiState.value.settings.selectedVoiceName
            )
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
            NovelAudioPlaybackService.instance?.updateTtsSettings(
                rate = _uiState.value.settings.speechRate,
                pitch = pitch,
                voiceName = _uiState.value.settings.selectedVoiceName
            )
        }
    }

    fun updateVoice(voiceName: String?) {
        viewModelScope.launch {
            settingsRepository.setVoice(voiceName)
            NovelAudioPlaybackService.instance?.updateTtsSettings(
                rate = _uiState.value.settings.speechRate,
                pitch = _uiState.value.settings.speechPitch,
                voiceName = voiceName
            )
        }
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

    fun checkForUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value.isCheckingUpdate) return@launch
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val currentVer = _uiState.value.installedVersionName
            when (val result = AppUpdateManager.checkForUpdate(currentVer)) {
                is CheckUpdateResult.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateInfo = result.updateInfo,
                            showUpdateDialog = true
                        )
                    }
                }
                is CheckUpdateResult.UpToDate -> {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                    if (!silent) {
                        _uiState.update {
                            it.copy(toastMessage = "You're on the latest version (${result.currentVersion}).")
                        }
                    }
                }
                is CheckUpdateResult.Error -> {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                    if (!silent) {
                        _uiState.update {
                            it.copy(toastMessage = "Update check failed: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun startUpdateDownload() {
        val info = _uiState.value.updateInfo ?: return
        val context = getApplication<Application>()
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateProgress = 0f,
                    updateDownloadedBytes = 0L,
                    updateTotalBytes = info.apkSize
                )
            }
            try {
                val apkFile = AppUpdateManager.downloadApk(
                    downloadUrl = info.downloadUrl,
                    expectedSize = info.apkSize,
                    context = context,
                    onProgress = { bytesRead, totalBytes ->
                        val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        _uiState.update {
                            it.copy(
                                updateProgress = progress,
                                updateDownloadedBytes = bytesRead,
                                updateTotalBytes = totalBytes
                            )
                        }
                    }
                )
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        downloadedApk = apkFile
                    )
                }
                // Automatically prompt installation
                installDownloadedApk(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        toastMessage = "Update download was cancelled."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        toastMessage = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(isDownloadingUpdate = false) }
    }

    fun installDownloadedApk(context: Context) {
        val apkFile = _uiState.value.downloadedApk ?: return
        try {
            if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                _uiState.update {
                    it.copy(toastMessage = "Please allow installing unknown apps from this source.")
                }
                AppUpdateManager.openInstallPermissionSettings(context)
            } else {
                AppUpdateManager.installApk(context, apkFile)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(toastMessage = "Failed to launch installer: ${e.message}")
            }
        }
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
        sleepTimerJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }
}
