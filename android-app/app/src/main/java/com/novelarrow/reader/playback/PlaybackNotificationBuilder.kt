package com.novelarrow.reader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.R
import com.novelarrow.reader.util.AppLogger
import java.io.ByteArrayOutputStream

internal object PlaybackNotificationBuilder {

    private const val TAG = "PlaybackNotificationBuilder"

    @Volatile
    private var cachedBookCoverBitmap: Bitmap? = null
    @Volatile
    private var cachedBookArtworkBytes: ByteArray? = null

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Novel Audio Reader"
            val descriptionText = "Playback controls for novel audio text-to-speech"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NovelAudioPlaybackService.CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getBookCoverArtwork(context: Context): Pair<Bitmap, ByteArray> {
        cachedBookCoverBitmap?.let { bmp ->
            cachedBookArtworkBytes?.let { bytes ->
                return Pair(bmp, bytes)
            }
        }
        val targetSize = 512
        var bitmap: Bitmap? = null
        try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val loaded = BitmapFactory.decodeResource(context.resources, R.drawable.notification_media_cover, opts)
            if (loaded != null) {
                bitmap = if (loaded.width != targetSize || loaded.height != targetSize) {
                    Bitmap.createScaledBitmap(loaded, targetSize, targetSize, true)
                } else {
                    loaded
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to decode notification_media_cover resource: ${e.message}", e)
        }

        val finalBitmap = bitmap ?: Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(24, 32, 54))
        }

        val stream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        val bytes = stream.toByteArray()

        cachedBookCoverBitmap = finalBitmap
        cachedBookArtworkBytes = bytes
        return Pair(finalBitmap, bytes)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun buildForegroundNotification(
        context: Context,
        novelTitle: String,
        chapterTitle: String,
        progressText: String,
        isPlaying: Boolean,
        coverBitmap: Bitmap?,
        mediaSession: MediaSession?,
        totalParagraphsCount: Int,
        currentParagraphIndex: Int
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevChapPendingIntent = PendingIntent.getService(
            context, 1,
            Intent(context, NovelAudioPlaybackService::class.java).apply { action = NovelAudioPlaybackService.ACTION_PREV_CHAP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevParaPendingIntent = PendingIntent.getService(
            context, 2,
            Intent(context, NovelAudioPlaybackService::class.java).apply { action = NovelAudioPlaybackService.ACTION_PREV_PARA },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            context, if (isPlaying) 3 else 33,
            Intent(context, NovelAudioPlaybackService::class.java).apply {
                action = if (isPlaying) NovelAudioPlaybackService.ACTION_PAUSE else NovelAudioPlaybackService.ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextParaPendingIntent = PendingIntent.getService(
            context, 4,
            Intent(context, NovelAudioPlaybackService::class.java).apply { action = NovelAudioPlaybackService.ACTION_NEXT_PARA },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextChapPendingIntent = PendingIntent.getService(
            context, 5,
            Intent(context, NovelAudioPlaybackService::class.java).apply { action = NovelAudioPlaybackService.ACTION_NEXT_CHAP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            prevChapPendingIntent
        ).build()

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

        val forwardAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "Forward",
            nextParaPendingIntent
        ).build()

        val builder = NotificationCompat.Builder(context, NovelAudioPlaybackService.CHANNEL_ID)
            .setContentTitle(chapterTitle.ifBlank { "BookReader" })
            .setContentText(novelTitle.ifBlank { "BookReader" })
            .setSubText(progressText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        val cssSelectorIntent = Intent(context, MainActivity::class.java).apply {
            action = NovelAudioPlaybackService.ACTION_EDIT_CSS_SELECTOR
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NovelAudioPlaybackService.EXTRA_OPEN_CSS_SELECTOR, true)
        }
        val cssSelectorPendingIntent = PendingIntent.getActivity(
            context, 99,
            cssSelectorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setProgress(
            totalParagraphsCount.coerceAtLeast(1),
            (currentParagraphIndex + 1).coerceIn(0, totalParagraphsCount.coerceAtLeast(1)),
            false
        )
        .addAction(prevAction)
        .addAction(playPauseAction)
        .addAction(forwardAction)
        .addAction(android.R.drawable.ic_media_rew, "Prev ¶", prevParaPendingIntent)
        .addAction(android.R.drawable.ic_media_ff, "Next Chap", nextChapPendingIntent)
        .addAction(android.R.drawable.ic_menu_edit, "CSS Selector", cssSelectorPendingIntent)

        mediaSession?.let { session ->
            val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
            builder.setStyle(mediaStyle)
        }

        return builder.build()
    }
}
