package com.novelarrow.reader.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import com.novelarrow.reader.util.AppLogger
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ExoAudioFileDelegate(
    private val context: Context,
    private val playerProvider: () -> ExoPlayer?,
    private val silenceSourceProvider: () -> SilenceMediaSource?,
    private val acquireWakeLock: () -> Unit,
    private val releaseWakeLock: () -> Unit,
    private val onMetadataUpdated: (MediaMetadata) -> Unit
) {

    private val TAG = "ExoAudioFileDelegate"
    var currentAudioFile: File? = null
        private set

    fun playAudioFile(file: File) {
        currentAudioFile = file
        val player = playerProvider() ?: return
        try {
            val uri = Uri.fromFile(file)
            val (_, coverArtworkBytes) = PlaybackNotificationBuilder.getBookCoverArtwork(context)
            val albumName = file.parentFile?.name ?: "Audio Studio"
            val metadata = MediaMetadata.Builder()
                .setTitle(file.nameWithoutExtension)
                .setArtist(albumName)
                .setAlbumTitle(albumName)
                .setDisplayTitle(file.nameWithoutExtension)
                .setSubtitle(file.name)
                .setArtworkData(coverArtworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            onMetadataUpdated(metadata)

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId("audio_file_${file.absolutePath.hashCode()}")
                .setMediaMetadata(metadata)
                .build()
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.play()
            acquireWakeLock()
            AppLogger.i(TAG, "ExoPlayer playing audio file: ${file.name}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error playing audio file ${file.name}", e)
        }
    }

    fun resumeAudioFile() {
        val player = playerProvider() ?: return
        try {
            acquireWakeLock()
            player.playWhenReady = true
            player.play()
            AppLogger.d(TAG, "Resumed audio file playback")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resuming audio file", e)
        }
    }

    fun pauseAudioFile() {
        val player = playerProvider() ?: return
        try {
            releaseWakeLock()
            player.playWhenReady = false
            player.pause()
            AppLogger.d(TAG, "Paused audio file playback")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error pausing audio file", e)
        }
    }

    fun stopAudioFile() {
        val player = playerProvider() ?: return
        try {
            releaseWakeLock()
            currentAudioFile = null
            player.stop()
            silenceSourceProvider()?.let {
                player.setMediaSource(it)
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.prepare()
            }
            AppLogger.d(TAG, "Stopped audio file playback and restored silence source")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error stopping audio file", e)
        }
    }

    fun getAudioFilePositionMs(): Long {
        val player = playerProvider() ?: return 0L
        return try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                player.currentPosition
            } else {
                0L
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error reading audio file position: ${e.message}", e)
            0L
        }
    }

    fun getAudioFileDurationMs(): Long {
        val player = playerProvider() ?: return 0L
        return try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                val dur = player.duration
                if (dur > 0L) dur else 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error reading audio file duration: ${e.message}", e)
            0L
        }
    }

    fun seekAudioFile(positionMs: Long) {
        val player = playerProvider() ?: return
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                player.seekTo(positionMs.coerceAtLeast(0L))
            } else {
                Handler(Looper.getMainLooper()).post {
                    try {
                        player.seekTo(positionMs.coerceAtLeast(0L))
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error seeking audio file on main looper", e)
                    }
                }
            }
            AppLogger.d(TAG, "Seeked audio file to $positionMs ms")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error seeking audio file to $positionMs ms", e)
        }
    }
}
