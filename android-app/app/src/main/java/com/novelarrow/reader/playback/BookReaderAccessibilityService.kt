package com.novelarrow.reader.playback

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class BookReaderAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
            Log.d(TAG, "BookReaderAccessibilityService connected with FLAG_REQUEST_FILTER_KEY_EVENTS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed configuring AccessibilityServiceInfo", e)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        val hwSettings = AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
            ?: return super.onKeyEvent(event)

        if (!hwSettings.enabled) {
            return super.onKeyEvent(event)
        }

        val consumed = HardwareButtonCoordinator.handleActivityKeyEvent(this, event, hwSettings)
        return if (consumed) true else super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used; service functions strictly for physical key filtering
    }

    override fun onInterrupt() {
        Log.d(TAG, "BookReaderAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "BookReaderA11y"

        fun isEnabled(context: Context): Boolean {
            return try {
                val expected = ComponentName(context, BookReaderAccessibilityService::class.java).flattenToString()
                val legacyExpected = "com.novelarrow.reader/.playback.NovelArrowAccessibilityService"
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)
                while (colonSplitter.hasNext()) {
                    val comp = colonSplitter.next()
                    if (comp.equals(expected, ignoreCase = true) ||
                        comp.equals(legacyExpected, ignoreCase = true) ||
                        comp.endsWith("/" + BookReaderAccessibilityService::class.java.name) ||
                        comp.endsWith("/.playback.BookReaderAccessibilityService") ||
                        comp.contains("BookReaderAccessibilityService") ||
                        comp.contains("NovelArrowAccessibilityService")
                    ) {
                        return true
                    }
                }
                false
            } catch (e: Exception) {
                false
            }
        }
    }
}

typealias NovelArrowAccessibilityService = BookReaderAccessibilityService

