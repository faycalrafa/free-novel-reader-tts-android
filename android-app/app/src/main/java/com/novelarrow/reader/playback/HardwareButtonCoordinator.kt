package com.novelarrow.reader.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import com.novelarrow.reader.data.model.HardwareButtonAction
import com.novelarrow.reader.data.model.HardwareButtonScreenCondition
import com.novelarrow.reader.data.model.HardwareButtonSettings

object HardwareButtonCoordinator {

    private const val DOUBLE_PRESS_TIMEOUT_MS = 320L
    private const val LONG_PRESS_TIMEOUT_MS = 450L
    private const val REPEAT_HOLD_THRESHOLD_MS = 180L

    private val mainHandler = Handler(Looper.getMainLooper())

    // Foreground Activity key tracking
    private var pendingSinglePressRunnable: Runnable? = null
    private var pendingLongPressRunnable: Runnable? = null
    private var isActivityLongPressTriggered = false
    private var lastActivityKeyUpTime = 0L
    private var lastActivityKeyCode = 0

    // Background Service volume tracking
    private var pendingServiceSinglePressRunnable: Runnable? = null
    private var lastServiceVolumeEventTime = 0L
    private var lastServiceVolumeIsUp: Boolean? = null
    private var isServiceLongPressTriggered = false

    // Cross-channel deduplication (AccessibilityService vs MediaSession VolumeProvider)
    @Volatile
    private var lastHandledActionTimestamp = 0L
    @Volatile
    private var lastHandledActionIsUp: Boolean? = null

