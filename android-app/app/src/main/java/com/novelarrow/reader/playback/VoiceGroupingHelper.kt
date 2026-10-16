package com.novelarrow.reader.playback

import android.speech.tts.Voice

/**
 * Utility helper for grouping TTS Voice models into language categories and extracting formatted metadata.
 */
object VoiceGroupingHelper {

    /**
     * Neatly groups voices by language/locale using `voice.locale.displayLanguage.ifBlank { "Other" }`.
     * Returns a map sorted alphabetically by language name.
     */
    fun groupVoicesByLanguage(voices: List<Voice>): Map<String, List<Voice>> {
        return voices.groupBy { voice ->
            val lang = voice.locale?.displayLanguage?.trim().orEmpty()
            if (lang.isBlank()) "Other" else lang
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Formats voice quality constant into human-readable description.
     */
    fun formatQuality(quality: Int): String {
        return when (quality) {
            Voice.QUALITY_VERY_HIGH -> "Very High"
            Voice.QUALITY_HIGH -> "High"
            Voice.QUALITY_NORMAL -> "Standard"
            Voice.QUALITY_LOW -> "Low"
            Voice.QUALITY_VERY_LOW -> "Very Low"
            else -> "Standard"
        }
    }

    /**
     * Formats voice latency constant into human-readable description.
     */
    fun formatLatency(latency: Int): String {
        return when (latency) {
            Voice.LATENCY_VERY_LOW -> "Ultra Low Latency"
            Voice.LATENCY_LOW -> "Low Latency"
            Voice.LATENCY_NORMAL -> "Normal Latency"
            Voice.LATENCY_HIGH -> "High Latency"
            Voice.LATENCY_VERY_HIGH -> "Very High Latency"
            else -> "Normal Latency"
        }
    }
}
