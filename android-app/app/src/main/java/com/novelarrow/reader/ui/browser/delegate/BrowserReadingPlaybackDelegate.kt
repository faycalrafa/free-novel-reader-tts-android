package com.novelarrow.reader.ui.browser.delegate

import android.app.Application
import com.novelarrow.reader.browser.engine.WebContentExtractor
import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.RuleImpact
import com.novelarrow.reader.data.model.RuleType
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.ui.browser.BrowserUiState
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserReadingPlaybackDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val dispatchEvaluateJs: (String, ((String?) -> Unit)?) -> Unit,
    private val clickNextChapter: () -> Unit,
    private val goBack: () -> Unit,
    private val savedReadingPositions: MutableMap<String, Int>,
    private val setPreExtractedNextChapterUrl: (String) -> Unit
) {
    companion object {
        private const val TAG = "BrowserReadingPlayback"
    }

    private var retryExtractionJob: Job? = null
    var pageAutoReadJob: Job? = null
    var lastAutoReadUrl: String? = null

    fun highlightWebParagraph(index: Int) {
        val autoScroll = uiState.value.settings.ttsAutoScroll
        val paras = uiState.value.liveParagraphs
        val currentPara = paras.getOrNull(index)
        val sourceIndices = currentPara?.sourceIndices ?: listOf(index)
        dispatchEvaluateJs(WebScriptInjector.buildHighlightScript(sourceIndices, autoScroll), null)
    }

    fun clearWebHighlight() {
        dispatchEvaluateJs(WebScriptInjector.buildClearHighlightScript(), null)
    }

    fun toggleLiveReading() {
        if (uiState.value.isLivePlaying) {
            pauseLiveReading()
        } else if (uiState.value.isLiveReading && uiState.value.liveParagraphs.isNotEmpty()) {
            resumeLiveReading()
        } else {
            startLiveReading()
        }
    }

    fun toggleReadingMode() {
        if (uiState.value.isReaderMode) {
            exitReadingMode()
        } else {
            enterReadingMode()
        }
    }

    fun enterReadingMode() {
        val settings = uiState.value.settings
        val currentUrl = uiState.value.currentUrl
        val script = WebContentExtractor.buildContentExtractionScript(
            contentSelector = settings.contentSelector,
            ignoredSelectors = settings.ignoredSelectors,
            contentSelectorRules = settings.contentSelectors,
            currentUrl = currentUrl
        )

        dispatchEvaluateJs(script) { result ->
            val extracted = WebContentExtractor.extractChapterContent(result, uiState.value.pageTitle)
            val title = extracted.title
            val rawList = extracted.paragraphs
            if (!extracted.nextUrl.isNullOrBlank()) {
                setPreExtractedNextChapterUrl(extracted.nextUrl)
                AppLogger.d(TAG, "Cached pre-extracted next chapter URL in reading mode: ${extracted.nextUrl}")
            }

            val containerImpact = if (extracted.matchedContainerSelector != null && extracted.containerFilteredChars > 0) {
                RuleImpact(
                    id = "container_${extracted.matchedContainerSelector}",
                    name = extracted.matchedContainerSelector,
                    ruleType = RuleType.CONTENT_CONTAINER,
                    charsFiltered = extracted.containerFilteredChars,
                    enabled = true,
                    details = "${extracted.containerFilteredChars} outer characters excluded by container"
                )
            } else null

            val ignoredImpacts = extracted.ignoredSelectorStats.map { (sel, count) ->
                RuleImpact(
                    id = "ignored_$sel",
                    name = sel,
                    ruleType = RuleType.IGNORED_SELECTOR,
                    charsFiltered = count,
                    enabled = true,
                    details = "$count characters removed"
                )
            }

            val parseResult = ChapterParser.parseWebParagraphsWithDiagnostics(
                rawParagraphs = rawList,
                currentUrl = currentUrl,
                headerSkipRules = settings.headerSkipRules,
                headerSkipEnabled = settings.headerSkipEnabled,
                footerSkipRules = settings.footerSkipRules,
                footerSkipEnabled = settings.footerSkipEnabled,
                pronunciationRules = settings.pronunciationRules,
                pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                skipWords = settings.skipWords,
                skipWordsEnabled = settings.skipWordsEnabled,
                skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols,
                containerImpact = containerImpact,
                ignoredSelectorImpacts = ignoredImpacts
            )

            val rawParagraphs = parseResult.paragraphs
            val paragraphs = ChapterParser.mergeShortParagraphs(rawParagraphs, minWordThreshold = 10)
            val curPos = (if (uiState.value.isLiveReading) uiState.value.liveCurrentParagraphIndex else (savedReadingPositions[currentUrl] ?: 0)).coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            uiState.update {
                it.copy(
                    isReaderMode = true,
                    readerModeTitle = title,
                    readerModeParagraphs = paragraphs,
                    readerModeCurrentParagraphIndex = curPos,
                    readerModeIsLoadingNext = false,
                    liveParagraphs = if (paragraphs.isNotEmpty()) paragraphs else it.liveParagraphs,
                    activeRuleImpacts = parseResult.ruleImpacts
                )
            }
        }
    }

    fun exitReadingMode() {
        uiState.update { it.copy(isReaderMode = false) }
    }

    fun onReaderModeParagraphClicked(index: Int) {
        val paras = uiState.value.readerModeParagraphs.ifEmpty { uiState.value.liveParagraphs }
        if (index in paras.indices) {
            uiState.update {
                it.copy(
                    readerModeCurrentParagraphIndex = index,
                    liveCurrentParagraphIndex = index
                )
            }
            val currentUrl = uiState.value.currentUrl
            if (currentUrl.isNotBlank()) {
                savedReadingPositions[currentUrl] = index
            }
            if (uiState.value.isLiveReading) {
                seekToParagraph(index)
            } else {
                startLiveReadingInternal(forceStartAtBeginning = false, initialIndex = index)
            }
        }
    }

    fun onReaderModeEndReached() {
        if (uiState.value.readerModeIsLoadingNext) return
        val autoNext = uiState.value.settings.autoClickOnScroll
        if (autoNext) {
            uiState.update { it.copy(readerModeIsLoadingNext = true, toastMessage = "Loading next chapter...") }
            clickNextChapter()
        }
    }

    fun startLiveReading() {
        AudioPlaybackCoordinator.saveCurrentReaderProgress()
        AudioPlaybackCoordinator.stop()
        uiState.update { it.copy(isLiveReading = true, isLivePlaying = true, flashHandleRed = false) }
        startLiveReadingInternal(forceStartAtBeginning = false)
    }

    fun startLiveReadingInternal(
        forceStartAtBeginning: Boolean = false,
        initialIndex: Int? = null,
        retryCount: Int = 0
    ) {
        val settings = uiState.value.settings
        val currentUrl = uiState.value.currentUrl
        val script = WebContentExtractor.buildContentExtractionScript(
            contentSelector = settings.contentSelector,
            ignoredSelectors = settings.ignoredSelectors,
            contentSelectorRules = settings.contentSelectors,
            currentUrl = currentUrl
        )

        dispatchEvaluateJs(script) { result ->
            val extracted = WebContentExtractor.extractChapterContent(result, uiState.value.pageTitle)
            val title = extracted.title
            val rawList = extracted.paragraphs
            if (!extracted.nextUrl.isNullOrBlank()) {
                setPreExtractedNextChapterUrl(extracted.nextUrl)
                AppLogger.d(TAG, "Cached pre-extracted next chapter URL: ${extracted.nextUrl}")
            }

            if (rawList.isEmpty() && result.isNullOrBlank()) {
                if (retryCount < 2) {
                    retryExtractionJob?.cancel()
                    retryExtractionJob = scope.launch {
                        delay(800)
                        startLiveReadingInternal(forceStartAtBeginning, initialIndex, retryCount + 1)
                    }
                }
                return@dispatchEvaluateJs
            }

            try {
                val containerImpact = if (extracted.matchedContainerSelector != null && extracted.containerFilteredChars > 0) {
                    RuleImpact(
                        id = "container_${extracted.matchedContainerSelector}",
                        name = extracted.matchedContainerSelector,
                        ruleType = RuleType.CONTENT_CONTAINER,
                        charsFiltered = extracted.containerFilteredChars,
                        enabled = true,
                        details = "${extracted.containerFilteredChars} outer characters excluded by container"
                    )
                } else null

                val ignoredImpacts = extracted.ignoredSelectorStats.map { (sel, count) ->
                    RuleImpact(
                        id = "ignored_$sel",
                        name = sel,
                        ruleType = RuleType.IGNORED_SELECTOR,
                        charsFiltered = count,
                        enabled = true,
                        details = "$count characters removed"
                    )
                }

                val parseResult = ChapterParser.parseWebParagraphsWithDiagnostics(
                    rawParagraphs = rawList,
                    currentUrl = currentUrl,
                    headerSkipRules = settings.headerSkipRules,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipRules = settings.footerSkipRules,
                    footerSkipEnabled = settings.footerSkipEnabled,
                    pronunciationRules = settings.pronunciationRules,
                    pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                    skipWords = settings.skipWords,
                    skipWordsEnabled = settings.skipWordsEnabled,
                    skipWordsIgnoreSymbols = settings.skipWordsIgnoreSymbols,
                    containerImpact = containerImpact,
                    ignoredSelectorImpacts = ignoredImpacts
                )

                val rawParagraphs = parseResult.paragraphs
                val readableParagraphs = ChapterParser.mergeShortParagraphs(rawParagraphs, minWordThreshold = 10)

                if (readableParagraphs.isNotEmpty()) {
                    val targetIndex = (initialIndex ?: if (forceStartAtBeginning) {
                        0
                    } else {
                        (savedReadingPositions[currentUrl] ?: uiState.value.readerModeCurrentParagraphIndex.takeIf { it > 0 } ?: uiState.value.liveCurrentParagraphIndex)
                    }).coerceIn(0, (readableParagraphs.size - 1).coerceAtLeast(0))
                    uiState.update {
                        it.copy(
                            liveParagraphs = readableParagraphs,
                            readerModeParagraphs = if (it.readerModeParagraphs.isEmpty()) readableParagraphs else it.readerModeParagraphs,
                            liveChapterTitle = title,
                            liveCurrentParagraphIndex = targetIndex,
                            readerModeCurrentParagraphIndex = targetIndex,
                            isLiveReading = true,
                            isLivePlaying = true,
                            flashHandleRed = false,
                            activeRuleImpacts = parseResult.ruleImpacts
                        )
                    }
                    AudioPlaybackCoordinator.playBrowserParagraphs(
                        context = application,
                        chapterTitle = title,
                        paragraphs = readableParagraphs,
                        startParagraphIndex = targetIndex,
                        rate = settings.ttsRate,
                        pitch = settings.ttsPitch,
                        voiceName = settings.ttsVoice,
                        isPreMerged = true
                    )
                } else {
                    if (retryCount < 2 && rawList.isEmpty()) {
                        retryExtractionJob?.cancel()
                        retryExtractionJob = scope.launch {
                            delay(1000)
                            startLiveReadingInternal(forceStartAtBeginning, initialIndex, retryCount + 1)
                        }
                    } else {
                        AppLogger.w(TAG, "No readable text detected on page $currentUrl. Rules applied: ${parseResult.ruleImpacts.size}")
                        uiState.update {
                            it.copy(
                                toastMessage = "No text detected. Check if filters or selectors are misconfigured.",
                                isLivePlaying = false,
                                flashHandleRed = true,
                                activeRuleImpacts = parseResult.ruleImpacts
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error starting live reading: ${e.message}", e)
            }
        }
    }

    fun handleLiveChapterEnd() {
        if (uiState.value.liveParagraphs.isEmpty()) return

        val autoNext = uiState.value.settings.autoReadNextChapter
        if (autoNext) {
            uiState.update {
                it.copy(toastMessage = "End of chapter reached. Loading next chapter...")
            }
            scope.launch(Dispatchers.Main) {
                clickNextChapter()
            }
        } else {
            stopLiveReading()
        }
    }

    fun pauseLiveReading() {
        AudioPlaybackCoordinator.pause()
        uiState.update { it.copy(isLivePlaying = false) }
    }

    fun resumeLiveReading() {
        if (uiState.value.liveParagraphs.isEmpty()) {
            startLiveReading()
            return
        }
        AudioPlaybackCoordinator.play()
        uiState.update { it.copy(isLivePlaying = true, isLiveReading = true) }
    }

    fun stopLiveReading() {
        AudioPlaybackCoordinator.stop()
        clearWebHighlight()
        uiState.update {
            it.copy(
                isLiveReading = false,
                isLivePlaying = false,
                liveParagraphs = emptyList()
            )
        }
    }

    fun seekToWebParagraph(domIndex: Int) {
        val paras = uiState.value.liveParagraphs.ifEmpty { uiState.value.readerModeParagraphs }
        if (paras.isEmpty()) return
        val resolvedIndex = paras.indexOfFirst { domIndex in it.sourceIndices }.takeIf { it >= 0 }
            ?: domIndex.coerceIn(0, paras.size - 1)
        seekToParagraph(resolvedIndex)
    }

    fun seekToParagraph(index: Int) {
        val paras = uiState.value.liveParagraphs.ifEmpty { uiState.value.readerModeParagraphs }
        if (paras.isEmpty()) return
        val target = index.coerceIn(0, paras.size - 1)
        val curUrl = uiState.value.currentUrl
        if (curUrl.isNotBlank()) {
            savedReadingPositions[curUrl] = target
        }
        AudioPlaybackCoordinator.seekToParagraph(target)
        uiState.update {
            it.copy(
                liveCurrentParagraphIndex = target,
                readerModeCurrentParagraphIndex = target,
                isLivePlaying = true,
                isLiveReading = true
            )
        }
        highlightWebParagraph(target)
    }

    fun nextLiveParagraph() {
        val paras = uiState.value.liveParagraphs
        val current = uiState.value.liveCurrentParagraphIndex
        if (current + 1 < paras.size) {
            seekToParagraph(current + 1)
        } else {
            clickNextChapter()
        }
    }

    fun prevLiveParagraph() {
        val current = uiState.value.liveCurrentParagraphIndex
        if (current - 1 >= 0) {
            seekToParagraph(current - 1)
        }
    }

    fun nextLiveChapterOrParagraph() {
        clickNextChapter()
    }

    fun prevLiveChapterOrParagraph() {
        goBack()
    }

    fun cycleSpeechRate() {
        AudioPlaybackCoordinator.cycleSpeechRate()
    }

    fun openSleepTimerDialog() {
        uiState.update { it.copy(showSleepTimerDialog = true) }
    }

    fun dismissSleepTimerDialog() {
        uiState.update { it.copy(showSleepTimerDialog = false) }
    }

    fun setSleepTimer(minutes: Int) {
        AudioPlaybackCoordinator.setSleepTimer(minutes)
        uiState.update {
            it.copy(
                showSleepTimerDialog = false,
                toastMessage = if (minutes > 0) "Sleep timer set for $minutes minutes" else "Sleep timer turned off"
            )
        }
    }

    fun onCleared() {
        retryExtractionJob?.cancel()
        pageAutoReadJob?.cancel()
    }
}