    fun isScreenInteractive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: true
    }

    fun isConditionSatisfied(settings: HardwareButtonSettings, isInteractive: Boolean): Boolean {
        return when (settings.screenCondition) {
            HardwareButtonScreenCondition.ALWAYS -> true
            HardwareButtonScreenCondition.SCREEN_OFF_ONLY -> !isInteractive
            HardwareButtonScreenCondition.SCREEN_ON_ONLY -> isInteractive
        }
    }

    fun performHapticFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Handles hardware volume key events from MainActivity dispatchKeyEvent.
     * Returns true if event was intercepted and consumed, false to allow default handling.
     */
    fun handleActivityKeyEvent(
        context: Context,
        event: KeyEvent,
        settings: HardwareButtonSettings
    ): Boolean {
        if (!settings.enabled) return false

        val isInteractive = isScreenInteractive(context)
        if (!isConditionSatisfied(settings, isInteractive)) return false

        val isUpKey = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }

        val singleAction = if (isUpKey) settings.volUpSinglePress else settings.volDownSinglePress
        val doubleAction = if (isUpKey) settings.volUpDoublePress else settings.volDownDoublePress
        val longAction = if (isUpKey) settings.volUpLongPress else settings.volDownLongPress

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                isActivityLongPressTriggered = false
                pendingLongPressRunnable?.let { mainHandler.removeCallbacks(it) }

                if (longAction != HardwareButtonAction.NONE) {
                    val longRunnable = Runnable {
                        isActivityLongPressTriggered = true
                        executeAction(context, longAction, isUpKey, settings.hapticFeedback)
                    }
                    pendingLongPressRunnable = longRunnable
                    mainHandler.postDelayed(longRunnable, LONG_PRESS_TIMEOUT_MS)
                }
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_UP) {
            pendingLongPressRunnable?.let {
                mainHandler.removeCallbacks(it)
                pendingLongPressRunnable = null
            }

            if (isActivityLongPressTriggered) {
                isActivityLongPressTriggered = false
                return true
            }

            val now = System.currentTimeMillis()
            val isSameKey = (lastActivityKeyCode == event.keyCode)
            val timeSinceLastUp = now - lastActivityKeyUpTime

            if (isSameKey && timeSinceLastUp < DOUBLE_PRESS_TIMEOUT_MS && doubleAction != HardwareButtonAction.NONE) {
                // Double press detected
                pendingSinglePressRunnable?.let {
                    mainHandler.removeCallbacks(it)
                    pendingSinglePressRunnable = null
                }
                lastActivityKeyUpTime = 0L
                lastActivityKeyCode = 0
                executeAction(context, doubleAction, isUpKey, settings.hapticFeedback)
            } else {
                // First press or double-click not configured
                lastActivityKeyUpTime = now
                lastActivityKeyCode = event.keyCode

                if (doubleAction == HardwareButtonAction.NONE) {
                    // No double press configured for this button, execute single press immediately
                    executeAction(context, singleAction, isUpKey, settings.hapticFeedback)
                } else {
                    // Wait to see if a second click occurs
                    val singleRunnable = Runnable {
                        executeAction(context, singleAction, isUpKey, settings.hapticFeedback)
                        pendingSinglePressRunnable = null
                    }
                    pendingSinglePressRunnable = singleRunnable
                    mainHandler.postDelayed(singleRunnable, DOUBLE_PRESS_TIMEOUT_MS)
                }
            }
            return true
        }

        return false
    }

    /**
     * Handles volume adjustments dispatched to ForwardingPlayer in NovelAudioPlaybackService
     * when device volume control is routed remotely.
     */
    fun onDeviceVolumeAdjusted(
        context: Context,
        isUp: Boolean,
        settings: HardwareButtonSettings
    ) {
        if (!settings.enabled) {
            adjustSystemStreamVolume(context, isUp)
            return
        }

        val isInteractive = isScreenInteractive(context)
        if (!isConditionSatisfied(settings, isInteractive)) {
            adjustSystemStreamVolume(context, isUp)
            return
        }

        val singleAction = if (isUp) settings.volUpSinglePress else settings.volDownSinglePress
        val doubleAction = if (isUp) settings.volUpDoublePress else settings.volDownDoublePress
        val longAction = if (isUp) settings.volUpLongPress else settings.volDownLongPress

        val now = System.currentTimeMillis()
        if (now - lastHandledActionTimestamp < 250L && lastHandledActionIsUp == isUp) {
            // Already handled via AccessibilityService KeyEvent
            return
        }

        val timeSinceLast = now - lastServiceVolumeEventTime
        val isSameKey = (lastServiceVolumeIsUp == isUp)

        lastServiceVolumeEventTime = now
        lastServiceVolumeIsUp = isUp

        // Detect rapid repeated key events from hardware button hold (long press)
        if (isSameKey && timeSinceLast < REPEAT_HOLD_THRESHOLD_MS) {
            if (!isServiceLongPressTriggered && longAction != HardwareButtonAction.NONE) {
                isServiceLongPressTriggered = true
                pendingServiceSinglePressRunnable?.let {
                    mainHandler.removeCallbacks(it)
                    pendingServiceSinglePressRunnable = null
                }
                executeAction(context, longAction, isUp, settings.hapticFeedback)
            }
            return
        }

        // Reset long press flag if gap is wider
        isServiceLongPressTriggered = false

        // Detect double click
        if (isSameKey && timeSinceLast < DOUBLE_PRESS_TIMEOUT_MS && doubleAction != HardwareButtonAction.NONE) {
            pendingServiceSinglePressRunnable?.let {
                mainHandler.removeCallbacks(it)
                pendingServiceSinglePressRunnable = null
            }
            lastServiceVolumeEventTime = 0L
            lastServiceVolumeIsUp = null
            executeAction(context, doubleAction, isUp, settings.hapticFeedback)
            return
        }

        // Potential single click
        if (doubleAction == HardwareButtonAction.NONE) {
            executeAction(context, singleAction, isUp, settings.hapticFeedback)
        } else {
            pendingServiceSinglePressRunnable?.let { mainHandler.removeCallbacks(it) }
            val singleRunnable = Runnable {
                executeAction(context, singleAction, isUp, settings.hapticFeedback)
                pendingServiceSinglePressRunnable = null
            }
            pendingServiceSinglePressRunnable = singleRunnable
            mainHandler.postDelayed(singleRunnable, DOUBLE_PRESS_TIMEOUT_MS)
        }
    }

    private fun executeAction(
        context: Context,
        action: HardwareButtonAction,
        isUp: Boolean,
        hapticFeedback: Boolean
    ) {
        lastHandledActionTimestamp = System.currentTimeMillis()
        lastHandledActionIsUp = isUp

        if (action == HardwareButtonAction.DEFAULT_VOLUME) {
            adjustSystemStreamVolume(context, isUp)
            return
        }

        if (action == HardwareButtonAction.NONE) {
            return
        }

        if (hapticFeedback) {
            performHapticFeedback(context)
        }

        AudioPlaybackCoordinator.executeHardwareAction(action, context)
    }

    private fun adjustSystemStreamVolume(context: Context, isUp: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val direction = if (isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            val flags = if (isScreenInteractive(context)) AudioManager.FLAG_SHOW_UI else 0
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
