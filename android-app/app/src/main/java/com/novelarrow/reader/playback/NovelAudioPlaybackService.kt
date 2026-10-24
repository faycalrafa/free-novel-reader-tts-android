package com.novelarrow.reader.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.flow.StateFlow
import java.io.File

import android.speech.tts.Voice

enum class PlaybackSource {
    NONE,
    READER,
    BROWSER,
    AUDIO_FILE
}

data class PlaybackProgress(
    val isPlaying: Boolean = false,
    val source: PlaybackSource = PlaybackSource.NONE,
    val novelId: String? = null,
    val novelTitle: String = "BookReader",
    val chapterId: String? = null,
    val chapterTitle: String = "No chapter loaded",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val currentParagraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentParagraphText: String = "",
    val availableVoices: List<Voice> = emptyList(),
    val audioFilePositionMs: Long = 0L,
    val audioFileDurationMs: Long = 0L
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NovelAudioPlaybackService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        fun getService(): NovelAudioPlaybackService = this@NovelAudioPlaybackService
    }

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentMediaMetadata: MediaMetadata = MediaMetadata.EMPTY
    private var silenceMediaSource: SilenceMediaSource? = null

    private val audioFileDelegate by lazy {
        ExoAudioFileDelegate(
            context = this,
            playerProvider = { exoPlayer },
            silenceSourceProvider = { silenceMediaSource },
            acquireWakeLock = { acquireWakeLock() },
            releaseWakeLock = { releaseWakeLock() },
            onMetadataUpdated = { currentMediaMetadata = it }
        )
    }

    private val stateCoordinator by lazy {
        ServicePlaybackStateCoordinator(
            service = this,
            playerProvider = { exoPlayer },
            mediaSessionProvider = { mediaSession },
            acquireWakeLock = { acquireWakeLock() },
            releaseWakeLock = { releaseWakeLock() },
            onMetadataUpdated = { currentMediaMetadata = it }
        )
    }

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

        const val ACTION_EDIT_CSS_SELECTOR = "com.novelarrow.reader.EDIT_CSS_SELECTOR"
        const val EXTRA_OPEN_CSS_SELECTOR = "open_css_selector"

        @Volatile
        var instance: NovelAudioPlaybackService? = null
            private set

        val isPlayingState: StateFlow<Boolean>
            get() = AudioPlaybackCoordinator.isPlayingState

        val currentInfo: StateFlow<String>
            get() = AudioPlaybackCoordinator.currentInfo

        val playbackProgressState: StateFlow<PlaybackProgress>
            get() = AudioPlaybackCoordinator.playbackProgressState

        val availableVoicesState: StateFlow<List<android.speech.tts.Voice>>
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
            voiceName: String? = null,
            isPreMerged: Boolean = false
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
                voiceName = voiceName,
                isPreMerged = isPreMerged
            )
        }

        fun playBrowserParagraphs(
            context: Context,
            chapterTitle: String,
            paragraphs: List<Paragraph>,
            startParagraphIndex: Int,
            rate: Float = 1.0f,
            pitch: Float = 1.0f,
            voiceName: String? = null,
            isPreMerged: Boolean = false
        ) {
            AudioPlaybackCoordinator.playBrowserParagraphs(
                context = context,
                chapterTitle = chapterTitle,
                paragraphs = paragraphs,
                startParagraphIndex = startParagraphIndex,
                rate = rate,
                pitch = pitch,
                voiceName = voiceName,
                isPreMerged = isPreMerged
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
                    AppLogger.e("NovelAudioPlaybackService", "Error starting/updating service: ${e.message}", e)
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
        PlaybackNotificationBuilder.createNotificationChannel(this)
        AudioPlaybackCoordinator.attachService(this)

        if (!noisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            noisyReceiverRegistered = true
        }

        val basePlayer = ExoPlayer.Builder(this).build()
        exoPlayer = basePlayer

        basePlayer.addListener(
            MediaSessionPlayerDelegate.createPlayerListener(
                onFocusOrNoisyPause = { dispatchPause() },
                onAudioFileCompleted = { AudioPlaybackCoordinator.onAudioFileCompleted() },
                onExoError = { releaseWakeLock() }
            )
        )

        val silenceSource = SilenceMediaSource(3_600_000_000L)
        silenceMediaSource = silenceSource
        basePlayer.setMediaSource(silenceSource)
        basePlayer.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        basePlayer.prepare()

        val forwardingPlayer = MediaSessionPlayerDelegate.createForwardingPlayer(
            context = this,
            basePlayer = basePlayer,
            currentMetadataProvider = { currentMediaMetadata },
            virtualDurationMsProvider = { stateCoordinator.virtualDurationMs },
            virtualPositionMsProvider = { stateCoordinator.virtualPositionMs },
            onSeekToVirtual = { handleSeekTo(it) },
            dispatchPlay = { dispatchPlay() },
            dispatchPause = { dispatchPause() },
            dispatchStop = { dispatchStop() },
            dispatchNextChap = { dispatchNextChap() },
            dispatchPrevChap = { dispatchPrevChap() }
        )

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCallback = MediaSessionPlayerDelegate.createSessionCallback(
            context = this,
            dispatchPlay = { dispatchPlay() },
            dispatchPause = { dispatchPause() },
            dispatchPrevPara = { dispatchPrevPara() },
            dispatchNextPara = { dispatchNextPara() },
            dispatchPrevChap = { dispatchPrevChap() },
            dispatchNextChap = { dispatchNextChap() },
            dispatchStop = { dispatchStop() }
        )

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(sessionCallback)
            .build()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BookReader::PlaybackWakeLock")
    }

    private fun handleSeekTo(positionMs: Long) {
        val total = stateCoordinator.totalParagraphsCount
        val vDur = stateCoordinator.virtualDurationMs
        if (total > 0 && vDur > 0) {
            val targetPara = (positionMs.toFloat() / vDur.toFloat() * total).toInt()
                .coerceIn(0, total - 1)
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
                val novelTitle = intent.getStringExtra(EXTRA_NOVEL_TITLE) ?: stateCoordinator.currentNovelTitle
                val chapterTitle = intent.getStringExtra(EXTRA_CHAPTER_TITLE) ?: stateCoordinator.currentChapterTitle
                val progressText = intent.getStringExtra(EXTRA_PROGRESS_TEXT) ?: stateCoordinator.currentProgressText
                val chapIdx = intent.getIntExtra(EXTRA_CHAPTER_INDEX, stateCoordinator.currentChapterIndex)
                val totalChaps = intent.getIntExtra(EXTRA_TOTAL_CHAPTERS, stateCoordinator.totalChaptersCount)
                val curPara = intent.getIntExtra(EXTRA_PROGRESS_CURRENT, stateCoordinator.currentParagraphIndex)
                val totalParas = intent.getIntExtra(EXTRA_PROGRESS_TOTAL, stateCoordinator.totalParagraphsCount)
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
        chapterIndex: Int = stateCoordinator.currentChapterIndex,
        totalChapters: Int = stateCoordinator.totalChaptersCount,
        currentParagraph: Int = stateCoordinator.currentParagraphIndex,
        totalParagraphs: Int = stateCoordinator.totalParagraphsCount
    ) = stateCoordinator.updatePlaybackState(
        isPlaying = isPlaying,
        novelTitle = novelTitle,
        chapterTitle = chapterTitle,
        paragraphProgress = paragraphProgress,
        chapterIndex = chapterIndex,
        totalChapters = totalChapters,
        currentParagraph = currentParagraph,
        totalParagraphs = totalParagraphs
    )

    fun playAudioFile(file: File) = audioFileDelegate.playAudioFile(file)
    fun resumeAudioFile() = audioFileDelegate.resumeAudioFile()
    fun pauseAudioFile() = audioFileDelegate.pauseAudioFile()
    fun stopAudioFile() = audioFileDelegate.stopAudioFile()
    fun getAudioFilePositionMs(): Long = audioFileDelegate.getAudioFilePositionMs()
    fun getAudioFileDurationMs(): Long = audioFileDelegate.getAudioFileDurationMs()
    fun seekAudioFile(positionMs: Long) = audioFileDelegate.seekAudioFile(positionMs)

    fun stopServicePlayback() = stateCoordinator.stopServicePlayback()

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
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
        stateCoordinator.isForegroundServiceStarted = false
        super.onDestroy()
    }
}
