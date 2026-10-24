package com.novelarrow.reader.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.novelarrow.reader.data.model.HardwareButtonSettings
import com.novelarrow.reader.util.AppLogger

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object MediaSessionPlayerDelegate {

    private const val TAG = "MediaSessionPlayerDelegate"

    fun createPlayerListener(
        onFocusOrNoisyPause: () -> Unit,
        onAudioFileCompleted: () -> Unit,
        onExoError: (PlaybackException) -> Unit
    ): Player.Listener {
        return object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    when (reason) {
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                            onFocusOrNoisyPause()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                        onAudioFileCompleted()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                onExoError(error)
                if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                    onAudioFileCompleted()
                }
            }
        }
    }

    fun createForwardingPlayer(
        context: Context,
        basePlayer: Player,
        currentMetadataProvider: () -> MediaMetadata,
        virtualDurationMsProvider: () -> Long,
        virtualPositionMsProvider: () -> Long,
        onSeekToVirtual: (Long) -> Unit,
        dispatchPlay: () -> Unit,
        dispatchPause: () -> Unit,
        dispatchStop: () -> Unit,
        dispatchNextChap: () -> Unit,
        dispatchPrevChap: () -> Unit
    ): ForwardingPlayer {
        return object : ForwardingPlayer(basePlayer) {
            override fun isPlaying(): Boolean = if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                basePlayer.isPlaying
            } else {
                AudioPlaybackCoordinator.isPlayingState.value
            }

            override fun getPlayWhenReady(): Boolean = if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                basePlayer.playWhenReady
            } else {
                AudioPlaybackCoordinator.isPlayingState.value
            }

            override fun getPlaybackState(): Int = if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                super.getPlaybackState()
            } else if (AudioPlaybackCoordinator.isPlayingState.value) {
                Player.STATE_READY
            } else {
                super.getPlaybackState()
            }

            override fun getCurrentMediaItem(): MediaItem? {
                return MediaItem.Builder()
                    .setMediaId("novel_tts_item")
                    .setMediaMetadata(currentMetadataProvider())
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

            override fun getDuration(): Long = if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                super.getDuration()
            } else {
                val vDur = virtualDurationMsProvider()
                if (vDur > 0) vDur else super.getDuration()
            }

            override fun getCurrentPosition(): Long = if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                super.getCurrentPosition()
            } else {
                val vDur = virtualDurationMsProvider()
                if (vDur > 0) virtualPositionMsProvider() else super.getCurrentPosition()
            }

            override fun seekTo(positionMs: Long) {
                if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                    super.seekTo(positionMs)
                } else {
                    onSeekToVirtual(positionMs)
                }
            }

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                if (AudioPlaybackCoordinator.playbackProgressState.value.source == PlaybackSource.AUDIO_FILE) {
                    super.seekTo(mediaItemIndex, positionMs)
                } else {
                    onSeekToVirtual(positionMs)
                }
            }

            override fun isCurrentMediaItemSeekable(): Boolean = true
            override fun isCurrentMediaItemDynamic(): Boolean = false

            override fun stop() {
                super.stop()
                dispatchStop()
            }

            override fun getDeviceInfo(): DeviceInfo {
                val hwSettings = AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
                if (hwSettings != null && hwSettings.enabled) {
                    return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                        .setMinVolume(0)
                        .setMaxVolume(15)
                        .build()
                }
                return super.getDeviceInfo()
            }

            @Suppress("DEPRECATION")
            override fun increaseDeviceVolume() {
                val hwSettings = AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
                    ?: HardwareButtonSettings()
                HardwareButtonCoordinator.onDeviceVolumeAdjusted(context, isUp = true, hwSettings)
            }

            override fun increaseDeviceVolume(flags: Int) {
                increaseDeviceVolume()
            }

            @Suppress("DEPRECATION")
            override fun decreaseDeviceVolume() {
                val hwSettings = AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
                    ?: HardwareButtonSettings()
                HardwareButtonCoordinator.onDeviceVolumeAdjusted(context, isUp = false, hwSettings)
            }

            override fun decreaseDeviceVolume(flags: Int) {
                decreaseDeviceVolume()
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
                    .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                    .add(Player.COMMAND_ADJUST_DEVICE_VOLUME)
                    .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                    .add(Player.COMMAND_SET_DEVICE_VOLUME)
                    .add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
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
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_ADJUST_DEVICE_VOLUME,
                    Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                    Player.COMMAND_SET_DEVICE_VOLUME,
                    Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                    Player.COMMAND_STOP -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getMediaMetadata(): MediaMetadata = currentMetadataProvider()
            override fun getPlaylistMetadata(): MediaMetadata = currentMetadataProvider()
        }
    }

    fun createSessionCallback(
        context: Context,
        dispatchPlay: () -> Unit,
        dispatchPause: () -> Unit,
        dispatchPrevPara: () -> Unit,
        dispatchNextPara: () -> Unit,
        dispatchPrevChap: () -> Unit,
        dispatchNextChap: () -> Unit,
        dispatchStop: () -> Unit
    ): MediaSession.Callback {
        return object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_PLAY, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_PAUSE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_PLAY_PAUSE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_PREV_PARA, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_NEXT_PARA, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_PREV_CHAP, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_NEXT_CHAP, android.os.Bundle.EMPTY))
                    .add(SessionCommand(NovelAudioPlaybackService.ACTION_STOP, android.os.Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null) {
                    val hwSettings = AudioPlaybackCoordinator.settingsRepository?.getSettings()?.hardwareButtonSettings
                    if (hwSettings != null && hwSettings.enabled) {
                        if (HardwareButtonCoordinator.handleActivityKeyEvent(context, keyEvent, hwSettings)) {
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: android.os.Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    NovelAudioPlaybackService.ACTION_PLAY -> dispatchPlay()
                    NovelAudioPlaybackService.ACTION_PAUSE -> dispatchPause()
                    NovelAudioPlaybackService.ACTION_PLAY_PAUSE -> {
                        if (AudioPlaybackCoordinator.isPlayingState.value) dispatchPause() else dispatchPlay()
                    }
                    NovelAudioPlaybackService.ACTION_PREV_PARA -> dispatchPrevPara()
                    NovelAudioPlaybackService.ACTION_NEXT_PARA -> dispatchNextPara()
                    NovelAudioPlaybackService.ACTION_PREV_CHAP -> dispatchPrevChap()
                    NovelAudioPlaybackService.ACTION_NEXT_CHAP -> dispatchNextChap()
                    NovelAudioPlaybackService.ACTION_STOP -> dispatchStop()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
    }
}
