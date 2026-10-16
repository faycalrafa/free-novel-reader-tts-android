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
import com.novelarrow.reader.data.repository.ChapterSortPreset
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.novelarrow.reader.data.updater.AppUpdateManager
import com.novelarrow.reader.data.updater.CheckUpdateResult
import com.novelarrow.reader.data.updater.SignatureCompatibility
import com.novelarrow.reader.data.updater.UpdateInfo
import com.novelarrow.reader.data.backup.BackupRestoreManager
import com.novelarrow.reader.data.backup.ExistingConfigsReport
import com.novelarrow.reader.data.model.WebBrowserSettings
import com.novelarrow.reader.data.repository.UserPreferencesBackup
import com.novelarrow.reader.util.PermissionUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderUiState(
    val novels: List<Novel> = emptyList(),
    val archivedNovels: List<Novel> = emptyList(),
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
    val installedVersionName: String = "2.9.0",
    val showRawEditor: Boolean = false,
    val rawChapterContent: String? = null,
    val showSignatureMismatchDialog: Boolean = false,
    val signatureMismatchInfo: SignatureCompatibility.Mismatch? = null,
    val showExistingConfigsDialog: Boolean = false,
    val existingConfigsReport: ExistingConfigsReport? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var downloadJob: Job? = null

    init {
        AudioPlaybackCoordinator.initialize(application)
        observeSettings()
        observePlaybackService()
        checkAndImportDefaultDownloadFolderOnFirstLaunch()
        loadInitialLibrary()
        val installedVer = AppUpdateManager.getInstalledVersionName(application)
        _uiState.update { it.copy(installedVersionName = installedVer) }
        checkForUpdate(silent = true)
    }

    fun syncActiveProgress(
        novelId: String? = _uiState.value.currentNovel?.id,
        chapterIndex: Int,
        chapterId: String? = null,
        paragraphIndex: Int = _uiState.value.currentParagraphIndex,
        isCompleted: Boolean = false
    ) {
        if (novelId == null) return
        _uiState.update { state ->
            val updatedNovels = state.novels.map { nov ->
                if (nov.id == novelId) nov.copy(currentChapterIndex = chapterIndex) else nov
            }
            val updatedCurrentNovel = if (state.currentNovel?.id == novelId) {
                state.currentNovel.copy(currentChapterIndex = chapterIndex)
            } else {
                state.currentNovel
            }
            val updatedChapters = state.chapters.map { chap ->
                if ((chapterId != null && chap.id == chapterId) || chap.orderIndex == chapterIndex) {
                    chap.copy(
                        lastReadParagraphIndex = paragraphIndex,
                        isCompleted = isCompleted || chap.isCompleted
                    )
                } else chap
            }
            val updatedCurrentChapter = if (state.currentChapter != null &&
                ((chapterId != null && state.currentChapter.id == chapterId) || state.currentChapter.orderIndex == chapterIndex)
            ) {
                state.currentChapter.copy(
                    lastReadParagraphIndex = paragraphIndex,
                    isCompleted = isCompleted || state.currentChapter.isCompleted
                )
            } else {
                state.currentChapter
            }

            state.copy(
                novels = updatedNovels,
                currentNovel = updatedCurrentNovel,
                chapters = updatedChapters,
                currentChapter = updatedCurrentChapter,
                currentParagraphIndex = paragraphIndex
            )
        }
    }

    private fun observePlaybackService() {
        viewModelScope.launch {
            AudioPlaybackCoordinator.playbackProgressState.collect { progress ->
                if (progress.source == PlaybackSource.READER) {
                    val curNovel = _uiState.value.currentNovel
                    val isMatchingNovel = curNovel != null && progress.novelId == curNovel.id
                    if (isMatchingNovel) {
                        val currentChap = _uiState.value.currentChapter
                        val isSameChapter = currentChap != null && (progress.chapterId == currentChap.id || progress.chapterIndex == currentChap.orderIndex)
                        if (isSameChapter) {
                            syncActiveProgress(
                                novelId = curNovel.id,
                                chapterIndex = progress.chapterIndex,
                                chapterId = progress.chapterId,
                                paragraphIndex = progress.currentParagraphIndex,
                                isCompleted = false
                            )
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
                                syncActiveProgress(
                                    novelId = curNovel.id,
                                    chapterIndex = newChap.orderIndex,
                                    chapterId = newChap.id,
                                    paragraphIndex = progress.currentParagraphIndex,
                                    isCompleted = false
                                )
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
            AudioPlaybackCoordinator.availableVoicesState.collect { voices ->
                if (voices.isNotEmpty()) {
                    _uiState.update { it.copy(availableVoices = voices) }
                }
            }
        }
        viewModelScope.launch {
            AudioPlaybackCoordinator.sleepTimerMinutesState.collect { mins ->
                _uiState.update { it.copy(sleepTimerMinutes = mins) }
            }
        }
        viewModelScope.launch {
            AudioPlaybackCoordinator.sleepTimerRemainingSecondsState.collect { secs ->
                _uiState.update { it.copy(sleepTimerRemainingSeconds = secs) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings
                    )
                }
            }
        }
    }

    fun loadInitialLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val novels = libraryRepository.scanDownloadedWebNovels()
            val archivedNovels = libraryRepository.getArchivedNovels()
            val lastActiveId = libraryRepository.getLastActiveNovelId()
            val targetNovel = novels.firstOrNull { it.id == lastActiveId } ?: novels.firstOrNull()

            _uiState.update { it.copy(novels = novels, archivedNovels = archivedNovels, isLoading = false) }

            if (targetNovel != null) {
                selectNovel(targetNovel)
            }
        }
    }

    fun checkForExistingConfigs(force: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val alreadyHandled = prefs.getBoolean("has_handled_existing_configs", false)
            if (!force && alreadyHandled) {
                onComplete(false)
                return@launch
            }
            val report = BackupRestoreManager.detectExistingConfigs(getApplication())
            if (report.hasConfigs) {
                _uiState.update {
                    it.copy(
                        showExistingConfigsDialog = true,
                        existingConfigsReport = report
                    )
                }
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun restoreExistingConfigs(report: ExistingConfigsReport) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
            _uiState.update { it.copy(showExistingConfigsDialog = false, isLoading = true) }
            BackupRestoreManager.restoreAllData(
                getApplication(),
                report,
                settingsRepository,
                libraryRepository
            )
            val novels = libraryRepository.getNovels()
            val target = novels.firstOrNull { it.id == report.libraryBackup?.lastActiveNovelId } ?: novels.firstOrNull()
            _uiState.update {
                it.copy(
                    novels = novels,
                    isLoading = false,
                    toastMessage = "Restored preferences and reading progress."
                )
            }
            if (target != null) {
                selectNovel(target)
            }
        }
    }

    fun refuseAndDeleteExistingConfigs(report: ExistingConfigsReport) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
            _uiState.update { it.copy(showExistingConfigsDialog = false, isLoading = true) }
            BackupRestoreManager.deleteAllHiddenConfigs(getApplication(), report)
            // Reset to defaults
            val defaultSettings = ReaderSettings()
            val defaultBrowser = WebBrowserSettings()
            settingsRepository.applyRestoredBackup(
                UserPreferencesBackup(
                    readerSettings = defaultSettings,
                    browserSettings = defaultBrowser
                )
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = "Saved backup files deleted. Starting fresh."
                )
            }
            loadInitialLibrary()
        }
    }

    fun skipExistingConfigs() {
        val prefs = getApplication<Application>().getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_handled_existing_configs", true).apply()
        _uiState.update { it.copy(showExistingConfigsDialog = false) }
        loadInitialLibrary()
    }

    fun refreshLibrary(targetNovelId: String? = null) {
        viewModelScope.launch {
            val novels = libraryRepository.scanDownloadedWebNovels()
            val archivedNovels = libraryRepository.getArchivedNovels()
            val target = if (targetNovelId != null) {
                novels.firstOrNull { it.id == targetNovelId }
            } else {
                _uiState.value.currentNovel ?: novels.firstOrNull()
            }
            _uiState.update { it.copy(novels = novels, archivedNovels = archivedNovels) }
            if (target != null) {
                selectNovel(target)
            }
        }
    }

    fun checkAndImportDefaultDownloadFolderOnFirstLaunch() {
        val hasPermission = PermissionUtils.hasStoragePermission(getApplication())
        if (!hasPermission) return // Don't consume first launch check if permission is not yet granted

        val prefs = getApplication<Application>().getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val alreadyChecked = prefs.getBoolean("has_checked_default_novelreader_folder", false)
        if (alreadyChecked) return

        val candidateDirs = BackupRestoreManager.getNovelReaderRoots(getApplication())

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val hasChapters = hasAnySupportedChapters(dir)
                if (hasChapters) {
                    prefs.edit().putBoolean("has_checked_default_novelreader_folder", true).apply()
                    scanRootFolder(Uri.fromFile(dir))
                    return
                }
            }
        }
        prefs.edit().putBoolean("has_checked_default_novelreader_folder", true).apply()
    }

    private fun hasAnySupportedChapters(dir: File): Boolean {
        return try {
            val files = dir.listFiles() ?: return false
            files.any { file ->
                if (file.isDirectory) {
                    val subFiles = file.listFiles() ?: emptyArray()
                    subFiles.any { it.isFile && libraryRepository.isSupported(it.name) }
                } else {
                    file.isFile && libraryRepository.isSupported(file.name)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openRawChapterEditor() {
        val chapter = _uiState.value.currentChapter ?: return
        viewModelScope.launch {
            try {
                val content = libraryRepository.readChapterContent(chapter)
                _uiState.update {
                    it.copy(
                        rawChapterContent = content,
                        showRawEditor = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(toastMessage = "Error loading raw chapter: ${e.message}") }
            }
        }
    }

    fun dismissRawEditor() {
        _uiState.update { it.copy(showRawEditor = false, rawChapterContent = null) }
    }

    fun saveRawChapterContent(newContent: String) {
        val chapter = _uiState.value.currentChapter ?: return
        viewModelScope.launch {
            val success = libraryRepository.saveChapterContent(chapter, newContent)
            if (success) {
                _uiState.update {
                    it.copy(
                        toastMessage = "Saved chapter changes successfully",
                        showRawEditor = false,
                        rawChapterContent = null
                    )
                }
                loadChapterContent(chapter, restoreProgress = true)
            } else {
                _uiState.update { it.copy(toastMessage = "Failed to save chapter content") }
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

        syncActiveProgress(
            novelId = novel.id,
            chapterIndex = chapter.orderIndex,
            chapterId = chapter.id,
            paragraphIndex = currentIdx,
            isCompleted = isCompleted
        )

        viewModelScope.launch {
            libraryRepository.updateChapterProgress(
                novelId = novel.id,
                chapterIndex = chapter.orderIndex,
                paragraphIndex = currentIdx,
                scrollOffset = 0,
                isCompleted = isCompleted
            )
        }
    }

    fun selectChapter(index: Int, forceStartAtBeginning: Boolean = false) {
        val currentChaps = _uiState.value.chapters
        if (index !in currentChaps.indices) return
        val wasPlaying = _uiState.value.isPlaying

        if (wasPlaying) {
            AudioPlaybackCoordinator.pause()
        }

        val currentChap = _uiState.value.currentChapter
        if (currentChap != null && currentChap.orderIndex != index) {
            saveCurrentChapterProgress()
        }

        val chapters = _uiState.value.chapters
        val chapter = chapters[index]

        val curNovel = _uiState.value.currentNovel
        if (curNovel != null) {
            syncActiveProgress(
                novelId = curNovel.id,
                chapterIndex = index,
                chapterId = chapter.id,
                paragraphIndex = if (forceStartAtBeginning) 0 else chapter.lastReadParagraphIndex
            )
        }

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
                    footerSkipEnabled = settings.footerSkipEnabled,
                    headerSkipRules = settings.headerSkipRules,
                    footerSkipRules = settings.footerSkipRules,
                    folderPath = chapter.fileUri.ifBlank { _uiState.value.currentNovel?.rootTreeUri },
                    novelUrl = chapter.webSourceUrl ?: _uiState.value.currentNovel?.webUrl
                )
            }

            val targetIndex = if (restoreProgress) {
                chapter.lastReadParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            } else 0

            val novel = _uiState.value.currentNovel
            if (novel != null) {
                syncActiveProgress(
                    novelId = novel.id,
                    chapterIndex = chapter.orderIndex,
                    chapterId = chapter.id,
                    paragraphIndex = targetIndex,
                    isCompleted = chapter.isCompleted
                )
            }

            _uiState.update {
                it.copy(
                    paragraphs = paragraphs,
                    currentParagraphIndex = targetIndex,
                    isLoading = false
                )
            }

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

        AudioPlaybackCoordinator.playReaderChapter(
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

    }

    fun pauseTts() {
        AudioPlaybackCoordinator.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pauseTts() else playTts()
    }

    fun stopTts() {
        AudioPlaybackCoordinator.stop()
        _uiState.update { it.copy(isPlaying = false, isTtsDockVisible = false) }
    }

    fun setSleepTimer(minutes: Int) {
        AudioPlaybackCoordinator.setSleepTimer(minutes)
    }

    fun speakAtParagraph(index: Int) {
        val total = _uiState.value.paragraphs.size
        if (index !in 0 until total) return
        val state = _uiState.value
        val novel = state.currentNovel ?: return
        val chapter = state.currentChapter ?: return

        _uiState.update { it.copy(currentParagraphIndex = index, isPlaying = true, isTtsDockVisible = true) }

        val progress = AudioPlaybackCoordinator.playbackProgressState.value
        if (progress.source == PlaybackSource.READER &&
            progress.novelId == novel.id &&
            progress.chapterId == chapter.id
        ) {
            AudioPlaybackCoordinator.seekToParagraph(index)
        } else {
            AudioPlaybackCoordinator.playReaderChapter(
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
                AudioPlaybackCoordinator.nextParagraph()
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
                AudioPlaybackCoordinator.previousParagraph()
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
            val nextIdx = currentIdx + 1
            val novel = _uiState.value.currentNovel
            if (novel != null && nextIdx in chapters.indices) {
                syncActiveProgress(
                    novelId = novel.id,
                    chapterIndex = nextIdx,
                    chapterId = chapters[nextIdx].id,
                    paragraphIndex = chapters[nextIdx].lastReadParagraphIndex
                )
            }
            if (_uiState.value.isPlaying) {
                AudioPlaybackCoordinator.nextChapter()
            } else {
                selectChapter(nextIdx, forceStartAtBeginning = false)
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
            val prevIdx = currentIdx - 1
            val novel = _uiState.value.currentNovel
            if (novel != null && prevIdx in chapters.indices) {
                syncActiveProgress(
                    novelId = novel.id,
                    chapterIndex = prevIdx,
                    chapterId = chapters[prevIdx].id,
                    paragraphIndex = chapters[prevIdx].lastReadParagraphIndex
                )
            }
            if (_uiState.value.isPlaying) {
                AudioPlaybackCoordinator.previousChapter()
            } else {
                selectChapter(prevIdx, forceStartAtBeginning = false)
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
        val wasPlaying = _uiState.value.isPlaying
        val isCurrent = _uiState.value.currentNovel?.id == novelId
        if (isCurrent && wasPlaying) {
            AudioPlaybackCoordinator.pause()
        }
        viewModelScope.launch {
            libraryRepository.deleteNovel(novelId, deleteFilesFromDisk)
            loadInitialLibrary()
        }
    }

    fun archiveNovel(novel: Novel) {
        val wasPlaying = _uiState.value.isPlaying
        val isCurrent = _uiState.value.currentNovel?.id == novel.id
        if (isCurrent && wasPlaying) {
            AudioPlaybackCoordinator.pause()
        }
        viewModelScope.launch {
            libraryRepository.archiveNovel(novel.id)
            refreshLibrary()
            _uiState.update { it.copy(toastMessage = "Archived '${novel.title}'") }
        }
    }

    fun restoreNovel(novel: Novel) {
        viewModelScope.launch {
            libraryRepository.restoreNovel(novel.id)
            refreshLibrary(targetNovelId = novel.id)
            _uiState.update { it.copy(toastMessage = "Restored '${novel.title}' to library") }
        }
    }

    fun updateNovelTitle(novelId: String, newTitle: String) {
        viewModelScope.launch {
            libraryRepository.updateNovelTitle(novelId, newTitle)
            refreshLibrary()
        }
    }

    suspend fun getArchivedChapters(novelId: String): List<Chapter> {
        return libraryRepository.getChapters(novelId)
    }

    suspend fun deleteArchivedChapter(novelId: String, chapter: Chapter, deleteFromDisk: Boolean = false): List<Chapter> {
        val (_, updatedChapters) = libraryRepository.deleteChapter(
            novelId = novelId,
            chapterId = chapter.id,
            deleteFromDisk = deleteFromDisk
        )
        refreshLibrary()
        return updatedChapters
    }

    fun deleteChapter(chapter: Chapter, deleteFromDisk: Boolean = false) {
        val currentNovel = _uiState.value.currentNovel ?: return
        val wasPlaying = _uiState.value.isPlaying
        val isDeletingCurrent = _uiState.value.currentChapter?.id == chapter.id

        if (isDeletingCurrent && wasPlaying) {
            AudioPlaybackCoordinator.pause()
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
                    AudioPlaybackCoordinator.stop()
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

    fun sortChapters(preset: ChapterSortPreset) {
        val currentNovel = _uiState.value.currentNovel ?: return
        viewModelScope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.sortChapters(
                novelId = currentNovel.id,
                preset = preset
            )
            if (updatedNovel != null) {
                val updatedNovels = _uiState.value.novels.map { nov ->
                    if (nov.id == currentNovel.id) updatedNovel else nov
                }
                val currentChap = _uiState.value.currentChapter
                val refreshedCurrentChap = updatedChapters.firstOrNull { it.id == currentChap?.id }
                    ?: updatedChapters.getOrNull(updatedNovel.currentChapterIndex)
                    ?: updatedChapters.firstOrNull()
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

    fun toggleChapterReadStatus(chapter: Chapter) {
        val currentNovel = _uiState.value.currentNovel ?: return
        viewModelScope.launch {
            val (updatedNovel, updatedChapters) = libraryRepository.toggleChapterReadStatus(
                novelId = currentNovel.id,
                chapterId = chapter.id
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
            AudioPlaybackCoordinator.updateTtsSettings(
                rate = rate,
                pitch = _uiState.value.settings.speechPitch,
                voiceName = _uiState.value.settings.selectedVoiceName
            )
        }
    }

    fun cycleSpeechRate() {
        AudioPlaybackCoordinator.cycleSpeechRate()
    }

    fun updateSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            settingsRepository.setSpeechPitch(pitch)
            AudioPlaybackCoordinator.updateTtsSettings(
                rate = _uiState.value.settings.speechRate,
                pitch = pitch,
                voiceName = _uiState.value.settings.selectedVoiceName
            )
        }
    }

    fun updateVoice(voiceName: String?) {
        viewModelScope.launch {
            settingsRepository.setVoice(voiceName)
            AudioPlaybackCoordinator.updateTtsSettings(
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

    fun updateQuickToolbarSettings(toolbarSettings: com.novelarrow.reader.data.model.QuickToolbarSettings) {
        viewModelScope.launch {
            settingsRepository.updateQuickToolbarSettings(toolbarSettings)
            com.novelarrow.reader.playback.QuickToolbarNotificationManager.updateToolbar(getApplication())
        }
    }

    fun updateHardwareButtonSettings(hardwareButtonSettings: com.novelarrow.reader.data.model.HardwareButtonSettings) {
        viewModelScope.launch {
            settingsRepository.updateHardwareButtonSettings(hardwareButtonSettings)
        }
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

    fun setSkipWordsEnabled(enabled: Boolean) {
        updateSkipWordsEnabled(enabled)
    }

    fun addSkipWord(word: String, exactMatch: Boolean) {
        viewModelScope.launch {
            settingsRepository.addSkipWord(com.novelarrow.reader.data.model.SkipWord(text = word, exactMatch = exactMatch))
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun addSkipWord(skipWord: com.novelarrow.reader.data.model.SkipWord) {
        viewModelScope.launch {
            settingsRepository.addSkipWord(skipWord)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updateSkipWord(word: com.novelarrow.reader.data.model.SkipWord) {
        viewModelScope.launch {
            settingsRepository.updateSkipWord(word)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun toggleSkipWord(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleSkipWord(id, enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun deleteSkipWord(id: String) {
        viewModelScope.launch {
            settingsRepository.deleteSkipWord(id)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun removeSkipWord(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeSkipWord(index)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun setPronunciationRulesEnabled(enabled: Boolean) {
        updatePronunciationRulesEnabled(enabled)
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

    fun addPronunciationRule(rule: com.novelarrow.reader.data.model.PronunciationRule) {
        viewModelScope.launch {
            settingsRepository.addPronunciationRule(rule)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun updatePronunciationRule(rule: com.novelarrow.reader.data.model.PronunciationRule) {
        viewModelScope.launch {
            settingsRepository.updatePronunciationRule(rule)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun togglePronunciationRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.togglePronunciationRule(id, enabled)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun deletePronunciationRule(id: String) {
        viewModelScope.launch {
            settingsRepository.deletePronunciationRule(id)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun removePronunciationRule(index: Int) {
        viewModelScope.launch {
            settingsRepository.removePronunciationRule(index)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun addHeaderSkipRule(text: String, exact: Boolean) {
        viewModelScope.launch {
            settingsRepository.addHeaderSkipRule(com.novelarrow.reader.data.model.SkipRule(text = text, exactMatch = exact))
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun removeHeaderSkipRule(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeHeaderSkipRule(index)
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun addFooterSkipRule(text: String, exact: Boolean) {
        viewModelScope.launch {
            settingsRepository.addFooterSkipRule(com.novelarrow.reader.data.model.SkipRule(text = text, exactMatch = exact))
            _uiState.value.currentChapter?.let { loadChapterContent(it, restoreProgress = true) }
        }
    }

    fun removeFooterSkipRule(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeFooterSkipRule(index)
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

    fun installDownloadedApk(context: Context, force: Boolean = false) {
        val apkFile = _uiState.value.downloadedApk ?: return
        try {
            if (!force) {
                val compatibility = AppUpdateManager.checkSignatureCompatibility(context, apkFile)
                if (compatibility is SignatureCompatibility.Mismatch) {
                    _uiState.update {
                        it.copy(
                            showSignatureMismatchDialog = true,
                            signatureMismatchInfo = compatibility
                        )
                    }
                    return
                }
            }

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

    fun dismissSignatureMismatchDialog() {
        _uiState.update {
            it.copy(
                showSignatureMismatchDialog = false,
                signatureMismatchInfo = null
            )
        }
    }

    fun saveApkToCustomUri(destinationUri: Uri, context: Context) {
        viewModelScope.launch {
            val apkFile = _uiState.value.downloadedApk
            if (apkFile != null && apkFile.exists()) {
                val success = AppUpdateManager.copyApkToUri(context, apkFile, destinationUri)
                _uiState.update {
                    it.copy(
                        toastMessage = if (success) "Saved update-latest.apk to selected path" else "Failed to save APK to selected path"
                    )
                }
            } else {
                val info = _uiState.value.updateInfo
                val downloadUrl = info?.downloadUrl ?: "https://github.com/faycalrafa/free-novel-reader-tts-android/releases/latest/download/update-latest.apk"
                val apkSize = info?.apkSize ?: 0L
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = true,
                        updateProgress = 0f,
                        updateDownloadedBytes = 0L,
                        updateTotalBytes = apkSize
                    )
                }
                try {
                    val tempApk = AppUpdateManager.downloadApk(
                        downloadUrl = downloadUrl,
                        expectedSize = apkSize,
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
                            downloadedApk = tempApk
                        )
                    }
                    val success = AppUpdateManager.copyApkToUri(context, tempApk, destinationUri)
                    _uiState.update {
                        it.copy(
                            toastMessage = if (success) "Saved update-latest.apk to selected path" else "Failed to save APK to selected path"
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
    }

    private fun updatePlaybackServiceNotification() {
        val state = _uiState.value
        val novel = state.currentNovel?.title ?: "NovelArrow Reader"
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

    override fun onCleared() {
        sleepTimerJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }
}
