package com.novelarrow.reader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NovelAudioPlaybackService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        fun getService(): NovelAudioPlaybackService = this@NovelAudioPlaybackService
    }

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var currentNovelTitle: String = "NovelArrow Reader"
    private var currentChapterTitle: String = "No chapter loaded"
    private var currentProgressText: String = "Ready"

    companion object {
        const val CHANNEL_ID = "novel_tts_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_STATE = "com.novelarrow.reader.UPDATE_STATE"
        const val ACTION_PLAY = "com.novelarrow.reader.PLAY"
        const val ACTION_PAUSE = "com.novelarrow.reader.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.novelarrow.reader.PLAY_PAUSE"
        const val ACTION_PREV_PARA = "com.novelarrow.reader.PREV_PARA"
        const val ACTION_NEXT_PARA = "com.novelarrow.reader.NEXT_PARA"
        const val ACTION_PREV_CHAP = "com.novelarrow.reader.PREV_CHAP"
        const val ACTION_NEXT_CHAP = "com.novelarrow.reader.NEXT_CHAP"
        const val ACTION_STOP = "com.novelarrow.reader.STOP"

        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_NOVEL_TITLE = "extra_novel_title"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"
        const val EXTRA_PROGRESS_TEXT = "extra_progress_text"
        const val EXTRA_PROGRESS_CURRENT = "extra_progress_current"
        const val EXTRA_PROGRESS_TOTAL = "extra_progress_total"

        // State shared with UI
        private val _isPlayingState = MutableStateFlow(false)
        val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

        private val _currentInfo = MutableStateFlow("Idle")
        val currentInfo: StateFlow<String> = _currentInfo.asStateFlow()

        // Callbacks to ReaderViewModel coordinator
        var onPlayRequested: (() -> Unit)? = null
        var onPauseRequested: (() -> Unit)? = null
        var onStopRequested: (() -> Unit)? = null
        var onNextParaRequested: (() -> Unit)? = null
        var onPrevParaRequested: (() -> Unit)? = null
        var onNextChapRequested: (() -> Unit)? = null
        var onPrevChapRequested: (() -> Unit)? = null

        fun startOrUpdateService(
            context: Context,
            isPlaying: Boolean,
            novelTitle: String,
            chapterTitle: String,
            progressText: String,
            currentParagraph: Int = 0,
            totalParagraphs: Int = 0
        ) {
            val intent = Intent(context, NovelAudioPlaybackService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_NOVEL_TITLE, novelTitle)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                putExtra(EXTRA_PROGRESS_TEXT, progressText)
                putExtra(EXTRA_PROGRESS_CURRENT, currentParagraph)
                putExtra(EXTRA_PROGRESS_TOTAL, totalParagraphs)
            }
            try {
                if (isPlaying) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val basePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = basePlayer

        // SilenceMediaSource keeps an active audio pipeline for system media controls & earbud key events
        val silenceSource = SilenceMediaSource(3_600_000_000L) // 1 hour silence
        basePlayer.setMediaSource(silenceSource)
        basePlayer.repeatMode = Player.REPEAT_MODE_ALL
        basePlayer.prepare()

        // ForwardingPlayer delegates lockscreen & earbud button controls to our TTS callbacks
        val forwardingPlayer = object : ForwardingPlayer(basePlayer) {
            override fun play() {
                super.play()
                onPlayRequested?.invoke()
            }

            override fun pause() {
                super.pause()
                onPauseRequested?.invoke()
            }

            override fun seekToNext() {
                onNextParaRequested?.invoke()
            }

            override fun seekToPrevious() {
                onPrevParaRequested?.invoke()
            }

            override fun seekToNextMediaItem() {
                onNextChapRequested?.invoke()
            }

            override fun seekToPreviousMediaItem() {
                onPrevChapRequested?.invoke()
            }

            override fun stop() {
                super.stop()
                onStopRequested?.invoke()
            }
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCallback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: android.os.Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_PLAY -> onPlayRequested?.invoke()
                    ACTION_PAUSE -> onPauseRequested?.invoke()
                    ACTION_PLAY_PAUSE -> {
                        if (_isPlayingState.value) onPauseRequested?.invoke() else onPlayRequested?.invoke()
                    }
                    ACTION_PREV_PARA -> onPrevParaRequested?.invoke()
                    ACTION_NEXT_PARA -> onNextParaRequested?.invoke()
                    ACTION_PREV_CHAP -> onPrevChapRequested?.invoke()
                    ACTION_NEXT_CHAP -> onNextChapRequested?.invoke()
                    ACTION_STOP -> onStopRequested?.invoke()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(sessionCallback)
            .build()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovelArrow::PlaybackWakeLock")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_STATE -> {
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val novelTitle = intent.getStringExtra(EXTRA_NOVEL_TITLE) ?: currentNovelTitle
                val chapterTitle = intent.getStringExtra(EXTRA_CHAPTER_TITLE) ?: currentChapterTitle
                val progressText = intent.getStringExtra(EXTRA_PROGRESS_TEXT) ?: currentProgressText
                updatePlaybackState(isPlaying, novelTitle, chapterTitle, progressText)
            }
            ACTION_PLAY -> onPlayRequested?.invoke()
            ACTION_PAUSE -> onPauseRequested?.invoke()
            ACTION_PLAY_PAUSE -> {
                if (_isPlayingState.value) onPauseRequested?.invoke() else onPlayRequested?.invoke()
            }
            ACTION_PREV_PARA -> onPrevParaRequested?.invoke()
            ACTION_NEXT_PARA -> onNextParaRequested?.invoke()
            ACTION_PREV_CHAP -> onPrevChapRequested?.invoke()
            ACTION_NEXT_CHAP -> onNextChapRequested?.invoke()
            ACTION_STOP -> onStopRequested?.invoke()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun updatePlaybackState(
        isPlaying: Boolean,
        novelTitle: String,
        chapterTitle: String,
        paragraphProgress: String
    ) {
        currentNovelTitle = novelTitle
        currentChapterTitle = chapterTitle
        currentProgressText = paragraphProgress

        _isPlayingState.value = isPlaying
        _currentInfo.value = "$novelTitle • $chapterTitle"

        // Update MediaSession metadata for lockscreen and system media controller
        val metadata = MediaMetadata.Builder()
            .setTitle(chapterTitle.ifBlank { "NovelArrow Reader" })
            .setArtist(novelTitle.ifBlank { "Text-to-Speech" })
            .setAlbumTitle("NovelArrow Reader")
            .setDisplayTitle(chapterTitle.ifBlank { "NovelArrow Reader" })
            .setSubtitle(paragraphProgress)
            .build()

        exoPlayer?.setPlaylistMetadata(metadata)

        if (isPlaying) {
            requestAudioFocus()
            acquireWakeLock()

            if (exoPlayer?.playbackState == Player.STATE_IDLE) {
                exoPlayer?.prepare()
            }
            if (exoPlayer?.isPlaying != true) {
                exoPlayer?.play()
            }

            val notification = buildForegroundNotification(novelTitle, chapterTitle, paragraphProgress, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            releaseWakeLock()
            abandonAudioFocus()

            if (exoPlayer?.isPlaying == true) {
                exoPlayer?.pause()
            }

            val notification = buildForegroundNotification(novelTitle, chapterTitle, paragraphProgress, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(
        novelTitle: String,
        chapterTitle: String,
        progressText: String,
        isPlaying: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevChapPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, NovelAudioPlaybackService::class.java).apply { action = ACTION_PREV_CHAP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevParaPendingIntent = PendingIntent.getService(
            this, 2,
            Intent(this, NovelAudioPlaybackService::class.java).apply { action = ACTION_PREV_PARA },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            this, 3,
            Intent(this, NovelAudioPlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextParaPendingIntent = PendingIntent.getService(
            this, 4,
            Intent(this, NovelAudioPlaybackService::class.java).apply { action = ACTION_NEXT_PARA },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextChapPendingIntent = PendingIntent.getService(
            this, 5,
            Intent(this, NovelAudioPlaybackService::class.java).apply { action = ACTION_NEXT_CHAP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                playPausePendingIntent
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                playPausePendingIntent
            ).build()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(chapterTitle.ifBlank { "NovelArrow Reader" })
            .setContentText("$novelTitle  ($progressText)")
            .setSubText(progressText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Prev Chap", prevChapPendingIntent) // action 0
            .addAction(android.R.drawable.ic_media_rew, "Prev ¶", prevParaPendingIntent)          // action 1
            .addAction(playPauseAction)                                                            // action 2
            .addAction(android.R.drawable.ic_media_ff, "Next ¶", nextParaPendingIntent)          // action 3
            .addAction(android.R.drawable.ic_media_next, "Next Chap", nextChapPendingIntent)      // action 4

        mediaSession?.let { session ->
            val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(1, 2, 3) // Compact view shows: Prev ¶, Play/Pause, Next ¶
            builder.setStyle(mediaStyle)
        }

        return builder.build()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> onPauseRequested?.invoke()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onPauseRequested?.invoke()
                        AudioManager.AUDIOFOCUS_GAIN -> onPlayRequested?.invoke()
                    }
                }
                .build()
            focusRequest = request
            audioManager?.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) onPauseRequested?.invoke()
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours timeout safety
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Novel Audio Reader"
            val descriptionText = "Playback controls for novel audio text-to-speech"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        abandonAudioFocus()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
