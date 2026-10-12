package com.novelarrow.reader.playback

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class TtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) {

    private var tts: TextToSpeech? = null
    var isInitialized: Boolean = false
        private set

    var onParagraphStart: ((Int) -> Unit)? = null
    var onParagraphDone: ((Int) -> Unit)? = null
    var onError: ((Int, String) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isInitialized = true
                setupProgressListener()
                onInitComplete(true)
            } else {
                isInitialized = false
                onInitComplete(false)
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: return
                onParagraphStart?.invoke(index)
            }

            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: return
                onParagraphDone?.invoke(index)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: -1
                onError?.invoke(index, "Playback error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val index = utteranceId?.toIntOrNull() ?: -1
                onError?.invoke(index, "Error code $errorCode")
            }
        })
    }

    fun speak(index: Int, text: String, rate: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isInitialized) return
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, index.toString())
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, index.toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun setVoiceByName(voiceName: String?) {
        if (voiceName == null) return
        val voice = getAvailableVoices().firstOrNull { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
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
}
