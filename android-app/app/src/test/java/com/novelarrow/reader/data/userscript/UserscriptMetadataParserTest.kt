package com.novelarrow.reader.data.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptMetadataParserTest {

    @Test
    fun testParseFullHeaderBlock() {
        val script = """
            // ==UserScript==
            // @name         NovelBin Ad Cleaner
            // @version      2.1.0
            // @description  Removes banner ads and sidebars
            // @author       BookReader Team
            // @match        *://*.novelbin.me/*
            // @match        *://*.novelbin.com/*
            // @run-at       document-end
            // @grant        GM_notification
            // ==/UserScript==

            (function() {
                console.log('cleaned');
            })();
        """.trimIndent()

        val parsed = UserscriptMetadataParser.parse(script)
        assertEquals("NovelBin Ad Cleaner", parsed.name)
        assertEquals("2.1.0", parsed.version)
        assertEquals("Removes banner ads and sidebars", parsed.description)
        assertEquals("BookReader Team", parsed.author)
        assertEquals(listOf("*://*.novelbin.me/*", "*://*.novelbin.com/*"), parsed.matchPatterns)
        assertEquals("document-end", parsed.runAt)
        assertEquals(listOf("GM_notification"), parsed.grantList)
    }

    @Test
    fun testParseMultipleIncludeAndDocumentStart() {
        val script = """
            // ==UserScript==
            // @name         Instant Font Changer
            // @version      0.9
            // @include      https://wuxiaworld.com/*
            // @include      https://novelfull.com/*
            // @run-at       document-start
            // ==/UserScript==
        """.trimIndent()

        val parsed = UserscriptMetadataParser.parse(script)
        assertEquals("Instant Font Changer", parsed.name)
        assertEquals("0.9", parsed.version)
        assertEquals(listOf("https://wuxiaworld.com/*", "https://novelfull.com/*"), parsed.matchPatterns)
        assertEquals("document-start", parsed.runAt)
    }

    @Test
    fun testParseFallbackWhenNoHeaderBlock() {
        val rawCode = """
            console.log('No metadata header here');
            alert('Hello World');
        """.trimIndent()

        val parsed = UserscriptMetadataParser.parse(rawCode, fallbackName = "My Fallback Script")
        assertEquals("My Fallback Script", parsed.name)
        assertEquals("1.0.0", parsed.version)
        assertEquals(listOf("*"), parsed.matchPatterns)
        assertEquals("document-end", parsed.runAt)
        assertEquals("", parsed.description)
        assertTrue(parsed.grantList.isEmpty())
    }

    @Test
    fun testGenerateDefaultTemplate() {
        val template = UserscriptMetadataParser.generateDefaultTemplate("https://read.novelbin.me/novel-book/chapter-1", "Test Script")
        assertTrue(template.contains("// @name         Test Script"))
        assertTrue(template.contains("// @match        *://read.novelbin.me/*"))
        assertTrue(template.contains("// @grant        GM_notification"))
        assertTrue(template.contains("GM_notification('Userscript [Test Script] loaded successfully!')"))
    }

    @Test
    fun testParseDefaultDemoScript() {
        val parsed = UserscriptMetadataParser.parse(UserscriptRepository.DEFAULT_DEMO_SCRIPT)
        assertEquals("Clean Novel Reader (Ad & Clutter Remover)", parsed.name)
        assertEquals("1.0.0", parsed.version)
        assertEquals(listOf("*://*/*"), parsed.matchPatterns)
        assertEquals("document-end", parsed.runAt)
        assertEquals(listOf("GM_notification"), parsed.grantList)
        assertTrue(parsed.description.contains("Removes floating ads"))
    }

    @Test
    fun testParseDefaultMultiDownloadScript() {
        val parsed = UserscriptMetadataParser.parse(UserscriptRepository.DEFAULT_MULTI_DOWNLOAD_SCRIPT)
        assertEquals("Multi-Download", parsed.name)
        assertEquals("3.0.0", parsed.version)
        assertEquals(listOf("*://*/*", "file://*/*"), parsed.matchPatterns)
        assertEquals("document-end", parsed.runAt)
        assertTrue(parsed.grantList.contains("GM_registerToolbarButton"))
        assertTrue(parsed.grantList.contains("GM_notification"))
        assertTrue(parsed.description.contains("Batch download loop"))
    }
}
