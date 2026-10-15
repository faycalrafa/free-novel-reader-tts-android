package com.novelarrow.reader.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AppError(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val stackTrace: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AppErrorBus {

    private val _currentError = MutableStateFlow<AppError?>(null)
    val currentError: StateFlow<AppError?> = _currentError.asStateFlow()

    fun postError(title: String, message: String, throwable: Throwable? = null) {
        val stack = if (throwable != null) {
            AppLogger.getStackTraceString(throwable)
        } else {
            message
        }
        AppLogger.e("AppErrorBus", "[$title] $message", throwable)
        _currentError.value = AppError(
            title = title,
            message = message,
            stackTrace = stack
        )
    }

    fun dismiss() {
        clear()
    }

    fun clear() {
        _currentError.value = null
    }
}
