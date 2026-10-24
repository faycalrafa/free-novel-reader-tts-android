package com.novelarrow.reader.playback

import android.speech.tts.TextToSpeech
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class CoordinatorSpeechQueue(
    private val scope: CoroutineScope,
    private val ttsManagerProvider: () -> TtsManager?,
    private val settingsRepositoryProvider: () -> SettingsRepository?,
    private val voiceEngine: CoordinatorVoiceEngine,
    private val paragraphsProvider: () -> List<Paragraph>,
    private val currentParagraphIndexProvider: () -> Int,
    private val isPlayingProvider: () -> Boolean,
    private val currentSourceProvider: () -> PlaybackSource,
    private val autoNextChapterProvider: () -> Boolean,
    private val autoNextBrowserPageProvider: () -> Boolean,
    private val currentNovelIdProvider: () -> String?,
    private val currentChapterIndexProvider: () -> Int,
    private val onParagraphChanged: (index: Int, paragraph: Paragraph?) -> Unit,
    private val onChapterAdvanceNeeded: () -> Unit,
    private val onBrowserNextPageNeeded: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onProgressSaveRequested: (novelId: String, chapIdx: Int, paraIdx: Int, isCompleted: Boolean) -> Unit,
    private val showToast: (String) -> Unit
) {

    private val TAG = "CoordinatorSpeechQueue"

    var lastEnqueuedParagraphIndex = -1
    private var lastProgressTimestamp = System.currentTimeMillis()
    private var consecutiveErrorCount = 0
    private var ttsWatchdogJob: Job? = null

    fun startWatchdog() {
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = scope.launch(Dispatchers.Main) {
            while (isPlayingProvider() && (currentSourceProvider() == PlaybackSource.READER || currentSourceProvider() == PlaybackSource.BROWSER)) {
                delay(3000L)
                if (!isPlayingProvider()) break
                val now = System.currentTimeMillis()
                val isSpeaking = ttsManagerProvider()?.isSpeaking() == true
                if (!isSpeaking && (now - lastProgressTimestamp) > 4500L && paragraphsProvider().isNotEmpty()) {
                    AppLogger.w(TAG, "Watchdog detected TTS stall at paragraph ${currentParagraphIndexProvider()}. Auto-recovering...")
                    lastProgressTimestamp = now
                    speakCurrentParagraph()
                }
            }
        }
    }

    fun stopWatchdog() {
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
    }

    fun enqueueParagraph(idx: Int, queueMode: Int) {
        val paras = paragraphsProvider()
        if (idx !in paras.indices) return

        val p = paras[idx]
        val spoken = p.spokenText.trim()
        if (spoken.isBlank()) {
            if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                handleParagraphDone(idx)
            }
            return
        }

        val detectedLang = LanguageDetectionEngine.detectLanguage(spoken)
        val settings = settingsRepositoryProvider()?.getSettings()
        val voiceForLang = when (detectedLang) {
            SupportedLanguage.ARABIC -> settings?.favoriteVoiceArabic ?: voiceEngine.currentVoiceName
            SupportedLanguage.FRENCH -> settings?.favoriteVoiceFrench ?: voiceEngine.currentVoiceName
            SupportedLanguage.ENGLISH -> voiceEngine.currentVoiceName ?: settings?.favoriteVoiceEnglish
        }
        val targetVoice = voiceForLang ?: voiceEngine.currentVoiceName
        ttsManagerProvider()?.setVoiceByName(targetVoice)

        ttsManagerProvider()?.speak(
            index = idx,
            text = spoken,
            rate = voiceEngine.currentSpeechRate,
            pitch = voiceEngine.currentSpeechPitch,
            queueMode = queueMode
        )
        lastEnqueuedParagraphIndex = maxOf(lastEnqueuedParagraphIndex, idx)
    }

    fun speakCurrentParagraph() {
        val paras = paragraphsProvider()
        val idx = currentParagraphIndexProvider()
        if (idx !in paras.indices) return

        ttsManagerProvider()?.stop()
        lastEnqueuedParagraphIndex = idx
        lastProgressTimestamp = System.currentTimeMillis()

        enqueueParagraph(idx, TextToSpeech.QUEUE_FLUSH)

        val lookaheadIdx = idx + 1
        if (lookaheadIdx < paras.size) {
            enqueueParagraph(lookaheadIdx, TextToSpeech.QUEUE_ADD)
        }

        startWatchdog()
    }

    fun handleParagraphStart(index: Int) {
        lastProgressTimestamp = System.currentTimeMillis()
        consecutiveErrorCount = 0
        val paras = paragraphsProvider()
        val para = paras.getOrNull(index)
        onParagraphChanged(index, para)
    }

    fun handleParagraphDone(index: Int) {
        lastProgressTimestamp = System.currentTimeMillis()
        val paras = paragraphsProvider()
        val total = paras.size
        val nextIndex = index + 1

        val novelId = currentNovelIdProvider()
        if (currentSourceProvider() == PlaybackSource.READER && novelId != null) {
            val chapIdx = currentChapterIndexProvider()
            onProgressSaveRequested(novelId, chapIdx, index, nextIndex >= total)
        }

        if (!isPlayingProvider()) return

        if (nextIndex < total) {
            val nextLookahead = lastEnqueuedParagraphIndex + 1
            if (nextLookahead < total) {
                enqueueParagraph(nextLookahead, TextToSpeech.QUEUE_ADD)
            }
        } else {
            if (currentSourceProvider() == PlaybackSource.READER && autoNextChapterProvider()) {
                onChapterAdvanceNeeded()
            } else if (currentSourceProvider() == PlaybackSource.BROWSER && autoNextBrowserPageProvider()) {
                AppLogger.i(TAG, "Browser playback finished last paragraph, advancing to next page")
                onBrowserNextPageNeeded()
            } else {
                onPauseRequested()
            }
        }
    }

    fun handleParagraphError(index: Int, error: String) {
        AppLogger.w(TAG, "TTS Error on paragraph $index: $error")
        consecutiveErrorCount++
        if (consecutiveErrorCount >= 3) {
            AppLogger.e(TAG, "Stopping playback: encountered $consecutiveErrorCount consecutive TTS errors (last error: $error)")
            showToast("Playback stopped due to recurring TTS error: $error")
            onPauseRequested()
            consecutiveErrorCount = 0
            return
        }

        val paras = paragraphsProvider()
        val next = index + 1
        if (next < paras.size && isPlayingProvider()) {
            scope.launch(Dispatchers.Main) {
                delay(120)
                if (isPlayingProvider()) {
                    onParagraphChanged(next, paras.getOrNull(next))
                    speakCurrentParagraph()
                }
            }
        } else {
            onPauseRequested()
        }
    }
}
