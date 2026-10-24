package com.novelarrow.reader.playback

import com.novelarrow.reader.data.model.ReaderSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCueManagerTest {

    @Test
    fun testReaderSettingsSoundCuesDefaultEnabled() {
        val settings = ReaderSettings()
        assertTrue("Sound cues should be enabled by default", settings.soundCuesEnabled)
    }

    @Test
    fun testReaderSettingsSoundCuesCanBeDisabled() {
        val settings = ReaderSettings(soundCuesEnabled = false)
        assertFalse(settings.soundCuesEnabled)
    }

    @Test
    fun testAudioCueManagerMethodsExecuteWithoutCrashing() {
        // Without Context initialization, AudioCueManager gracefully handles null soundPool/toneGen
        AudioCueManager.playChapterChange()
        AudioCueManager.playTimerStop()
        AudioCueManager.playFolderDone()
        AudioCueManager.release()
    }
}
