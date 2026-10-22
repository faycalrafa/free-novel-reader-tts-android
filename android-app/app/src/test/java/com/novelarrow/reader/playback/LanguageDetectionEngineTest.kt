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

        val englishText3 = "His son got into the car with a ton of books."
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(englishText3))

        val englishText4 = "Would you like this or that? Either way is fine."
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(englishText4))

        val englishText5 = "She sat in the café chatting with her fiancé and drinking tea."
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(englishText5))
    }

    @Test
    fun testDetectLanguage_blankOrFallbackDefaultsToEnglish() {
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage(""))
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage("    "))
        assertEquals(SupportedLanguage.ENGLISH, LanguageDetectionEngine.detectLanguage("1234567890 !?#@%"))
    }
}
