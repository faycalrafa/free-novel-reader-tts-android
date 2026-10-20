package com.novelarrow.reader.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile service allowing users to tap the tile in their
 * system quick settings / notification shade to resume novel reading and playback
 * where they last left off.
 */
class BookReaderTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObservationJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        AudioPlaybackCoordinator.initialize(applicationContext)
        updateTileState()

        stateObservationJob?.cancel()
        stateObservationJob = serviceScope.launch {
            AudioPlaybackCoordinator.isPlayingState.collectLatest {
                updateTileState()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateObservationJob?.cancel()
        stateObservationJob = null
    }

    override fun onClick() {
        super.onClick()
        AppLogger.i(TAG, "Quick Settings Tile tapped: triggering TTS playback directly in background")

        val application = applicationContext
        AudioPlaybackCoordinator.initialize(application)

        if (AudioPlaybackCoordinator.isPlayingState.value) {
            AudioPlaybackCoordinator.pause()
        } else {
            AudioPlaybackCoordinator.restoreLastHearingSession(autoPlay = true)
        }

        updateTileState()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isPlaying = AudioPlaybackCoordinator.isPlayingState.value
        tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isPlaying) "Playing: BookReader" else "Resume: BookReader"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isPlaying) "Playing now" else "Tap to resume"
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "BookReaderTile"
    }
}
