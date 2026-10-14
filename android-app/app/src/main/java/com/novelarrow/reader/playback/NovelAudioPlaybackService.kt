package com.novelarrow.reader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
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
import com.novelarrow.reader.data.model.Paragraph
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackSource {
    NONE,
    READER,
    BROWSER
}

data class PlaybackProgress(
    val isPlaying: Boolean = false,
    val source: PlaybackSource = PlaybackSource.NONE,
    val novelId: String? = null,
    val novelTitle: String = "NovelArrow Reader",
    val chapterId: String? = null,
    val chapterTitle: String = "No chapter loaded",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val currentParagraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentParagraphText: String = "",
    val availableVoices: List<Voice> = emptyList()
)

class NovelAudioPlaybackService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        fun getService(): NovelAudioPlaybackService = this@NovelAudioPlaybackService
    }

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundServiceStarted = false
    private var currentMediaMetadata: MediaMetadata = MediaMetadata.EMPTY

    private var currentNovelTitle: String = "NovelArrow Reader"
    private var currentChapterTitle: String = "No chapter loaded"
    private var currentProgressText: String = "Ready"
    private var currentChapterIndex: Int = 0
    private var totalChaptersCount: Int = 0
    private var currentParagraphIndex: Int = 0
    private var totalParagraphsCount: Int = 0

    private var virtualDurationMs: Long = 0L
    private var virtualPositionMs: Long = 0L

    // Receiver to prevent loudspeaker blaring when headphones/Bluetooth disconnect
    private var noisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                dispatchPause()
            }
        }
    }

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
        const val EXTRA_CHAPTER_INDEX = "extra_chapter_index"
        const val EXTRA_TOTAL_CHAPTERS = "extra_total_chapters"
        const val EXTRA_PROGRESS_CURRENT = "extra_progress_current"
        const val EXTRA_PROGRESS_TOTAL = "extra_progress_total"

        @Volatile
        var instance: NovelAudioPlaybackService? = null
            private set

        val isPlayingState: StateFlow<Boolean>
            get() = AudioPlaybackCoordinator.isPlayingState

        val currentInfo: StateFlow<String>
            get() = AudioPlaybackCoordinator.currentInfo

        val playbackProgressState: StateFlow<PlaybackProgress>
            get() = AudioPlaybackCoordinator.playbackProgressState

        val availableVoicesState: StateFlow<List<Voice>>
            get() = AudioPlaybackCoordinator.availableVoicesState

        fun dispatchPlay() {
            AudioPlaybackCoordinator.play()
        }

        fun dispatchPause() {
            AudioPlaybackCoordinator.pause()
        }

        fun dispatchStop() {
            AudioPlaybackCoordinator.stop()
        }

        fun dispatchNextPara() {
            AudioPlaybackCoordinator.nextParagraph()
        }

        fun dispatchPrevPara() {
            AudioPlaybackCoordinator.previousParagraph()
        }

        fun dispatchNextChap() {
            AudioPlaybackCoordinator.nextChapter()
        }

        fun dispatchPrevChap() {
            AudioPlaybackCoordinator.previousChapter()
        }

        fun dispatchSeekToPara(targetPara: Int) {
            AudioPlaybackCoordinator.seekToParagraph(targetPara)
        }

        fun playReaderChapter(
            context: Context,
            novelId: String,
            novelTitle: String,
            chapterId: String,
            chapterTitle: String,
            chapterIndex: Int,
            totalChapters: Int,
            paragraphs: List<Paragraph>,
            startParagraphIndex: Int,
            autoNext: Boolean = true,
            rate: Float = 1.0f,
            pitch: Float = 1.0f,
            voiceName: String? = null
        ) {
            AudioPlaybackCoordinator.playReaderChapter(
                context = context,
                novelId = novelId,
                novelTitle = novelTitle,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                paragraphs = paragraphs,
                startParagraphIndex = startParagraphIndex,
                autoNext = autoNext,
                rate = rate,
                pitch = pitch,
                voiceName = voiceName
            )
        }

        fun playBrowserParagraphs(
            context: Context,
            chapterTitle: String,
            paragraphs: List<Paragraph>,
            startParagraphIndex: Int,
            rate: Float = 1.0f,
            pitch: Float = 1.0f,
            voiceName: String? = null
        ) {
            AudioPlaybackCoordinator.playBrowserParagraphs(
                context = context,
                chapterTitle = chapterTitle,
                paragraphs = paragraphs,
                startParagraphIndex = startParagraphIndex,
                rate = rate,
                pitch = pitch,
                voiceName = voiceName
            )
        }

        fun startOrUpdateService(
            context: Context,
            isPlaying: Boolean,
            novelTitle: String,
            chapterTitle: String,
            progressText: String,
            currentChapterIndex: Int = 0,
            totalChapters: Int = 0,
            currentParagraph: Int = 0,
            totalParagraphs: Int = 0
        ) {
            val service = instance
            if (service != null) {
                service.updatePlaybackState(
                    isPlaying = isPlaying,
                    novelTitle = novelTitle,
                    chapterTitle = chapterTitle,
                    paragraphProgress = progressText,
                    chapterIndex = currentChapterIndex,
                    totalChapters = totalChapters,
                    currentParagraph = currentParagraph,
                    totalParagraphs = totalParagraphs
                )
            } else {
                val intent = Intent(context, NovelAudioPlaybackService::class.java).apply {
                    action = ACTION_UPDATE_STATE
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                    putExtra(EXTRA_NOVEL_TITLE, novelTitle)
                    putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                    putExtra(EXTRA_PROGRESS_TEXT, progressText)
                    putExtra(EXTRA_CHAPTER_INDEX, currentChapterIndex)
                    putExtra(EXTRA_TOTAL_CHAPTERS, totalChapters)
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

        fun stopService(context: Context) {
            AudioPlaybackCoordinator.stop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        AudioPlaybackCoordinator.initialize(applicationContext)
        AudioPlaybackCoordinator.attachService(this)

        // Register broadcast receiver for headphone unplugs
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Base ExoPlayer configured for speech audio attributes & noisy headset disconnects
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

        // Direct dispatchPause() on audio focus loss or headphones disconnection
        basePlayer.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    when (reason) {
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                            dispatchPause()
                        }
                    }
                }
            }
        })

        // Silence loop maintains the media audio pipeline for notification and lockscreen controls
        val silenceSource = SilenceMediaSource(3_600_000_000L) // 1 hour silence loop
        basePlayer.setMediaSource(silenceSource)
        basePlayer.repeatMode = Player.REPEAT_MODE_ALL
        basePlayer.prepare()

        val forwardingPlayer = object : ForwardingPlayer(basePlayer) {
            override fun isPlaying(): Boolean = AudioPlaybackCoordinator.isPlayingState.value
            override fun getPlayWhenReady(): Boolean = AudioPlaybackCoordinator.isPlayingState.value
            override fun getPlaybackState(): Int = if (AudioPlaybackCoordinator.isPlayingState.value) Player.STATE_READY else super.getPlaybackState()

            override fun getCurrentMediaItem(): androidx.media3.common.MediaItem? {
                return androidx.media3.common.MediaItem.Builder()
                    .setMediaId("novel_tts_item")
                    .setMediaMetadata(currentMediaMetadata)
                    .build()
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun play() {
                super.play()
                dispatchPlay()
            }

            override fun pause() {
                super.pause()
                dispatchPause()
            }

            override fun seekToNext() = dispatchNextChap()
            override fun seekToPrevious() = dispatchPrevChap()
            override fun seekToNextMediaItem() = dispatchNextChap()
            override fun seekToPreviousMediaItem() = dispatchPrevChap()
            override fun seekForward() = dispatchNextChap()
            override fun seekBack() = dispatchPrevChap()

            override fun getDuration(): Long = if (virtualDurationMs > 0) virtualDurationMs else super.getDuration()
            override fun getCurrentPosition(): Long = if (virtualDurationMs > 0) virtualPositionMs else super.getCurrentPosition()

            override fun seekTo(positionMs: Long) = handleSeekTo(positionMs)
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) = handleSeekTo(positionMs)

            override fun stop() {
                super.stop()
                dispatchStop()
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_STOP)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_STOP -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getMediaMetadata(): MediaMetadata = currentMediaMetadata
            override fun getPlaylistMetadata(): MediaMetadata = currentMediaMetadata
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
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(ACTION_PLAY, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_PAUSE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_PLAY_PAUSE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_PREV_PARA, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_NEXT_PARA, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_PREV_CHAP, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_NEXT_CHAP, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_STOP, android.os.Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: android.os.Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_PLAY -> dispatchPlay()
                    ACTION_PAUSE -> dispatchPause()
                    ACTION_PLAY_PAUSE -> {
                        if (AudioPlaybackCoordinator.isPlayingState.value) dispatchPause() else dispatchPlay()
                    }
                    ACTION_PREV_PARA -> dispatchPrevPara()
                    ACTION_NEXT_PARA -> dispatchNextPara()
                    ACTION_PREV_CHAP -> dispatchPrevChap()
                    ACTION_NEXT_CHAP -> dispatchNextChap()
                    ACTION_STOP -> dispatchStop()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(sessionCallback)
            .build()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovelArrow::PlaybackWakeLock")
    }

    private fun handleSeekTo(positionMs: Long) {
        if (totalParagraphsCount > 0 && virtualDurationMs > 0) {
            val targetPara = (positionMs.toFloat() / virtualDurationMs.toFloat() * totalParagraphsCount).toInt()
                .coerceIn(0, totalParagraphsCount - 1)
            dispatchSeekToPara(targetPara)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
                val chapIdx = intent.getIntExtra(EXTRA_CHAPTER_INDEX, currentChapterIndex)
                val totalChaps = intent.getIntExtra(EXTRA_TOTAL_CHAPTERS, totalChaptersCount)
                val curPara = intent.getIntExtra(EXTRA_PROGRESS_CURRENT, currentParagraphIndex)
                val totalParas = intent.getIntExtra(EXTRA_PROGRESS_TOTAL, totalParagraphsCount)
                updatePlaybackState(
                    isPlaying = isPlaying,
                    novelTitle = novelTitle,
                    chapterTitle = chapterTitle,
                    paragraphProgress = progressText,
                    chapterIndex = chapIdx,
                    totalChapters = totalChaps,
                    currentParagraph = curPara,
                    totalParagraphs = totalParas
                )
            }
            ACTION_PLAY -> dispatchPlay()
            ACTION_PAUSE -> dispatchPause()
            ACTION_PLAY_PAUSE -> {
                if (AudioPlaybackCoordinator.isPlayingState.value) dispatchPause() else dispatchPlay()
            }
            ACTION_PREV_PARA -> dispatchPrevPara()
            ACTION_NEXT_PARA -> dispatchNextPara()
            ACTION_PREV_CHAP -> dispatchPrevChap()
            ACTION_NEXT_CHAP -> dispatchNextChap()
            ACTION_STOP -> dispatchStop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun updatePlaybackState(
        isPlaying: Boolean,
        novelTitle: String,
        chapterTitle: String,
        paragraphProgress: String,
        chapterIndex: Int = currentChapterIndex,
        totalChapters: Int = totalChaptersCount,
        currentParagraph: Int = currentParagraphIndex,
        totalParagraphs: Int = totalParagraphsCount
    ) {
        currentNovelTitle = novelTitle
        currentChapterTitle = chapterTitle
        currentProgressText = paragraphProgress
        currentChapterIndex = chapterIndex
        totalChaptersCount = totalChapters
        currentParagraphIndex = currentParagraph
        totalParagraphsCount = totalParagraphs

        val PARAGRAPH_UNIT_MS = 1_000L
        val safeParas = totalParagraphs.coerceAtLeast(1)
        virtualDurationMs = safeParas.toLong() * PARAGRAPH_UNIT_MS
        virtualPositionMs = (currentParagraph.coerceIn(0, safeParas).toLong() * PARAGRAPH_UNIT_MS).coerceIn(0L, virtualDurationMs)

        val metadata = MediaMetadata.Builder()
            .setTitle(chapterTitle.ifBlank { "NovelArrow Reader" })
            .setArtist(novelTitle.ifBlank { "Text-to-Speech" })
            .setAlbumTitle("NovelArrow Reader")
            .setDisplayTitle(chapterTitle.ifBlank { "NovelArrow Reader" })
            .setSubtitle(paragraphProgress)
            .build()
        currentMediaMetadata = metadata
        try {
            exoPlayer?.setPlaylistMetadata(metadata)
        } catch (_: Exception) {}

        val notification = buildForegroundNotification(novelTitle, chapterTitle, paragraphProgress, isPlaying)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isPlaying) {
            acquireWakeLock()

            if (exoPlayer?.playbackState == Player.STATE_IDLE) {
                exoPlayer?.prepare()
            }
            try {
                exoPlayer?.seekTo(virtualPositionMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (exoPlayer?.playWhenReady != true) {
                exoPlayer?.playWhenReady = true
            }
            if (exoPlayer?.isPlaying != true) {
                exoPlayer?.play()
            }

            if (!isForegroundServiceStarted) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    isForegroundServiceStarted = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                manager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            releaseWakeLock()

            try {
                exoPlayer?.seekTo(virtualPositionMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (exoPlayer?.playWhenReady != false) {
                exoPlayer?.playWhenReady = false
            }
            if (exoPlayer?.isPlaying == true) {
                exoPlayer?.pause()
            }

            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun stopServicePlayback() {
        releaseWakeLock()
        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.pause()
        }
        if (isForegroundServiceStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundServiceStarted = false
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        stopSelf()
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
            .setProgress(
                totalParagraphsCount.coerceAtLeast(1),
                (currentParagraphIndex + 1).coerceIn(0, totalParagraphsCount.coerceAtLeast(1)),
                false
            )
            .addAction(android.R.drawable.ic_media_previous, "Prev Chap", prevChapPendingIntent)
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "Next Chap", nextChapPendingIntent)
            .addAction(android.R.drawable.ic_media_rew, "Prev ¶", prevParaPendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "Next ¶", nextParaPendingIntent)

        mediaSession?.let { session ->
            val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
            builder.setStyle(mediaStyle)
        }

        return builder.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(4 * 60 * 60 * 1000L)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!AudioPlaybackCoordinator.isPlayingState.value) {
            stopServicePlayback()
        }
    }

    override fun onDestroy() {
        AudioPlaybackCoordinator.detachService(this)
        if (noisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
                noisyReceiverRegistered = false
            } catch (_: Exception) {}
        }
        instance = null
        releaseWakeLock()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        isForegroundServiceStarted = false
        super.onDestroy()
    }
}
