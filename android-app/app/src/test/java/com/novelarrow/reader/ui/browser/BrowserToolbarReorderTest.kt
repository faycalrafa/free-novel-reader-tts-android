package com.novelarrow.reader.ui.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserToolbarReorderTest {

    @Test
    fun testDefaultToolbarButtonOrder() {
        assertEquals(10, DEFAULT_TOOLBAR_BUTTON_ORDER.size)
        assertEquals("tts", DEFAULT_TOOLBAR_BUTTON_ORDER[0])
        assertEquals("reader_mode", DEFAULT_TOOLBAR_BUTTON_ORDER[1])
        assertEquals("auto_clicker", DEFAULT_TOOLBAR_BUTTON_ORDER[2])
        assertEquals("multi_download", DEFAULT_TOOLBAR_BUTTON_ORDER[3])

        // Verify all 10 standard items are unique
        assertEquals(DEFAULT_TOOLBAR_BUTTON_ORDER.toSet().size, DEFAULT_TOOLBAR_BUTTON_ORDER.size)
    }

    @Test
    fun testResolveToolbarIcon() {
        assertEquals(Icons.Default.CloudDownload, resolveToolbarIcon("CloudDownload"))
        assertEquals(Icons.Default.CloudDownload, resolveToolbarIcon("download"))
        assertEquals(Icons.Default.CloudDownload, resolveToolbarIcon("multi_download"))

        assertEquals(Icons.Default.Headphones, resolveToolbarIcon("Headphones"))
        assertEquals(Icons.Default.Headphones, resolveToolbarIcon("tts"))

        assertEquals(Icons.AutoMirrored.Filled.MenuBook, resolveToolbarIcon("MenuBook"))
        assertEquals(Icons.AutoMirrored.Filled.MenuBook, resolveToolbarIcon("reader_mode"))

        assertEquals(Icons.Default.Mouse, resolveToolbarIcon("Mouse"))
        assertEquals(Icons.Default.Mouse, resolveToolbarIcon("auto_clicker"))

        assertEquals(Icons.Default.Refresh, resolveToolbarIcon("Refresh"))
        assertEquals(Icons.Default.Refresh, resolveToolbarIcon("reload"))

        // Unknown names fallback to Extension icon
        assertEquals(Icons.Default.Extension, resolveToolbarIcon("unknown_custom_action"))
    }

    @Test
    fun testMax8ButtonsVisibleInNavbarPartition() {
        // Enforce maximum of 8 visible buttons in Browser navbar
        val items = DEFAULT_TOOLBAR_BUTTON_ORDER
        val visibleButtons = items.take(8)
        val overflowButtons = items.drop(8)

        assertEquals(8, visibleButtons.size)
        assertEquals(2, overflowButtons.size)
        assertEquals(listOf("extensions", "inspect_clickables"), overflowButtons)

        // Right-to-left layout: directly shown items are reversed so #1 is rightmost next to 3-dot menu
        val rtlDisplayOrder = visibleButtons.reversed()
        assertEquals("save", rtlDisplayOrder[0])
        assertEquals("tts", rtlDisplayOrder.last())
    }
}
