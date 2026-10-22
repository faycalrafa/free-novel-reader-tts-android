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

    private val ENGLISH_STOP_WORDS = setOf(
        "the", "and", "to", "of", "a", "in", "that", "is", "was", "for", "it",
        "with", "as", "his", "her", "at", "by", "this", "have", "from", "they",
        "which", "you", "were", "their", "would", "been", "there", "what",
        "about", "when", "one", "all", "out", "them", "had", "she",
        "him", "my", "me", "your", "who", "will", "so", "up", "said"
    )

    private val FRENCH_STOP_WORDS = setOf(
        "dans", "pour", "avec", "cette", "étaient", "etaient", "sont", "était",
        "etait", "nous", "vous", "ils", "elles", "faire", "comme", "leur",
        "leurs", "après", "apres", "très", "tres", "même", "meme", "qui",
        "que", "quoi", "celui", "celle", "ceux", "celles", "alors", "encore",
        "toujours", "jamais", "entre", "sans", "sous", "pendant", "depuis",
        "chose", "autres", "beaucoup"
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

        // 2. Tokenize and count language-specific stop words
        val words = text.lowercase().split(Regex("[\\s.,;:!?\"'’()—\\[\\]{}]+")).filter { it.length >= 2 }
        var englishWordMatches = 0
        var frenchWordMatches = 0
        for (w in words) {
            if (ENGLISH_STOP_WORDS.contains(w)) {
                englishWordMatches++
            }
            if (FRENCH_STOP_WORDS.contains(w)) {
                frenchWordMatches++
            }
        }

        // If strong English presence, definitively classify as English
        if (englishWordMatches > 0 && englishWordMatches >= frenchWordMatches) {
            return SupportedLanguage.ENGLISH
        }

        // Check for French characteristics
        val accentMatches = FRENCH_ACCENT_REGEX.findAll(text).count()
        if (frenchWordMatches >= 2 && frenchWordMatches > englishWordMatches) {
            return SupportedLanguage.FRENCH
        }
        if (frenchWordMatches >= 1 && accentMatches >= 2 && englishWordMatches == 0) {
            return SupportedLanguage.FRENCH
        }
        if (accentMatches >= 4 && englishWordMatches == 0 && words.size <= 8) {
            return SupportedLanguage.FRENCH
        }

        // 3. Default to English
        return SupportedLanguage.ENGLISH
    }
}
