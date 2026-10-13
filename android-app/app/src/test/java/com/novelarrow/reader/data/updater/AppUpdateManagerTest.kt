package com.novelarrow.reader.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testIsNewerVersion() {
        // Tag is newer than initial 1.0 version
        assertTrue(AppUpdateManager.isNewerVersion("1.0", "v1.0.20261013154205"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0", "v1.0.20261013154205"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0", "v1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.1", "1.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.20261013154205", "v1.0.20261013154206"))

        // Same version is not newer
        assertFalse(AppUpdateManager.isNewerVersion("1.0.20261013154205", "v1.0.20261013154205"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.20261013154205", "v1.0.20261013154205"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.0.0"))

        // Older version or downgrade is not newer
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.2", "v1.0.1"))
        assertFalse(AppUpdateManager.isNewerVersion("2.0.0", "v1.9.9"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.20261013154206", "v1.0.20261013154205"))
    }

    @Test
    fun testGitHubReleaseJsonParsing() {
        val sampleJson = """
        {
          "tag_name": "v1.0.20261013154205",
          "name": "NovelArrow Reader v1.0.20261013154205",
          "body": "### NovelArrow Reader Release\n- Offline TTS\n- Background Audio",
          "html_url": "https://github.com/faycalrafa/free-novel-reader-tts-android/releases/tag/v1.0.20261013154205",
          "assets": [
            {
              "name": "app-release.apk",
              "size": 15889351,
              "browser_download_url": "https://github.com/faycalrafa/free-novel-reader-tts-android/releases/download/v1.0.20261013154205/app-release.apk",
              "content_type": "application/vnd.android.package-archive"
            }
          ]
        }
        """.trimIndent()

        val release = AppUpdateManager.parseReleaseJson(sampleJson)
        assertNotNull(release)
        assertEquals("v1.0.20261013154205", release?.tagName)
        assertEquals("NovelArrow Reader v1.0.20261013154205", release?.title)
        assertEquals("app-release.apk", release?.apkName)
        assertEquals(15889351L, release?.apkSize)
        assertEquals("15.2 MB", release?.formattedSize)
    }
}
