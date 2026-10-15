package com.novelarrow.reader.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class QuickToolbarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AudioPlaybackCoordinator.initialize(context.applicationContext)

        when (action) {
            ACTION_TOOLBAR_PLAY_PAUSE -> {
                if (AudioPlaybackCoordinator.isPlayingState.value) {
                    AudioPlaybackCoordinator.pause()
                } else {
                    val progress = AudioPlaybackCoordinator.playbackProgressState.value
                    if (progress.source == PlaybackSource.NONE) {
                        AudioPlaybackCoordinator.restoreLastHearingSession(autoPlay = true)
                    } else {
                        AudioPlaybackCoordinator.play()
                    }
                }
            }
            ACTION_TOOLBAR_CYCLE_SPEED -> {
                AudioPlaybackCoordinator.cycleSpeechRate()
                QuickToolbarNotificationManager.updateToolbar(context)
            }
            ACTION_TOOLBAR_NEXT_CHAP -> {
                AudioPlaybackCoordinator.nextChapter()
            }
            ACTION_TOOLBAR_PREV_CHAP -> {
                AudioPlaybackCoordinator.previousChapter()
            }
            ACTION_TOOLBAR_SITE_NEXT -> {
                AudioPlaybackCoordinator.triggerSiteNextChapter()
            }
            ACTION_TOOLBAR_CLOSE -> {
                QuickToolbarNotificationManager.dismissToolbar(context)
            }
        }
    }

    companion object {
        const val ACTION_TOOLBAR_PLAY_PAUSE = "com.novelarrow.reader.TOOLBAR_PLAY_PAUSE"
        const val ACTION_TOOLBAR_CYCLE_SPEED = "com.novelarrow.reader.TOOLBAR_CYCLE_SPEED"
        const val ACTION_TOOLBAR_NEXT_CHAP = "com.novelarrow.reader.TOOLBAR_NEXT_CHAP"
        const val ACTION_TOOLBAR_PREV_CHAP = "com.novelarrow.reader.TOOLBAR_PREV_CHAP"
        const val ACTION_TOOLBAR_SITE_NEXT = "com.novelarrow.reader.TOOLBAR_SITE_NEXT"
        const val ACTION_TOOLBAR_CLOSE = "com.novelarrow.reader.TOOLBAR_CLOSE"
    }
}
