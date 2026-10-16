package com.novelarrow.reader.data.export

import com.novelarrow.reader.data.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class FileFusionManagerTest {

    @Test
    fun testDetectFusionTypeHtml() {
        val chapters = listOf(
            Chapter(id = "1", novelId = "n1", title = "Chapter 1", fileUri = "content://path/ch1.html", orderIndex = 0),
            Chapter(id = "2", novelId = "n1", title = "Chapter 2", fileUri = "content://path/ch2.htm", orderIndex = 1),
            Chapter(id = "3", novelId = "n1", title = "Chapter 3", fileUri = "content://path/ch3.xhtml", orderIndex = 2)
        )
        val type = FileFusionManager.detectFusionType(chapters)
        assertEquals(FileFusionManager.FusionType.HTML, type)
    }

    @Test
    fun testDetectFusionTypeTxt() {
        val chapters = listOf(
            Chapter(id = "1", novelId = "n1", title = "Chapter 1", fileUri = "content://path/ch1.txt", orderIndex = 0),
            Chapter(id = "2", novelId = "n1", title = "Chapter 2", fileUri = "content://path/ch2.text", orderIndex = 1)
        )
        val type = FileFusionManager.detectFusionType(chapters)
        assertEquals(FileFusionManager.FusionType.TXT, type)
    }

    @Test
    fun testDetectFusionTypeMd() {
        val chapters = listOf(
            Chapter(id = "1", novelId = "n1", title = "Chapter 1", fileUri = "content://path/ch1.md", orderIndex = 0),
            Chapter(id = "2", novelId = "n1", title = "Chapter 2", fileUri = "content://path/ch2.markdown", orderIndex = 1)
        )
        val type = FileFusionManager.detectFusionType(chapters)
        assertEquals(FileFusionManager.FusionType.MD, type)
    }

    @Test
    fun testDetectFusionTypeMixed() {
        val chapters = listOf(
            Chapter(id = "1", novelId = "n1", title = "Chapter 1", fileUri = "content://path/ch1.html", orderIndex = 0),
            Chapter(id = "2", novelId = "n1", title = "Chapter 2", fileUri = "content://path/ch2.txt", orderIndex = 1)
        )
        val type = FileFusionManager.detectFusionType(chapters)
        assertEquals(FileFusionManager.FusionType.MIXED, type)
    }

    @Test
    fun testDetectFusionTypeEmpty() {
        val type = FileFusionManager.detectFusionType(emptyList())
        assertEquals(FileFusionManager.FusionType.TXT, type)
    }

    @Test
    fun testSanitizeFilename() {
        val sanitized = FileFusionManager.sanitizeFilename("My Novel: Volume 1 / Part 2? *Special*")
        assertEquals("My Novel_ Volume 1 _ Part 2_ _Special_", sanitized)

        val fallback = FileFusionManager.sanitizeFilename("???///:::***")
        assertEquals("bookreader_novel", fallback)
    }

    @Test
    fun testEscapeHtml() {
        val escaped = FileFusionManager.escapeHtml("AT&T <The Beginning> \"Volume 1\" & 'Part 2'")
        assertEquals("AT&amp;T &lt;The Beginning&gt; &quot;Volume 1&quot; &amp; &#39;Part 2&#39;", escaped)
    }

    @Test
    fun testGetExtension() {
        assertEquals("html", FileFusionManager.getExtension("chapter_1.html"))
        assertEquals("txt", FileFusionManager.getExtension("chapter_2.txt"))
        assertEquals("md", FileFusionManager.getExtension("chapter_3.md"))
        assertEquals("txt", FileFusionManager.getExtension("chapter_no_extension"))
    }
}
