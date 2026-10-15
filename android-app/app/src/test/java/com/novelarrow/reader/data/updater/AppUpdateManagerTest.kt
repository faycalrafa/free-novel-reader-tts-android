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

    @Test
    fun testCertToSha256() {
        val sampleBytes = "test certificate data".toByteArray(Charsets.UTF_8)
        val fingerprint = AppUpdateManager.certToSha256(sampleBytes)
        assertTrue(fingerprint.matches(Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){31}$")))
        assertEquals(
            "2E:FD:4E:4B:04:FA:96:F1:14:32:5B:59:4A:74:1A:D7:C8:A1:C2:4B:7B:FC:FE:A1:3B:D2:7C:19:6F:5D:C9:A0",
            fingerprint
        )
    }
}
