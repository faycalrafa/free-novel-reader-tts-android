package com.novelarrow.reader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.R
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppErrorBus
import com.novelarrow.reader.util.AppLogger
import java.util.Locale

class QuickToolbarForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "QuickToolbarForegroundService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_OR_UPDATE
        AppLogger.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_STOP -> {
                AppLogger.i(TAG, "Stopping foreground toolbar service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTORE_TOOLBAR -> {
                // User swiped toolbar on Android 14+; re-check if user wanted it enabled
                val settingsRepo = SettingsRepository(applicationContext)
                val isEnabled = settingsRepo.settingsFlow.value.quickToolbarSettings.enabled
                if (isEnabled) {
                    AppLogger.d(TAG, "Restoring toolbar notification after swipe-dismiss on Android 14+")
                    updateForegroundNotification()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                updateForegroundNotification()
            }
        }

        // START_STICKY tells Android to resurrect this service if killed by memory pressure
        return START_STICKY
    }

    private fun updateForegroundNotification() {
        try {
            val settingsRepo = SettingsRepository(applicationContext)
            val toolbarSettings = settingsRepo.settingsFlow.value.quickToolbarSettings

            if (!toolbarSettings.enabled) {
                AppLogger.d(TAG, "Toolbar disabled in preferences, stopping service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            val notification = buildToolbarNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            AppLogger.d(TAG, "startForeground called successfully with NOTIFICATION_ID=$NOTIFICATION_ID")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start or update foreground toolbar: ${e.message}", e)
            AppErrorBus.postError(
                title = "Toolbar Notification Error",
                message = "Failed to render or start quick toolbar foreground service: ${e.message}",
                throwable = e
            )
        }
    }

    private fun buildToolbarNotification(): Notification {
        val appContext = applicationContext
        val settingsRepo = SettingsRepository(appContext)
        val toolbarSettings = settingsRepo.settingsFlow.value.quickToolbarSettings

        val isPlaying = AudioPlaybackCoordinator.isPlayingState.value
        val progress = AudioPlaybackCoordinator.playbackProgressState.value
        val currentSpeed = AudioPlaybackCoordinator.currentSpeechRateState.value
        val speedText = formatSpeed(currentSpeed)
        val sleepTimerText = AudioPlaybackCoordinator.formatSleepTimerForNotification()

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

        // 1. PendingIntents
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
        val sleepTimerPendingIntent = createActionPendingIntent(appContext, 17, QuickToolbarReceiver.ACTION_TOOLBAR_CYCLE_SLEEP_TIMER)

        // DeleteIntent for Android 14+ swipe rebound
        val deleteIntent = Intent(appContext, QuickToolbarForegroundService::class.java).apply {
            action = ACTION_RESTORE_TOOLBAR
        }
        val deletePendingIntent = PendingIntent.getService(
            appContext, 99, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Compact View RemoteViews
        val compactView = RemoteViews(appContext.packageName, R.layout.notification_quick_toolbar).apply {
            setTextViewText(R.id.tv_novel_title, novelTitle)
            setTextViewText(R.id.tv_chapter_status, chapterStatus)
            setTextViewText(R.id.btn_speed, speedText)
            setTextViewText(R.id.btn_sleep_timer, sleepTimerText)

            setImageViewResource(
                R.id.btn_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            setViewVisibility(R.id.btn_speed, if (toolbarSettings.showSpeedButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_sleep_timer, if (toolbarSettings.showSleepTimerButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_prev_chap, if (toolbarSettings.showPrevChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_play_pause, if (toolbarSettings.showPlayPauseButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_next_chap, if (toolbarSettings.showNextChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_site_next, if (toolbarSettings.showSiteNextButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_close, if (toolbarSettings.showCloseButton) View.VISIBLE else View.GONE)

            setOnClickPendingIntent(R.id.layout_toolbar_text, openAppPendingIntent)
            setOnClickPendingIntent(R.id.iv_toolbar_icon, openAppPendingIntent)
            setOnClickPendingIntent(R.id.btn_speed, speedPendingIntent)
            setOnClickPendingIntent(R.id.btn_sleep_timer, sleepTimerPendingIntent)
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
            setTextViewText(R.id.btn_sleep_timer_exp, sleepTimerText)

            setImageViewResource(
                R.id.btn_play_pause_exp,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            setViewVisibility(R.id.btn_speed_exp, if (toolbarSettings.showSpeedButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_sleep_timer_exp, if (toolbarSettings.showSleepTimerButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_prev_chap_exp, if (toolbarSettings.showPrevChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_play_pause_exp, if (toolbarSettings.showPlayPauseButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_next_chap_exp, if (toolbarSettings.showNextChapButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_site_next_exp, if (toolbarSettings.showSiteNextButton) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.btn_close_exp, if (toolbarSettings.showCloseButton) View.VISIBLE else View.GONE)

            setOnClickPendingIntent(R.id.tv_novel_title_exp, openAppPendingIntent)
            setOnClickPendingIntent(R.id.tv_chapter_status_exp, openAppPendingIntent)
            setOnClickPendingIntent(R.id.iv_toolbar_icon_exp, openAppPendingIntent)
            setOnClickPendingIntent(R.id.btn_speed_exp, speedPendingIntent)
            setOnClickPendingIntent(R.id.btn_sleep_timer_exp, sleepTimerPendingIntent)
            setOnClickPendingIntent(R.id.btn_play_pause_exp, playPausePendingIntent)
            setOnClickPendingIntent(R.id.btn_prev_chap_exp, prevChapPendingIntent)
            setOnClickPendingIntent(R.id.btn_next_chap_exp, nextChapPendingIntent)
            setOnClickPendingIntent(R.id.btn_site_next_exp, siteNextPendingIntent)
            setOnClickPendingIntent(R.id.btn_close_exp, closePendingIntent)
        }

        // Test inflate locally in this process before passing to SystemUI
        var useFallback = false
        try {
            val testParent = android.widget.FrameLayout(appContext)
            compactView.apply(appContext, testParent)
            expandedView.apply(appContext, testParent)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Local pre-inflation test failed for RemoteViews: ${t.message}", t)
            AppErrorBus.postError(
                title = "Notification Layout Error",
                message = "Quick reading toolbar failed local layout inflation: ${t.message}. Using safe fallback notification.",
                throwable = t
            )
            useFallback = true
        }

        // 4. Notification Builder
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_toolbar)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return if (!useFallback) {
            builder
                .setCustomContentView(compactView)
                .setCustomBigContentView(expandedView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()
        } else {
            builder
                .setContentTitle(novelTitle)
                .setContentText(chapterStatus)
                .addAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePendingIntent
                )
                .build()
        }
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val name = "Quick Reading Toolbar"
            val desc = "Persistent reading controls toolbar in notification drawer"
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = desc
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "QuickToolbarFGS"
        const val CHANNEL_ID = "novel_quick_toolbar_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_OR_UPDATE = "com.novelarrow.reader.ACTION_START_OR_UPDATE_TOOLBAR"
        const val ACTION_STOP = "com.novelarrow.reader.ACTION_STOP_TOOLBAR"
        const val ACTION_RESTORE_TOOLBAR = "com.novelarrow.reader.ACTION_RESTORE_TOOLBAR"
    }
}
