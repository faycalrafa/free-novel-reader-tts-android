package com.novelarrow.reader.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TelephonyAudioInterruptionCoordinatorTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var isPlaying = false
    private var pauseCalled = false
    private var resumeCalled = false

    private lateinit var coordinator: TelephonyAudioInterruptionCoordinator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        isPlaying = false
        pauseCalled = false
        resumeCalled = false

        coordinator = TelephonyAudioInterruptionCoordinator(
            context = context,
            scope = testScope,
            isPlayingProvider = { isPlaying },
            onPauseRequested = {
                pauseCalled = true
                isPlaying = false
            },
            onResumeRequested = {
                resumeCalled = true
                isPlaying = true
            }
        )
    }

    @Test
    fun testCallInterruption_pausesWhenPlaying_andResumesWhenCallEnds() = testScope.runTest {
        // Given playing TTS
        isPlaying = true

        // When call becomes active
        coordinator.simulateCallInterruption(active = true)

        // Then playback is paused and pausedForCall is true
        assertTrue(pauseCalled)
        assertFalse(isPlaying)
        assertTrue(coordinator.pausedForCall)

        // When call ends
        coordinator.simulateCallInterruption(active = false)

        // Then playback resumes
        assertTrue(resumeCalled)
        assertTrue(isPlaying)
        assertFalse(coordinator.pausedForCall)
    }

    @Test
    fun testCallInterruption_doesNotPauseOrResume_whenNotPlayingInitially() = testScope.runTest {
        // Given not playing
        isPlaying = false

        // When call arrives
        coordinator.simulateCallInterruption(active = true)

        // Then pause is not triggered and pausedForCall is false
        assertFalse(pauseCalled)
        assertFalse(coordinator.pausedForCall)

        // When call ends
        coordinator.simulateCallInterruption(active = false)
        assertFalse(resumeCalled)
        assertFalse(isPlaying)
    }

    @Test
    fun testUserManualPauseDuringCall_preventsAutoResume() = testScope.runTest {
        // Given playing TTS
        isPlaying = true

        // Call arrives
        coordinator.simulateCallInterruption(active = true)
        assertTrue(coordinator.pausedForCall)

        // User manually cancels or pauses during call
        coordinator.onUserManuallyPausedOrStopped()
        assertFalse(coordinator.pausedForCall)

        // Call ends
        coordinator.simulateCallInterruption(active = false)

        // Resumption should NOT happen because user explicitly cancelled
        assertFalse(resumeCalled)
        assertFalse(isPlaying)
    }
}
