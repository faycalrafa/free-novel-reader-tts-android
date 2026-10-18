package com.novelarrow.reader.ui.about

import com.novelarrow.reader.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutBrandingTest {

    private fun findReadmeFile(): File {
        val candidates = listOf(
            File("README.md"),
            File("../README.md"),
            File("../../README.md"),
            File("/workspaces/free-novel-reader-tts-android/README.md")
        )
        val file = candidates.firstOrNull { it.exists() && it.isFile }
        assertNotNull("README.md should be located in workspace", file)
        return file!!
    }

    @Test
    fun testLauncherIconResourceExists() {
        assertTrue("Launcher icon mipmap resource ID must be non-zero", R.mipmap.ic_launcher != 0)
        assertTrue("Launcher round icon mipmap resource ID must be non-zero", R.mipmap.ic_launcher_round != 0)
        assertTrue("About screen app icon resource ID must be non-zero", R.drawable.ic_app_icon_round != 0)
        assertTrue("Squircle icon resource ID must be non-zero", R.drawable.ic_app_icon_squircle != 0)
        assertTrue("Loading animation drawable resource ID must be non-zero", R.drawable.app_loading_animation != 0)
    }

    @Test
    fun testReadmeAbsenceOfFossAndLicenseSections() {
        val readme = findReadmeFile().readText()

        // Verify absence of FOSS badge / callout
        assertFalse("README must not contain FOSS badge/callout", readme.contains("100% Free and Open Source Software (FOSS)"))
        assertFalse("README must not mention FOSS acronym", readme.contains(Regex("""\bFOSS\b""", RegexOption.IGNORE_CASE)))

        // Verify absence of Apache / License badges and section
        assertFalse("README must not contain Apache 2.0 license badge", readme.contains("Apache_2.0-blue.svg"))
        assertFalse("README must not contain License & Freedom section", readme.contains("## 📄 License & Freedom"))
        assertFalse("README must not contain Apache License 2.0 text", readme.contains("Apache License 2.0"))
        assertFalse("README must not contain Open Source phrase", readme.contains(Regex("""\bOpen Source\b""", RegexOption.IGNORE_CASE)))
    }

    @Test
    fun testReadmePresenceOfPersonalHobbyProjectAndAuthor() {
        val readme = findReadmeFile().readText()

        // Verify creator reference and branding
        assertTrue("README must mention creator faycalrafa", readme.contains("faycalrafa", ignoreCase = true))
        assertTrue("README must contain BookReader heading", readme.contains("BookReader"))
        assertTrue("README must state offline novel reader and TTS", readme.contains("Offline Novel Reader & TTS Engine"))
    }

    @Test
    fun testReadmePreservesFeaturesArchitectureAndBuildInstructions() {
        val readme = findReadmeFile().readText()

        // Architectural Highlights & Pipeline
        assertTrue("README must contain Key Architectural Highlights section", readme.contains("Key Architectural Highlights"))
        assertTrue("README must describe background audio Media3", readme.contains("Zero-Thread-Collision Background Audio"))
        assertTrue("README must mention Jetpack Compose", readme.contains("Jetpack Compose"))
        assertTrue("README must mention Media3", readme.contains("AndroidX Media3"))
        assertTrue("README must describe architecture and audio pipeline", readme.contains("System Architecture & Audio Pipeline"))
    }
}
