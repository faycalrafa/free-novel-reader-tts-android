package com.novelarrow.reader.playback

import java.util.Locale

enum class SupportedLanguage(val code: String, val displayName: String, val locale: Locale) {
    ENGLISH("en", "English", Locale.ENGLISH),
    ARABIC("ar", "العربية (Arabic)", Locale("ar")),
    FRENCH("fr", "Français (French)", Locale.FRENCH);

    companion object {
        fun fromCode(code: String?): SupportedLanguage {
            return when (code?.lowercase()?.take(2)) {
                "ar" -> ARABIC
                "fr" -> FRENCH
                else -> ENGLISH
            }
        }
    }
}

object LanguageDetectionEngine {

    private val FRENCH_STOP_WORDS = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "dans", "en", "pour",
        "avec", "sur", "et", "est", "sont", "était", "etaient", "ont", "qui", "que",
        "quoi", "ce", "cette", "ces", "mais", "ou", "donc", "or", "ni", "car",
        "pas", "plus", "nous", "vous", "ils", "elles", "mon", "ma", "mes", "ton",
        "ta", "tes", "son", "sa", "ses", "faire", "comme", "tout", "tous", "au",
        "aux", "par", "lui", "leur", "leurs", "y", "ne", "se", "si", "bonjour",
        "merci", "oui", "non", "alors", "après", "bien", "très", "aussi", "encore"
    )

    private val FRENCH_ACCENT_REGEX = Regex("[éèêëàâçîïôùûüœæ]", RegexOption.IGNORE_CASE)

    fun detectLanguage(text: String): SupportedLanguage {
        if (text.isBlank()) return SupportedLanguage.ENGLISH

        // 1. Check for Arabic characters (Unicode Script)
        var arabicCount = 0
        var totalLetters = 0
        for (ch in text) {
            val codePoint = ch.code
            if (Character.isLetter(ch)) {
                totalLetters++
                if ((codePoint in 0x0600..0x06FF) || // Arabic
                    (codePoint in 0x0750..0x077F) || // Arabic Supplement
                    (codePoint in 0x08A0..0x08FF) || // Arabic Extended-A
                    (codePoint in 0xFB50..0xFDFF) || // Arabic Presentation Forms-A
                    (codePoint in 0xFE70..0xFEFF)    // Arabic Presentation Forms-B
                ) {
                    arabicCount++
                }
            }
        }

        if (arabicCount > 0 && (arabicCount >= 3 || (totalLetters > 0 && (arabicCount.toFloat() / totalLetters) > 0.15f))) {
            return SupportedLanguage.ARABIC
        }

        // 2. Check for French
        val words = text.lowercase().split(Regex("[\\s.,;:!?\"'’()—\\[\\]{}]+")).filter { it.length >= 2 }
        var frenchWordMatches = 0
        for (w in words) {
            if (FRENCH_STOP_WORDS.contains(w)) {
                frenchWordMatches++
            }
        }

        val accentMatches = FRENCH_ACCENT_REGEX.findAll(text).count()
        if (accentMatches >= 2 || (accentMatches >= 1 && frenchWordMatches >= 1) || frenchWordMatches >= 2) {
            return SupportedLanguage.FRENCH
        }

        // 3. Default to English
        return SupportedLanguage.ENGLISH
    }
}
