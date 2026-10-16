package com.novelarrow.reader.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickToolbarFormattingTest {

    @Test
    fun testFormatSpeedNeverAppendsDoubleX() {
        assertEquals("1.0x", QuickToolbarForegroundService.formatSpeed(1.0f))
        assertEquals("1.25x", QuickToolbarForegroundService.formatSpeed(1.25f))
        assertEquals("1.5x", QuickToolbarForegroundService.formatSpeed(1.5f))
        assertEquals("1.75x", QuickToolbarForegroundService.formatSpeed(1.75f))
        assertEquals("2.0x", QuickToolbarForegroundService.formatSpeed(2.0f))
        assertEquals("0.75x", QuickToolbarForegroundService.formatSpeed(0.75f))
    }

    @Test
    fun testFormatSleepTimerOutputsLiveCountdown() {
        // Off states
        assertEquals("💤 Off", QuickToolbarForegroundService.formatSleepTimer(0, 0))
        assertEquals("💤 Off", QuickToolbarForegroundService.formatSleepTimer(15, 0))
        assertEquals("💤 Off", QuickToolbarForegroundService.formatSleepTimer(0, 60))

        // Active countdown states (< 1 hour)
        assertEquals("⏱ 29:45", QuickToolbarForegroundService.formatSleepTimer(30, 1785))
        assertEquals("⏱ 14:02", QuickToolbarForegroundService.formatSleepTimer(15, 842))
        assertEquals("⏱ 0:45", QuickToolbarForegroundService.formatSleepTimer(1, 45))
        assertEquals("⏱ 0:05", QuickToolbarForegroundService.formatSleepTimer(1, 5))

        // Active countdown states (>= 1 hour)
        assertEquals("⏱ 1:00:00", QuickToolbarForegroundService.formatSleepTimer(60, 3600))
        assertEquals("⏱ 1:01:05", QuickToolbarForegroundService.formatSleepTimer(65, 3665))
    }

    @Test
    fun testFormatChapterProgressOutputsCleanFraction() {
        // Normal playlist chapters (0-indexed to 1-indexed)
        assertEquals("1/10", QuickToolbarForegroundService.formatChapterProgress(0, 10))
        assertEquals("45/120", QuickToolbarForegroundService.formatChapterProgress(44, 120))
        assertEquals("100/100", QuickToolbarForegroundService.formatChapterProgress(99, 100))

        // When total chapters is unknown or 0 (e.g. single web chapter)
        assertEquals("1", QuickToolbarForegroundService.formatChapterProgress(0, 0))
        assertEquals("12", QuickToolbarForegroundService.formatChapterProgress(11, 0))
    }
}
