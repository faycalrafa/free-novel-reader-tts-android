package com.novelarrow.reader.ui.reader

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelarrow.reader.data.backup.ExistingConfigsReport
import com.novelarrow.reader.data.model.*
import com.novelarrow.reader.data.repository.ChapterSortPreset
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.LibraryUpdateEvent
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.updater.AppUpdateManager
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.ui.reader.delegate.*
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepository = LibraryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var lastResumeRefreshTimeMs = 0L

    // Content Loader
    private val contentLoader = ReaderContentLoader(
        scope = viewModelScope,
        uiState = _uiState,
        libraryRepository = libraryRepository,
        playbackDelegateProvider = { playbackDelegate }
    )

    // Delegates
    private val settingsDelegate = ReaderSettingsDelegate(
        application = application,
        settingsRepository = settingsRepository,
        scope = viewModelScope,
        getSettings = { _uiState.value.settings },
        getCurrentChapter = { _uiState.value.currentChapter },
        reloadCurrentChapter = { chap -> viewModelScope.launch { contentLoader.loadChapterContent(chap, true) } }
    )

    private val updateDelegate = ReaderAppUpdateDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState
    )

    private val playbackDelegate: ReaderPlaybackDelegate = ReaderPlaybackDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState,
        settingsRepository = settingsRepository,
        saveCurrentChapterProgress = { contentLoader.saveCurrentChapterProgress() },
        syncActiveProgress = { novId, chIdx, chId, pIdx, comp ->
            contentLoader.syncActiveProgress(novId, chIdx, chId, pIdx, comp)
        },
        selectChapter = { idx, forceStart -> contentLoader.selectChapter(idx, forceStart) },
        reloadCurrentChapter = { chap -> viewModelScope.launch { contentLoader.loadChapterContent(chap, true) } }
    )

    private val novelOpsDelegate: ReaderNovelOperationsDelegate = ReaderNovelOperationsDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState,
        libraryRepository = libraryRepository,
        selectNovel = { contentLoader.selectNovel(it) },
        selectChapter = { contentLoader.selectChapter(it) },
        refreshCurrentNovelState = { contentLoader.refreshCurrentNovelState(it) }
    )

    private val backupDelegate = ReaderConfigBackupDelegate(
        application = application,
        scope = viewModelScope,
        uiState = _uiState,
        settingsRepository = settingsRepository,
        libraryRepository = libraryRepository,
        selectNovel = { contentLoader.selectNovel(it) },
        scanRootFolder = { novelOpsDelegate.scanRootFolder(it) },
        loadInitialLibrary = { loadInitialLibrary() }
    )

    private val chapterOpsDelegate = ReaderChapterOperationsDelegate(
        scope = viewModelScope,
        uiState = _uiState,
        libraryRepository = libraryRepository,
        loadChapterContent = { chap, restore -> viewModelScope.launch { contentLoader.loadChapterContent(chap, restore) } },
        speakAtParagraph = { playbackDelegate.speakAtParagraph(it) },
        updatePlaybackServiceNotification = { playbackDelegate.updatePlaybackServiceNotification() },
        onRefreshLibrary = { novelOpsDelegate.refreshLibrary() }
    )

    init {
        AudioPlaybackCoordinator.initialize(application)
        observeSettings()
        observePlaybackService()
        observeLibraryUpdates()
        checkAndImportDefaultDownloadFolderOnFirstLaunch()
        loadInitialLibrary()
        val installedVer = AppUpdateManager.getInstalledVersionName(application)
        _uiState.update { it.copy(installedVersionName = installedVer) }
        checkForUpdate(silent = true)
    }

    private fun observePlaybackService() {
        viewModelScope.launch {
            AudioPlaybackCoordinator.playbackProgressState.collect { progress ->
                if (progress.source == PlaybackSource.READER) {
                    val curNovel = _uiState.value.currentNovel
                    val isMatchingNovel = curNovel != null && progress.novelId == curNovel.id
                    if (progress.novelId != null) {
                        contentLoader.syncActiveProgress(
                            novelId = progress.novelId,
                            chapterIndex = progress.chapterIndex,
                            chapterId = progress.chapterId,
                            paragraphIndex = progress.currentParagraphIndex,
                            isCompleted = false
                        )
                        _uiState.update { it.copy(lastActiveNovelId = progress.novelId) }
                    }
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
                                contentLoader.loadChapterContent(newChap, restoreProgress = false)
                            }
                        }
                    } else if (progress.novelId != null) {
                        _uiState.update { it.copy(isPlaying = progress.isPlaying) }
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
                val prevSettings = _uiState.value.settings
                _uiState.update { it.copy(settings = settings) }

                val filtersChanged = prevSettings.pronunciationRules != settings.pronunciationRules ||
                        prevSettings.pronunciationRulesEnabled != settings.pronunciationRulesEnabled ||
                        prevSettings.skipWords != settings.skipWords ||
                        prevSettings.skipWordsEnabled != settings.skipWordsEnabled ||
                        prevSettings.headerSkipRules != settings.headerSkipRules ||
                        prevSettings.footerSkipRules != settings.footerSkipRules ||
                        prevSettings.contentSelectors != settings.contentSelectors ||
                        prevSettings.ignoredSelectors != settings.ignoredSelectors

                val currentChapter = _uiState.value.currentChapter
                if (filtersChanged && currentChapter != null && _uiState.value.paragraphs.isNotEmpty()) {
                    contentLoader.loadChapterContent(currentChapter, restoreProgress = true)
                }
            }
        }
    }

    private fun observeLibraryUpdates() {
        viewModelScope.launch {
            var lastEventTime = 0L
            libraryRepository.libraryUpdateEvents.collect { event ->
                val now = System.currentTimeMillis()
                val isHighFreq = event is LibraryUpdateEvent.LibraryChanged
                if (isHighFreq && now - lastEventTime < 200L) {
                    return@collect
                }
                lastEventTime = now

                AppLogger.d("ReaderViewModel", "Received library update event: $event")
                val novels = libraryRepository.getNovels()
                val archived = libraryRepository.getArchivedNovels()
                _uiState.update { current ->
                    current.copy(
                        novels = novels,
                        archivedNovels = archived
                    )
                }

                val curNovel = _uiState.value.currentNovel
                val inspNovel = _uiState.value.inspectedNovel

                when (event) {
                    is LibraryUpdateEvent.ChapterDownloaded,
                    is LibraryUpdateEvent.BatchCommitted -> {
                        val novelTitle = if (event is LibraryUpdateEvent.ChapterDownloaded) event.novelTitle else (event as LibraryUpdateEvent.BatchCommitted).novelTitle
                        if (curNovel != null && curNovel.title.equals(novelTitle, ignoreCase = true)) {
                            val updatedNovel = novels.firstOrNull { it.id == curNovel.id } ?: curNovel
                            contentLoader.refreshCurrentNovelState(updatedNovel)
                        }
                        if (inspNovel != null && inspNovel.title.equals(novelTitle, ignoreCase = true)) {
                            val inspectedChaps = libraryRepository.getChapters(inspNovel.id)
                            _uiState.update { it.copy(inspectedChapters = inspectedChaps) }
                        }
                    }
                    LibraryUpdateEvent.LibraryRefreshed,
                    LibraryUpdateEvent.LibraryChanged -> {
                        if (curNovel != null) {
                            val updatedNovel = novels.firstOrNull { it.id == curNovel.id }
                            if (updatedNovel != null) {
                                contentLoader.refreshCurrentNovelState(updatedNovel)
                            }
                        }
                        if (inspNovel != null) {
                            val inspectedChaps = libraryRepository.getChapters(inspNovel.id)
                            _uiState.update { it.copy(inspectedChapters = inspectedChaps) }
                        }
                    }
                }
            }
        }
    }

    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (now - lastResumeRefreshTimeMs < 4000L) {
            AppLogger.d("ReaderViewModel", "Skipping onAppResumed refresh (debounced)")
            return
        }
        lastResumeRefreshTimeMs = now
        viewModelScope.launch {
            try {
                AppLogger.i("ReaderViewModel", "App resumed: executing background library refresh...")
                libraryRepository.scanDownloadedWebNovels()
                val refreshedNovels = libraryRepository.refreshAllLinkedFolders()
                val archived = libraryRepository.getArchivedNovels()
                _uiState.update {
                    it.copy(
                        novels = refreshedNovels,
                        archivedNovels = archived
                    )
                }
                _uiState.value.currentNovel?.let { cur ->
                    val updated = refreshedNovels.firstOrNull { it.id == cur.id }
                    if (updated != null) {
                        contentLoader.refreshCurrentNovelState(updated)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("ReaderViewModel", "Error in onAppResumed refresh: ${e.message}", e)
            }
        }
    }

    fun loadInitialLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            libraryRepository.scanDownloadedWebNovels()
            val novels = libraryRepository.getNovels()
            val archivedNovels = libraryRepository.getArchivedNovels()
            val lastActiveId = libraryRepository.getLastActiveNovelId()
            val sortedNovels = if (lastActiveId != null) {
                val active = novels.firstOrNull { it.id == lastActiveId }
                if (active != null) {
                    listOf(active) + novels.filterNot { it.id == lastActiveId }
                } else novels
            } else novels

            _uiState.update { it.copy(novels = sortedNovels, archivedNovels = archivedNovels, isLoading = false) }
        }
    }

    // Content & Navigation facade
    fun syncActiveProgress(
        novelId: String? = _uiState.value.currentNovel?.id,
        chapterIndex: Int,
        chapterId: String? = null,
        paragraphIndex: Int = _uiState.value.currentParagraphIndex,
        isCompleted: Boolean = false
    ) {
        contentLoader.syncActiveProgress(novelId, chapterIndex, chapterId, paragraphIndex, isCompleted)
    }

    fun selectNovel(novel: Novel) {
        contentLoader.selectNovel(novel)
    }

    fun selectChapter(index: Int, forceStartAtBeginning: Boolean = false) {
        contentLoader.selectChapter(index, forceStartAtBeginning)
    }

    fun saveCurrentChapterProgress() {
        contentLoader.saveCurrentChapterProgress()
    }

    // Playback facade
    fun playTts() = playbackDelegate.playTts()
    fun pauseTts() = playbackDelegate.pauseTts()
    fun togglePlayPause() = playbackDelegate.togglePlayPause()
    fun stopTts() = playbackDelegate.stopTts()
    fun setSleepTimer(minutes: Int) = playbackDelegate.setSleepTimer(minutes)
    fun seekToParagraph(index: Int) = playbackDelegate.seekToParagraph(index)
    fun speakAtParagraph(index: Int) = playbackDelegate.speakAtParagraph(index)
    fun nextParagraph() = playbackDelegate.nextParagraph()
    fun previousParagraph() = playbackDelegate.previousParagraph()
    fun nextChapter() = playbackDelegate.nextChapter()
    fun previousChapter() = playbackDelegate.previousChapter()
    fun toggleAutoLoop() = playbackDelegate.toggleAutoLoop()
    fun toggleTtsDock() = playbackDelegate.toggleTtsDock()
    fun resetFlashHandleRed() = playbackDelegate.resetFlashHandleRed()
    fun toggleActiveRule(rule: RuleImpact, enabled: Boolean) = playbackDelegate.toggleActiveRule(rule, enabled)
    fun updatePlaybackServiceNotification() = playbackDelegate.updatePlaybackServiceNotification()

    // Library Operations facade
    fun scanRootFolder(treeUri: Uri) = novelOpsDelegate.scanRootFolder(treeUri)
    fun loadSampleNovel() = novelOpsDelegate.loadSampleNovel()
    fun inspectNovel(novel: Novel) = novelOpsDelegate.inspectNovel(novel)
    fun selectChapterFromInspected(index: Int) = novelOpsDelegate.selectChapterFromInspected(index)
    fun deleteNovel(novelId: String, deleteFilesFromDisk: Boolean = false) = novelOpsDelegate.deleteNovel(novelId, deleteFilesFromDisk)
    fun archiveNovel(novel: Novel) = novelOpsDelegate.archiveNovel(novel)
    fun restoreNovel(novel: Novel) = novelOpsDelegate.restoreNovel(novel)
    fun updateNovelTitle(novelId: String, newTitle: String) = novelOpsDelegate.updateNovelTitle(novelId, newTitle)
    fun refreshLinkedFolders() = novelOpsDelegate.refreshLinkedFolders()
    fun clearAllData() = novelOpsDelegate.clearAllData()
    fun refreshLibrary(targetNovelId: String? = null) = novelOpsDelegate.refreshLibrary(targetNovelId)

    // Chapter Operations facade
    fun openRawChapterEditor() = chapterOpsDelegate.openRawChapterEditor()
    fun dismissRawEditor() = chapterOpsDelegate.dismissRawEditor()
    fun saveRawChapterContent(newContent: String) = chapterOpsDelegate.saveRawChapterContent(newContent)
    suspend fun getArchivedChapters(novelId: String): List<Chapter> = chapterOpsDelegate.getArchivedChapters(novelId)
    suspend fun deleteArchivedChapter(novelId: String, chapter: Chapter, deleteFromDisk: Boolean = false): List<Chapter> = chapterOpsDelegate.deleteArchivedChapter(novelId, chapter, deleteFromDisk)
    fun deleteChapter(chapter: Chapter, deleteFromDisk: Boolean = false) = chapterOpsDelegate.deleteChapter(chapter, deleteFromDisk)
    fun moveChapter(fromIndex: Int, toIndex: Int) = chapterOpsDelegate.moveChapter(fromIndex, toIndex)
    fun sortChapters(preset: ChapterSortPreset) = chapterOpsDelegate.sortChapters(preset)
    fun toggleChapterReadStatus(chapter: Chapter) = chapterOpsDelegate.toggleChapterReadStatus(chapter)

    // Config & Backup facade
    fun checkForExistingConfigs(force: Boolean = false, onComplete: (Boolean) -> Unit = {}) = backupDelegate.checkForExistingConfigs(force, onComplete)
    fun restoreExistingConfigs(report: ExistingConfigsReport) = backupDelegate.restoreExistingConfigs(report)
    fun refuseAndDeleteExistingConfigs(report: ExistingConfigsReport) = backupDelegate.refuseAndDeleteExistingConfigs(report)
    fun skipExistingConfigs() = backupDelegate.skipExistingConfigs()
    fun checkAndImportDefaultDownloadFolderOnFirstLaunch() = backupDelegate.checkAndImportDefaultDownloadFolderOnFirstLaunch()

    // Settings facade
    fun updateTheme(theme: ThemeMode) = settingsDelegate.updateTheme(theme)
    fun updateFontSize(sizeSp: Float) = settingsDelegate.updateFontSize(sizeSp)
    fun updateSpeechRate(rate: Float) = settingsDelegate.updateSpeechRate(rate)
    fun cycleSpeechRate() = settingsDelegate.cycleSpeechRate()
    fun updateSpeechPitch(pitch: Float) = settingsDelegate.updateSpeechPitch(pitch)
    fun updateVoice(voiceName: String?) = settingsDelegate.updateVoice(voiceName)
    fun updateAutoScroll(enabled: Boolean) = settingsDelegate.updateAutoScroll(enabled)
    fun updateAutoNext(enabled: Boolean) = settingsDelegate.updateAutoNext(enabled)
    fun updateSoundCuesEnabled(enabled: Boolean) = settingsDelegate.updateSoundCuesEnabled(enabled)
    fun updateQuickToolbarSettings(toolbarSettings: QuickToolbarSettings) = settingsDelegate.updateQuickToolbarSettings(toolbarSettings)
    fun updateHardwareButtonSettings(hardwareButtonSettings: HardwareButtonSettings) = settingsDelegate.updateHardwareButtonSettings(hardwareButtonSettings)
    fun updateContentSelector(selector: String) = settingsDelegate.updateContentSelector(selector)
    fun updateContentSelectorEnabled(enabled: Boolean) = settingsDelegate.updateContentSelectorEnabled(enabled)
    fun updateHeaderSkipString(headerStr: String) = settingsDelegate.updateHeaderSkipString(headerStr)
    fun updateHeaderSkipEnabled(enabled: Boolean) = settingsDelegate.updateHeaderSkipEnabled(enabled)
    fun updateFooterSkipString(footerStr: String) = settingsDelegate.updateFooterSkipString(footerStr)
    fun updateFooterSkipEnabled(enabled: Boolean) = settingsDelegate.updateFooterSkipEnabled(enabled)
    fun updateSkipWordsEnabled(enabled: Boolean) = settingsDelegate.updateSkipWordsEnabled(enabled)
    fun setSkipWordsEnabled(enabled: Boolean) = settingsDelegate.setSkipWordsEnabled(enabled)
    fun updateSkipWordsIgnoreSymbols(enabled: Boolean) = settingsDelegate.updateSkipWordsIgnoreSymbols(enabled)
    fun addSkipWord(word: String, exactMatch: Boolean) = settingsDelegate.addSkipWord(word, exactMatch)
    fun addSkipWord(skipWord: SkipWord) = settingsDelegate.addSkipWord(skipWord)
    fun updateSkipWord(word: SkipWord) = settingsDelegate.updateSkipWord(word)
    fun toggleSkipWord(id: String, enabled: Boolean) = settingsDelegate.toggleSkipWord(id, enabled)
    fun deleteSkipWord(id: String) = settingsDelegate.deleteSkipWord(id)
    fun removeSkipWord(index: Int) = settingsDelegate.removeSkipWord(index)
    fun setPronunciationRulesEnabled(enabled: Boolean) = settingsDelegate.setPronunciationRulesEnabled(enabled)
    fun updatePronunciationRulesEnabled(enabled: Boolean) = settingsDelegate.updatePronunciationRulesEnabled(enabled)
    fun addPronunciationRule(from: String, to: String, exactMatch: Boolean) = settingsDelegate.addPronunciationRule(from, to, exactMatch)
    fun addPronunciationRule(rule: PronunciationRule) = settingsDelegate.addPronunciationRule(rule)
    fun updatePronunciationRule(rule: PronunciationRule) = settingsDelegate.updatePronunciationRule(rule)
    fun togglePronunciationRule(id: String, enabled: Boolean) = settingsDelegate.togglePronunciationRule(id, enabled)
    fun deletePronunciationRule(id: String) = settingsDelegate.deletePronunciationRule(id)
    fun removePronunciationRule(index: Int) = settingsDelegate.removePronunciationRule(index)
    fun addHeaderSkipRule(text: String, exact: Boolean) = settingsDelegate.addHeaderSkipRule(text, exact)
    fun removeHeaderSkipRule(index: Int) = settingsDelegate.removeHeaderSkipRule(index)
    fun addContentSelector(rule: ContentSelectorRule) = settingsDelegate.addContentSelector(rule)
    fun updateContentSelector(rule: ContentSelectorRule) = settingsDelegate.updateContentSelector(rule)
    fun toggleContentSelector(id: String, enabled: Boolean) = settingsDelegate.toggleContentSelector(id, enabled)
    fun deleteContentSelector(id: String) = settingsDelegate.deleteContentSelector(id)
    fun addHeaderSkipRule(rule: SkipRule) = settingsDelegate.addHeaderSkipRule(rule)
    fun updateHeaderSkipRule(rule: SkipRule) = settingsDelegate.updateHeaderSkipRule(rule)
    fun toggleHeaderSkipRule(id: String, enabled: Boolean) = settingsDelegate.toggleHeaderSkipRule(id, enabled)
    fun deleteHeaderSkipRule(id: String) = settingsDelegate.deleteHeaderSkipRule(id)
    fun addFooterSkipRule(rule: SkipRule) = settingsDelegate.addFooterSkipRule(rule)
    fun updateFooterSkipRule(rule: SkipRule) = settingsDelegate.updateFooterSkipRule(rule)
    fun toggleFooterSkipRule(id: String, enabled: Boolean) = settingsDelegate.toggleFooterSkipRule(id, enabled)
    fun deleteFooterSkipRule(id: String) = settingsDelegate.deleteFooterSkipRule(id)
    fun addFooterSkipRule(text: String, exact: Boolean) = settingsDelegate.addFooterSkipRule(text, exact)
    fun removeFooterSkipRule(index: Int) = settingsDelegate.removeFooterSkipRule(index)

    // Update facade
    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
    fun checkForUpdate(silent: Boolean = false) = updateDelegate.checkForUpdate(silent)
    fun dismissUpdateDialog() = updateDelegate.dismissUpdateDialog()
    fun startUpdateDownload() = updateDelegate.startUpdateDownload()
    fun cancelUpdateDownload() = updateDelegate.cancelUpdateDownload()
    fun installDownloadedApk(context: Context, force: Boolean = false) = updateDelegate.installDownloadedApk(context, force)
    fun dismissSignatureMismatchDialog() = updateDelegate.dismissSignatureMismatchDialog()
    fun saveApkToCustomUri(destinationUri: Uri, context: Context) = updateDelegate.saveApkToCustomUri(destinationUri, context)

    override fun onCleared() {
        updateDelegate.onCleared()
        super.onCleared()
    }
}
