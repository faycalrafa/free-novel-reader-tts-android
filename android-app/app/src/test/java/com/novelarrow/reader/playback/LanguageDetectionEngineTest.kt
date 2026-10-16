package com.novelarrow.reader.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectionEngineTest {

    @Test
    fun testDetectLanguage_arabic() {
        val arabicText1 = "كان ياما كان في قديم الزمان وسالف العصر والأوان"
        assertEquals(SupportedLanguage.ARABIC, LanguageDetectionEngine.detectLanguage(arabicText1))

        val arabicText2 = "الفصل الأول: البداية الجديدة للمغامر الشاب"
        assertEquals(SupportedLanguage.ARABIC, LanguageDetectionEngine.detectLanguage(arabicText2))
    }

    @Test
    fun testDetectLanguage_french() {
        val frenchText1 = "C'était une journée paisible au bord de la rivière et dans la forêt."
        assertEquals(SupportedLanguage.FRENCH, LanguageDetectionEngine.detectLanguage(frenchText1))

        val frenchText2 = "Le jeune homme regarda les étoiles avec émerveillement et émotion."
        assertEquals(SupportedLanguage.FRENCH, LanguageDetectionEngine.detectLanguage(frenchText2))
    }

    @Test
    fun testDetectLanguage_english() {
        val englishText1 = "Chapter 1: The boy who discovered the ancient artifact."
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(englishText1))

        val englishText2 = "He walked slowly through the dark hallway without making any sound."
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(englishText2))
    }

    @Test
    fun testDetectLanguage_blankOrFallbackDefaultsToEnglish() {
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(""))
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage("    "))
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage("1234567890 !?#@%"))
    }
}
