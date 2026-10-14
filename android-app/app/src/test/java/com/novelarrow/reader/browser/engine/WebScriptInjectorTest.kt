package com.novelarrow.reader.browser.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebScriptInjectorTest {

    @Before
    @After
    fun cleanup() {
        WebScriptInjector.clearUserscripts()
    }

    @Test
    fun testMatchesPattern_wildcardsAndAllUrls() {
        assertTrue(WebScriptInjector.matchesPattern("https://example.com/page", "*"))
        assertTrue(WebScriptInjector.matchesPattern("https://example.com/page", "<all_urls>"))
        assertTrue(WebScriptInjector.matchesPattern("https://example.com/page", "*://*/*"))

        // Domain wildcard matching
        val novelbinPattern = "*://*.novelbin.*/*"
        assertTrue(WebScriptInjector.matchesPattern("https://read.novelbin.me/novel-book/chapter-1", novelbinPattern))
        assertTrue(WebScriptInjector.matchesPattern("http://novelbin.com/ch1", novelbinPattern))
        assertFalse(WebScriptInjector.matchesPattern("https://otherdomain.org/ch1", novelbinPattern))

        // Exact path matching
        val specificPattern = "https://example.com/books/*"
        assertTrue(WebScriptInjector.matchesPattern("https://example.com/books/123", specificPattern))
        assertFalse(WebScriptInjector.matchesPattern("https://example.com/articles/123", specificPattern))
    }

    @Test
    fun testRegisterAndRetrieveUserscripts() {
        val testScript1 = object : Userscript {
            override val id = "script_1"
            override val name = "Novelbin Dark Mode"
            override val matchPatterns = listOf("*://*.novelbin.*/*")
            override val runAt = RunAt.DOCUMENT_END
            override fun getScriptContent() = "document.body.style.backgroundColor = '#111';"
        }

        val testScript2 = object : Userscript {
            override val id = "script_2"
            override val name = "Global Ad Skipper"
            override val matchPatterns = listOf("<all_urls>")
            override val runAt = RunAt.DOCUMENT_START
            override fun getScriptContent() = "console.log('ad skipper');"
        }

        WebScriptInjector.registerUserscript(testScript1)
        WebScriptInjector.registerUserscript(testScript2)

        assertEquals(2, WebScriptInjector.getAllUserscripts().size)

        // Matching URL at DOCUMENT_END
        val endScripts = WebScriptInjector.getUserscriptsForUrl("https://read.novelbin.me/ch1", RunAt.DOCUMENT_END)
        assertEquals(1, endScripts.size)
        assertEquals("script_1", endScripts[0].id)

        // Matching URL at DOCUMENT_START (should return script2 because <all_urls>)
        val startScripts = WebScriptInjector.getUserscriptsForUrl("https://read.novelbin.me/ch1", RunAt.DOCUMENT_START)
        assertEquals(1, startScripts.size)
        assertEquals("script_2", startScripts[0].id)

        // URL that only matches script2
        val otherEndScripts = WebScriptInjector.getUserscriptsForUrl("https://wikipedia.org", RunAt.DOCUMENT_END)
        assertTrue(otherEndScripts.isEmpty())

        // Unregister
        WebScriptInjector.unregisterUserscript("script_1")
        assertEquals(1, WebScriptInjector.getAllUserscripts().size)
    }

    @Test
    fun testBuildUserscriptInjectionCode() {
        val testScript = object : Userscript {
            override val id = "custom_remover"
            override val name = "Element Remover"
            override val matchPatterns = listOf("*://*.site.com/*")
            override val runAt = RunAt.DOCUMENT_IDLE
            override fun getScriptContent() = "document.querySelectorAll('.ad').forEach(e => e.remove());"
        }

        WebScriptInjector.registerUserscript(testScript)

        val code = WebScriptInjector.buildUserscriptInjectionCode("https://www.site.com/chapter-1", RunAt.DOCUMENT_IDLE)
        assertTrue(code.startsWith("(function() {"))
        assertTrue(code.endsWith("})();"))
        assertTrue(code.contains("Element Remover"))
        assertTrue(code.contains("document.querySelectorAll('.ad').forEach"))
        assertTrue(code.contains("console.error('Error executing userscript [custom_remover]:'"))

        // When no scripts match
        val emptyCode = WebScriptInjector.buildUserscriptInjectionCode("https://other.com", RunAt.DOCUMENT_IDLE)
        assertEquals("", emptyCode)
    }

    @Test
    fun testBuildHelperScript() {
        val script = WebScriptInjector.buildHelperScript()
        assertTrue(script.contains("na-tts-styles"))
        assertTrue(script.contains("__novelArrowClickBound"))
        assertTrue(script.contains("__novelArrowScrollBound"))
        assertTrue(script.contains("window.AndroidBridge.onParagraphClicked"))
        assertTrue(script.contains("window.AndroidBridge.onScrolledToBottom"))
    }

    @Test
    fun testBuildHighlightScript() {
        val scriptWithScroll = WebScriptInjector.buildHighlightScript(5, autoScroll = true)
        assertTrue(scriptWithScroll.contains("data-novel-para-idx"))
        assertTrue(scriptWithScroll.contains("+ 5 +"))
        assertTrue(scriptWithScroll.contains("scrollIntoView"))

        val scriptWithoutScroll = WebScriptInjector.buildHighlightScript(10, autoScroll = false)
        assertTrue(scriptWithoutScroll.contains("data-novel-para-idx"))
        assertTrue(scriptWithoutScroll.contains("+ 10 +"))
        assertFalse(scriptWithoutScroll.contains("scrollIntoView"))
    }

    @Test
    fun testBuildNavigationScripts() {
        val nextScript = WebScriptInjector.buildNextChapterScript("#my-next-btn")
        assertTrue(nextScript.contains("#my-next-btn"))
        assertTrue(nextScript.contains("click()"))

        val prevScript = WebScriptInjector.buildPrevChapterScript()
        assertTrue(prevScript.contains("click()"))

        val clearScript = WebScriptInjector.buildClearHighlightScript()
        assertTrue(clearScript.contains("classList.remove('na-tts-highlight')"))
    }
}
