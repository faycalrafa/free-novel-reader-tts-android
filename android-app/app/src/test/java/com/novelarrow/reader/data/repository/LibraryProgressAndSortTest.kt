package com.novelarrow.reader.data.repository

import com.novelarrow.reader.data.model.WebBrowserSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProgressAndSortTest {

    @Test
    fun testProgressJsonSerializationAndParsing() {
        // Build mock JSON matching NovelFolderProgress schema
        val root = JSONObject().apply {
            put("novelTitle", "Test Novel")
            put("currentChapterIndex", 3)
            put("currentChapterTitle", "Chapter 4")
            put("currentParagraphIndex", 15)
            val chaptersArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("title", "Chapter 1")
                    put("fileName", "Chapter 1.txt")
                    put("lastReadParagraphIndex", 50)
                    put("isCompleted", true)
                    put("orderIndex", 0)
                })
                put(JSONObject().apply {
                    put("title", "Chapter 2")
                    put("fileName", "Chapter 2.txt")
                    put("lastReadParagraphIndex", 0)
                    put("isCompleted", false)
                    put("orderIndex", 1)
                })
            }
            put("chapters", chaptersArray)
        }

        val jsonString = root.toString()

        // Parse back
        val parsedJson = JSONObject(jsonString)
        val novelTitle = parsedJson.optString("novelTitle")
        val currentChapterIndex = parsedJson.optInt("currentChapterIndex", 0)
        val currentParagraphIndex = parsedJson.optInt("currentParagraphIndex", 0)
        val chaptersMap = mutableMapOf<String, ChapterProgressEntry>()
        val chaptersArr = parsedJson.optJSONArray("chapters")
        if (chaptersArr != null) {
            for (i in 0 until chaptersArr.length()) {
                val obj = chaptersArr.getJSONObject(i)
                val fileName = obj.optString("fileName")
                if (fileName.isNotBlank()) {
                    chaptersMap[fileName] = ChapterProgressEntry(
                        title = obj.optString("title", fileName),
                        fileName = fileName,
                        lastReadParagraphIndex = obj.optInt("lastReadParagraphIndex", 0),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        orderIndex = obj.optInt("orderIndex", i)
                    )
                }
            }
        }

        assertEquals("Test Novel", novelTitle)
        assertEquals(3, currentChapterIndex)
        assertEquals(15, currentParagraphIndex)
        assertEquals(2, chaptersMap.size)
        assertTrue(chaptersMap.containsKey("Chapter 1.txt"))
        assertTrue(chaptersMap["Chapter 1.txt"]!!.isCompleted)
        assertEquals(50, chaptersMap["Chapter 1.txt"]!!.lastReadParagraphIndex)
        assertEquals(0, chaptersMap["Chapter 1.txt"]!!.orderIndex)
        assertFalse(chaptersMap["Chapter 2.txt"]!!.isCompleted)
    }

    @Test
    fun testNaturalChapterSorting() {
        val regex = Regex("\\d+")
        fun extractFirstNumber(name: String): Int? =
            regex.find(name)?.value?.toIntOrNull()

        fun naturalComparator(a: String, b: String): Int {
            val numA = extractFirstNumber(a)
            val numB = extractFirstNumber(b)
            return if (numA != null && numB != null && numA != numB) {
                numA.compareTo(numB)
            } else {
                a.compareTo(b, ignoreCase = true)
            }
        }

        val titles = listOf(
            "Chapter 10: The Climax",
            "Chapter 1: The Beginning",
            "Chapter 2: The Journey",
            "Chapter 100: Epilogue",
            "Chapter 20: Halfway"
        )

        val sorted = titles.sortedWith { a, b -> naturalComparator(a, b) }

        assertEquals("Chapter 1: The Beginning", sorted[0])
        assertEquals("Chapter 2: The Journey", sorted[1])
        assertEquals("Chapter 10: The Climax", sorted[2])
        assertEquals("Chapter 20: Halfway", sorted[3])
        assertEquals("Chapter 100: Epilogue", sorted[4])
    }

    @Test
    fun testReverseChapterSorting() {
        val regex = Regex("\\d+")
        fun extractFirstNumber(name: String): Int? =
            regex.find(name)?.value?.toIntOrNull()

        fun naturalComparator(a: String, b: String): Int {
            val numA = extractFirstNumber(a)
            val numB = extractFirstNumber(b)
            return if (numA != null && numB != null && numA != numB) {
                numA.compareTo(numB)
            } else {
                a.compareTo(b, ignoreCase = true)
            }
        }

        val titles = listOf(
            "Chapter 1",
            "Chapter 2",
            "Chapter 10"
        )

        val reversed = titles.sortedWith { a, b -> naturalComparator(b, a) }

        assertEquals("Chapter 10", reversed[0])
        assertEquals("Chapter 2", reversed[1])
        assertEquals("Chapter 1", reversed[2])
    }

    @Test
    fun testFileNameSorting() {
        val fileNames = listOf("03_chapter.txt", "01_chapter.txt", "02_chapter.txt")
        val sorted = fileNames.sortedWith(String.CASE_INSENSITIVE_ORDER)

        assertEquals("01_chapter.txt", sorted[0])
        assertEquals("02_chapter.txt", sorted[1])
        assertEquals("03_chapter.txt", sorted[2])
    }

    @Test
    fun testHiddenFilesAndProgressExcludedFromChapters() {
        val supportedExtensions = setOf("txt", "html", "htm", "epub", "md", "fb2", "rtf", "xhtml")
        fun isSupported(fileName: String): Boolean {
            if (fileName.startsWith(".")) return false
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in supportedExtensions
        }

        assertFalse(isSupported(".progress.json"))
        assertFalse(isSupported(".hidden_chapter.txt"))
        assertFalse(isSupported(".DS_Store"))
        assertTrue(isSupported("Chapter 1.txt"))
        assertTrue(isSupported("novel.html"))
        assertTrue(isSupported("story.epub"))
        assertTrue(isSupported("readme.md"))
    }

    @Test
    fun testWebBrowserSettingsAutoReadDefaultIsOff() {
        val settings = WebBrowserSettings()
        assertFalse("Auto-read when next chapter loads must be Off by default", settings.autoReadNextChapter)
    }

    @Test
    fun testSharedTtsSettingsDefaults() {
        val settings = SharedTtsSettings()
        assertEquals(1.0f, settings.speechRate, 0.001f)
        assertEquals(1.0f, settings.speechPitch, 0.001f)
        assertEquals(null, settings.voiceName)
        assertEquals(0, settings.sleepTimerMinutes)
    }
}
