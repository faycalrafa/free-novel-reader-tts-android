package com.novelarrow.reader.playback

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.novelarrow.reader.util.AppLogger

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ServicePlaybackStateCoordinator(
    private val service: NovelAudioPlaybackService,
    private val playerProvider: () -> ExoPlayer?,
    private val mediaSessionProvider: () -> MediaSession?,
    private val acquireWakeLock: () -> Unit,
    private val releaseWakeLock: () -> Unit,
    private val onMetadataUpdated: (MediaMetadata) -> Unit
) {

    private val TAG = "ServicePlaybackStateCoordinator"

    var currentNovelTitle: String = "BookReader"
        private set
    var currentChapterTitle: String = "No chapter loaded"
        private set
    var currentProgressText: String = "Ready"
        private set
    var currentChapterIndex: Int = 0
        private set
    var totalChaptersCount: Int = 0
        private set
    var currentParagraphIndex: Int = 0
        private set
    var totalParagraphsCount: Int = 0
        private set

    var virtualDurationMs: Long = 0L
        private set
    var virtualPositionMs: Long = 0L
        private set

    var isForegroundServiceStarted = false

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
                    isPlaying,
                    novelTitle,
                    chapterTitle,
                    paragraphProgress,
                    chapterIndex,
                    totalChapters,
                    currentParagraph,
                    totalParagraphs
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

        val (coverBitmap, coverArtworkBytes) = PlaybackNotificationBuilder.getBookCoverArtwork(service)

        val metadata = MediaMetadata.Builder()
            .setTitle(chapterTitle.ifBlank { "BookReader" })
            .setArtist(novelTitle.ifBlank { "Text-to-Speech" })
            .setAlbumTitle(novelTitle.ifBlank { "BookReader" })
            .setDisplayTitle(chapterTitle.ifBlank { "BookReader" })
            .setSubtitle(paragraphProgress)
            .setArtworkData(coverArtworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        onMetadataUpdated(metadata)

        val mediaItem = MediaItem.Builder()
            .setMediaId("novel_tts_${novelTitle.hashCode()}_${chapterTitle.hashCode()}")
            .setMediaMetadata(metadata)
            .build()

        val exoPlayer = playerProvider()
        try {
            if (AudioPlaybackCoordinator.playbackProgressState.value.source != PlaybackSource.AUDIO_FILE) {
                exoPlayer?.setMediaItem(mediaItem, false)
            }
            exoPlayer?.setPlaylistMetadata(metadata)
        } catch (_: Exception) {}

        val notification = PlaybackNotificationBuilder.buildForegroundNotification(
            context = service,
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            progressText = paragraphProgress,
            isPlaying = isPlaying,
            coverBitmap = coverBitmap,
            mediaSession = mediaSessionProvider(),
            totalParagraphsCount = totalParagraphsCount,
            currentParagraphIndex = currentParagraphIndex
        )
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isPlaying) {
            acquireWakeLock()

            if (exoPlayer?.playbackState == Player.STATE_IDLE) {
                exoPlayer?.prepare()
            }
            if (AudioPlaybackCoordinator.playbackProgressState.value.source != PlaybackSource.AUDIO_FILE) {
                try {
                    exoPlayer?.seekTo(virtualPositionMs)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error seeking virtual position: ${e.message}", e)
                }
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
                        service.startForeground(NovelAudioPlaybackService.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        service.startForeground(NovelAudioPlaybackService.NOTIFICATION_ID, notification)
                    }
                    isForegroundServiceStarted = true
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error starting foreground service: ${e.message}", e)
                }
            } else {
                manager.notify(NovelAudioPlaybackService.NOTIFICATION_ID, notification)
            }
        } else {
            releaseWakeLock()

            if (AudioPlaybackCoordinator.playbackProgressState.value.source != PlaybackSource.AUDIO_FILE) {
                try {
                    exoPlayer?.seekTo(virtualPositionMs)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error seeking virtual position: ${e.message}", e)
                }
            }
            if (exoPlayer?.playWhenReady != false) {
                exoPlayer?.playWhenReady = false
            }
            if (exoPlayer?.isPlaying == true) {
                exoPlayer?.pause()
            }

            if (!isForegroundServiceStarted) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        service.startForeground(NovelAudioPlaybackService.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        service.startForeground(NovelAudioPlaybackService.NOTIFICATION_ID, notification)
                    }
                    isForegroundServiceStarted = true
                } catch (e: Exception) {
                    manager.notify(NovelAudioPlaybackService.NOTIFICATION_ID, notification)
                }
            } else {
                manager.notify(NovelAudioPlaybackService.NOTIFICATION_ID, notification)
            }
        }
    }

    fun stopServicePlayback() {
        releaseWakeLock()
        val exoPlayer = playerProvider()
        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.pause()
        }
        if (isForegroundServiceStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                service.stopForeground(true)
            }
            isForegroundServiceStarted = false
        }
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NovelAudioPlaybackService.NOTIFICATION_ID)
        service.stopSelf()
    }
}
