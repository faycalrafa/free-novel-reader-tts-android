package com.novelarrow.reader.ui.browser.delegate

import android.app.Application
import android.util.Log
import com.novelarrow.reader.browser.engine.WebContentExtractor
import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.data.browser.DomContentVerifier
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.playback.AudioCueManager
import com.novelarrow.reader.ui.browser.BrowserUiState
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BrowserDownloadDelegate(
    private val application: Application,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchEvaluateJs: (String, ((String?) -> Unit)?) -> Unit,
    private val dispatchLoadUrl: (String) -> Unit,
    private val evaluateJsWithTimeout: suspend (String, Long) -> String?,
    private val playClickSound: () -> Unit,
    private val onClearLiveHighlight: () -> Unit,
    private val preExtractedNextChapterUrlProvider: () -> String?,
    private val setPreExtractedNextChapterUrl: (String?) -> Unit,
    private val clearPreExtractedNextChapterUrl: () -> Unit,
    val downloadHistoryRepository: com.novelarrow.reader.data.repository.DownloadHistoryRepository = com.novelarrow.reader.data.repository.DownloadHistoryRepository(application)
) {
    companion object {
        private const val TAG = "BrowserDownloadDelegate"
    }

    private var downloadCountdownJob: Job? = null
    private var domVerificationJob: Job? = null

    private var lastBatchDownloadedUrl: String = ""
    private var lastBatchDownloadedChapterNum: Int = -1
    private var duplicatePageRetryCount: Int = 0

    private var lastExtractedHeading: String = ""
    private var lastExtractedSample: String = ""
    var isNavigatingNextChapter: Boolean = false

    private val chapterExtractor = BrowserChapterExtractor(
        application = application,
        scope = scope,
        uiState = uiState,
        libraryRepository = libraryRepository,
        dispatchEvaluateJs = dispatchEvaluateJs,
        clickNextChapter = { clickNextChapter() },
        startDomContentVerification = { startDomContentVerification() },
        getLastBatchDownloadedUrl = { lastBatchDownloadedUrl },
        setLastBatchDownloadedUrl = { lastBatchDownloadedUrl = it },
        setLastBatchDownloadedChapterNum = { lastBatchDownloadedChapterNum = it },
        getLastExtractedHeading = { lastExtractedHeading },
        setLastExtractedHeading = { lastExtractedHeading = it },
        getLastExtractedSample = { lastExtractedSample },
        setLastExtractedSample = { lastExtractedSample = it },
        setPreExtractedNextChapterUrl = setPreExtractedNextChapterUrl
    )

    fun clickNextChapter() {
        playClickSound()
        AudioCueManager.playChapterChange()
        if (uiState.value.isLiveReading) {
            onClearLiveHighlight()
            uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0) }
        }
        isNavigatingNextChapter = true
        val settings = uiState.value.settings
        val currentUrl = uiState.value.currentUrl

        val directUrl = preExtractedNextChapterUrlProvider()
        if (!directUrl.isNullOrBlank() && directUrl != currentUrl && (directUrl.startsWith("http://") || directUrl.startsWith("https://"))) {
            AppLogger.i(TAG, "Navigating to next chapter directly via pre-extracted URL: $directUrl")
            clearPreExtractedNextChapterUrl()
            dispatchLoadUrl(directUrl)
            return
        }

        dispatchEvaluateJs(
            WebScriptInjector.buildNextChapterScript(
                customSelector = settings.nextSelector,
                rules = settings.nextChapterRules,
                currentUrl = currentUrl
            ),
            null
        )
    }

    fun clickPrevChapter() {
        playClickSound()
        if (uiState.value.isLiveReading) {
            onClearLiveHighlight()
            uiState.update { it.copy(liveParagraphs = emptyList(), liveCurrentParagraphIndex = 0) }
        }
        dispatchEvaluateJs(WebScriptInjector.buildPrevChapterScript(), null)
    }

    fun openDownloadDialog() {
        val currentUrl = uiState.value.currentUrl
        val pageTitle = uiState.value.pageTitle
        val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
        val settings = uiState.value.settings

        uiState.update {
            it.copy(
                showDownloadDialog = true,
                detectedTitle = detected.novelTitle,
                detectedChapter = detected.chapterNumber,
                dialogNovelTitle = detected.novelTitle,
                dialogStartingChapter = detected.chapterNumber,
                dialogLoopCount = settings.loopCount,
                dialogDelayMs = settings.countdownDelayMs
            )
        }
    }

    fun dismissDownloadDialog() {
        uiState.update { it.copy(showDownloadDialog = false) }
    }

    fun setDialogNovelTitle(title: String) {
        uiState.update { it.copy(dialogNovelTitle = title) }
    }

    fun setDialogStartingChapter(chapter: Int) {
        uiState.update { it.copy(dialogStartingChapter = chapter.coerceAtLeast(1)) }
    }

    fun setDialogLoopCount(count: Int) {
        uiState.update { it.copy(dialogLoopCount = count.coerceAtLeast(1)) }
    }

    fun setDialogDelayMs(delayMs: Long) {
        uiState.update { it.copy(dialogDelayMs = delayMs.coerceAtLeast(200L)) }
    }

    fun saveDownloadedChapterFromScript(novelTitle: String, chapterTitle: String, chapterNumber: Int, htmlContent: String) =
        chapterExtractor.saveDownloadedChapterFromScript(novelTitle, chapterTitle, chapterNumber, htmlContent)

    private val genericDownloadHandler: BrowserGenericDownloadHandler = BrowserGenericDownloadHandler(
        application = application,
        scope = scope,
        uiState = uiState,
        downloadHistoryRepository = downloadHistoryRepository,
        dispatchEvaluateJs = dispatchEvaluateJs
    )

    fun handleGenericFileDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        contentLength: Long
    ) = genericDownloadHandler.handleGenericFileDownload(url, userAgent, contentDisposition, mimetype, contentLength)

    fun handleBase64Download(dataUri: String, fileName: String, mimeType: String) =
        genericDownloadHandler.handleBase64Download(dataUri, fileName, mimeType)

    fun syncFolder(novelTitle: String?) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                AppLogger.i(TAG, "Triggering folder sync for novel: $novelTitle")
                if (!novelTitle.isNullOrBlank()) {
                    libraryRepository.commitBatch(novelTitle)
                }
                libraryRepository.refreshEntireLibrary()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    uiState.update {
                        it.copy(
                            isAutoLoopActive = false,
                            toastMessage = if (!novelTitle.isNullOrBlank()) "Library synced: $novelTitle" else "Library synced"
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error syncing novel folder: ${e.message}", e)
            }
        }
    }

    fun onAutoLoopStateChanged(active: Boolean, remainingCount: Int, status: String) {
        uiState.update {
            it.copy(
                isAutoLoopActive = active,
                autoLoopRemainingCount = remainingCount,
                autoLoopStatus = status
            )
        }
    }

    fun onLoopButtonClicked() {
        dispatchEvaluateJs(
            """
            (function() {
                if (typeof window.__triggerCustomToolbarButton === 'function' && window.__gmCustomButtons && window.__gmCustomButtons['multi_download']) {
                    window.__triggerCustomToolbarButton('multi_download');
                } else if (typeof window.__novelMultiDownloadToggle === 'function') {
                    window.__novelMultiDownloadToggle();
                } else {
                    var btn = document.getElementById('na-dl-btn');
                    if (btn) {
                        btn.click();
                    } else if (typeof window.__triggerCustomToolbarButton === 'function') {
                        window.__triggerCustomToolbarButton('multi_download');
                    }
                }
            })();
            """.trimIndent(),
            null
        )
    }

    fun startDownloadLoop() {
        val state = uiState.value
        val title = state.dialogNovelTitle.trim().ifBlank { state.detectedTitle }
        val startingChapter = state.dialogStartingChapter
        val loopCount = state.dialogLoopCount
        val delayMs = state.dialogDelayMs.coerceAtLeast(200L)

        lastBatchDownloadedUrl = ""
        lastBatchDownloadedChapterNum = -1
        duplicatePageRetryCount = 0
        lastExtractedHeading = ""
        lastExtractedSample = ""
        domVerificationJob?.cancel()
        domVerificationJob = null

        uiState.update {
            it.copy(
                showDownloadDialog = false,
                isDownloading = true,
                downloadNovelTitle = title,
                downloadCurrentChapter = startingChapter,
                downloadRemainingCount = loopCount,
                downloadTotalCount = loopCount,
                downloadCountdownSeconds = 0,
                downloadStatus = "Starting download loop..."
            )
        }

        scope.launch {
            settingsRepository.updateBrowserSettings {
                it.copy(loopCount = loopCount, countdownDelayMs = delayMs)
            }
        }

        if (!state.isLoading && state.progress == 100) {
            triggerBatchDownloadSequence()
        } else {
            uiState.update { it.copy(downloadStatus = "Waiting for page to fully load...") }
        }
    }

    fun stopDownloadLoop() {
        downloadCountdownJob?.cancel()
        downloadCountdownJob = null
        domVerificationJob?.cancel()
        domVerificationJob = null
        lastExtractedHeading = ""
        lastExtractedSample = ""
        duplicatePageRetryCount = 0
        lastBatchDownloadedUrl = ""
        lastBatchDownloadedChapterNum = -1
        val novelTitle = uiState.value.downloadNovelTitle
        if (novelTitle.isNotBlank()) {
            scope.launch {
                libraryRepository.commitBatch(novelTitle)
            }
        }
        uiState.update {
            it.copy(
                isDownloading = false,
                downloadStatus = "Stopped",
                downloadCountdownSeconds = 0,
                toastMessage = "Batch download stopped."
            )
        }
    }

    fun onPageFinishedHook(url: String) {
        AppLogger.d(TAG, "onPageFinished during batch download for URL: $url")
        if (lastBatchDownloadedUrl.isNotBlank() && url != lastBatchDownloadedUrl) {
            AppLogger.i(TAG, "onPageFinished: URL changed from '$lastBatchDownloadedUrl' to '$url'. Triggering download directly!")
            domVerificationJob?.cancel()
            uiState.update { it.copy(downloadStatus = "New chapter URL loaded, downloading...") }
            triggerBatchDownloadSequence(isDomTransitionVerified = true)
            return
        }
        domVerificationJob?.cancel()
        startDomContentVerification()
    }

    fun startDomContentVerification() {
        domVerificationJob?.cancel()
        domVerificationJob = scope.launch {
            AppLogger.d(TAG, "Starting DOM content verification polling...")
            uiState.update { it.copy(downloadStatus = "Verifying DOM transition...") }

            val timeoutMs = 20000L
            val pollIntervalMs = 700L
            val startTime = System.currentTimeMillis()
            var contentTransitioned = false

            while (isActive && (System.currentTimeMillis() - startTime) < timeoutMs) {
                delay(pollIntervalMs)
                if (!uiState.value.isDownloading) return@launch

                if (uiState.value.isLoading) {
                    uiState.update { it.copy(downloadStatus = "Loading page...") }
                    continue
                }

                val currentUrl = uiState.value.currentUrl
                val urlChanged = lastBatchDownloadedUrl.isNotBlank() && currentUrl != lastBatchDownloadedUrl

                if (urlChanged) {
                    AppLogger.i(TAG, "DOM transition verified via URL change: '$lastBatchDownloadedUrl' -> '$currentUrl'")
                    contentTransitioned = true
                    isNavigatingNextChapter = false
                    break
                }

                val settings = uiState.value.settings
                val script = WebContentExtractor.buildContentExtractionScript(
                    contentSelector = settings.contentSelector,
                    ignoredSelectors = settings.ignoredSelectors,
                    contentSelectorRules = settings.contentSelectors,
                    currentUrl = currentUrl
                )
                val rawResult = evaluateJsWithTimeout(script, 2000L)

                if (rawResult != null && rawResult != "null") {
                    val extracted = WebContentExtractor.extractChapterContent(rawResult, uiState.value.pageTitle)
                    val currentHeading = extracted.title
                    val currentSample = DomContentVerifier.createChapterFingerprint(extracted.paragraphs)

                    if (extracted.paragraphs.isNotEmpty() || currentHeading.isNotBlank()) {
                        val headingChanged = currentHeading.isNotBlank() && currentHeading != lastExtractedHeading
                        val bodyChanged = currentSample.isNotBlank() && currentSample != lastExtractedSample

                        if (headingChanged || bodyChanged) {
                            AppLogger.i(TAG, "DOM transition verified! headingChanged=$headingChanged, bodyChanged=$bodyChanged (heading='$currentHeading')")
                            contentTransitioned = true
                            isNavigatingNextChapter = false
                            break
                        }
                    }
                }

                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                uiState.update { it.copy(downloadStatus = "Waiting for chapter content (${elapsedSec}s)...") }
            }

            if (!uiState.value.isDownloading) return@launch

            if (contentTransitioned) {
                uiState.update { it.copy(downloadStatus = "New chapter detected, preparing download...") }
                triggerBatchDownloadSequence(isDomTransitionVerified = true)
            } else {
                AppLogger.w(TAG, "DOM transition verification timed out after 20s without content change.")
                if (duplicatePageRetryCount < 2) {
                    duplicatePageRetryCount++
                    uiState.update { it.copy(downloadStatus = "Retrying next chapter navigation (attempt $duplicatePageRetryCount)...") }
                    clickNextChapter()
                    startDomContentVerification()
                } else {
                    AppLogger.w(TAG, "Stopping download loop cleanly: DOM content did not advance after retries.")
                    stopDownloadLoop()
                    uiState.update {
                        it.copy(
                            downloadStatus = "Stopped: Content did not advance",
                            toastMessage = "Batch download stopped: Page content did not transition."
                        )
                    }
                }
            }
        }
    }

    fun triggerBatchDownloadSequence(isDomTransitionVerified: Boolean = false) {
        if (!uiState.value.isDownloading) return
        downloadCountdownJob?.cancel()
        domVerificationJob?.cancel()

        downloadCountdownJob = scope.launch {
            val currentUrl = uiState.value.currentUrl
            val pageTitle = uiState.value.pageTitle

            if (!isDomTransitionVerified && lastBatchDownloadedUrl.isNotBlank() && currentUrl == lastBatchDownloadedUrl) {
                AppLogger.d(TAG, "Page is still on last downloaded URL ($currentUrl). Waiting for navigation...")
                uiState.update { it.copy(downloadStatus = "Waiting for next chapter...") }
                startDomContentVerification()
                return@launch
            }

            val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
            val novelTitle = uiState.value.downloadNovelTitle.ifBlank { detected.novelTitle }

            val chapterNum = if (isDomTransitionVerified && uiState.value.downloadCurrentChapter > 0) {
                uiState.value.downloadCurrentChapter
            } else if (detected.chapterNumber > 0) {
                detected.chapterNumber
            } else if (uiState.value.downloadCurrentChapter > 0) {
                uiState.value.downloadCurrentChapter
            } else {
                1
            }
            uiState.update { it.copy(downloadCurrentChapter = chapterNum) }

            val chapterAlreadyExists = WebNovelDownloader.chapterAlreadyExists(application, novelTitle, chapterNum, currentUrl)

            if (chapterAlreadyExists) {
                Log.d(TAG, "Chapter $chapterNum already exists on disk. Skipping file write and advancing.")
                duplicatePageRetryCount = 0
                val remaining = uiState.value.downloadRemainingCount
                val newRemaining = remaining - 1
                val nextChapterNum = chapterNum + 1
                lastBatchDownloadedUrl = currentUrl
                lastBatchDownloadedChapterNum = chapterNum

                uiState.update {
                    it.copy(
                        downloadRemainingCount = newRemaining,
                        downloadCurrentChapter = nextChapterNum,
                        downloadStatus = "Chapter $chapterNum already exists, advancing to next..."
                    )
                }

                if (newRemaining <= 0) {
                    uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Completed",
                            toastMessage = "Batch download completed for $novelTitle!"
                        )
                    }
                } else {
                    delay(600)
                    clickNextChapter()
                    startDomContentVerification()
                }
                return@launch
            }

            duplicatePageRetryCount = 0

            val delayMs = uiState.value.settings.countdownDelayMs.coerceAtLeast(200L)
            val totalSteps = (delayMs / 100).toInt().coerceAtLeast(1)
            for (step in totalSteps downTo 1) {
                if (!uiState.value.isDownloading) return@launch
                val remainingMs = step * 100L
                uiState.update {
                    it.copy(
                        downloadCountdownSeconds = ((remainingMs + 999) / 1000).toInt(),
                        downloadStatus = "Page loaded. Waiting ${remainingMs}ms..."
                    )
                }
                delay(100)
            }
            if (!uiState.value.isDownloading) return@launch
            uiState.update {
                it.copy(
                    downloadCountdownSeconds = 0,
                    downloadStatus = "Downloading Chapter ${uiState.value.downloadCurrentChapter}..."
                )
            }
            chapterExtractor.extractAndSaveChapter(isBatch = true)
        }
    }

    fun extractAndSaveChapter(isBatch: Boolean) = chapterExtractor.extractAndSaveChapter(isBatch)
    fun saveSingleChapter() = chapterExtractor.saveSingleChapter()

    fun onCleared() {
        downloadCountdownJob?.cancel()
        domVerificationJob?.cancel()
    }
}
