package com.novelarrow.reader.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages audio focus requests and telephony/call state interruptions for TTS playback.
 * Automatically pauses TTS when an incoming or active call occurs, and resumes playback
 * when the call finishes or audio focus is regained.
 */
class TelephonyAudioInterruptionCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val isPlayingProvider: () -> Boolean,
    private val onPauseRequested: () -> Unit,
    private val onResumeRequested: () -> Unit
) {
    companion object {
        private const val TAG = "TelephonyAudioInterruptionCoordinator"
        private const val RESUME_CALL_DELAY_MS = 600L
    }

    @Volatile
    var pausedForCall: Boolean = false
        private set

    @Volatile
    var hasAudioFocus: Boolean = false
        private set

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPhoneReceiverRegistered: Boolean = false
    private var resumeJob: Job? = null

    private val audioManager: AudioManager? by lazy {
        try {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed obtaining AudioManager: ${t.message}", t)
            null
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        try {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    val wasPlaying = isPlayingProvider()
                    AppLogger.i(TAG, "Audio focus transient loss ($focusChange). Active playback=$wasPlaying")
                    if (wasPlaying) {
                        pausedForCall = true
                        resumeJob?.cancel()
                        onPauseRequested()
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    AppLogger.i(TAG, "Audio focus regained (AUDIOFOCUS_GAIN). pausedForCall=$pausedForCall")
                    hasAudioFocus = true
                    if (pausedForCall && !isPlayingProvider()) {
                        triggerAutoResume("AudioFocus GAIN")
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    AppLogger.i(TAG, "Permanent audio focus loss. Clearing pausedForCall and pausing playback.")
                    pausedForCall = false
                    hasAudioFocus = false
                    resumeJob?.cancel()
                    onPauseRequested()
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error handling audio focus change: ${t.message}", t)
        }
    }

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val wasPlaying = isPlayingProvider()
                AppLogger.d(TAG, "Phone state intent received: state=$stateStr, isPlaying=$wasPlaying")

                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        if (wasPlaying) {
                            AppLogger.i(TAG, "Incoming or active call detected ($stateStr). Pausing TTS playback.")
                            pausedForCall = true
                            resumeJob?.cancel()
                            onPauseRequested()
                        }
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        AppLogger.d(TAG, "Phone call returned to IDLE. pausedForCall=$pausedForCall")
                        if (pausedForCall && !isPlayingProvider()) {
                            triggerAutoResume("Telephony IDLE")
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error handling phone state broadcast: ${t.message}", t)
            }
        }
    }

    fun start() {
        registerPhoneReceiverSafely()
    }

    fun stop() {
        abandonAudioFocus()
        unregisterPhoneReceiverSafely()
        resumeJob?.cancel()
        pausedForCall = false
    }

    fun requestAudioFocus(): Boolean {
        try {
            val am = audioManager ?: return false
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()

                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }

            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            AppLogger.d(TAG, "requestAudioFocus result=$result, granted=$hasAudioFocus")
            return hasAudioFocus
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed requesting audio focus: ${t.message}", t)
            return false
        }
    }

    fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
            hasAudioFocus = false
            AppLogger.d(TAG, "Abandoned audio focus")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Error abandoning audio focus: ${t.message}", t)
        }
    }

    fun onUserManuallyPausedOrStopped() {
        AppLogger.d(TAG, "User manually paused or stopped. Resetting pausedForCall flag.")
        pausedForCall = false
        resumeJob?.cancel()
    }

    fun triggerAutoResume(reason: String) {
        resumeJob?.cancel()
        resumeJob = scope.launch(Dispatchers.Main) {
            try {
                AppLogger.i(TAG, "Scheduling TTS auto-resume in ${RESUME_CALL_DELAY_MS}ms (reason: $reason)...")
                delay(RESUME_CALL_DELAY_MS)
                if (pausedForCall && !isPlayingProvider()) {
                    pausedForCall = false
                    AppLogger.i(TAG, "Executing auto-resume of TTS playback after call interruption ($reason)")
                    onResumeRequested()
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Error during delayed auto-resume: ${t.message}", t)
            }
        }
    }

    // Testing hook to simulate call state or focus changes in unit tests
    internal fun simulateCallInterruption(active: Boolean) {
        if (active) {
            if (isPlayingProvider()) {
                pausedForCall = true
                onPauseRequested()
            }
        } else {
            if (pausedForCall) {
                pausedForCall = false
                onResumeRequested()
            }
        }
    }

    private fun registerPhoneReceiverSafely() {
        if (isPhoneReceiverRegistered) return
        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                phoneStateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isPhoneReceiverRegistered = true
            AppLogger.i(TAG, "Registered phone state receiver successfully")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Telephony receiver registration skipped (${t.message}). AudioFocus handles call interruptions.")
        }
    }

    private fun unregisterPhoneReceiverSafely() {
        if (!isPhoneReceiverRegistered) return
        try {
            context.unregisterReceiver(phoneStateReceiver)
            isPhoneReceiverRegistered = false
            AppLogger.d(TAG, "Unregistered phone state receiver")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Error unregistering phone receiver: ${t.message}", t)
        }
    }
}
