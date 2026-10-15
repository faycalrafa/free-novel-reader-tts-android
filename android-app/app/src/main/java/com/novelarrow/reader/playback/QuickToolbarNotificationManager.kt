package com.novelarrow.reader.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.R
import com.novelarrow.reader.data.repository.SettingsRepository
import java.util.Locale

object QuickToolbarNotificationManager {

    const val CHANNEL_ID = "novel_quick_toolbar_channel"
    const val NOTIFICATION_ID = 2001

    fun updateToolbar(context: Context) {
        val appContext = context.applicationContext
        val settingsRepo = SettingsRepository(appContext)
        val toolbarSettings = settingsRepo.settingsFlow.value.quickToolbarSettings

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!toolbarSettings.enabled) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        createNotificationChannel(notificationManager)

        val isPlaying = AudioPlaybackCoordinator.isPlayingState.value
        val progress = AudioPlaybackCoordinator.playbackProgressState.value
        val currentSpeed = AudioPlaybackCoordinator.currentSpeechRateState.value
        val speedText = formatSpeed(currentSpeed)

        val novelTitle = if (progress.novelTitle.isNotBlank() && progress.novelTitle != "NovelArrow Reader") {
            progress.novelTitle
        } else {
            "NovelArrow Reader"
        }

        val chapterStatus = if (progress.chapterTitle.isNotBlank() && progress.chapterTitle != "No chapter loaded") {
            if (progress.totalParagraphs > 0) {
                "${progress.chapterTitle} • ¶ ${progress.currentParagraphIndex + 1}/${progress.totalParagraphs}"
            } else {
                progress.chapterTitle
            }
        } else {
            "Tap Play to Resume Reading"
        }

        // 1. PendingIntents for actions
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            appContext, 10, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = createActionPendingIntent(appContext, 11, QuickToolbarReceiver.ACTION_TOOLBAR_PLAY_PAUSE)
        val speedPendingIntent = createActionPendingIntent(appContext, 12, QuickToolbarReceiver.ACTION_TOOLBAR_CYCLE_SPEED)
        val nextChapPendingIntent = createActionPendingIntent(appContext, 13, QuickToolbarReceiver.ACTION_TOOLBAR_NEXT_CHAP)
        val prevChapPendingIntent = createActionPendingIntent(appContext, 14, QuickToolbarReceiver.ACTION_TOOLBAR_PREV_CHAP)
        val siteNextPendingIntent = createActionPendingIntent(appContext, 15, QuickToolbarReceiver.ACTION_TOOLBAR_SITE_NEXT)
        val closePendingIntent = createActionPendingIntent(appContext, 16, QuickToolbarReceiver.ACTION_TOOLBAR_CLOSE)

        // 2. Compact View RemoteViews
        val compactView = RemoteViews(appContext.packageName, R.layout.notification_quick_toolbar).apply {
            setTextViewText(R.id.tv_novel_title, novelTitle)
            setTextViewText(R.id.tv_chapter_status, chapterStatus)
            setTextViewText(R.id.btn_speed, speedText)

            setImageViewResource(
                R.id.btn_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            // Button visibility based on user preferences
            setViewVisibility(R.id.btn_speed, if (toolbarSettings.showSpeedButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_prev_chap, if (toolbarSettings.showPrevChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_play_pause, if (toolbarSettings.showPlayPauseButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_next_chap, if (toolbarSettings.showNextChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_site_next, if (toolbarSettings.showSiteNextButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_close, if (toolbarSettings.showCloseButton) View.VISIBLE else View.GONE)

            setOnClickPendingIntent(R.id.layout_toolbar_text, openAppPendingIntent)
            setOnClickPendingIntent(R.id.iv_toolbar_icon, openAppPendingIntent)
            setOnClickPendingIntent(R.id.btn_speed, speedPendingIntent)
            setOnClickPendingIntent(R.id.btn_play_pause, playPausePendingIntent)
            setOnClickPendingIntent(R.id.btn_prev_chap, prevChapPendingIntent)
            setOnClickPendingIntent(R.id.btn_next_chap, nextChapPendingIntent)
            setOnClickPendingIntent(R.id.btn_site_next, siteNextPendingIntent)
            setOnClickPendingIntent(R.id.btn_close, closePendingIntent)
        }

        // 3. Expanded View RemoteViews
        val expandedView = RemoteViews(appContext.packageName, R.layout.notification_quick_toolbar_expanded).apply {
            setTextViewText(R.id.tv_novel_title_exp, novelTitle)
            setTextViewText(R.id.tv_chapter_status_exp, chapterStatus)
            setTextViewText(R.id.btn_speed_exp, speedText)

            setImageViewResource(
                R.id.btn_play_pause_exp,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            setViewVisibility(R.id.btn_speed_exp, if (toolbarSettings.showSpeedButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_prev_chap_exp, if (toolbarSettings.showPrevChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_play_pause_exp, if (toolbarSettings.showPlayPauseButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_next_chap_exp, if (toolbarSettings.showNextChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_site_next_exp, if (toolbarSettings.showSiteNextButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_close_exp, if (toolbarSettings.showCloseButton) View.VISIBLE else View.GONE)

            setOnClickPendingIntent(R.id.tv_novel_title_exp, openAppPendingIntent)
            setOnClickPendingIntent(R.id.tv_chapter_status_exp, openAppPendingIntent)
            setOnClickPendingIntent(R.id.iv_toolbar_icon_exp, openAppPendingIntent)
            setOnClickPendingIntent(R.id.btn_speed_exp, speedPendingIntent)
            setOnClickPendingIntent(R.id.btn_play_pause_exp, playPausePendingIntent)
            setOnClickPendingIntent(R.id.btn_prev_chap_exp, prevChapPendingIntent)
            setOnClickPendingIntent(R.id.btn_next_chap_exp, nextChapPendingIntent)
            setOnClickPendingIntent(R.id.btn_site_next_exp, siteNextPendingIntent)
            setOnClickPendingIntent(R.id.btn_close_exp, closePendingIntent)
        }

        // 4. Construct Notification
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true) // Pinned toolbar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissToolbar(context: Context) {
        val notificationManager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createActionPendingIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, QuickToolbarReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatSpeed(rate: Float): String {
        return if (rate == rate.toInt().toFloat()) {
            "${rate.toInt()}.0x"
        } else {
            String.format(Locale.US, "%.2fx", rate).trimEnd('0').let {
                if (it.endsWith(".")) "${it}0x" else "${it}x"
            }
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Quick Reading Toolbar"
            val desc = "Persistent reading controls toolbar in notification drawer"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
