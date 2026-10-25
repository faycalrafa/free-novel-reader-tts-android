package com.novelarrow.reader.ui.browser.delegate

import android.app.Application
import android.util.Log
import com.novelarrow.reader.browser.engine.WebContentExtractor
import com.novelarrow.reader.data.browser.DomContentVerifier
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.ui.browser.BrowserUiState
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserChapterExtractor(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val libraryRepository: LibraryRepository,
    private val dispatchEvaluateJs: (String, ((String?) -> Unit)?) -> Unit,
    private val clickNextChapter: () -> Unit,
    private val startDomContentVerification: () -> Unit,
    private val getLastBatchDownloadedUrl: () -> String,
    private val setLastBatchDownloadedUrl: (String) -> Unit,
    private val setLastBatchDownloadedChapterNum: (Int) -> Unit,
    private val getLastExtractedHeading: () -> String,
    private val setLastExtractedHeading: (String) -> Unit,
    private val getLastExtractedSample: () -> String,
    private val setLastExtractedSample: (String) -> Unit,
    private val setPreExtractedNextChapterUrl: ((String?) -> Unit)? = null
) {
    companion object {
        private const val TAG = "BrowserChapterExtractor"
    }

    fun saveDownloadedChapterFromScript(
        novelTitle: String,
        chapterTitle: String,
        chapterNumber: Int,
        htmlContent: String
    ) {
        AppLogger.i(TAG, "Received downloaded chapter from script: $novelTitle Ch. $chapterNumber ($chapterTitle), length: ${htmlContent.length}")
        scope.launch(Dispatchers.IO) {
            try {
                val savedFile = WebNovelDownloader.saveCleanHtml(
                    context = application,
                    novelTitle = novelTitle,
                    chapterTitle = chapterTitle,
                    chapterNumber = chapterNumber,
                    cleanHtml = htmlContent
                )
                val updatedNovel = libraryRepository.addDownloadedChapter(
                    novelTitle = novelTitle,
                    chapterFile = savedFile,
                    chapterTitle = chapterTitle,
                    chapterNumber = chapterNumber,
                    sourceUrl = uiState.value.currentUrl,
                    commitNow = true
                )
                withContext(Dispatchers.Main) {
                    uiState.update {
                        it.copy(
                            lastDownloadedNovel = updatedNovel,
                            toastMessage = "Saved $novelTitle Ch. $chapterNumber to Downloads/NovelReader!"
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error saving downloaded chapter from script: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    uiState.update {
                        it.copy(toastMessage = "Failed to save Chapter $chapterNumber: ${e.message}")
                    }
                }
            }
        }
    }

    fun saveSingleChapter() {
        val currentUrl = uiState.value.currentUrl
        val pageTitle = uiState.value.pageTitle
        val detected = WebNovelDownloader.detectNovelInfo(currentUrl, pageTitle)
        if (WebNovelDownloader.chapterAlreadyExists(application, detected.novelTitle, detected.chapterNumber, currentUrl)) {
            uiState.update {
                it.copy(toastMessage = "Chapter ${detected.chapterNumber} already downloaded for ${detected.novelTitle}")
            }
            return
        }
        extractAndSaveChapter(isBatch = false)
    }

    fun extractAndSaveChapter(isBatch: Boolean) {
        if (!isBatch) {
            uiState.update { it.copy(toastMessage = "Extracting chapter content...") }
        }
        val settings = uiState.value.settings
        val currentUrl = uiState.value.currentUrl
        val script = WebContentExtractor.buildContentExtractionScript(
            contentSelector = settings.contentSelector,
            ignoredSelectors = settings.ignoredSelectors,
            contentSelectorRules = settings.contentSelectors,
            currentUrl = currentUrl
        )
        dispatchEvaluateJs(script) { result ->
            handleExtractedChapter(result, isBatch)
        }
    }

    private fun handleExtractedChapter(result: String?, isBatch: Boolean) {
        val currentUrl = uiState.value.currentUrl
        val pageTitle = uiState.value.pageTitle
        val extracted = WebContentExtractor.extractChapterContent(result, pageTitle)
        val title = extracted.title
        val paragraphs = extracted.paragraphs
        if (!extracted.nextUrl.isNullOrBlank()) {
            setPreExtractedNextChapterUrl?.invoke(extracted.nextUrl)
            AppLogger.d(TAG, "Cached pre-extracted next chapter URL in chapter extractor: ${extracted.nextUrl}")
        }

        if (!isBatch) {
            val detected = WebNovelDownloader.detectNovelInfo(currentUrl, title)
            if (WebNovelDownloader.chapterAlreadyExists(application, detected.novelTitle, detected.chapterNumber, currentUrl)) {
                uiState.update {
                    it.copy(toastMessage = "Chapter ${detected.chapterNumber} already downloaded for ${detected.novelTitle}")
                }
                return
            }
            val chapterTitle = title.ifBlank { "${detected.novelTitle} Chapter ${detected.chapterNumber}" }
            scope.launch {
                try {
                    val savedFile = WebNovelDownloader.saveChapter(
                        context = application,
                        novelTitle = detected.novelTitle,
                        chapterTitle = chapterTitle,
                        chapterNumber = detected.chapterNumber,
                        paragraphs = paragraphs,
                        sourceUrl = currentUrl
                    )
                    val updatedNovel = libraryRepository.addDownloadedChapter(
                        novelTitle = detected.novelTitle,
                        chapterFile = savedFile,
                        chapterTitle = chapterTitle,
                        chapterNumber = detected.chapterNumber,
                        sourceUrl = currentUrl,
                        commitNow = true
                    )
                    uiState.update {
                        it.copy(
                            lastDownloadedNovel = updatedNovel,
                            toastMessage = "Saved Chapter ${detected.chapterNumber} to Downloads/NovelReader/${detected.novelTitle}!"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving single page: ${e.message}")
                    uiState.update { it.copy(toastMessage = "Error saving page: ${e.message}") }
                }
            }
        } else {
            if (!uiState.value.isDownloading) return
            val state = uiState.value
            val novelTitle = state.downloadNovelTitle
            val chapterNum = state.downloadCurrentChapter
            val remaining = state.downloadRemainingCount
            val isTitleJustNovelTitle = title.equals(novelTitle, ignoreCase = true) || title.isBlank()
            val chapterTitle = if (isTitleJustNovelTitle) "$novelTitle Chapter $chapterNum" else title

            val urlChanged = getLastBatchDownloadedUrl().isNotBlank() && currentUrl != getLastBatchDownloadedUrl()

            if (paragraphs.isEmpty()) {
                AppLogger.w(TAG, "Extracted 0 paragraphs for Chapter $chapterNum. Waiting for DOM content to load...")
                uiState.update { it.copy(downloadStatus = "Waiting for chapter content to render...") }
                startDomContentVerification()
                return
            }

            if (getLastExtractedHeading().isNotBlank() || getLastExtractedSample().isNotBlank()) {
                val currentSample = DomContentVerifier.createChapterFingerprint(paragraphs)
                if (DomContentVerifier.isDuplicateContent(title, currentSample, getLastExtractedHeading(), getLastExtractedSample(), urlChanged = urlChanged)) {
                    AppLogger.w(TAG, "Duplicate DOM content detected for Chapter $chapterNum. Waiting for DOM update...")
                    uiState.update { it.copy(downloadStatus = "Waiting for DOM re-render...") }
                    startDomContentVerification()
                    return
                }
            }

            if (WebNovelDownloader.chapterAlreadyExists(application, novelTitle, chapterNum, currentUrl)) {
                Log.d(TAG, "Batch download: Chapter $chapterNum already exists on disk. Skipping file write.")
                val newRemaining = remaining - 1
                val nextChapterNum = chapterNum + 1
                setLastExtractedHeading(title)
                setLastExtractedSample(DomContentVerifier.createChapterFingerprint(paragraphs))
                uiState.update {
                    it.copy(
                        downloadRemainingCount = newRemaining,
                        downloadCurrentChapter = nextChapterNum,
                        downloadStatus = "Chapter $chapterNum already saved, advancing to next..."
                    )
                }
                if (newRemaining <= 0) {
                    scope.launch {
                        libraryRepository.commitBatch(novelTitle)
                    }
                    uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Completed",
                            toastMessage = "Finished batch download for $novelTitle!"
                        )
                    }
                } else {
                    clickNextChapter()
                    startDomContentVerification()
                }
                return
            }

            scope.launch {
                try {
                    val savedFile = WebNovelDownloader.saveChapter(
                        context = application,
                        novelTitle = novelTitle,
                        chapterTitle = chapterTitle,
                        chapterNumber = chapterNum,
                        paragraphs = paragraphs,
                        sourceUrl = currentUrl
                    )
                    val updatedNovel = libraryRepository.addDownloadedChapter(
                        novelTitle = novelTitle,
                        chapterFile = savedFile,
                        chapterTitle = chapterTitle,
                        chapterNumber = chapterNum,
                        sourceUrl = currentUrl,
                        commitNow = false
                    )
                    setLastBatchDownloadedUrl(currentUrl)
                    setLastBatchDownloadedChapterNum(chapterNum)
                    setLastExtractedHeading(title)
                    setLastExtractedSample(DomContentVerifier.createChapterFingerprint(paragraphs))

                    val newRemaining = remaining - 1
                    val nextChapterNum = chapterNum + 1

                    uiState.update {
                        it.copy(
                            downloadRemainingCount = newRemaining,
                            downloadCurrentChapter = nextChapterNum,
                            lastDownloadedNovel = updatedNovel,
                            downloadStatus = "Saved Chapter $chapterNum (100%)"
                        )
                    }

                    if (newRemaining <= 0) {
                        libraryRepository.commitBatch(novelTitle)
                        uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadStatus = "Completed",
                                toastMessage = "Successfully downloaded ${it.downloadTotalCount} chapters for $novelTitle!"
                            )
                        }
                    } else {
                        uiState.update { it.copy(downloadStatus = "Loading next chapter...") }
                        clickNextChapter()
                        startDomContentVerification()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save downloaded chapter: ${e.message}", e)
                    uiState.update { it.copy(toastMessage = "Error saving chapter: ${e.message}") }
                }
            }
        }
    }
}
