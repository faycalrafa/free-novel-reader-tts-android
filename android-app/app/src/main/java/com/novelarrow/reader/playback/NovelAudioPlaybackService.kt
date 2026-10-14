package com.novelarrow.reader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.Voice
import android.util.Log
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
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    interface PlaybackClient {
        fun onPlay()
        fun onPause()
        fun onStop()
        fun onNextParagraph()
        fun onPrevParagraph()
        fun onNextChapter()
        fun onPrevChapter()
        fun onSeekToParagraph(index: Int)
    }

    inner class LocalBinder : Binder() {
        fun getService(): NovelAudioPlaybackService = this@NovelAudioPlaybackService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var settingsRepository: SettingsRepository

    private var ttsManager: TtsManager? = null
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundServiceStarted = false
    private var currentMediaMetadata: MediaMetadata = MediaMetadata.EMPTY

    private var currentSource: PlaybackSource = PlaybackSource.NONE
    private var currentNovelId: String? = null
    private var currentNovelTitle: String = "NovelArrow Reader"
    private var currentChapterId: String? = null
    private var currentChapterTitle: String = "No chapter loaded"
    private var currentProgressText: String = "Ready"

    private var currentChapterIndex: Int = 0
    private var totalChaptersCount: Int = 0
    private var currentParagraphIndex: Int = 0
    private var totalParagraphsCount: Int = 0
    private var currentParagraphs: List<Paragraph> = emptyList()
    private var autoNextChapterEnabled: Boolean = true

    private var currentSpeechRate: Float = 1.0f
    private var currentSpeechPitch: Float = 1.0f
    private var currentVoiceName: String? = null

    private var virtualDurationMs: Long = 0L
    private var virtualPositionMs: Long = 0L

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

        private val _isPlayingState = MutableStateFlow(false)
        val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

        private val _currentInfo = MutableStateFlow("Idle")
        val currentInfo: StateFlow<String> = _currentInfo.asStateFlow()

        private val _playbackProgressState = MutableStateFlow(PlaybackProgress())
        val playbackProgressState: StateFlow<PlaybackProgress> = _playbackProgressState.asStateFlow()

        private val _availableVoicesState = MutableStateFlow<List<Voice>>(emptyList())
        val availableVoicesState: StateFlow<List<Voice>> = _availableVoicesState.asStateFlow()

        @Volatile
        @Deprecated("ViewModels should observe playbackProgressState and send commands rather than registering directly")
        var activeClient: PlaybackClient? = null

        var onPlayRequested: (() -> Unit)? = null
        var onPauseRequested: (() -> Unit)? = null
        var onStopRequested: (() -> Unit)? = null
        var onNextParaRequested: (() -> Unit)? = null
        var onPrevParaRequested: (() -> Unit)? = null
        var onNextChapRequested: (() -> Unit)? = null
        var onPrevChapRequested: (() -> Unit)? = null
        var onSeekToChapterRequested: ((Int) -> Unit)? = null
        var onSeekToParaRequested: ((Int) -> Unit)? = null

        fun dispatchPlay() {
            _isPlayingState.value = true
            val service = instance
            if (service != null) {
                service.resumePlayback()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onPlay() ?: onPlayRequested?.invoke()
                }
            }
        }

        fun dispatchPause() {
            _isPlayingState.value = false
            val service = instance
            if (service != null) {
                service.pausePlayback()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onPause() ?: onPauseRequested?.invoke()
                }
            }
        }

        fun dispatchStop() {
            _isPlayingState.value = false
            val service = instance
            if (service != null) {
                service.stopPlayback()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onStop() ?: onStopRequested?.invoke()
                }
            }
        }

        fun dispatchNextPara() {
            val service = instance
            if (service != null) {
                service.nextParagraph()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onNextParagraph() ?: onNextParaRequested?.invoke()
                }
            }
        }

        fun dispatchPrevPara() {
            val service = instance
            if (service != null) {
                service.previousParagraph()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onPrevParagraph() ?: onPrevParaRequested?.invoke()
                }
            }
        }

        fun dispatchNextChap() {
            val service = instance
            if (service != null) {
                service.advanceToNextChapter()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onNextChapter() ?: onNextChapRequested?.invoke()
                }
            }
        }

        fun dispatchPrevChap() {
            val service = instance
            if (service != null) {
                service.previousChapter()
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onPrevChapter() ?: onPrevChapRequested?.invoke()
                }
            }
        }

        fun dispatchSeekToPara(targetPara: Int) {
            val service = instance
            if (service != null) {
                service.seekToParagraph(targetPara)
            } else {
                Handler(Looper.getMainLooper()).post {
                    activeClient?.onSeekToParagraph(targetPara) ?: onSeekToParaRequested?.invoke(targetPara)
                }
            }
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
            val service = instance
            if (service != null) {
                service.startReaderChapterPlayback(
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
            } else {
                val intent = Intent(context, NovelAudioPlaybackService::class.java).apply {
                    action = ACTION_PLAY
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Once instance initializes, it will be invoked or state observed
                Handler(Looper.getMainLooper()).postDelayed({
                    instance?.startReaderChapterPlayback(
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
                }, 300)
            }
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
            val service = instance
            if (service != null) {
                service.startBrowserPlayback(
                    chapterTitle = chapterTitle,
                    paragraphs = paragraphs,
                    startParagraphIndex = startParagraphIndex,
                    rate = rate,
                    pitch = pitch,
                    voiceName = voiceName
                )
            } else {
                val intent = Intent(context, NovelAudioPlaybackService::class.java).apply {
                    action = ACTION_PLAY
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    instance?.startBrowserPlayback(
                        chapterTitle = chapterTitle,
                        paragraphs = paragraphs,
                        startParagraphIndex = startParagraphIndex,
                        rate = rate,
                        pitch = pitch,
                        voiceName = voiceName
                    )
                }, 300)
            }
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
            val service = instance
            if (service != null) {
                service.stopServicePlayback()
            } else {
                val intent = Intent(context, NovelAudioPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        libraryRepository = LibraryRepository(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()

        // Initialize TtsManager owned strictly by the foreground service
        ttsManager = TtsManager(applicationContext) { success ->
            if (success) {
                val voices = ttsManager?.getAvailableVoices() ?: emptyList()
                _availableVoicesState.value = voices
                _playbackProgressState.update { it.copy(availableVoices = voices) }
                applyTtsSettings()
            }
        }.apply {
            onParagraphStart = { index ->
                handleParagraphStart(index)
            }
            onParagraphDone = { index ->
                handleParagraphDone(index)
            }
            onError = { index, error ->
                handleParagraphError(index, error)
            }
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

        // TASK 2: Direct dispatchPause() on audio focus loss or headphones disconnection
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
            override fun isPlaying(): Boolean = _isPlayingState.value
            override fun getPlayWhenReady(): Boolean = _isPlayingState.value
            override fun getPlaybackState(): Int = if (_isPlayingState.value) Player.STATE_READY else super.getPlaybackState()

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
                stopServicePlayback()
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
                        if (_isPlayingState.value) dispatchPause() else dispatchPlay()
                    }
                    ACTION_PREV_PARA -> dispatchPrevPara()
                    ACTION_NEXT_PARA -> dispatchNextPara()
                    ACTION_PREV_CHAP -> dispatchPrevChap()
                    ACTION_NEXT_CHAP -> dispatchNextChap()
                    ACTION_STOP -> {
                        dispatchStop()
                        stopServicePlayback()
                    }
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

        // Sync with settings repository
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSpeechRate = settings.speechRate
                currentSpeechPitch = settings.speechPitch
                currentVoiceName = settings.selectedVoiceName
                applyTtsSettings()
            }
        }
    }

    private fun handleSeekTo(positionMs: Long) {
        if (totalParagraphsCount > 0 && virtualDurationMs > 0) {
            val targetPara = (positionMs.toFloat() / virtualDurationMs.toFloat() * totalParagraphsCount).toInt()
                .coerceIn(0, totalParagraphsCount - 1)
            seekToParagraph(targetPara)
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
                if (_isPlayingState.value) dispatchPause() else dispatchPlay()
            }
            ACTION_PREV_PARA -> dispatchPrevPara()
            ACTION_NEXT_PARA -> dispatchNextPara()
            ACTION_PREV_CHAP -> dispatchPrevChap()
            ACTION_NEXT_CHAP -> dispatchNextChap()
            ACTION_STOP -> {
                dispatchStop()
                stopServicePlayback()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // =========================================================================
    // Core TTS Playback Coordination
    // =========================================================================

    fun startReaderChapterPlayback(
        novelId: String,
        novelTitle: String,
        chapterId: String,
        chapterTitle: String,
        chapterIndex: Int,
        totalChapters: Int,
        paragraphs: List<Paragraph>,
        startParagraphIndex: Int,
        autoNext: Boolean = true,
        rate: Float = currentSpeechRate,
        pitch: Float = currentSpeechPitch,
        voiceName: String? = currentVoiceName
    ) {
        currentSource = PlaybackSource.READER
        currentNovelId = novelId
        currentNovelTitle = novelTitle
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        currentChapterIndex = chapterIndex
        totalChaptersCount = totalChapters
        currentParagraphs = paragraphs
        totalParagraphsCount = paragraphs.size
        currentParagraphIndex = startParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        autoNextChapterEnabled = autoNext
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName

        applyTtsSettings()

        _isPlayingState.value = true
        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.READER,
                novelId = novelId,
                novelTitle = novelTitle,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                currentParagraphIndex = currentParagraphIndex,
                totalParagraphs = paragraphs.size,
                currentParagraphText = paragraphs.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }

        val progressText = if (totalChapters > 0) "Ch. ${chapterIndex + 1}/$totalChapters • ¶ ${currentParagraphIndex + 1}/${paragraphs.size}" else "Ready"
        updatePlaybackState(
            isPlaying = true,
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            paragraphProgress = progressText,
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            currentParagraph = currentParagraphIndex,
            totalParagraphs = paragraphs.size
        )

        speakCurrentParagraph()
    }

    fun startBrowserPlayback(
        chapterTitle: String,
        paragraphs: List<Paragraph>,
        startParagraphIndex: Int,
        rate: Float = currentSpeechRate,
        pitch: Float = currentSpeechPitch,
        voiceName: String? = currentVoiceName
    ) {
        currentSource = PlaybackSource.BROWSER
        currentNovelId = null
        currentNovelTitle = "Web Reading"
        currentChapterId = null
        currentChapterTitle = chapterTitle
        currentChapterIndex = 0
        totalChaptersCount = 1
        currentParagraphs = paragraphs
        totalParagraphsCount = paragraphs.size
        currentParagraphIndex = startParagraphIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        autoNextChapterEnabled = false
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName

        applyTtsSettings()

        _isPlayingState.value = true
        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                source = PlaybackSource.BROWSER,
                novelId = null,
                novelTitle = "Web Reading",
                chapterId = null,
                chapterTitle = chapterTitle,
                chapterIndex = 0,
                totalChapters = 1,
                currentParagraphIndex = currentParagraphIndex,
                totalParagraphs = paragraphs.size,
                currentParagraphText = paragraphs.getOrNull(currentParagraphIndex)?.spokenText ?: ""
            )
        }

        val progressText = "¶ ${currentParagraphIndex + 1}/${paragraphs.size}"
        updatePlaybackState(
            isPlaying = true,
            novelTitle = "Web Reading",
            chapterTitle = chapterTitle,
            paragraphProgress = progressText,
            chapterIndex = 0,
            totalChapters = 1,
            currentParagraph = currentParagraphIndex,
            totalParagraphs = paragraphs.size
        )

        speakCurrentParagraph()
    }

    private fun applyTtsSettings() {
        ttsManager?.setRate(currentSpeechRate)
        ttsManager?.setPitch(currentSpeechPitch)
        ttsManager?.setVoiceByName(currentVoiceName)
    }

    fun updateTtsSettings(rate: Float, pitch: Float, voiceName: String?) {
        currentSpeechRate = rate
        currentSpeechPitch = pitch
        currentVoiceName = voiceName
        applyTtsSettings()
    }

    private fun speakCurrentParagraph() {
        val paras = currentParagraphs
        val idx = currentParagraphIndex
        if (idx !in paras.indices) return

        val p = paras[idx]
        val spoken = p.spokenText.ifBlank { p.rawText }.trim()
        if (spoken.isBlank()) {
            handleParagraphDone(idx)
            return
        }

        ttsManager?.speak(
            index = idx,
            text = spoken,
            rate = currentSpeechRate,
            pitch = currentSpeechPitch
        )
    }

    private fun handleParagraphStart(index: Int) {
        currentParagraphIndex = index
        _isPlayingState.value = true
        _playbackProgressState.update {
            it.copy(
                isPlaying = true,
                currentParagraphIndex = index,
                currentParagraphText = currentParagraphs.getOrNull(index)?.spokenText ?: ""
            )
        }
        val progressText = if (currentSource == PlaybackSource.READER && totalChaptersCount > 0) {
            "Ch. ${currentChapterIndex + 1}/$totalChaptersCount • ¶ ${index + 1}/$totalParagraphsCount"
        } else {
            "¶ ${index + 1}/$totalParagraphsCount"
        }
        updatePlaybackState(
            isPlaying = true,
            novelTitle = currentNovelTitle,
            chapterTitle = currentChapterTitle,
            paragraphProgress = progressText,
            chapterIndex = currentChapterIndex,
            totalChapters = totalChaptersCount,
            currentParagraph = index,
            totalParagraphs = totalParagraphsCount
        )
    }

    private fun handleParagraphDone(index: Int) {
        if (index != currentParagraphIndex) return
        val total = currentParagraphs.size
        val nextIndex = index + 1

        if (currentSource == PlaybackSource.READER && currentNovelId != null) {
            val novelId = currentNovelId!!
            val chapIdx = currentChapterIndex
            serviceScope.launch(Dispatchers.IO) {
                libraryRepository.updateChapterProgress(
                    novelId = novelId,
                    chapterIndex = chapIdx,
                    paragraphIndex = index,
                    scrollOffset = 0,
                    isCompleted = nextIndex >= total
                )
            }
        }

        if (nextIndex < total) {
            if (_isPlayingState.value) {
                currentParagraphIndex = nextIndex
                _playbackProgressState.update { it.copy(currentParagraphIndex = nextIndex) }
                speakCurrentParagraph()
            }
        } else {
            if (currentSource == PlaybackSource.READER && autoNextChapterEnabled) {
                advanceToNextChapter()
            } else {
                pausePlayback()
            }
        }
    }

    private fun handleParagraphError(index: Int, error: String) {
        Log.w("NovelAudioPlayback", "TTS Error on paragraph $index: $error")
        val next = index + 1
        if (next < currentParagraphs.size && _isPlayingState.value) {
            serviceScope.launch {
                delay(120)
                if (_isPlayingState.value) {
                    currentParagraphIndex = next
                    _playbackProgressState.update { it.copy(currentParagraphIndex = next) }
                    speakCurrentParagraph()
                }
            }
        } else {
            pausePlayback()
        }
    }

    fun nextParagraph() {
        if (currentParagraphIndex + 1 < currentParagraphs.size) {
            currentParagraphIndex++
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        } else {
            if (currentSource == PlaybackSource.READER && autoNextChapterEnabled) {
                advanceToNextChapter()
            }
        }
    }

    fun previousParagraph() {
        if (currentParagraphIndex - 1 >= 0) {
            currentParagraphIndex--
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        }
    }

    fun seekToParagraph(targetPara: Int) {
        if (targetPara in currentParagraphs.indices) {
            currentParagraphIndex = targetPara
            _playbackProgressState.update { it.copy(currentParagraphIndex = currentParagraphIndex) }
            speakCurrentParagraph()
        }
    }

    fun advanceToNextChapter() {
        val novelId = currentNovelId ?: return
        serviceScope.launch(Dispatchers.IO) {
            val chapters = libraryRepository.getChapters(novelId)
            val nextIdx = currentChapterIndex + 1
            if (nextIdx in chapters.indices) {
                val nextChap = chapters[nextIdx]
                val content = libraryRepository.readChapterContent(nextChap)
                val settings = settingsRepository.getSettings()
                val parsedParas = ChapterParser.parse(
                    content = content,
                    fileName = nextChap.title,
                    contentSelector = settings.contentSelector,
                    contentSelectorEnabled = settings.contentSelectorEnabled,
                    pronunciationRules = settings.pronunciationRules,
                    pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                    skipWords = settings.skipWords,
                    skipWordsEnabled = settings.skipWordsEnabled,
                    headerSkipString = settings.headerSkipString,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipString = settings.footerSkipString,
                    footerSkipEnabled = settings.footerSkipEnabled
                )
                withContext(Dispatchers.Main) {
                    startReaderChapterPlayback(
                        novelId = novelId,
                        novelTitle = currentNovelTitle,
                        chapterId = nextChap.id,
                        chapterTitle = nextChap.title,
                        chapterIndex = nextIdx,
                        totalChapters = chapters.size,
                        paragraphs = parsedParas,
                        startParagraphIndex = 0,
                        autoNext = autoNextChapterEnabled
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    pausePlayback()
                }
            }
        }
    }

    fun previousChapter() {
        val novelId = currentNovelId ?: return
        serviceScope.launch(Dispatchers.IO) {
            val chapters = libraryRepository.getChapters(novelId)
            val prevIdx = currentChapterIndex - 1
            if (prevIdx in chapters.indices) {
                val prevChap = chapters[prevIdx]
                val content = libraryRepository.readChapterContent(prevChap)
                val settings = settingsRepository.getSettings()
                val parsedParas = ChapterParser.parse(
                    content = content,
                    fileName = prevChap.title,
                    contentSelector = settings.contentSelector,
                    contentSelectorEnabled = settings.contentSelectorEnabled,
                    pronunciationRules = settings.pronunciationRules,
                    pronunciationRulesEnabled = settings.pronunciationRulesEnabled,
                    skipWords = settings.skipWords,
                    skipWordsEnabled = settings.skipWordsEnabled,
                    headerSkipString = settings.headerSkipString,
                    headerSkipEnabled = settings.headerSkipEnabled,
                    footerSkipString = settings.footerSkipString,
                    footerSkipEnabled = settings.footerSkipEnabled
                )
                withContext(Dispatchers.Main) {
                    startReaderChapterPlayback(
                        novelId = novelId,
                        novelTitle = currentNovelTitle,
                        chapterId = prevChap.id,
                        chapterTitle = prevChap.title,
                        chapterIndex = prevIdx,
                        totalChapters = chapters.size,
                        paragraphs = parsedParas,
                        startParagraphIndex = 0,
                        autoNext = autoNextChapterEnabled
                    )
                }
            }
        }
    }

    fun pausePlayback() {
        ttsManager?.stop()
        _isPlayingState.value = false
        _playbackProgressState.update { it.copy(isPlaying = false) }
        updatePlaybackState(
            isPlaying = false,
            novelTitle = currentNovelTitle,
            chapterTitle = currentChapterTitle,
            paragraphProgress = currentProgressText,
            chapterIndex = currentChapterIndex,
            totalChapters = totalChaptersCount,
            currentParagraph = currentParagraphIndex,
            totalParagraphs = totalParagraphsCount
        )
    }

    fun resumePlayback() {
        _isPlayingState.value = true
        _playbackProgressState.update { it.copy(isPlaying = true) }
        updatePlaybackState(
            isPlaying = true,
            novelTitle = currentNovelTitle,
            chapterTitle = currentChapterTitle,
            paragraphProgress = currentProgressText,
            chapterIndex = currentChapterIndex,
            totalChapters = totalChaptersCount,
            currentParagraph = currentParagraphIndex,
            totalParagraphs = totalParagraphsCount
        )
        speakCurrentParagraph()
    }

    fun stopPlayback() {
        pausePlayback()
        stopServicePlayback()
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                updatePlaybackState(
                    isPlaying = isPlaying,
                    novelTitle = novelTitle,
                    chapterTitle = chapterTitle,
                    paragraphProgress = paragraphProgress,
                    chapterIndex = chapterIndex,
                    totalChapters = totalChapters,
                    currentParagraph = currentParagraph,
                    totalParagraphs = totalParagraphs
                )
            }
            return
        }

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

        _isPlayingState.value = isPlaying
        _currentInfo.value = "$novelTitle • $chapterTitle"

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
        _isPlayingState.value = false
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
        if (!_isPlayingState.value) {
            stopServicePlayback()
        }
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        ttsManager?.shutdown()
        ttsManager = null
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
