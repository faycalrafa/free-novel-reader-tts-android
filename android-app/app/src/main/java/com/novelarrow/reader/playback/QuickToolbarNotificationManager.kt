package com.novelarrow.reader.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppErrorBus
import com.novelarrow.reader.util.AppLogger

object QuickToolbarNotificationManager {

    private const val TAG = "QuickToolbarMgr"

    fun updateToolbar(context: Context) {
        val appContext = context.applicationContext
        val settingsRepo = SettingsRepository(appContext)
        val toolbarSettings = settingsRepo.settingsFlow.value.quickToolbarSettings

        if (!toolbarSettings.enabled) {
            AppLogger.d(TAG, "Quick toolbar disabled in settings, stopping foreground service")
            dismissToolbar(appContext)
            return
        }

        try {
            val intent = Intent(appContext, QuickToolbarForegroundService::class.java).apply {
                action = QuickToolbarForegroundService.ACTION_START_OR_UPDATE
            }
            ContextCompat.startForegroundService(appContext, intent)
            AppLogger.d(TAG, "Dispatched startForegroundService to QuickToolbarForegroundService")
        } catch (e: Exception) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                AppLogger.w(TAG, "Foreground service start not allowed in current state: ${e.message}")
            } else {
                AppLogger.e(TAG, "Error starting QuickToolbarForegroundService: ${e.message}", e)
                AppErrorBus.postError(
                    title = "Notification Toolbar Error",
                    message = "Could not start persistent toolbar service: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    fun dismissToolbar(context: Context) {
        val appContext = context.applicationContext
        try {
            val intent = Intent(appContext, QuickToolbarForegroundService::class.java).apply {
                action = QuickToolbarForegroundService.ACTION_STOP
            }
            appContext.stopService(intent)
            AppLogger.d(TAG, "Requested stopService on QuickToolbarForegroundService")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping QuickToolbarForegroundService: ${e.message}")
        }
    }
}
