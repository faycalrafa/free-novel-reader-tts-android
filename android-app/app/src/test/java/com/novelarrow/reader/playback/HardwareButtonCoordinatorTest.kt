package com.novelarrow.reader.playback

import com.novelarrow.reader.data.model.HardwareButtonAction
import com.novelarrow.reader.data.model.HardwareButtonScreenCondition
import com.novelarrow.reader.data.model.HardwareButtonSettings
import com.novelarrow.reader.data.model.ReaderSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareButtonCoordinatorTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun testScreenConditionEvaluation() {
        val alwaysSettings = HardwareButtonSettings(
            enabled = true,
            screenCondition = HardwareButtonScreenCondition.ALWAYS
        )
        assertTrue(HardwareButtonCoordinator.isConditionSatisfied(alwaysSettings, isInteractive = true))
        assertTrue(HardwareButtonCoordinator.isConditionSatisfied(alwaysSettings, isInteractive = false))

        val screenOffSettings = HardwareButtonSettings(
            enabled = true,
            screenCondition = HardwareButtonScreenCondition.SCREEN_OFF_ONLY
        )
        assertFalse(HardwareButtonCoordinator.isConditionSatisfied(screenOffSettings, isInteractive = true))
        assertTrue(HardwareButtonCoordinator.isConditionSatisfied(screenOffSettings, isInteractive = false))

        val screenOnSettings = HardwareButtonSettings(
            enabled = true,
            screenCondition = HardwareButtonScreenCondition.SCREEN_ON_ONLY
        )
        assertTrue(HardwareButtonCoordinator.isConditionSatisfied(screenOnSettings, isInteractive = true))
        assertFalse(HardwareButtonCoordinator.isConditionSatisfied(screenOnSettings, isInteractive = false))
    }

    @Test
    fun testHardwareButtonSettingsDefaults() {
        val defaults = HardwareButtonSettings()
        assertFalse(defaults.enabled)
        assertEquals(HardwareButtonScreenCondition.ALWAYS, defaults.screenCondition)
        assertTrue(defaults.hapticFeedback)
        assertEquals(HardwareButtonAction.DEFAULT_VOLUME, defaults.volUpSinglePress)
        assertEquals(HardwareButtonAction.NEXT_PARAGRAPH, defaults.volUpDoublePress)
        assertEquals(HardwareButtonAction.NEXT_CHAPTER, defaults.volUpLongPress)
        assertEquals(HardwareButtonAction.DEFAULT_VOLUME, defaults.volDownSinglePress)
        assertEquals(HardwareButtonAction.PREV_PARAGRAPH, defaults.volDownDoublePress)
        assertEquals(HardwareButtonAction.PREV_CHAPTER, defaults.volDownLongPress)
    }

    @Test
    fun testHardwareButtonSettingsSerialization() {
        val custom = HardwareButtonSettings(
            enabled = true,
            screenCondition = HardwareButtonScreenCondition.SCREEN_OFF_ONLY,
            hapticFeedback = false,
            volUpSinglePress = HardwareButtonAction.PLAY_PAUSE,
            volUpDoublePress = HardwareButtonAction.INCREASE_SPEED,
            volUpLongPress = HardwareButtonAction.SKIP_FORWARD_5,
            volDownSinglePress = HardwareButtonAction.NONE,
            volDownDoublePress = HardwareButtonAction.DECREASE_SPEED,
            volDownLongPress = HardwareButtonAction.SKIP_BACKWARD_5
        )

        val serialized = json.encodeToString(custom)
        val deserialized = json.decodeFromString<HardwareButtonSettings>(serialized)

        assertEquals(custom, deserialized)
    }

    @Test
    fun testReaderSettingsBackwardCompatibilityWithoutHardwareButtonsField() {
        // Simulated JSON from prior release without hardwareButtonSettings
        val legacyJson = """
            {
                "theme": "SEPIA",
                "fontSizeSp": 16.0,
                "speechRate": 1.25,
                "speechPitch": 1.0,
                "autoNextChapter": true
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ReaderSettings>(legacyJson)
        assertEquals(1.25f, parsed.speechRate)
        assertFalse(parsed.hardwareButtonSettings.enabled)
        assertEquals(HardwareButtonAction.NEXT_PARAGRAPH, parsed.hardwareButtonSettings.volUpDoublePress)
        assertEquals(HardwareButtonAction.PREV_PARAGRAPH, parsed.hardwareButtonSettings.volDownDoublePress)
    }

    @Test
    fun testAllActionsAndConditionsHaveReadableLabels() {
        HardwareButtonAction.entries.forEach { action ->
            assertTrue(action.label.isNotBlank())
        }

        HardwareButtonScreenCondition.entries.forEach { condition ->
            assertTrue(condition.label.isNotBlank())
        }
    }

    @Test
    fun testAccessibilityEnabledStringParsing() {
        fun isServiceInList(enabledServices: String?, target: String): Boolean {
            if (enabledServices.isNullOrBlank()) return false
            val parts = enabledServices.split(":")
            return parts.any { comp ->
                comp.equals(target, ignoreCase = true) ||
                comp.endsWith("/$target") ||
                comp.contains(target)
            }
        }

        val target = "NovelArrowAccessibilityService"

        // Single service
        assertTrue(isServiceInList("com.novelarrow.reader/.playback.NovelArrowAccessibilityService", target))

        // Multiple services separated by colon
        val multiServices = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:com.novelarrow.reader/.playback.NovelArrowAccessibilityService:com.example.other/OtherService"
        assertTrue(isServiceInList(multiServices, target))

        // Not in list
        val otherServices = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:com.example.other/OtherService"
        assertFalse(isServiceInList(otherServices, target))

        // Empty / null
        assertFalse(isServiceInList("", target))
        assertFalse(isServiceInList(null, target))
    }
}

