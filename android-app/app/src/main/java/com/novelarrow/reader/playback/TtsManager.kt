package com.novelarrow.reader.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.novelarrow.reader.util.AppLogger
import java.util.Locale

/**
 * Robust wrapper for Android TextToSpeech engine with AudioAttributes, safe language
 * autodetection and override, and utterance chunking to prevent silent synthesis limits.
 */
class TtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) {

    private var tts: TextToSpeech? = null
    var isInitialized: Boolean = false
        private set

    private var languageOverride: Locale? = null

    var onParagraphStart: ((Int) -> Unit)? = null
    var onParagraphDone: ((Int) -> Unit)? = null
    var onError: ((Int, String) -> Unit)? = null
    var onPreviewDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set AudioAttributes: ${e.message}")
                }
                configureLanguageSafely()
                isInitialized = true
                setupProgressListener()
                onInitComplete(true)
            } else {
                isInitialized = false
                onInitComplete(false)
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set speech rate: ${e.message}")
        }
    }

    fun setLanguageOverride(locale: Locale?) {
        languageOverride = locale
        if (locale != null && isInitialized) {
            try {
                if ((tts?.isLanguageAvailable(locale) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = locale
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply language override: ${e.message}")
            }
        }
    }

    /**
     * Safely selects an available language for the TTS engine, falling back cleanly
     * without hardcoding Locale.getDefault() if unsupported.
     */
    fun configureLanguageSafely(targetText: String? = null) {
        val t = tts ?: return
        try {
            // 0. Manual language override if set
            languageOverride?.let { override ->
                if (t.isLanguageAvailable(override) >= TextToSpeech.LANG_AVAILABLE) {
                    t.language = override
                    return
                }
            }

            // 1. If text is provided, detect language dynamically
            if (!targetText.isNullOrBlank()) {
                val detected = LanguageDetectionEngine.detectLanguage(targetText)
                if (t.isLanguageAvailable(detected.locale) >= TextToSpeech.LANG_AVAILABLE) {
                    t.language = detected.locale
                    return
                }
            }

            // 2. Try default system locale if available
            val defaultLocale = Locale.getDefault()
            if (t.isLanguageAvailable(defaultLocale) >= TextToSpeech.LANG_AVAILABLE) {
                t.language = defaultLocale
                return
            }

            // 3. Fallback to English US
            if (t.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) {
                t.language = Locale.US
                return
            }

            // 4. Fallback to first available language from available voices
            val voiceLocale = t.voices?.mapNotNull { it.locale }?.firstOrNull {
                t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            }
            if (voiceLocale != null) {
                t.language = voiceLocale
            }
        } catch (e: Exception) {
            Log.w(TAG, "Safe language configuration error: ${e.message}")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                if (utteranceId.startsWith("preview_")) {
                    AppLogger.d(TAG, "Voice preview started: $utteranceId")
                    return
                }
                if (utteranceId.startsWith("chunk_")) {
                    val parts = utteranceId.split("_")
                    if (parts.size >= 4) {
                        val index = parts[1].toIntOrNull() ?: return
                        val chunkIdx = parts[2].toIntOrNull() ?: return
                        if (chunkIdx == 0) {
                            onParagraphStart?.invoke(index)
                        }
                    }
                } else {
                    val index = utteranceId.toIntOrNull() ?: return
                    onParagraphStart?.invoke(index)
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                if (utteranceId.startsWith("preview_")) {
                    AppLogger.d(TAG, "Voice preview done: $utteranceId")
                    onPreviewDone?.invoke()
                    return
                }
                if (utteranceId.startsWith("chunk_")) {
                    val parts = utteranceId.split("_")
                    if (parts.size >= 4) {
                        val index = parts[1].toIntOrNull() ?: return
                        val chunkIdx = parts[2].toIntOrNull() ?: return
                        val total = parts[3].toIntOrNull() ?: return
                        if (chunkIdx == total - 1) {
                            onParagraphDone?.invoke(index)
                        }
                    }
                } else {
                    val index = utteranceId.toIntOrNull() ?: return
                    onParagraphDone?.invoke(index)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith("preview_") == true) {
                    AppLogger.w(TAG, "Voice preview error: $utteranceId")
                    onPreviewDone?.invoke()
                    return
                }
                val index = parseIndexFromUtteranceId(utteranceId)
                onError?.invoke(index, "Playback error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId?.startsWith("preview_") == true) {
                    AppLogger.w(TAG, "Voice preview error code $errorCode: $utteranceId")
                    onPreviewDone?.invoke()
                    return
                }
                val index = parseIndexFromUtteranceId(utteranceId)
                onError?.invoke(index, "Error code $errorCode")
            }

            private fun parseIndexFromUtteranceId(utteranceId: String?): Int {
                if (utteranceId == null) return -1
                return if (utteranceId.startsWith("chunk_")) {
                    utteranceId.split("_").getOrNull(1)?.toIntOrNull() ?: -1
                } else {
                    utteranceId.toIntOrNull() ?: -1
                }
            }
        })
    }

    /**
     * Splits long text into chunks that do not exceed the TTS engine max input length.
     */
    private fun chunkText(text: String, maxChunkSize: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxChunkSize) {
                chunks.add(remaining)
                break
            }
            var breakIdx = -1
            val candidate = remaining.substring(0, maxChunkSize)
            val punctMatch = Regex("""[.!?。！？\n]\s*["'”’」』）］｝»)\\]}]*\s*""").findAll(candidate).lastOrNull()
            if (punctMatch != null && punctMatch.range.last > maxChunkSize / 3) {
                breakIdx = punctMatch.range.last + 1
            } else {
                val lastSpace = candidate.lastIndexOf(' ')
                if (lastSpace > maxChunkSize / 3) {
                    breakIdx = lastSpace + 1
                }
            }
            if (breakIdx <= 0) {
                breakIdx = maxChunkSize
            }
            val chunk = remaining.substring(0, breakIdx).trim()
            if (chunk.isNotEmpty()) chunks.add(chunk)
            remaining = remaining.substring(breakIdx).trim()
        }
        return chunks.ifEmpty { listOf(text) }
    }

    fun speak(index: Int, text: String, rate: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isInitialized) return
        configureLanguageSafely(text)
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)

        val maxInputLength = try {
            TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(500)
        } catch (_: Exception) {
            3900
        }
        val safeMaxLen = minOf(maxInputLength, 3500)

        val chunks = chunkText(text, safeMaxLen)
        for (i in chunks.indices) {
            val chunk = chunks[i]
            val utteranceId = if (chunks.size == 1) "$index" else "chunk_${index}_${i}_${chunks.size}"
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.speak(chunk, queueMode, params, utteranceId)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun setRate(rate: Float) {
        if (isInitialized) {
            tts?.setSpeechRate(rate)
        }
    }

    fun setPitch(pitch: Float) {
        if (isInitialized) {
            tts?.setPitch(pitch)
        }
    }

    fun setVoiceByName(voiceName: String?) {
        if (voiceName == null) {
            try {
                tts?.defaultVoice?.let { tts?.voice = it }
                configureLanguageSafely()
                AppLogger.d(TAG, "Reset voice to system default voice")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to reset voice to default: ${e.message}", e)
            }
            return
        }
        val voice = getAvailableVoices().firstOrNull { it.name == voiceName }
        if (voice != null) {
            // Apply locale first, then set explicit voice so setLanguage does not reset voice
            try {
                voice.locale?.let { loc ->
                    if ((tts?.isLanguageAvailable(loc) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
                        tts?.language = loc
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to apply locale for voice ${voice.name}: ${e.message}", e)
            }
            try {
                tts?.voice = voice
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to apply voice ${voice.name}: ${e.message}", e)
            }
        }
    }

    fun speakPreview(voice: Voice, sampleText: String) {
        if (!isInitialized || tts == null) {
            AppLogger.w(TAG, "Cannot speak preview: TTS engine not initialized")
            onPreviewDone?.invoke()
            return
        }
        try {
            // Stop current speech cleanly with QUEUE_FLUSH
            tts?.stop()

            // 1. Safely set locale FIRST so setLanguage does not overwrite voice
            try {
                voice.locale?.let { loc ->
                    if ((tts?.isLanguageAvailable(loc) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
                        tts?.language = loc
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to apply locale ${voice.locale} for preview: ${e.message}", e)
            }

            // 2. Safely set the preview voice AFTER setting language
            try {
                tts?.voice = voice
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to apply voice ${voice.name} for preview: ${e.message}", e)
            }

            val utteranceId = "preview_${voice.name}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val res = tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                AppLogger.w(TAG, "TTS speakPreview returned status $res for voice ${voice.name}")
                onPreviewDone?.invoke()
            } else {
                AppLogger.d(TAG, "Speaking preview for voice ${voice.name}: \"$sampleText\"")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error playing preview for voice ${voice.name}: ${e.message}", e)
            onPreviewDone?.invoke()
        }
    }

    fun stopPreview() {
        try {
            tts?.stop()
            onPreviewDone?.invoke()
            AppLogger.d(TAG, "Voice preview stopped cleanly")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping voice preview: ${e.message}", e)
        }
    }

    fun getDefaultEngineName(): String? {
        return try {
            tts?.defaultEngine
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to query default TTS engine: ${e.message}", e)
            null
        }
    }

    fun getEngines(): List<TextToSpeech.EngineInfo> {
        return try {
            tts?.engines ?: emptyList()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to query TTS engines: ${e.message}", e)
            emptyList()
        }
    }

    fun getSampleTextForLocale(locale: Locale?): String = Companion.getSampleTextForLocale(locale)

    fun setLanguage(locale: Locale) {
        if (isInitialized && (tts?.isLanguageAvailable(locale) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = locale
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return try {
            tts?.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TtsManager"

        /**
         * Returns a localized sample phrase tailored to the voice's locale for audio preview.
         */
        fun getSampleTextForLocale(locale: Locale?): String {
            if (locale == null) return "Testing Text to Speech voice preview."
            val lang = locale.language.lowercase().trim()
            return when {
                lang == "en" || lang == "eng" -> "This is a voice sample for BookReader."
                lang == "ja" || lang == "jpn" -> "これは音声プレビューです。"
                lang == "zh" || lang == "zho" || lang == "chi" -> "这是语音预览示例。"
                lang == "es" || lang == "spa" -> "Esta es una muestra de voz."
                lang == "fr" || lang == "fra" || lang == "fre" -> "Ceci est un échantillon vocal."
                lang == "de" || lang == "deu" || lang == "ger" -> "Dies ist ein Sprachbeispiel."
                locale.displayLanguage.equals("English", ignoreCase = true) -> "This is a voice sample for BookReader."
                locale.displayLanguage.equals("Japanese", ignoreCase = true) -> "これは音声プレビューです。"
                locale.displayLanguage.equals("Chinese", ignoreCase = true) -> "这是语音预览示例。"
                locale.displayLanguage.equals("Spanish", ignoreCase = true) -> "Esta es una muestra de voz."
                locale.displayLanguage.equals("French", ignoreCase = true) -> "Ceci est un échantillon vocal."
                locale.displayLanguage.equals("German", ignoreCase = true) -> "Dies ist ein Sprachbeispiel."
                else -> "Testing Text to Speech voice preview."
            }
        }
    }
}
