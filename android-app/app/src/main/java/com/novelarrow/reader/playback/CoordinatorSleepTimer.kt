package com.novelarrow.reader.playback

import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class CoordinatorSleepTimer(
    private val scope: CoroutineScope,
    private val settingsRepositoryProvider: () -> SettingsRepository?,
    private val isPlayingProvider: () -> Boolean,
    private val onTimerExpired: () -> Unit
) {

    private val TAG = "CoordinatorSleepTimer"

    private val _sleepTimerMinutesState = MutableStateFlow(0)
    val sleepTimerMinutesState: StateFlow<Int> = _sleepTimerMinutesState.asStateFlow()

    private val _sleepTimerRemainingSecondsState = MutableStateFlow(0)
    val sleepTimerRemainingSecondsState: StateFlow<Int> = _sleepTimerRemainingSecondsState.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutesState.value = minutes
        val totalSecs = minutes * 60
        _sleepTimerRemainingSecondsState.value = totalSecs
        scope.launch(Dispatchers.IO) {
            settingsRepositoryProvider()?.setSleepTimerMinutes(minutes)
        }
        sleepTimerJob?.cancel()
        if (minutes > 0 && isPlayingProvider()) {
            startSleepTimerCountdown()
        }
    }

    fun cycleSleepTimer() {
        val presets = listOf(0, 15, 30, 45, 60)
        val current = _sleepTimerMinutesState.value
        val currentIndex = presets.indexOf(current)
        val nextMinutes = if (currentIndex == -1 || currentIndex == presets.lastIndex) {
            presets[0]
        } else {
            presets[currentIndex + 1]
        }
        setSleepTimer(nextMinutes)
    }

    fun formatSleepTimerForNotification(): String {
        return QuickToolbarForegroundService.formatSleepTimer(
            mins = _sleepTimerMinutesState.value,
            remainingSecs = _sleepTimerRemainingSecondsState.value
        )
    }

    fun startSleepTimerCountdown() {
        sleepTimerJob?.cancel()
        val totalSecs = if (_sleepTimerRemainingSecondsState.value > 0) {
            _sleepTimerRemainingSecondsState.value
        } else {
            _sleepTimerMinutesState.value * 60
        }
        _sleepTimerRemainingSecondsState.value = totalSecs

        sleepTimerJob = scope.launch {
            var remaining = totalSecs
            while (remaining > 0 && isPlayingProvider()) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSecondsState.value = remaining
            }
            if (remaining <= 0 && isPlayingProvider()) {
                AppLogger.i(TAG, "Sleep timer reached zero, playing timer stop sound cue and pausing playback")
                AudioCueManager.playTimerStop()
                onTimerExpired()
                _sleepTimerRemainingSecondsState.value = 0
            }
        }
    }

    fun cancel(resetRemaining: Boolean = false) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (resetRemaining) {
            _sleepTimerRemainingSecondsState.value = 0
        }
    }
}
