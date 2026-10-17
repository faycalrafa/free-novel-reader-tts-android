package com.novelarrow.reader.playback.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TtsUiState {
    data object Idle : TtsUiState
    data object Initializing : TtsUiState
    data object Speaking : TtsUiState
    data class Error(val message: String) : TtsUiState
}

class TtsViewModel(application: Application) : AndroidViewModel(application) {

    private val ttsEngine = LocalTtsEngine(
        context = application.applicationContext,
        numThreads = 4
    )

    private val _uiState = MutableStateFlow<TtsUiState>(TtsUiState.Initializing)
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private var speechJob: Job? = null

    init {
        // Warm up model extraction and native allocation on startup
        viewModelScope.launch {
            ttsEngine.initialize()
                .onSuccess {
                    _uiState.value = TtsUiState.Idle
                }
                .onFailure { error ->
                    _uiState.value = TtsUiState.Error(error.localizedMessage ?: "Failed to init TTS")
                }
        }
    }

    fun speak(text: String, speed: Float = 1.0f) {
        // Cancel any in-flight synthesis or playback before starting a new utterance
        speechJob?.cancel()
        ttsEngine.stopPlayback()

        speechJob = viewModelScope.launch {
            _uiState.value = TtsUiState.Speaking
            runCatching {
                ttsEngine.speak(text = text, speed = speed)
            }.onSuccess {
                _uiState.value = TtsUiState.Idle
            }.onFailure { error ->
                if (error !is CancellationException) {
                    AppLogger.e("TtsViewModel", "TTS playback failed: ${error.message}", error)
                    _uiState.value = TtsUiState.Error(error.localizedMessage ?: "TTS playback failed")
                }
            }
        }
    }

    fun stop() {
        speechJob?.cancel()
        ttsEngine.stopPlayback()
        _uiState.value = TtsUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.release()
    }
}
