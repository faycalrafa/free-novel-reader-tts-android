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

        // Verify personal hobby project framing
        assertTrue("README must state it is a personal hobby project", readme.contains("personal hobby project"))
        assertTrue("README must mention creator Faycal Rafa", readme.contains("Faycal Rafa"))

        // Verify header tagline
        assertTrue("README must contain personal offline reader header",
            readme.contains("A personal offline novel reader and Text-to-Speech (TTS) audiobook application for Android."))
        assertTrue("README must contain offline feature bullets",
            readme.contains("No Ads • No Trackers • No Subscriptions • Fully Offline • Native Android & Text-to-Speech"))
    }

    @Test
    fun testReadmePreservesFeaturesArchitectureAndBuildInstructions() {
        val readme = findReadmeFile().readText()

        // Features
        assertTrue("README must contain Key Features section", readme.contains("## 🚀 Key Features"))
        assertTrue("README must describe background TTS", readme.contains("Seamless Background Text-to-Speech"))
        assertTrue("README must describe web browser & downloader", readme.contains("Integrated Web Browser & Batch Chapter Downloader"))
        assertTrue("README must describe sleep timer", readme.contains("Smart Sleep Timer"))
        assertTrue("README must describe content filters", readme.contains("Advanced Content & Parsing Filters"))
        assertTrue("README must describe modern reader UI", readme.contains("Modern, Responsive Reader UI"))

        // Architecture
        assertTrue("README must describe architecture", readme.contains("## 🏗️ Architecture & Technology Stack"))
        assertTrue("README must mention Jetpack Compose", readme.contains("Jetpack Compose"))
        assertTrue("README must mention Media3", readme.contains("AndroidX Media3"))

        // Build instructions
        assertTrue("README must contain build instructions", readme.contains("## 🛠️ Building & Developing"))
        assertTrue("README must document assembleDebug command", readme.contains("./gradlew assembleDebug"))
        assertTrue("README must document assembleRelease command", readme.contains("./gradlew assembleRelease"))
        assertTrue("README must document testDebugUnitTest command", readme.contains("./gradlew testDebugUnitTest"))
    }
}
